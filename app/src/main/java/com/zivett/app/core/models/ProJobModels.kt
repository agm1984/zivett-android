@file:UseSerializers(LaravelInstantSerializer::class)

package com.zivett.app.core.models

import com.zivett.app.core.network.LaravelInstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

/// A company's own jobs (Business tools slice 4).

/// `ProJobPayload::row`.
@Serializable
data class ProJobRow(
    val id: Int,
    val code: String? = null,
    val title: String = "",
    /// scheduled | in_progress | completed | cancelled
    val status: String = "scheduled",
    val customer: OwnCustomerRef? = null,
    val address: String? = null,
    val scheduledDate: String? = null,
    val scheduledWindow: String? = null,
    /// "13:30"
    val startTime: String? = null,
    val estimatedHours: Double? = null,
    val priceCents: Int? = null,
    val category: CategoryRef? = null,
    val completedAt: Instant? = null,
    val createdAt: Instant? = null,
    val crew: List<CrewMember> = emptyList(),
    /// Part of a repeating series.
    val repeats: Boolean = false,
)

@Serializable
data class CategoryRef(val id: Int, val name: String, val code: String? = null)

/// Lead first. `inactive`: deactivated or removed since being put on it.
@Serializable
data class CrewMember(val id: Int, val name: String, val photoUrl: String? = null, val inactive: Boolean = false)
