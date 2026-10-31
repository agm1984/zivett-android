package com.zivett.app.features.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zivett.app.app.AppEvents
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.PhpRatesSerializer
import com.zivett.app.core.realtime.RealtimeEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/// `GET /api/nav-badges` → `{ badges: { key: count } }` — each role only
/// its own keys (bookers `messages`, companies `opportunities`). PHP
/// writes an empty map as `[]`, hence the tolerant coder.
@Serializable
data class NavBadgesResponse(@Serializable(with = PhpRatesSerializer::class) val badges: Map<String, Int> = emptyMap())

@Serializable
data class NavBadgeSeenBody(val key: String)

object NavBadgeEndpoints {
    fun badges() = ApiRequest.get<NavBadgesResponse>("api/nav-badges")
    /// "I've looked" — resets a new-since-last-visit badge; returns the same payload.
    fun seen(key: String) = ApiRequest.post<NavBadgesResponse, NavBadgeSeenBody>("api/nav-badges/seen", NavBadgeSeenBody(key))
}

/// The tab-bar attention counts (web `stores/navBadges.js`). Event-driven,
/// never polled: read on launch and every foreground, and again on any
/// live notification or push. While a "since you looked" page is open
/// (`view`), every refresh also marks it seen — a job landing in the feed
/// you're reading isn't news. Badges degrade silently: a missing dot never
/// blocks a screen.
class NavBadgesModel(private val client: ApiClient, private val scope: CoroutineScope? = null) {
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /// Bumped on every live notification / push — screens that should
    /// refetch on the same events (the opportunities feed) key on it.
    var liveEvents by mutableIntStateOf(0)
        private set

    fun received() { liveEvents++ }

    private var viewing: String? = null
    private val lock = Mutex()

    fun count(key: String): Int = counts[key] ?: 0

    /// Fire-and-forget on the shell's scope — safe from a screen's
    /// dispose (leaving a thread you just read clears its badge).
    fun refreshSoon() { scope?.launch { refresh() } }

    suspend fun refresh() {
        lock.withLock {
            val key = viewing
            runCatching { client.send(if (key != null) NavBadgeEndpoints.seen(key) else NavBadgeEndpoints.badges()) }
                .onSuccess { counts = it.badges }
        }
    }

    /// The seen-able page that's open now (null when it closes). Zeroes
    /// its count straight away, then refreshes (which posts the seen).
    suspend fun view(key: String) {
        viewing = key
        counts = counts + (key to 0)
        refresh()
    }

    /// The page closed — synchronous, so it works from a dispose.
    fun stopViewing(key: String) {
        if (viewing == key) viewing = null
    }

    companion object {
        /// An org shell without the server's key (pre-approval, a member
        /// without the section) simply never shows a dot.
        const val MESSAGES = "messages"
        const val OPPORTUNITIES = "opportunities"
    }
}

/// Null outside a shell that tracks badges (previews, the signed-out flow).
val LocalNavBadges = staticCompositionLocalOf<NavBadgesModel?> { null }

/// One per role shell: loads, follows the user's live notifications and
/// FCM pushes, and re-reads on every foreground (the socket napped).
@Composable
fun rememberNavBadges(): NavBadgesModel {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember { NavBadgesModel(environment.client, scope) }

    LifecycleResumeEffect(model) {
        scope.launch { model.refresh() }
        onPauseOrDispose { }
    }
    val pushes = AppEvents.pushReceived
    LaunchedEffect(pushes) { if (pushes > 0) { model.received(); model.refresh() } }
    val userId = environment.session.user?.id
    DisposableEffect(model, userId) {
        val live = userId?.let { id ->
            environment.realtime.subscribe("App.Models.User.$id") { event, _ ->
                if (event == RealtimeEvents.notificationCreated) scope.launch { model.received(); model.refresh() }
            }
        }
        onDispose { live?.cancel() }
    }
    return model
}

/// Marks a "since you looked" badge seen while its screen is showing.
@Composable
fun ViewingNavBadge(key: String) {
    val badges = LocalNavBadges.current ?: return
    val scope = rememberCoroutineScope()
    DisposableEffect(badges, key) {
        scope.launch { badges.view(key) }
        onDispose { badges.stopViewing(key) }
    }
}
