package com.zivett.app.features.company

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.zivett.app.core.models.ClockState
import com.zivett.app.core.models.MyHours
import com.zivett.app.core.models.TeamShift
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZFlowRow
import com.zivett.app.design.ZRadius
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/// The team clock's pure rules (web `lib/payroll.js`, `ClockCard.vue`,
/// `ShiftModal.vue`, `MyHours.vue`). Tested in `TeamClockTest`.
object TeamClockLogic {
    /// "7 h 30 min" / "7 h 05 min" / "7 h" / "45 min" / "—" (nothing).
    fun formatDuration(minutes: Int): String {
        if (minutes <= 0) return "—"
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0 -> "$rest min"
            rest == 0 -> "$hours h"
            else -> String.format(Locale.US, "%d h %02d min", hours, rest)
        }
    }

    /// [formatDuration], but zero reads "0 min" (a clock just started).
    fun durationOrZero(minutes: Int): String = if (minutes <= 0) "0 min" else formatDuration(minutes)

    private val clockFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    /// "7:02 am" on this device's clock.
    fun timeOf(moment: Instant?, zone: ZoneId = ZoneId.systemDefault()): String =
        moment?.let { clockFormat.withZone(zone).format(it).lowercase(Locale.US) } ?: ""

    private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

    /// "Thu, Nov 12" from "2026-11-12".
    fun dayOf(date: String): String = runCatching { dayFormat.format(LocalDate.parse(date)) }.getOrDefault(date)

    /// "Mon, Nov 9 – Sun, Nov 22".
    fun periodHeading(period: MyHours.Period): String = "${dayOf(period.start)} – ${dayOf(period.end)}"

    // MARK: The clock card

    /// Paid so far today: the server's figure plus the minutes since it
    /// was read, while on the clock and not on a break.
    fun todayMinutes(state: ClockState, loadedAt: Instant, now: Instant): Int {
        val extra = if (state.clockedIn && !state.onBreak) Duration.between(loadedAt, now).toMinutes().toInt() else 0
        return state.todayMinutes + maxOf(0, extra)
    }

    /// How long the current break has run.
    fun breakMinutes(state: ClockState, now: Instant): Int =
        state.breakStartedAt?.let { maxOf(0, Duration.between(it, now).toMinutes().toInt()) } ?: 0

    /// "You're not clocked in" / "On a break · 12 min" / "On the clock since 7:02 am".
    fun cardTitle(state: ClockState, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val shift = state.shift ?: return "You're not clocked in"
        return if (state.onBreak) "On a break · ${durationOrZero(breakMinutes(state, now))}"
        else "On the clock since ${timeOf(shift.startedAt, zone)}"
    }

    /// "3 h 10 min paid today", or the nudge when nothing's been worked.
    fun cardLine(state: ClockState, loadedAt: Instant, now: Instant): String {
        val today = todayMinutes(state, loadedAt, now)
        return if (state.clockedIn || today > 0) "${durationOrZero(today)} paid today" else "Clock in when your working day starts."
    }

    const val STARTED_AT_WORK = "We started your day at your first job step. If you started earlier, fix it on My hours."

    /// The first job step started the day for them — say so.
    fun startedByWork(state: ClockState): Boolean = state.shift?.startSource == TeamShift.SOURCE_WORK

    /// "End your day?" — what pressing it does, said once.
    fun endDayMessage(now: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        "You'll be clocked out at ${timeOf(now, zone)}. A job timer still running stops too."

    // MARK: My hours

    /// Why a shift needs a look, in the person's own words.
    fun flags(shift: TeamShift): List<String> = listOfNotNull(
        "Started at your first job step".takeIf { shift.startSource == TeamShift.SOURCE_WORK },
        "Closed automatically — you forgot to clock out".takeIf { shift.endSource == TeamShift.SOURCE_AUTO },
        "Added after the fact".takeIf { shift.startSource == TeamShift.SOURCE_ADDED },
        "Times changed".takeIf { shift.edited },
    )

    /// "7:02 am – 3:30 pm" / "7:02 am – now".
    fun shiftTimes(shift: TeamShift, zone: ZoneId = ZoneId.systemDefault()): String =
        "${timeOf(shift.startedAt, zone)} – ${shift.endedAt?.let { timeOf(it, zone) } ?: "now"}"

    /// "30 min break · 7 h 30 min paid".
    fun shiftLine(shift: TeamShift): String =
        listOfNotNull(shift.breakMinutes.takeIf { it > 0 }?.let { "${formatDuration(it)} break" }, "${formatDuration(shift.paidMinutes)} paid").joinToString(" · ")

    /// "incl. 2 h overtime" on a day with any.
    fun dayOvertime(day: MyHours.Day): String? =
        (day.overtimeMinutes + day.doubleMinutes).takeIf { it > 0 }?.let { "incl. ${formatDuration(it)} overtime" }

    /// The totals strip: Paid hours, Regular, Overtime (1.5×), Double time
    /// (only when there is some), Days worked.
    fun totals(totals: MyHours.Totals): List<Pair<String, String>> = listOfNotNull(
        "Paid hours" to formatDuration(totals.paidMinutes),
        "Regular" to formatDuration(totals.regularMinutes),
        "Overtime (1.5×)" to formatDuration(totals.overtimeMinutes),
        totals.doubleMinutes.takeIf { it > 0 }?.let { "Double time" to formatDuration(it) },
        "Days worked" to totals.daysWorked.toString(),
    )

    private fun hours(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    /// "Your company's rules: over 8 h a day is time-and-a-half; …." — null
    /// when no rule is on.
    fun rulesLine(rules: MyHours.Rules): String? {
        val parts = listOfNotNull(
            rules.dailyOt?.takeIf { it > 0 }?.let { "over ${hours(it)} h a day is time-and-a-half" },
            rules.dailyDouble?.takeIf { it > 0 }?.let { "over ${hours(it)} h is double time" },
            rules.weeklyOt?.takeIf { it > 0 }?.let { "over ${hours(it)} regular hours a week is time-and-a-half" },
        )
        return if (parts.isEmpty()) null else "Your company's rules: ${parts.joinToString("; ")}."
    }

    // MARK: Adding or fixing a shift

    /// The shift's end on the right day: an end at or before the start ran
    /// past midnight, so it lands on the next day.
    fun endMoment(day: LocalDate, start: LocalTime, end: LocalTime, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
        ZonedDateTime.of(if (!end.isAfter(start)) day.plusDays(1) else day, end, zone)

    fun endsNextDay(start: LocalTime, end: LocalTime): Boolean = !end.isAfter(start)

    /// Paid time for the sheet's live line: end − start − break, never negative.
    fun paidMinutes(day: LocalDate, start: LocalTime, end: LocalTime, breakMinutes: Int, zone: ZoneId = ZoneId.systemDefault()): Int {
        val from = ZonedDateTime.of(day, start, zone)
        val minutes = Duration.between(from, endMoment(day, start, end, zone)).toMinutes().toInt() - maxOf(0, breakMinutes)
        return maxOf(0, minutes)
    }

    /// The ISO moment the server takes ("2026-11-12T15:00:00Z").
    fun iso(day: LocalDate, time: LocalTime, zone: ZoneId = ZoneId.systemDefault()): String = ZonedDateTime.of(day, time, zone).toInstant().toString()

    /// "08:00" ⇄ LocalTime, for the sheet's fields.
    fun parseTime(text: String): LocalTime? = runCatching { LocalTime.parse(text.trim().let { if (it.length == 4 && it[1] == ':') "0$it" else it }) }.getOrNull()
}

/// The team clock's one card on My day (web `ClockCard.vue`): Start my day
/// → Take a break / End my day → back. `now` ticks from the screen.
@Composable
fun ClockCard(
    state: ClockState,
    loadedAt: Instant,
    now: Instant,
    busy: Boolean,
    onStart: () -> Unit,
    onBreak: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
) {
    val colors = ZTheme.colors
    val (dot, edge) = when {
        !state.clockedIn -> colors.borderStrong to colors.border
        state.onBreak -> colors.warning to colors.warning.copy(alpha = 0.4f)
        else -> colors.success to colors.success.copy(alpha = 0.4f)
    }
    val shape = RoundedCornerShape(ZRadius.card)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, edge, shape).padding(ZSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ZBodyStrong(TeamClockLogic.cardTitle(state, now))
                ZCaption(TeamClockLogic.cardLine(state, loadedAt, now))
            }
        }
        ZFlowRow(spacing = ZSpacing.xs) {
            if (!state.clockedIn) {
                ZButton("Start my day", compact = true, fullWidth = false, loading = busy, onClick = onStart)
            } else {
                if (state.onBreak) ZButton("End break", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false, enabled = !busy, onClick = onResume)
                else ZButton("Take a break", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false, enabled = !busy, onClick = onBreak)
                ZButton("End my day", compact = true, fullWidth = false, loading = busy, onClick = onEnd)
            }
        }
        if (TeamClockLogic.startedByWork(state)) ZCaption(TeamClockLogic.STARTED_AT_WORK)
    }
}

/// "End my day" asks once — the day's hours close on it.
@Composable
fun EndDayDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("End your day?") },
        text = { Text(TeamClockLogic.endDayMessage(Instant.now())) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("End my day") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep working") } },
    )
}
