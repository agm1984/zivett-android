package com.zivett.app.core.push

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/// Where a tapped push should land. The FCM data payload carries the same
/// `route_name` + `route_params` the bell rows do (see `App\Support\Push`),
/// so a tap resolves through the same path a bell tap does.
object PushRouting {
    const val EXTRA_ROUTE_NAME = "route_name"
    const val EXTRA_ROUTE_ID = "route_id"

    /// The job reference (public code like `Z-93U4H3`, or a legacy
    /// numeric id) inside a push data map — null when the push points at
    /// something other than a job. `route_params` arrives either as a
    /// JSON string (FCM data values are strings) or flattened as
    /// `route_params.id`/`route_id`.
    fun jobRef(data: Map<String, String>): String? {
        val route = data["route_name"] ?: return null
        if (!(route.startsWith("customer.jobs") || route.startsWith("business.requests") || route.startsWith("company.jobs"))) return null

        data["route_id"]?.let { return it }
        data["route_params.id"]?.let { return it }
        val params = data["route_params"] ?: return null
        return runCatching {
            val element = kotlinx.serialization.json.Json.parseToJsonElement(params)
            (element as? kotlinx.serialization.json.JsonObject)?.get("id")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        }.getOrNull()
    }
}

/// A tap that arrived before any screen was listening — a cold launch
/// from a push, where the intent lands before the shell mounts. The
/// first shell to appear consumes it; a warm tap flips the same state
/// and the mounted shell reacts.
object PendingPushOpen {
    var ref: String? by mutableStateOf(null)
        private set

    fun store(reference: String) { ref = reference }

    /// Reads and clears — a pending tap routes exactly once.
    fun take(): String? {
        val current = ref
        ref = null
        return current
    }
}
