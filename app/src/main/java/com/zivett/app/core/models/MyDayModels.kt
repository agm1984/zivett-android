@file:UseSerializers(LaravelInstantSerializer::class)

package com.zivett.app.core.models

import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.ApiRequest.Method
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.network.LaravelInstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

/// `GET /api/company/my-day?date=Y-m-d` (`App\Support\MyDay`, 2026-11-12) —
/// one person's day: every job THEY are on that date, ZiVETT and own work
/// together in visit order, each with where to go, who to call and the one
/// next thing to press. Personal (no section gates it). Everything past
/// `date` defaults so a partial payload still reads.
@Serializable
data class MyDay(
    val date: String,
    val isToday: Boolean = false,
    val jobs: List<DayJob> = emptyList(),
    /// The JOB TIMER the person left running, wherever it is — the strip on top.
    val clock: RunningClock? = null,
    /// Business tools is on for the org: own jobs carry job timer buttons.
    val canClock: Boolean = false,
    /// The team clock (my working day, breaks) — `TeamShifts::state`.
    /// Null from a server older than 2026-11-12.
    val shift: ClockState? = null,
) {
    @Serializable
    data class RunningClock(val jobId: Int, val code: String? = null, val title: String? = null, val startedAt: Instant? = null)
}

/// One stop on the day (`MyDay::jobRow` / `ownRow`).
@Serializable
data class DayJob(
    /// `job` = a ZiVETT marketplace job, `own` = one of the company's own
    /// (Business tools) jobs.
    val kind: String = KIND_JOB,
    val id: Int,
    val code: String? = null,
    val title: String = "",
    /// A JobStatus wire value for `job`, a ProJobStatus one for `own` —
    /// kept a string so neither enum breaks the other.
    val status: String = "",
    val trade: String? = null,
    val date: String? = null,
    val window: String? = null,
    /// "09:30" — the planned arrival (job) or start time (own).
    val time: String? = null,
    /// [start, end] in minutes from midnight; null when unplaced.
    val span: List<Int>? = null,
    val address: String? = null,
    val unit: String? = null,
    val accessNotes: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val contact: Contact? = null,
    /// Lead first.
    val crew: List<CompanyCalendar.CrewTech> = emptyList(),
    /// The viewer leads this one.
    val lead: Boolean = false,
    val next: Next? = null,
    /// Own jobs that are still open: whether THIS person's clock runs on it.
    val clock: JobClock? = null,
) {
    @Serializable data class Contact(val name: String? = null, val phone: String? = null)

    /// `{action, status?, label}` — `advance` (ZiVETT next stage), `start`
    /// (Start job picker), `own_status` (own job start/finish), `waiting`
    /// (a sentence, no button).
    @Serializable data class Next(val action: String, val status: String? = null, val label: String = "")

    @Serializable data class JobClock(val running: Boolean = false)

    val isOwn: Boolean get() = kind == KIND_OWN

    companion object {
        const val KIND_JOB = "job"
        const val KIND_OWN = "own"
    }
}

/// `ProJobStatus` — an own job's lifecycle.
object OwnJobStatus {
    const val SCHEDULED = "scheduled"
    const val IN_PROGRESS = "in_progress"
    const val COMPLETED = "completed"
    const val CANCELLED = "cancelled"

    fun label(status: String): String = when (status) {
        SCHEDULED -> "Scheduled"
        IN_PROGRESS -> "In progress"
        COMPLETED -> "Completed"
        CANCELLED -> "Cancelled"
        else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

/// `GET /api/company/own-jobs/{id}` — an own job as a teammate on its crew
/// sees it (`ProJobPayload::crewView`): what, when, where, who to call,
/// the hours logged and the photos — never prices or invoices.
@Serializable
data class OwnJob(
    val id: Int,
    val code: String? = null,
    val title: String = "",
    val status: String = OwnJobStatus.SCHEDULED,
    val customer: Customer? = null,
    val address: String? = null,
    val addressUnit: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val scheduledDate: String? = null,
    val scheduledWindow: String? = null,
    /// "09:30" or null.
    val startTime: String? = null,
    val estimatedHours: Double? = null,
    val category: Category? = null,
    /// Lead first (full names on this payload).
    val crew: List<CompanyCalendar.CrewTech> = emptyList(),
    val description: String? = null,
    val workNotes: String? = null,
    val actualHours: Double? = null,
    /// Time entries, oldest first (`ProTimesheets::entries`).
    val time: List<TimeEntry> = emptyList(),
    val photos: List<JobPhoto> = emptyList(),
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val cancelledAt: Instant? = null,
    /// The statuses a crew member may move it to (never cancelled).
    val next: List<String> = emptyList(),
) {
    @Serializable data class Customer(val name: String? = null, val phone: String? = null)
    @Serializable data class Category(val id: Int? = null, val name: String? = null, val code: String? = null)

    @Serializable
    data class TimeEntry(
        val id: Int,
        val user: CompanyCalendar.CrewTech? = null,
        val workedOn: String? = null,
        val minutes: Int = 0,
        val running: Boolean = false,
        val startedAt: Instant? = null,
        val endedAt: Instant? = null,
        val note: String? = null,
        val removable: Boolean = false,
    )

    /// Whether `userId`'s clock is running on this job.
    fun clockRunning(userId: Int?): Boolean = userId != null && time.any { it.running && it.user?.id == userId }

    fun onCrew(userId: Int?): Boolean = userId != null && crew.any { it.id == userId }
}

@Serializable data class OwnJobResponse(val job: OwnJob)
@Serializable data class OwnJobStatusBody(val status: String)

/// `PATCH /api/company/jobs/{id}/arrival` — explicit null clears it back to
/// "any time in the window".
@Serializable data class ArrivalBody(val arrivalTime: String?)

/// A teammate's clash on the job's slot (`TechSchedule::clashes`), riding
/// each crew-picker roster member as `busy`.
@Serializable
data class BusyBooking(val kind: String = DayJob.KIND_JOB, val code: String? = null, val title: String? = null, val start: String? = null, val end: String? = null)

object MyDayEndpoints {
    /// `date` null = today on the marketplace clock.
    fun day(date: String? = null) = ApiRequest.get<MyDay>("api/company/my-day", date?.let { listOf("date" to it) } ?: emptyList())

    fun ownJob(id: Int) = ApiRequest.get<OwnJobResponse>("api/company/own-jobs/$id")
    /// `in_progress` (clocks the person in) or `completed` (stops every clock).
    fun ownJobStatus(id: Int, status: String) = ApiRequest.post<OwnJobResponse, OwnJobStatusBody>("api/company/own-jobs/$id/status", OwnJobStatusBody(status))
    /// Crew only; clocking in stops a clock left running elsewhere.
    fun clockIn(id: Int) = ApiRequest.post<OwnJobResponse>("api/company/own-jobs/$id/clock-in")
    fun clockOut(id: Int) = ApiRequest.post<OwnJobResponse>("api/company/own-jobs/$id/clock-out")

    /// The dispatcher's planned arrival inside the agreed window (org
    /// admins; "HH:MM" on a quarter hour, null = any time).
    fun setArrival(jobId: Int, time: String?) = ApiRequest.json<JobResponse, ArrivalBody>(Method.PATCH, "api/company/jobs/$jobId/arrival", ArrivalBody(time), coder = JsonCoding.explicitNulls)
}
