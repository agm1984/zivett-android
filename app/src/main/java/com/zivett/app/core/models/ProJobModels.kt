@file:UseSerializers(LaravelInstantSerializer::class)

package com.zivett.app.core.models

import com.zivett.app.core.network.ApiRequest
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

/// `ProJobPayload::full` — an own job as the office sees it.
@Serializable
data class ProJob(
    val id: Int,
    val code: String? = null,
    val title: String = "",
    val status: String = "scheduled",
    val customer: Customer? = null,
    val address: String? = null,
    val scheduledDate: String? = null,
    val scheduledWindow: String? = null,
    val startTime: String? = null,
    val estimatedHours: Double? = null,
    val priceCents: Int? = null,
    val category: CategoryRef? = null,
    val completedAt: Instant? = null,
    val createdAt: Instant? = null,
    val crew: List<CrewMember> = emptyList(),
    val repeats: Boolean = false,
    val description: String? = null,
    val proCustomerAddressId: Int? = null,
    val addressUnit: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val quote: QuoteRef? = null,
    /// Priced like a quote (null on a bare price, or none).
    val lineItems: List<ProLine>? = null,
    val changes: List<Change> = emptyList(),
    /// Why no more changes can be added (invoiced, cancelled), if so.
    val changesLocked: String? = null,
    /// Before tax, with change orders.
    val totalPriceCents: Int? = null,
    val startedAt: Instant? = null,
    val cancelledAt: Instant? = null,
    val cancelReason: String? = null,
    val reminderSentAt: Instant? = null,
    val next: List<String> = emptyList(),
    val workNotes: String? = null,
    val actualHours: Double? = null,
    val time: List<OwnJob.TimeEntry> = emptyList(),
    val photos: List<JobPhoto> = emptyList(),
    val money: Money? = null,
    val series: Series? = null,
    val invoices: List<DocumentRef> = emptyList(),
) {
    @Serializable data class Customer(val id: Int? = null, val name: String? = null, val phone: String? = null, val email: String? = null)
    @Serializable data class QuoteRef(val id: Int, val number: String? = null, val totalCents: Int = 0, val subtotalCents: Int = 0, val lineItems: List<ProLine> = emptyList())

    @Serializable
    data class Change(val id: Int, val label: String = "", val note: String? = null, val lineItems: List<ProLine> = emptyList(), val amountCents: Int = 0, val createdAt: Instant? = null, val createdBy: String? = null)

    /// What it cost and made (`ProJobRecords::money`).
    @Serializable
    data class Money(
        val costs: List<Cost> = emptyList(),
        val costsCents: Int = 0,
        /// invoiced | quoted | priced | null
        val priceBasis: String? = null,
        val priceCents: Int? = null,
        val leftCents: Int? = null,
        val perHourCents: Int? = null,
    )

    @Serializable data class Cost(val id: Int, val description: String = "", val amountCents: Int = 0)

    @Serializable
    data class Series(
        val id: Int,
        val frequency: String? = null,
        val label: String? = null,
        val endsOn: String? = null,
        val active: Boolean = false,
        val upcomingCount: Int = 0,
        val upcoming: List<Visit> = emptyList(),
    ) {
        @Serializable data class Visit(val id: Int, val scheduledDate: String? = null, val scheduledWindow: String? = null)
    }

    val isOpen: Boolean get() = status == OwnJobStatus.SCHEDULED || status == OwnJobStatus.IN_PROGRESS
    val customerFirstName: String get() = customer?.name?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "the customer"
}

@Serializable data class ProJobResponse(val job: ProJob)

@Serializable
data class ProJobsResponse(
    val jobs: List<ProJobRow> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false,
    @Serializable(with = com.zivett.app.core.network.PhpRatesSerializer::class) val counts: Map<String, Int> = emptyMap(),
)

@Serializable data class ProJobContext(val categories: List<CategoryRef> = emptyList())

/// Create / update. On update, `quote_id`, `pro_customer_id` and `repeat`
/// are refused by the server, so they stay null (omitted).
@Serializable
data class ProJobBody(
    val quoteId: Int? = null,
    val proCustomerId: Int? = null,
    val proCustomerAddressId: Int? = null,
    val serviceCategoryId: Int? = null,
    val title: String? = null,
    val description: String? = null,
    val scheduledDate: String? = null,
    val scheduledWindow: String? = null,
    val startTime: String? = null,
    val estimatedHours: Double? = null,
    val priceCents: Int? = null,
    val lineItems: List<QuoteLineBody>? = null,
    val repeat: Repeat? = null,
) {
    @Serializable data class Repeat(val frequency: String, val endsOn: String? = null)
}

@Serializable data class ProJobStatusBody(val status: String, val reason: String? = null)
@Serializable data class WorkNotesBody(val workNotes: String?)
@Serializable data class TimeBody(val workedOn: String, val hours: Double, val note: String? = null, val userId: Int? = null)
@Serializable data class CostBody(val description: String, val amountCents: Int)
@Serializable data class JobChangeBody(val label: String, val note: String? = null, val lineItems: List<QuoteLineBody>)
@Serializable data class OwnCrewBody(val userIds: List<Int>, val unassign: Boolean? = null)

@Serializable data class ProJobCrewResponse(val members: List<RosterMember> = emptyList(), val crewUserIds: List<Int> = emptyList())
@Serializable data class SetCrewResponse(val job: ProJob, val members: List<RosterMember> = emptyList())
@Serializable data class SeriesStopped(val job: ProJob, val cancelled: Int = 0)
@Serializable data class SeriesCrewApplied(val job: ProJob, val changed: Int = 0)

object ProJobEndpoints {
    private const val BASE = "api/company/business/jobs"

    /// `status`: active | scheduled | in_progress | completed | cancelled | all.
    fun list(status: String, q: String?, page: Int = 1) = ApiRequest.get<ProJobsResponse>(
        BASE, listOfNotNull("status" to status, q?.takeIf { it.isNotBlank() }?.let { "q" to it }, "page" to page.toString()),
    )
    fun context() = ApiRequest.get<ProJobContext>("$BASE/context")
    fun show(id: Int) = ApiRequest.get<ProJobResponse>("$BASE/$id")
    fun create(body: ProJobBody) = ApiRequest.post<ProJobResponse, ProJobBody>(BASE, body)
    /// `explicitNulls` so clearing a date, time, window or price reaches the server.
    fun update(id: Int, body: ProJobBody) = ApiRequest.json<ProJobResponse, ProJobBody>(ApiRequest.Method.PATCH, "$BASE/$id", body, coder = com.zivett.app.core.network.JsonCoding.explicitNulls)
    fun status(id: Int, status: String, reason: String? = null) = ApiRequest.post<ProJobResponse, ProJobStatusBody>("$BASE/$id/status", ProJobStatusBody(status, reason))
    fun record(id: Int, notes: String?) = ApiRequest.json<ProJobResponse, WorkNotesBody>(ApiRequest.Method.PATCH, "$BASE/$id/record", WorkNotesBody(notes), coder = com.zivett.app.core.network.JsonCoding.explicitNulls)
    /// `kind`: before | after | other. One photo per call.
    fun addPhoto(id: Int, kind: String, jpeg: ByteArray): ApiRequest<ProJobResponse> {
        val form = com.zivett.app.core.network.MultipartForm()
        form.addField("kind", kind)
        form.addFile("photo", "photo.jpg", "image/jpeg", jpeg)
        return ApiRequest.multipart("$BASE/$id/photos", form)
    }
    fun deletePhoto(id: Int, photoId: Int) = ApiRequest.delete<ProJobResponse>("$BASE/$id/photos/$photoId")
    fun addTime(id: Int, body: TimeBody) = ApiRequest.post<ProJobResponse, TimeBody>("$BASE/$id/time", body)
    fun deleteTime(id: Int, entryId: Int) = ApiRequest.delete<ProJobResponse>("$BASE/$id/time/$entryId")
    fun addCost(id: Int, body: CostBody) = ApiRequest.post<ProJobResponse, CostBody>("$BASE/$id/costs", body)
    fun deleteCost(id: Int, costId: Int) = ApiRequest.delete<ProJobResponse>("$BASE/$id/costs/$costId")
    fun addChange(id: Int, body: JobChangeBody) = ApiRequest.post<ProJobResponse, JobChangeBody>("$BASE/$id/changes", body)
    fun deleteChange(id: Int, changeId: Int) = ApiRequest.delete<ProJobResponse>("$BASE/$id/changes/$changeId")
    fun crew(id: Int) = ApiRequest.get<ProJobCrewResponse>("$BASE/$id/crew")
    fun setCrew(id: Int, userIds: List<Int>) = ApiRequest.post<SetCrewResponse, OwnCrewBody>("$BASE/$id/crew", OwnCrewBody(userIds, if (userIds.isEmpty()) true else null))
    fun stopSeries(id: Int) = ApiRequest.post<SeriesStopped>("$BASE/$id/series/stop")
    fun seriesCrew(id: Int) = ApiRequest.post<SeriesCrewApplied>("$BASE/$id/series/crew")
}

object ProJobFormat {
    val frequencies = listOf("weekly" to "Every week", "biweekly" to "Every 2 weeks", "every_4_weeks" to "Every 4 weeks", "monthly" to "Every month")
    val filters = listOf("active" to "Active", "scheduled" to "Scheduled", "in_progress" to "In progress", "completed" to "Completed", "cancelled" to "Cancelled", "all" to "All")
}
