package com.zivett.app.features.customer.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.AppNotification
import com.zivett.app.core.models.CustomerEndpoints
import com.zivett.app.core.models.JobRefEndpoints
import com.zivett.app.core.models.NotificationArea
import com.zivett.app.core.models.NotificationFeed
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.realtime.RealtimeEvents
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTopBar
import com.zivett.app.features.company.Dates
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/// The bell: last 15 notifications + unread count. Shared by every role
/// — the feed endpoint is role-agnostic; `area` only decides which job
/// endpoint a tapped notification resolves through.
class NotificationsModel(private val client: ApiClient, val area: NotificationArea = NotificationArea.CUSTOMER) {
    var state by mutableStateOf<Loadable<NotificationFeed>>(Loadable.Loading)

    val unread: Int get() = state.value?.unread ?: 0

    suspend fun load() { state = state.reloaded { client.send(CustomerEndpoints.notifications()) } }

    /// The route param is the job's public code (`Z-93U4H3`); navigation
    /// wants the numeric id. Legacy numeric params short-circuit; codes
    /// resolve with one fetch (the server's route binding takes either).
    suspend fun resolveJobId(ref: String): Int? {
        ref.toIntOrNull()?.let { return it }
        return runCatching { client.send(JobRefEndpoints.job(area, ref)).job.id }.getOrNull()
    }

    /// A pushed bell row (the user channel's broadcast): prepend + bump,
    /// deduped — the poll fallback may already have fetched it.
    fun receive(event: String, payload: String) {
        if (event != RealtimeEvents.notificationCreated) return
        val row = RealtimeEvents.notification(payload) ?: return
        val feed = state.value ?: return
        if (feed.notifications.any { it.id == row.id }) return
        state = Loadable.Loaded(feed.copy(notifications = listOf(row) + feed.notifications, unread = feed.unread + 1))
    }

    suspend fun markRead(notification: AppNotification) {
        if (notification.read) return
        val feed = state.value ?: return
        state = Loadable.Loaded(feed.copy(
            notifications = feed.notifications.map { if (it.id == notification.id) it.copy(read = true) else it },
            unread = maxOf(0, feed.unread - 1),
        ))
        runCatching { client.send(CustomerEndpoints.markRead(notification.id)) }
    }

    suspend fun markAllRead() {
        val feed = state.value ?: return
        state = Loadable.Loaded(feed.copy(notifications = feed.notifications.map { it.copy(read = true) }, unread = 0))
        runCatching { client.send(CustomerEndpoints.markAllRead()) }
    }
}

/// Keeps a bell model live: loads it, subscribes to the user's private
/// channel, polls every 60s only while the socket is down, and reloads
/// on every foreground (the socket napped). One per bell surface.
@Composable
fun rememberNotificationsModel(area: NotificationArea): NotificationsModel {
    val environment = LocalAppEnvironment.current
    val model = remember(area) { NotificationsModel(environment.client, area) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle.currentStateAsState()

    LaunchedEffect(model) {
        model.load()
        while (true) {
            delay(60_000)
            if (lifecycle.value.isAtLeast(Lifecycle.State.RESUMED) && !environment.realtime.connected) model.load()
        }
    }
    val userId = environment.session.user?.id
    DisposableEffect(model, userId) {
        val live = userId?.let { id -> environment.realtime.subscribe("App.Models.User.$id") { event, payload -> model.receive(event, payload) } }
        onDispose { live?.cancel() }
    }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(model) {
        scope.launch { model.load() }
        onPauseOrDispose { }
    }
    return model
}

/// The header bell with the "9+" badge.
@Composable
fun NotificationBell(unread: Int, dark: Boolean = false, onClick: () -> Unit) {
    val colors = ZTheme.colors
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = if (unread > 0) "$unread unread notifications" else "Notifications" }) {
        val icon = @Composable { Icon(if (unread > 0) Icons.Outlined.Notifications else Icons.Outlined.NotificationsNone, contentDescription = null, tint = if (dark) Color.White else colors.ink) }
        if (unread > 0) {
            BadgedBox(badge = { Badge(containerColor = colors.danger, contentColor = Color.White) { Text(if (unread > 9) "9+" else unread.toString()) } }) { icon() }
        } else icon()
    }
}

/// The inbox screen: rows, mark-all-read, tap → the area's job.
@Composable
fun NotificationsScreen(model: NotificationsModel, onBack: () -> Unit, openJob: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors

    LaunchedEffect(model) { model.load() }

    Column {
        ZTopBar("Notifications", onBack = onBack, actions = {
            ZTextAction("Mark all read", enabled = model.unread > 0) { scope.launch { model.markAllRead() } }
        })
        ZScreen(onRefresh = { model.load() }) {
            ZLoadable(model.state, retry = { scope.launch { model.load() } }) { feed ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                    if (feed.notifications.isEmpty()) ZEmptyState(Icons.Outlined.NotificationsNone, "You're all caught up", "Updates about your jobs land here.")
                    for (notification in feed.notifications) {
                        ZCard(padding = ZSpacing.sm, onClick = {
                            scope.launch {
                                model.markRead(notification)
                                val ref = notification.jobRef ?: return@launch
                                val id = model.resolveJobId(ref) ?: return@launch
                                openJob(id)
                            }
                        }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.Top) {
                                Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(if (notification.read) Color.Transparent else colors.brandGold))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                                    if (notification.read) ZBody(notification.title) else ZBodyStrong(notification.title)
                                    notification.body?.let { ZCaption(it, maxLines = 3) }
                                    notification.createdAt?.let { ZCaption(Dates.relative(it), tone = ZTextTone.FAINT) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }
}

@Suppress("unused")
private val keepFillMaxWidth: Modifier = Modifier.fillMaxWidth()
