package com.zivett.app.features.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.realtime.RealtimeEvents
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

/// Keeps an open job page live (web's `lib/liveJob.js`): subscribes to
/// `job.{id}` and quietly refetches on every `job.updated` ping (the
/// payload is never rendered — the detail is masked per viewer), on
/// every return to the foreground (the socket napped; pings were
/// missed), and every 30s only while the socket is down. Refetches are
/// serialized and a burst collapses into one; `refresh` must keep the
/// last good value on failure (`Loadable.reloaded`) — no spinner, no
/// error. The screen still does its own first load.
@Composable
fun LiveJobEffect(jobId: Int, refresh: suspend () -> Unit) {
    val environment = LocalAppEnvironment.current
    val currentRefresh by rememberUpdatedState(refresh)
    val lifecycle = LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val requests = remember(jobId) { Channel<Unit>(Channel.CONFLATED) }

    LaunchedEffect(requests) {
        for (request in requests) currentRefresh()
    }
    LaunchedEffect(requests) {
        while (true) {
            delay(30_000)
            if (lifecycle.value.isAtLeast(Lifecycle.State.RESUMED) && !environment.realtime.connected) requests.trySend(Unit)
        }
    }
    DisposableEffect(jobId) {
        val live = environment.realtime.subscribe(RealtimeEvents.jobChannel(jobId)) { event, payload ->
            if (RealtimeEvents.isJobPing(event, payload, jobId)) requests.trySend(Unit)
        }
        onDispose { live.cancel() }
    }
    // The first resume is the screen opening — its own load covers that.
    val opened = remember(jobId) { booleanArrayOf(false) }
    LifecycleResumeEffect(jobId) {
        if (opened[0]) requests.trySend(Unit) else opened[0] = true
        onPauseOrDispose { }
    }
}
