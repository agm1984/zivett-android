@file:UseSerializers(LaravelInstantSerializer::class)

package com.zivett.app.core.models

import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.network.LaravelInstantSerializer
import com.zivett.app.core.network.PhpRatesSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import java.time.Instant

/// `GET /api/company/calendar` (`Company\CalendarController::index`) — a
/// month (`?month=Y-m`) or a Monday–Sunday week (`?week=Y-m-d`) over the
/// same payload: the month grid and the week dispatch board both read it.
/// Everything past `month` arrived 2026-10-25, so it all defaults.
@Serializable
data class CompanyCalendar(
    val month: String,
    val from: String? = null,
    val to: String? = null,
    val jobs: List<CalendarJob> = emptyList(),
    val tentative: List<Tentative>? = null,
    /// The pro's pending asks to move a visit — ghosts on the NEW slot.
    val moves: List<CalendarMove> = emptyList(),
    /// Feed jobs whose offered windows hit an open slot, by date. PHP
    /// sends `[]` for an empty object.
    @Serializable(with = OpeningsSerializer::class) val openings: Map<String, List<Opening>> = emptyMap(),
    val blockedDates: List<AvailabilityResponse.BlockedDate> = emptyList(),
    /// ISO weekdays 1–7; null = every day.
    val workingDays: List<Int>? = null,
    /// Weekly window blocks ("no evenings on Fridays").
    val blockedWindows: List<WeeklyBlock>? = null,
    /// Jobs per window across the org; null = no cap.
    val windowCapacity: Int? = null,
    /// `"Y-m-d|window"` → confirmed jobs, org-wide. `[]` when empty.
    @Serializable(with = PhpRatesSerializer::class) val load: Map<String, Int> = emptyMap(),
    val timeOff: List<TimeOff> = emptyList(),
    /// Admins: every active member (board rows + the tech filter). A
    /// member: only themselves.
    val team: List<CalendarMember> = emptyList(),
    val base: Base? = null,
    val canManage: Boolean = false,
    val canQuote: Boolean = false,
) {
    /// Fields mirror `CalendarController::jobRow`.
    @Serializable
    data class CalendarJob(
        val id: Int,
        val code: String? = null,
        val title: String,
        val status: JobStatus,
        val mode: String? = null,
        /// Scheduled date, or the acceptance day for instant work — the
        /// server drops rows with neither.
        val date: String,
        /// Null = instant ("Now").
        val window: String? = null,
        val categoryCode: String? = null,
        val address: String? = null,
        val lat: Double? = null,
        val lng: Double? = null,
        /// Lead first.
        val crew: List<CrewTech> = emptyList(),
        val crewSize: Int? = null,
        val dispatchedAt: Instant? = null,
        val needsCrew: Boolean = false,
        val needsStart: Boolean = false,
        /// Accepted, nobody en route, past the progress SLA's nudge.
        val overdue: Boolean = false,
        val movable: Boolean = false,
    ) {
        val lead: CrewTech? get() = crew.firstOrNull()
    }

    @Serializable data class CrewTech(val id: Int, val name: String, val photoUrl: String? = null)

    /// `{quote_id, job_code, title, amount_cents, date, window}`.
    @Serializable
    data class Tentative(val quoteId: Int, val jobCode: String? = null, val title: String? = null, val amountCents: Int = 0, val date: String, val window: String? = null)

    @Serializable
    data class CalendarMove(val id: Int, val jobCode: String? = null, val title: String? = null, val date: String, val window: String? = null, val fromDate: String? = null, val fromWindow: String? = null)

    @Serializable
    data class Opening(
        val id: Int,
        val code: String? = null,
        val title: String = "",
        val mode: String? = null,
        val categoryCode: String? = null,
        val windows: List<String> = emptyList(),
        val distanceKm: Double? = null,
    )

    @Serializable data class TimeOff(val id: Int, val userId: Int, val name: String? = null, val date: String, val window: String? = null)

    @Serializable
    data class CalendarMember(val id: Int, val name: String, val shortName: String? = null, val photoUrl: String? = null, val takesJobs: Boolean = true) {
        val display: String get() = shortName ?: name
    }

    @Serializable data class Base(val lat: Double, val lng: Double, val label: String? = null)
}

/// One weekly block: ISO weekday 1–7 × window.
@Serializable data class WeeklyBlock(val weekday: Int, val window: String)

/// `PATCH /api/company/availability` — explicit nulls: null clears the
/// cap / the weekly blocks, so the body is encoded with explicitNulls.
@Serializable
data class AvailabilitySettingsBody(val blockedWindows: List<WeeklyBlock>?, val windowCapacity: Int?)

@Serializable
data class AvailabilitySettingsResponse(val blockedWindows: List<WeeklyBlock>? = null, val windowCapacity: Int? = null)

@Serializable data class TimeOffBody(val userId: Int, val date: String, val window: String? = null)
@Serializable data class TimeOffResponse(val timeOff: CompanyCalendar.TimeOff)
@Serializable data class BlockDateBody(val date: String, val window: String? = null)

/// The pro's live ask to move a confirmed visit (`Reschedules::payload`),
/// on job payloads of both sides while the job is accepted.
@Serializable
data class RescheduleRequest(
    val id: Int,
    val date: String,
    val window: String? = null,
    val fromDate: String? = null,
    val fromWindow: String? = null,
    val reason: String? = null,
    val proposedAt: Instant? = null,
)

/// `GET /api/company/jobs/{id}/reschedule/slots` — the next 21 days × 3.
@Serializable
data class RescheduleSlots(val slots: List<Slot> = emptyList(), val rescheduleRequest: RescheduleRequest? = null) {
    @Serializable
    data class Slot(
        val date: String,
        val window: String,
        val open: Boolean = false,
        /// `blocked` · `day_off` · `full` · null.
        val reason: String? = null,
        val booked: Int = 0,
        val capacity: Int? = null,
        val current: Boolean = false,
    )
}

@Serializable data class RescheduleBody(val date: String, val window: String, val reason: String? = null)

/// The calendar's `openings`: an object of date → rows, or `[]` when PHP
/// had nothing to key. A row that doesn't decode is dropped rather than
/// failing the whole calendar.
object OpeningsSerializer : KSerializer<Map<String, List<CompanyCalendar.Opening>>> {
    private val delegate = MapSerializer(String.serializer(), ListSerializer(CompanyCalendar.Opening.serializer()))
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): Map<String, List<CompanyCalendar.Opening>> {
        val input = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val element = input.decodeJsonElement() as? JsonObject ?: return emptyMap()
        return element.entries.associate { (date, rows) ->
            date to ((rows as? JsonArray)?.mapNotNull { row ->
                runCatching { JsonCoding.json.decodeFromJsonElement(CompanyCalendar.Opening.serializer(), row) }.getOrNull()
            } ?: emptyList())
        }.filterValues { it.isNotEmpty() }
    }

    override fun serialize(encoder: Encoder, value: Map<String, List<CompanyCalendar.Opening>>) = delegate.serialize(encoder, value)
}
