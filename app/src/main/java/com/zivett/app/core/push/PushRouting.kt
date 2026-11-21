package com.zivett.app.core.push

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/// Where a notification leads (web `lib/notificationRoute.js`), for the
/// routes the app has a screen for. Job routes carry the job's public
/// code; the rest open a company tab (2026-10-29: "New … job near you",
/// "Your quote went to the booker", "Payout sent", "Payouts are set up").
sealed interface PushTarget {
    /// `review` = the one-time "How did X do?" reminder
    /// (`Notify::reviewRequested`, `route_params.review = 1`): open the job
    /// and offer the quick rating sheet when it's closed and unrated.
    /// Older servers never send it, so absent = a plain job open.
    data class Job(val ref: String, val review: Boolean = false) : PushTarget
    /// The feed; `jobCode` opens that job's quote composer when it's quotable.
    data class Opportunities(val jobCode: String?) : PushTarget
    data object Quotes : PushTarget
    data object Payouts : PushTarget
    /// One of the company's own (Business tools) jobs — "You're on a job"
    /// (`company.own-jobs.show`, numeric `id`).
    data class OwnJob(val id: Int) : PushTarget
    /// The team clock's nudges — "Still working?" and "We clocked you out"
    /// (`company.my-day`): open My day on today and re-read it.
    data object MyDay : PushTarget
}

/// Where a tapped push should land. The FCM data payload carries the same
/// `route_name` + `route_params` the bell rows do (see `App\Support\Push`),
/// so a tap resolves through the same path a bell tap does.
object PushRouting {
    const val LOG_TAG = "ZivettPush"
    const val EXTRA_ROUTE_NAME = "route_name"
    const val EXTRA_ROUTE_ID = "route_id"
    const val EXTRA_REVIEW = "review"

    private fun isJobRoute(route: String) = route.startsWith("customer.jobs") || route.startsWith("business.requests") || route.startsWith("company.jobs")

    /// Pure: a route name + a param lookup → the app's destination, null
    /// for routes the app has no screen for (a no-op tap, like the web's).
    fun target(routeName: String?, param: (String) -> String?): PushTarget? {
        val route = routeName ?: return null
        return when {
            isJobRoute(route) -> param("id")?.let { PushTarget.Job(it, review = flag(param("review"))) }
            route == "company.opportunities" -> PushTarget.Opportunities(param("job"))
            route == "company.quotes" -> PushTarget.Quotes
            route == "company.payouts" -> PushTarget.Payouts
            route == "company.own-jobs.show" -> param("id")?.toIntOrNull()?.let { PushTarget.OwnJob(it) }
            route == "company.my-day" -> PushTarget.MyDay
            else -> null
        }
    }

    /// The target inside a push data map. `route_params` arrives either as
    /// a JSON string (FCM data values are strings) or flattened as
    /// `route_params.<key>`; `route_id` is the job reference.
    fun target(data: Map<String, String>): PushTarget? = target(data["route_name"]) { key -> param(data, key) }

    /// The job reference (public code like `Z-93U4H3`, or a legacy
    /// numeric id) inside a push data map — null when the push points at
    /// something other than a job.
    fun jobRef(data: Map<String, String>): String? = (target(data) as? PushTarget.Job)?.ref

    /// The target a tapped notification's intent extras describe (what
    /// `ZivettMessagingService` packed: route name, `paramKey`, the review
    /// flag). A job ref with no route name is a tap from an older build's
    /// notification — still a job.
    fun intentTarget(routeName: String?, routeId: String?, review: Boolean): PushTarget? {
        if (routeName == null) return routeId?.let { PushTarget.Job(it, review) }
        return target(routeName) { key ->
            when (key) {
                "id", "job" -> routeId
                "review" -> if (review) "1" else null
                else -> null
            }
        }
    }

    /// A truthy route param: PHP sends `1` (or `true`); FCM data and the
    /// bell's `RouteParam` hand it over as a string.
    fun flag(value: String?): Boolean = value?.trim()?.lowercase() in setOf("1", "true")

    /// The single param a tapped notification's intent carries.
    fun paramKey(target: PushTarget): String? = when (target) {
        is PushTarget.Job -> target.ref
        is PushTarget.Opportunities -> target.jobCode
        is PushTarget.OwnJob -> target.id.toString()
        else -> null
    }

    private fun param(data: Map<String, String>, key: String): String? {
        if (key == "id") data["route_id"]?.let { return it }
        data["route_params.$key"]?.let { return it }
        val params = data["route_params"] ?: return null
        return runCatching {
            val element = kotlinx.serialization.json.Json.parseToJsonElement(params)
            (element as? kotlinx.serialization.json.JsonObject)?.get(key)?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        }.getOrNull()
    }
}

/// A tap that arrived before any screen was listening — a cold launch
/// from a push, where the intent lands before the shell mounts. The
/// first shell to appear consumes it; a warm tap flips the same state
/// and the mounted shell reacts.
object PendingPushOpen {
    var target: PushTarget? by mutableStateOf(null)
        private set

    fun store(target: PushTarget) { this.target = target }

    /// Reads and clears — a pending tap routes exactly once.
    fun take(): PushTarget? {
        val current = target
        target = null
        return current
    }
}
