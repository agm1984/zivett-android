package com.zivett.app.core.realtime

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID

/// `GET /api/realtime` — the Reverb connection details (the mobile
/// mirror of the SPA's baked-in VITE_REVERB_* values). Null host means
/// "use the API host".
@Serializable
data class RealtimeConfig(val key: String, val host: String? = null, val port: Int = 443, val scheme: String = "https")

object RealtimeEndpoints {
    fun config() = ApiRequest.get<RealtimeConfig>("api/realtime", authenticated = false)

    fun channelAuth(socketId: String, channel: String) =
        ApiRequest.post<PusherProtocol.ChannelAuth, Map<String, String>>("api/broadcasting/auth", mapOf("socket_id" to socketId, "channel_name" to channel))
}

/// A live-channel handle: cancel it (or let the owning screen die) and
/// the channel unwinds; the socket itself closes when the last channel
/// goes — which is also how logout tears realtime down, since the
/// screens holding subscriptions unmount with the session.
class RealtimeSubscription internal constructor(internal val channel: String, private var client: RealtimeClient?) {
    internal val id: UUID = UUID.randomUUID()

    fun cancel() {
        client?.remove(this)
        client = null
    }
}

/// A minimal Pusher-protocol client over an OkHttp WebSocket — the
/// app-side mirror of the SPA's Echo setup (`lib/echo.js`). Lazily
/// connects on the first subscription, re-subscribes everything after a
/// drop (capped exponential backoff), naps in the background, and hands
/// each frame to the channel's handlers on the main thread. Polling in
/// the consumers stays as the fallback whenever `connected` is false.
class RealtimeClient(
    private val api: ApiClient,
    private val apiBaseUrl: String,
    private val http: OkHttpClient = OkHttpClient(),
    observeLifecycle: Boolean = true,
) {
    var connected: Boolean by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var config: RealtimeConfig? = null
    private var socket: WebSocket? = null
    private var socketId: String? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var attempts = 0
    private val handlers: MutableMap<String, MutableMap<UUID, (String, String) -> Unit>> = mutableMapOf()

    init {
        if (observeLifecycle) {
            // The socket naps in the background (Android would eventually
            // kill it anyway) and comes back with the app; consumers poll
            // while it's away.
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) { teardown(reconnect = false) }
                override fun onStart(owner: LifecycleOwner) {
                    if (handlers.isEmpty()) return
                    attempts = 0
                    connect()
                }
            })
        }
    }

    // Subscribing

    /// Listen on a private channel (pass the bare name — `conversation.7`,
    /// `App.Models.User.3`; the wire prefix is added here). Events arrive
    /// as (event name, payload JSON text) on the main thread.
    fun subscribe(channel: String, onEvent: (String, String) -> Unit): RealtimeSubscription {
        val wireName = "private-$channel"
        val subscription = RealtimeSubscription(wireName, this)
        val firstForChannel = handlers[wireName] == null

        handlers.getOrPut(wireName) { mutableMapOf() }[subscription.id] = onEvent

        val currentSocketId = socketId
        if (socket == null) {
            connect()
        } else if (firstForChannel && currentSocketId != null) {
            scope.launch { authorizeAndJoin(wireName, currentSocketId) }
        }

        return subscription
    }

    internal fun remove(subscription: RealtimeSubscription) {
        handlers[subscription.channel]?.remove(subscription.id)

        if (handlers[subscription.channel]?.isEmpty() == true) {
            handlers.remove(subscription.channel)
            send(PusherProtocol.unsubscribeFrame(subscription.channel))
        }

        if (handlers.isEmpty()) teardown(reconnect = false)
    }

    // Connection

    private fun connect() {
        if (socket != null || handlers.isEmpty()) return

        scope.launch {
            if (config == null) config = runCatching { api.send(RealtimeEndpoints.config()) }.getOrNull()
            val config = config ?: return@launch
            if (socket != null) return@launch

            val host = config.host?.takeIf { it.isNotEmpty() } ?: Uri.parse(apiBaseUrl).host ?: "localhost"
            val url = PusherProtocol.socketUrl(config.key, host, config.port, config.scheme)

            val listener = object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    scope.launch { if (socket === webSocket) handle(text) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    scope.launch { if (socket === webSocket) dropped() }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    scope.launch { if (socket === webSocket) dropped() }
                }
            }

            socket = http.newWebSocket(Request.Builder().url(url).build(), listener)
        }
    }

    private suspend fun handle(text: String) {
        val frame = PusherProtocol.decodeFrame(text) ?: return

        when (frame.event) {
            "pusher:connection_established" -> {
                val established = PusherProtocol.decodeEstablished(frame) ?: return
                socketId = established.socketId
                attempts = 0
                connected = true
                startPing(established.activityTimeout ?: 60)
                for (channel in handlers.keys.toList()) authorizeAndJoin(channel, established.socketId)
            }
            "pusher:ping" -> send(PusherProtocol.pongFrame)
            // Bad key / protocol errors aren't transient — stop trying
            // until the next foreground or fresh subscribe.
            "pusher:error" -> teardown(reconnect = false)
            "pusher_internal:subscription_succeeded", "pusher:pong" -> Unit
            else -> {
                val channel = frame.channel ?: return
                for (handler in handlers[channel]?.values?.toList() ?: emptyList()) handler(frame.event, frame.payload)
            }
        }
    }

    private suspend fun authorizeAndJoin(channel: String, socketId: String) {
        val auth = runCatching { api.send(RealtimeEndpoints.channelAuth(socketId, channel)) }.getOrNull() ?: return
        send(PusherProtocol.subscribeFrame(channel, auth.auth))
    }

    /// Keeps NAT mappings warm on quiet channels; a failed send is how
    /// half-open sockets get noticed.
    private fun startPing(seconds: Int) {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive) {
                delay(seconds * 1000L)
                if (!isActive) return@launch
                send(PusherProtocol.pingFrame)
            }
        }
    }

    private fun send(text: String) {
        socket?.send(text)
    }

    private fun dropped() {
        if (socket == null) return
        teardown(reconnect = handlers.isNotEmpty())
    }

    private fun teardown(reconnect: Boolean) {
        pingJob?.cancel(); pingJob = null
        socket?.close(1001, null)
        socket = null
        socketId = null
        connected = false

        reconnectJob?.cancel()
        if (reconnect) {
            val delayMillis = RealtimeBackoff.delayMillis(attempts)
            attempts += 1
            reconnectJob = scope.launch {
                delay(delayMillis)
                if (isActive) connect()
            }
        }
    }
}
