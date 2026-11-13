package com.zivett.app.features.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.ClockEndpoints
import com.zivett.app.core.models.MyHours
import com.zivett.app.core.models.ShiftBody
import com.zivett.app.core.models.TeamShift
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextArea
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/// The add / fix sheet's fields (web `ShiftModal.vue`): the day, start,
/// end (at or before the start = the next day; null = still on the clock,
/// only when fixing an open shift), unpaid break minutes and the note
/// (required — the person is changing their own hours).
data class ShiftForm(
    val shiftId: Int? = null,
    val day: LocalDate,
    val start: LocalTime = LocalTime.of(8, 0),
    val end: LocalTime? = LocalTime.of(16, 30),
    val breakText: String = "30",
    val note: String = "",
    /// Fixing a shift that's still running: the end may stay blank.
    val stillOpen: Boolean = false,
) {
    val breakMinutes: Int get() = breakText.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
    val canSave: Boolean get() = note.isNotBlank() && (end != null || stillOpen)

    fun paidMinutes(zone: ZoneId = ZoneId.systemDefault()): Int? = end?.let { TeamClockLogic.paidMinutes(day, start, it, breakMinutes, zone) }

    /// "Paid time: 8 h — ends the next day".
    fun paidLine(zone: ZoneId = ZoneId.systemDefault()): String? = paidMinutes(zone)?.let { minutes ->
        "Paid time: ${TeamClockLogic.durationOrZero(minutes)}" + if (end != null && TeamClockLogic.endsNextDay(start, end)) " — ends the next day" else ""
    }

    fun body(periodDate: String?, zone: ZoneId = ZoneId.systemDefault()) = ShiftBody(
        startedAt = TeamClockLogic.iso(day, start, zone),
        endedAt = end?.let { TeamClockLogic.endMoment(day, start, it, zone).toInstant().toString() },
        breakMinutes = breakMinutes,
        note = note.trim().ifEmpty { null },
        date = periodDate,
    )

    companion object {
        /// A missed day: today, 8:00 – 4:30, a 30 minute break.
        fun adding(today: LocalDate = LocalDate.now()) = ShiftForm(day = today)

        /// Fixing a shift: its own times on this device's clock.
        fun fixing(shift: TeamShift, zone: ZoneId = ZoneId.systemDefault()): ShiftForm {
            val started = (shift.startedAt ?: Instant.now()).atZone(zone)
            return ShiftForm(
                shiftId = shift.id,
                day = started.toLocalDate(),
                start = started.toLocalTime().withSecond(0).withNano(0),
                end = shift.endedAt?.atZone(zone)?.toLocalTime()?.withSecond(0)?.withNano(0),
                breakText = shift.breakMinutes.toString(),
                stillOpen = shift.endedAt == null,
            )
        }
    }
}

/// "My hours" (web `pages/company/MyHours.vue`): my pay period on the team
/// clock — day by day with the overtime split, hours only — fixing a
/// wrong day or adding a forgotten one, each with a note.
class MyHoursModel(private val client: ApiClient) {
    var state by mutableStateOf<Loadable<MyHours>>(Loadable.Loading)
    /// A date inside the period asked for; null = the one holding today.
    var date by mutableStateOf<String?>(null)
        private set
    var toast by mutableStateOf<String?>(null)

    /// The sheet: open when non-null.
    var form by mutableStateOf<ShiftForm?>(null)
    var saving by mutableStateOf(false)
        private set
    /// The server's refusal, shown inline: per field, plus a general line.
    var fieldErrors by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var formError by mutableStateOf<String?>(null)
        private set

    val hours: MyHours? get() = state.value

    suspend fun load() { state = state.reloaded { client.send(ClockEndpoints.myHours(date)) } }

    suspend fun step(to: String?) {
        to ?: return
        date = to
        state = Loadable.Loading
        load()
    }

    fun openAdd() = open(ShiftForm.adding())
    fun openFix(shift: TeamShift) = open(ShiftForm.fixing(shift))

    private fun open(value: ShiftForm) {
        fieldErrors = emptyMap()
        formError = null
        form = value
    }

    fun close() { if (!saving) form = null }

    /// Adds or fixes; true when it went through (the sheet closes).
    suspend fun save(zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val current = form ?: return false
        if (saving || !current.canSave) return false
        saving = true
        fieldErrors = emptyMap()
        formError = null
        try {
            val body = current.body(hours?.period?.start ?: date, zone)
            state = Loadable.Loaded(current.shiftId?.let { client.send(ClockEndpoints.fixShift(it, body)) } ?: client.send(ClockEndpoints.addShift(body)))
            toast = if (current.shiftId != null) "Hours fixed — the office will see the change" else "Day added — the office will check it"
            form = null
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError.Validation) {
            fieldErrors = e.errors.firstMessages
            // A refusal with no field (an approved period) is the message itself.
            formError = if (fieldErrors.isEmpty()) e.errors.message else null
            return false
        } catch (e: Exception) {
            formError = e.userMessage
            return false
        } finally {
            saving = false
        }
    }
}

@Composable
fun MyHoursScreen(onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val model = remember { MyHoursModel(environment.client) }
    LaunchedEffect(model) { model.load() }

    Column {
        ZTopBar("My hours", onBack = onBack)
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                val hours = model.hours
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { model.step(hours?.previous) } }, enabled = hours?.previous != null) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous pay period", tint = colors.ink)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        ZHeadline(hours?.let { TeamClockLogic.periodHeading(it.period) } ?: "Pay period")
                        ZCaption("Pay period")
                    }
                    IconButton(onClick = { scope.launch { model.step(hours?.next) } }, enabled = hours?.next != null) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next pay period", tint = colors.ink)
                    }
                }
                ZButton("Add a missed day") { model.openAdd() }

                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { data ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        TotalsStrip(TeamClockLogic.totals(data.totals))
                        TeamClockLogic.rulesLine(data.rules)?.let { ZCaption(it) }
                        if (data.days.isEmpty()) {
                            ZEmptyState(Icons.Outlined.Schedule, "No hours yet", "No hours on the clock in this pay period.")
                        }
                        for (day in data.days) {
                            ZCard {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                                    ZBodyStrong(TeamClockLogic.dayOf(day.date), modifier = Modifier.weight(1f))
                                    ZBodyStrong(TeamClockLogic.formatDuration(day.paidMinutes))
                                }
                                TeamClockLogic.dayOvertime(day)?.let { ZCaption(it) }
                                for (shift in day.shifts) ShiftRow(shift) { model.openFix(shift) }
                            }
                        }
                        Spacer(Modifier.padding(ZSpacing.lg))
                    }
                }
            }
        }
    }

    model.form?.let { form -> ShiftSheet(model, form) }
}

/// Label over value, split evenly (web `RpStatStrip`), wrapping by three.
@Composable
private fun TotalsStrip(items: List<Pair<String, String>>) {
    ZCard(padding = ZSpacing.sm) {
        for (row in items.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                for ((label, value) in row) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ZLabel(label)
                        ZHeadline(value)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ShiftRow(shift: TeamShift, onFix: () -> Unit) {
    val colors = ZTheme.colors
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ZBodyStrong(TeamClockLogic.shiftTimes(shift), tone = ZTextTone.SOFT)
            ZCaption(TeamClockLogic.shiftLine(shift))
            for (flag in TeamClockLogic.flags(shift)) ZCaption(flag, color = colors.warning)
            shift.note?.takeIf { it.isNotBlank() }?.let { ZCaption("“$it”", tone = ZTextTone.SOFT) }
        }
        if (shift.approved) ZBadge("Approved", ZTone.SUCCESS)
        else ZTextAction("Fix times", onClick = onFix)
    }
}

private val pickerDay = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftSheet(model: MyHoursModel, form: ShiftForm) {
    val scope = rememberCoroutineScope()
    var pickingDay by remember { mutableStateOf(false) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    fun update(change: (ShiftForm) -> ShiftForm) { model.form = change(form) }

    ZSheet(onDismiss = { model.close() }, title = if (form.shiftId != null) "Fix these hours" else "Add a missed day", dismissable = !model.saving) {
        Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
            model.formError?.let { ZBody(it, color = ZTheme.colors.danger) }
            PickerField("Day", pickerDay.format(form.day)) { pickingDay = true }
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                Column(Modifier.weight(1f)) { PickerField("Started", clockText(form.start), model.fieldErrors["started_at"]) { pickingStart = true } }
                Column(Modifier.weight(1f)) {
                    PickerField(if (form.stillOpen) "Ended (blank = still on)" else "Ended", form.end?.let { clockText(it) } ?: "Still on the clock", model.fieldErrors["ended_at"]) { pickingEnd = true }
                    if (form.stillOpen && form.end != null) ZTextAction("Still on the clock") { update { it.copy(end = null) } }
                }
            }
            ZTextField(
                "Unpaid break (minutes)", form.breakText, { text -> update { it.copy(breakText = text.filter(Char::isDigit).take(3)) } },
                keyboardType = KeyboardType.Number, error = model.fieldErrors["break_minutes"],
            )
            form.paidLine()?.let { ZBody(it, tone = ZTextTone.SOFT) }
            ZTextArea("What happened? (required)", form.note, { text -> update { it.copy(note = text.take(300)) } }, placeholder = "Forgot to clock in at the yard", error = model.fieldErrors["note"], minLines = 2)
            ZCaption("The office sees your note and the original times, and approves the pay period.")
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                ZButton("Cancel", style = ZButtonStyle.OUTLINE, modifier = Modifier.weight(1f), enabled = !model.saving) { model.close() }
                ZButton("Save", modifier = Modifier.weight(1f), loading = model.saving, enabled = form.canSave) { scope.launch { model.save() } }
            }
            Spacer(Modifier.padding(ZSpacing.sm))
        }
    }

    if (pickingDay) {
        val todayMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = form.day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDay = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { millis -> update { it.copy(day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) } }
                pickingDay = false
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickingDay = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
    if (pickingStart) TimeDialog(form.start, { pickingStart = false }) { time -> update { it.copy(start = time) } }
    if (pickingEnd) TimeDialog(form.end ?: LocalTime.now().withSecond(0).withNano(0), { pickingEnd = false }) { time -> update { it.copy(end = time) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

@Composable
private fun PickerField(label: String, value: String, error: String? = null, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ZLabel(label)
        ZButton(value, style = ZButtonStyle.OUTLINE, onClick = onClick)
        error?.let { ZCaption(it, color = ZTheme.colors.danger) }
    }
}

private fun clockText(time: LocalTime): String = MyDayLogic.formatClock(String.format(Locale.US, "%02d:%02d", time.hour, time.minute))
