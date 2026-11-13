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
    /// The company's own (Business tools) jobs on their slots — full rows
    /// for people who may open them, `busy` placeholders (when, nothing
    /// else) for the rest. Absent before 2026-11-12. A row that doesn't
    /// decode is dropped.
    @Serializable(with = OwnCalendarJobsSerializer::class) val ownJobs: List<OwnCalendarJob> = emptyList(),
    /// Whether full own-job rows open (Business tools, or on the crew).
    val canOpenOwnJobs: Boolean = false,
) {
    /// ZiVETT jobs and own jobs as ONE list — what the grid, the board, the
    /// day sheet and the map read. Busy placeholders get negative ids (they
    /// never open).
    val entries: List<CalendarJob> get() = jobs + ownJobs.mapIndexed { index, row -> row.toCalendarJob(index) }

    /// Fields mirror `CalendarController::jobRow`.
    @Serializable
    data class CalendarJob(
        val id: Int,
        val code: String? = null,
        val title: String = "",
        /// Null on own jobs (their lifecycle is `ownStatus`).
        val status: JobStatus? = null,
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
        /// The dispatcher's planned arrival inside the window ("09:30").
        val arrivalTime: String? = null,
        /// The accepted quote's estimated hours.
        val hours: Double? = null,
        /// [start, end] minutes from midnight (`TechSchedule::jobSpan`).
        val span: List<Int>? = null,
        // Set only on rows made from `own_jobs` (never on the wire for
        // ZiVETT jobs): `kind` = "own", `ownStatus` its ProJobStatus,
        // `busy` = a placeholder with no detail, `startTime` its start.
        val kind: String = DayJob.KIND_JOB,
        val ownStatus: String? = null,
        val busy: Boolean = false,
        val startTime: String? = null,
    ) {
        val lead: CrewTech? get() = crew.firstOrNull()
        val isOwn: Boolean get() = kind == DayJob.KIND_OWN
        /// Planned arrival (ZiVETT) or start time (own).
        val clockTime: String? get() = arrivalTime ?: startTime
    }

    /// One `own_jobs` row (`ProJobPayload::calendarRow`). A `busy: true`
    /// row's id is a string ("busy-12") and it carries only the slot.
    @Serializable
    data class OwnCalendarJob(
        val id: RouteParam,
        val busy: Boolean = false,
        val code: String? = null,
        val title: String? = null,
        val status: String? = null,
        val customerName: String? = null,
        val address: String? = null,
        val lat: Double? = null,
        val lng: Double? = null,
        val categoryCode: String? = null,
        val crew: List<CrewTech> = emptyList(),
        val mine: Boolean = false,
        val date: String,
        val window: String? = null,
        val startTime: String? = null,
        val estimatedHours: Double? = null,
        val span: List<Int>? = null,
    ) {
        fun toCalendarJob(index: Int): CalendarJob = CalendarJob(
            id = if (busy) -(index + 1) else (id.intValue ?: -(index + 1)),
            code = if (busy) null else code,
            title = if (busy) "Busy" else title.orEmpty(),
            date = date,
            window = window,
            categoryCode = categoryCode,
            address = address,
            lat = lat,
            lng = lng,
            crew = crew,
            hours = estimatedHours,
            span = span,
            kind = DayJob.KIND_OWN,
            ownStatus = status,
            busy = busy,
            startTime = startTime,
        )
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

/// The calendar's `own_jobs`, dropping rows that don't decode rather than
/// failing the whole calendar.
object OwnCalendarJobsSerializer : KSerializer<List<CompanyCalendar.OwnCalendarJob>> {
    private val delegate = ListSerializer(CompanyCalendar.OwnCalendarJob.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<CompanyCalendar.OwnCalendarJob> {
        val input = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val element = input.decodeJsonElement() as? JsonArray ?: return emptyList()
        return element.mapNotNull { row -> runCatching { JsonCoding.json.decodeFromJsonElement(CompanyCalendar.OwnCalendarJob.serializer(), row) }.getOrNull() }
    }

    override fun serialize(encoder: Encoder, value: List<CompanyCalendar.OwnCalendarJob>) = delegate.serialize(encoder, value)
}

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
