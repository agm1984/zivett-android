package com.zivett.app.core.realtime

import com.zivett.app.core.models.AppNotification
import com.zivett.app.core.models.JobLocation
import com.zivett.app.core.models.Message
import com.zivett.app.core.models.RouteParam
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.network.LaravelInstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/// The wire pieces of the Pusher protocol Reverb speaks — pure framing,
/// no sockets, so every shape is unit-testable. The client sits on top.
object PusherProtocol {
    /// Every server frame: `data` arrives as a STRING of JSON (the
    /// protocol double-encodes payloads).
    @Serializable
    data class Frame(val event: String, val channel: String? = null, val data: String? = null) {
        /// The payload as text ready for a real decode.
        val payload: String get() = data ?: "{}"
    }

    /// `pusher:connection_established`'s payload.
    @Serializable
    data class Established(val socketId: String, val activityTimeout: Int? = null)

    /// `POST /api/broadcasting/auth`'s answer.
    @Serializable
    data class ChannelAuth(val auth: String)

    fun decodeFrame(text: String): Frame? = runCatching { JsonCoding.json.decodeFromString<Frame>(text) }.getOrNull()

    fun decodeEstablished(frame: Frame): Established? = runCatching { JsonCoding.json.decodeFromString<Established>(frame.payload) }.getOrNull()

    // Outbound frames

    fun subscribeFrame(channel: String, auth: String): String =
        encode(buildJsonObject { put("event", "pusher:subscribe"); put("data", buildJsonObject { put("channel", channel); put("auth", auth) }) })

    fun unsubscribeFrame(channel: String): String =
        encode(buildJsonObject { put("event", "pusher:unsubscribe"); put("data", buildJsonObject { put("channel", channel) }) })

    const val pingFrame = """{"event":"pusher:ping","data":{}}"""
    const val pongFrame = """{"event":"pusher:pong","data":{}}"""

    /// `ws(s)://host:port/app/{key}?protocol=7`.
    fun socketUrl(key: String, host: String, port: Int, scheme: String): String {
        val wsScheme = if (scheme == "https") "wss" else "ws"
        return "$wsScheme://$host:$port/app/$key?protocol=7&client=zivett-android&version=1.0"
    }

    private fun encode(obj: JsonObject): String = JsonCoding.plain.encodeToString(JsonObject.serializer(), obj)
}

/// Reconnect pacing: 1s, 2s, 4s… capped at 30s. Attempt 0 is the first
/// RETRY (the initial connect is immediate).
object RealtimeBackoff {
    fun delayMillis(attempt: Int): Long = minOf(30, 1 shl minOf(attempt, 5)).toLong() * 1000
}

/// The three live payloads the app consumes, decoded from a frame's
/// inner JSON. Names verified against the Laravel side:
/// - notifications: Laravel's default class-name event on
///   `App.Models.User.{id}` with `MarketplaceEvent::toArray` + id/type
/// - `message.sent` on `conversation.{id}` (`App\Events\MessageSent`)
/// - `location.updated` on `job.{id}.location` (`App\Events\LocationUpdated`)
object RealtimeEvents {
    const val notificationCreated = """Illuminate\Notifications\Events\BroadcastNotificationCreated"""
    const val messageSent = "message.sent"
    const val locationUpdated = "location.updated"

    /// The bell-row shape a broadcast carries (no read/created_at — the
    /// row is new by definition).
    @Serializable
    data class PushedNotification(val id: String, val title: String, val body: String? = null, val routeName: String? = null, val routeParams: Map<String, RouteParam>? = null)

    /// `MessageSent::broadcastWith` — no `mine`/`read`; the consumer
    /// derives them from the signed-in user.
    @Serializable
    data class PushedMessage(
        val id: Int,
        val conversationId: Int,
        val senderId: Int,
        val sender: String,
        val body: String,
        @Serializable(with = LaravelInstantSerializer::class) val createdAt: Instant? = null,
    )

    /// `LocationUpdated::broadcastWith`.
    @Serializable
    data class PushedPoint(val lat: Double, val lng: Double, @Serializable(with = LaravelInstantSerializer::class) val at: Instant? = null)

    fun notification(payload: String): AppNotification? {
        val pushed = runCatching { JsonCoding.json.decodeFromString<PushedNotification>(payload) }.getOrNull() ?: return null
        return AppNotification(pushed.id, pushed.title, pushed.body, pushed.routeName, pushed.routeParams, read = false, createdAt = Instant.now())
    }

    fun message(payload: String, currentUserId: Int?): Message? {
        val pushed = runCatching { JsonCoding.json.decodeFromString<PushedMessage>(payload) }.getOrNull() ?: return null
        return Message(pushed.id, pushed.senderId, pushed.sender, pushed.body, mine = pushed.senderId == currentUserId, read = true, createdAt = pushed.createdAt)
    }

    fun point(payload: String): JobLocation.Point? {
        val pushed = runCatching { JsonCoding.json.decodeFromString<PushedPoint>(payload) }.getOrNull() ?: return null
        return JobLocation.Point(pushed.lat, pushed.lng, pushed.at)
    }
}

@Suppress("unused")
private val keepPrimitive: JsonPrimitive? = null
