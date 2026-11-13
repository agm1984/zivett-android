@file:UseSerializers(LaravelInstantSerializer::class)

package com.zivett.app.core.models

import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.ApiRequest.Method
import com.zivett.app.core.network.LaravelInstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

/// The team clock (`App\Support\TeamShifts`, 2026-11-12): a person clocks
/// in when their working day starts and out when it ends, with unpaid
/// breaks between. Business tools companies only. Distinct from the JOB
/// TIMER on own jobs (`MyDayEndpoints.clockIn/clockOut`), which says where
/// the time went.

/// `TeamShifts::state` — `GET api/company/clock`, every clock action's
/// answer, and `shift` on My day. A 404 (no Business tools) reads as
/// [disabled].
@Serializable
data class ClockState(
    val enabled: Boolean = false,
    /// The open shift, null when not clocked in.
    val shift: TeamShift? = null,
    val onBreak: Boolean = false,
    val breakStartedAt: Instant? = null,
    /// Paid minutes today: closed shifts plus the open one, as of the read.
    val todayMinutes: Int = 0,
) {
    val clockedIn: Boolean get() = shift != null

    companion object {
        val disabled = ClockState(enabled = false)
    }
}

/// `Payroll::shiftRow` — one stretch on the clock.
@Serializable
data class TeamShift(
    val id: Int,
    val workDate: String? = null,
    val startedAt: Instant? = null,
    val endedAt: Instant? = null,
    val open: Boolean = false,
    val onBreak: Boolean = false,
    val paidMinutes: Int = 0,
    val breakMinutes: Int = 0,
    /// 'manual' | 'work' (the first job step started the day) | 'admin' | 'added'.
    val startSource: String? = null,
    /// 'manual' | 'auto' (closed for them) | 'admin' | 'added' | null while open.
    val endSource: String? = null,
    val edited: Boolean = false,
    val originalStartedAt: Instant? = null,
    val originalEndedAt: Instant? = null,
    val note: String? = null,
    val approved: Boolean = false,
    val needsReview: Boolean = false,
) {
    companion object {
        const val SOURCE_WORK = "work"
        const val SOURCE_AUTO = "auto"
        const val SOURCE_ADDED = "added"
    }
}

/// `GET api/company/my-hours?date=` — the person's pay period: hours
/// only (rates are the office's), with the overtime split the company's
/// rules give it.
@Serializable
data class MyHours(
    val period: Period,
    /// A date inside the previous / next period, for stepping.
    val previous: String? = null,
    val next: String? = null,
    val totals: Totals = Totals(),
    /// Only days with shifts.
    val days: List<Day> = emptyList(),
    val clock: ClockState? = null,
    val rules: Rules = Rules(),
) {
    @Serializable data class Period(val start: String, val end: String)

    @Serializable
    data class Totals(
        val paidMinutes: Int = 0,
        val regularMinutes: Int = 0,
        val overtimeMinutes: Int = 0,
        val doubleMinutes: Int = 0,
        val breakMinutes: Int = 0,
        val daysWorked: Int = 0,
    )

    @Serializable
    data class Day(
        val date: String,
        val paidMinutes: Int = 0,
        val regularMinutes: Int = 0,
        val overtimeMinutes: Int = 0,
        val doubleMinutes: Int = 0,
        val breakMinutes: Int = 0,
        val shifts: List<TeamShift> = emptyList(),
    )

    /// Hours thresholds; null = that rule is off.
    @Serializable data class Rules(val dailyOt: Double? = null, val dailyDouble: Double? = null, val weeklyOt: Double? = null)
}

/// Add a missed day / fix a shift's times. `note` is required for
/// members (the server 422s `errors.note` without one). `date` keeps the
/// answer on the period being viewed.
@Serializable
data class ShiftBody(
    val startedAt: String,
    val endedAt: String?,
    val breakMinutes: Int,
    val note: String?,
    val date: String? = null,
)

object ClockEndpoints {
    fun state() = ApiRequest.get<ClockState>("api/company/clock")
    fun clockIn() = ApiRequest.post<ClockState>("api/company/clock/in")
    fun clockOut() = ApiRequest.post<ClockState>("api/company/clock/out")
    fun startBreak() = ApiRequest.post<ClockState>("api/company/clock/break")
    fun endBreak() = ApiRequest.json<ClockState>(Method.DELETE, "api/company/clock/break")

    /// `date` null = the period holding today.
    fun myHours(date: String? = null) = ApiRequest.get<MyHours>("api/company/my-hours", date?.let { listOf("date" to it) } ?: emptyList())
    fun addShift(body: ShiftBody) = ApiRequest.post<MyHours, ShiftBody>("api/company/my-hours/shifts", body)
    fun fixShift(id: Int, body: ShiftBody) = ApiRequest.patch<MyHours, ShiftBody>("api/company/my-hours/shifts/$id", body)
}

/// The clock, with "no Business tools" (404) read as disabled rather than
/// an error.
suspend fun ApiClient.clockState(): ClockState = try {
    send(ClockEndpoints.state())
} catch (_: ApiError.NotFound) {
    ClockState.disabled
}
