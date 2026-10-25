package com.zivett.app.features.company

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zivett.app.app.CompanyJobRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.AvailabilityResponse
import com.zivett.app.core.models.AvailabilitySettingsBody
import com.zivett.app.core.models.CompanyCalendar
import com.zivett.app.core.models.CompanyCalendar.CalendarJob
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.WeeklyBlock
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZAvatar
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZPhotoAvatar
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSectionHeader
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTopBar
import com.zivett.app.design.ZType
import com.zivett.app.features.company.CalendarLogic.TechFilter
import com.zivett.app.features.shared.JobStatusBadge
import com.zivett.app.features.shared.LocalNav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/// The company calendar (`Company\CalendarController`) as a dispatch
/// tool: a month grid or a week board over the same payload, a day sheet
/// for everything about one date, and (admins) the availability settings.
class CompanyCalendarModel(private val client: ApiClient, today: LocalDate = LocalDate.now()) {
    enum class Mode { MONTH, WEEK }

    var mode by mutableStateOf(Mode.MONTH)
    var month by mutableStateOf(YearMonth.from(today))
    var weekStart by mutableStateOf(CalendarLogic.weekStart(today))
    var state by mutableStateOf<Loadable<CompanyCalendar>>(Loadable.Loading)
    var filter by mutableStateOf<TechFilter>(TechFilter.Everyone)
    var toast by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)

    val calendar: CompanyCalendar? get() = state.value

    suspend fun load() {
        state = state.reloaded {
            if (mode == Mode.MONTH) client.send(CompanyEndpoints.monthCalendar(CalendarLogic.monthKey(month)))
            else client.send(CompanyEndpoints.weekCalendar(CalendarLogic.dateKey(weekStart)))
        }
    }

    suspend fun shift(delta: Int) {
        if (mode == Mode.MONTH) month = month.plusMonths(delta.toLong()) else weekStart = weekStart.plusWeeks(delta.toLong())
        state = Loadable.Loading
        load()
    }

    /// Switching keeps the place: a month opens on its first week (this
    /// week when it's the current month); a week opens its month.
    suspend fun switchTo(next: Mode, today: LocalDate = LocalDate.now()) {
        if (next == mode) return
        if (next == Mode.WEEK) weekStart = CalendarLogic.weekStart(if (YearMonth.from(today) == month) today else month.atDay(1))
        else month = YearMonth.from(weekStart)
        mode = next
        state = Loadable.Loading
        load()
    }

    suspend fun goToday(today: LocalDate = LocalDate.now()) {
        month = YearMonth.from(today); weekStart = CalendarLogic.weekStart(today)
        state = Loadable.Loading
        load()
    }

    /// Runs one change, reloads the calendar, and toasts the outcome.
    private suspend fun act(success: String, operation: suspend () -> Unit): Boolean {
        busy = true
        try {
            operation()
            toast = success
            load()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast = e.userMessage
        } finally {
            busy = false
        }
        return false
    }

    private fun label(date: String) = CalendarLogic.dayTitle(date)

    suspend fun blockDay(date: String) = act("${label(date)} blocked — scheduled jobs that day leave your feed") { client.send(CompanyEndpoints.blockDate(date)) }
    suspend fun blockWindow(date: String, window: String) = act("${CalendarLogic.windowTag(window)} blocked on ${label(date)}") { client.send(CompanyEndpoints.blockDate(date, window)) }
    suspend fun unblock(row: AvailabilityResponse.BlockedDate) = act(if (row.window == null) "${label(row.date)} reopened" else "${CalendarLogic.windowTag(row.window)} reopened on ${label(row.date)}") { client.send(CompanyEndpoints.unblockDate(row.id)) }

    /// Reopens the whole date — every block row on it.
    suspend fun reopenDay(date: String) = act("${label(date)} reopened") {
        calendar?.blockedDates?.filter { it.date == date }?.forEach { client.send(CompanyEndpoints.unblockDate(it.id)) }
    }

    suspend fun addTimeOff(userId: Int, name: String, date: String, window: String?) =
        act("$name is off ${window?.let { "the ${CalendarLogic.windowTag(it).lowercase()} of " } ?: ""}${label(date)}") { client.send(CompanyEndpoints.addTimeOff(userId, date, window)) }

    suspend fun removeTimeOff(row: CompanyCalendar.TimeOff) = act("Time off removed") { client.send(CompanyEndpoints.removeTimeOff(row.id)) }

    /// Saves what changed: working days through the details PATCH, the
    /// weekly window blocks + cap through the availability PATCH.
    suspend fun saveSettings(workingDays: Set<Int>, weekly: List<WeeklyBlock>, capacity: Int?): Boolean {
        val current = calendar ?: return false
        val days = CalendarLogic.workingDaysBody(workingDays)
        val daysChanged = days != current.workingDays?.sorted()
        val availabilityChanged = weekly.toSet() != current.blockedWindows.orEmpty().toSet() || capacity != current.windowCapacity
        if (!daysChanged && !availabilityChanged) return true
        return act("Calendar settings saved") {
            if (daysChanged) client.send(CompanyEndpoints.updateWorkingDays(days))
            if (availabilityChanged) client.send(CompanyEndpoints.updateAvailability(AvailabilitySettingsBody(weekly.ifEmpty { null }, capacity)))
        }
    }
}

/// What the calendar is showing on top of itself — one sheet at a time;
/// the move / start / quote sheets return to the day they came from.
sealed interface CalendarSheet {
    data class Day(val date: String) : CalendarSheet
    data object Settings : CalendarSheet
    data class Move(val jobId: Int, val code: String?, val date: String) : CalendarSheet
    data class Start(val jobId: Int, val date: String) : CalendarSheet
    data class Quote(val jobId: Int, val date: String) : CalendarSheet
}

@Composable
fun CompanyCalendarScreen(onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val model = remember { CompanyCalendarModel(environment.client) }
    var sheet by remember { mutableStateOf<CalendarSheet?>(null) }
    val today = CalendarLogic.dateKey(LocalDate.now())
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    LaunchedEffect(model) { model.load() }

    Column {
        ZTopBar("Calendar", onBack = onBack) {
            if (model.calendar?.canManage == true) IconButton(onClick = { sheet = CalendarSheet.Settings }) { Icon(Icons.Outlined.Tune, contentDescription = "Calendar settings", tint = colors.ink) }
        }
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = model.mode == CompanyCalendarModel.Mode.MONTH, onClick = { scope.launch { model.switchTo(CompanyCalendarModel.Mode.MONTH) } }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Month") }
                    SegmentedButton(selected = model.mode == CompanyCalendarModel.Mode.WEEK, onClick = { scope.launch { model.switchTo(CompanyCalendarModel.Mode.WEEK) } }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Week") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val week = model.mode == CompanyCalendarModel.Mode.WEEK
                    IconButton(onClick = { scope.launch { model.shift(-1) } }) { Icon(Icons.Filled.ChevronLeft, contentDescription = if (week) "Previous week" else "Previous month", tint = colors.ink) }
                    Spacer(Modifier.weight(1f))
                    ZHeadline(if (week) CalendarLogic.weekLabel(model.weekStart) else model.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)))
                    Spacer(Modifier.weight(1f))
                    ZTextAction("Today") { scope.launch { model.goToday() } }
                    IconButton(onClick = { scope.launch { model.shift(1) } }) { Icon(Icons.Filled.ChevronRight, contentDescription = if (week) "Next week" else "Next month", tint = colors.ink) }
                }
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { calendar ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        if (calendar.canManage && calendar.team.size > 1) TechFilterRow(calendar, model.filter) { model.filter = it }
                        if (model.mode == CompanyCalendarModel.Mode.MONTH) {
                            MonthGrid(calendar, model.month, model.filter, today, wide) { sheet = CalendarSheet.Day(it) }
                            Legend()
                            ZCaption("Tap a day for its jobs, map, openings, time off and blocks.")
                            val attention = CalendarLogic.filtered(calendar.jobs, model.filter).filter { CalendarLogic.warns(it) && it.date >= today }
                            if (attention.isNotEmpty()) {
                                ZSectionHeader("Needs attention")
                                for (job in attention) CalendarJobRow(job) { nav.navigate(CompanyJobRoute(job.id)) }
                            }
                            val jobs = CalendarLogic.filtered(calendar.jobs, model.filter).filter { !CalendarLogic.warns(it) || it.date < today }
                            if (jobs.isNotEmpty()) {
                                ZSectionHeader("This month")
                                for (job in jobs) CalendarJobRow(job) { nav.navigate(CompanyJobRoute(job.id)) }
                            }
                        } else {
                            if (wide) WeekBoardGrid(calendar, model.weekStart, model.filter, today, onDay = { sheet = CalendarSheet.Day(it) }, onJob = { nav.navigate(CompanyJobRoute(it)) })
                            else WeekBoardList(calendar, model.weekStart, model.filter, today, onDay = { sheet = CalendarSheet.Day(it) }, onJob = { nav.navigate(CompanyJobRoute(it)) })
                        }
                        Spacer(Modifier.padding(ZSpacing.lg))
                    }
                }
            }
        }
    }

    val calendar = model.calendar
    when (val current = sheet) {
        is CalendarSheet.Day -> if (calendar != null) CalendarDaySheet(
            date = current.date, calendar = calendar, model = model, me = environment.session.user?.id, today = today,
            onDismiss = { sheet = null },
            onOpenJob = { sheet = null; nav.navigate(CompanyJobRoute(it)) },
            onMove = { job -> sheet = CalendarSheet.Move(job.id, job.code, current.date) },
            onStart = { job -> sheet = CalendarSheet.Start(job.id, current.date) },
            onQuote = { opening -> sheet = CalendarSheet.Quote(opening.id, current.date) },
        )
        CalendarSheet.Settings -> if (calendar != null) CalendarSettingsSheet(calendar, model) { sheet = null }
        is CalendarSheet.Move -> MoveVisitSheet(current.jobId, current.code, environment.client, onDismiss = { sheet = CalendarSheet.Day(current.date) }) { _, message ->
            model.toast = message; sheet = CalendarSheet.Day(current.date); scope.launch { model.load() }
        }
        is CalendarSheet.Start -> StartFromCalendar(current.jobId, environment.client, onDismiss = { sheet = CalendarSheet.Day(current.date) }) { message ->
            model.toast = message; scope.launch { model.load() }
        }
        is CalendarSheet.Quote -> QuoteFromCalendar(current.jobId, environment.client, isAdmin = calendar?.canManage == true, onDismiss = { sheet = CalendarSheet.Day(current.date) }) { message ->
            model.toast = message; sheet = CalendarSheet.Day(current.date); scope.launch { model.load() }
        }
        null -> Unit
    }
}

@Composable
private fun TechFilterRow(calendar: CompanyCalendar, filter: TechFilter, onChange: (TechFilter) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = filter == TechFilter.Everyone, onClick = { onChange(TechFilter.Everyone) }, label = { Text("Everyone") })
        for (member in calendar.team) {
            FilterChip(
                selected = filter == TechFilter.Member(member.id), onClick = { onChange(TechFilter.Member(member.id)) }, label = { Text(member.display) },
                leadingIcon = { ZPhotoAvatar(member.photoUrl, member.display.take(1), 20.dp) },
            )
        }
        FilterChip(selected = filter == TechFilter.Unassigned, onClick = { onChange(TechFilter.Unassigned) }, label = { Text("Unassigned") })
    }
}

@Composable
private fun Legend() {
    val colors = ZTheme.colors
    com.zivett.app.design.ZFlowRow(spacing = ZSpacing.sm) {
        LegendSwatch(colors.navy, "Job"); LegendSwatch(colors.warningSoft, "Needs action", border = colors.warning)
        LegendSwatch(Color.Transparent, "Proposed / moving", dashed = true); LegendSwatch(colors.dangerSoft, "Blocked"); LegendSwatch(colors.surfaceSunken, "Off day")
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String, border: Color? = null, dashed: Boolean = false) {
    val colors = ZTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color).then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(3.dp)) else Modifier).then(if (dashed) Modifier.dashedBorder(colors.inkMuted) else Modifier))
        ZCaption(label)
    }
}

/// A dashed outline — the ghost treatment for proposals and moves.
fun Modifier.dashedBorder(color: Color, radius: Dp = 4.dp): Modifier = drawBehind {
    drawRoundRect(color = color, style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))), cornerRadius = CornerRadius(radius.toPx()))
}

@Composable
private fun MonthGrid(calendar: CompanyCalendar, month: YearMonth, filter: TechFilter, today: String, wide: Boolean, onDay: (String) -> Unit) {
    val colors = ZTheme.colors
    val days = month.lengthOfMonth()
    val lead = month.atDay(1).dayOfWeek.value - 1 // Monday-first
    val perCell = if (wide) 3 else 2
    val minHeight = if (wide) 96.dp else 74.dp

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (name in listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")) ZMono(name, modifier = Modifier.weight(1f))
        }
        for (week in 0 until (lead + days + 6) / 7) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (slot in 0 until 7) {
                    val dayNumber = week * 7 + slot - lead + 1
                    if (dayNumber < 1 || dayNumber > days) { Spacer(Modifier.weight(1f).heightIn(min = minHeight)); continue }
                    val date = month.atDay(dayNumber)
                    val key = CalendarLogic.dateKey(date)
                    val working = CalendarLogic.isWorkingDay(calendar, date)
                    val wholeDay = CalendarLogic.wholeDayBlock(calendar, key) != null
                    val someBlocked = CalendarLogic.anyBlock(calendar, key)
                    val jobs = CalendarLogic.jobsOn(calendar, key, filter)
                    val moves = calendar.moves.filter { it.date == key }
                    val tentative = calendar.tentative.orEmpty().filter { it.date == key }
                    val openings = CalendarLogic.openingsOn(calendar, key, today)
                    val isPast = key < today
                    val anyOff = calendar.timeOff.any { it.date == key }
                    Column(
                        modifier = Modifier.weight(1f).heightIn(min = minHeight).clip(RoundedCornerShape(6.dp))
                            .background(if (wholeDay) colors.dangerSoft else if (!working) colors.surfaceSunken else colors.surface)
                            .then(if (key == today) Modifier.border(1.dp, colors.navy, RoundedCornerShape(6.dp)) else Modifier)
                            .clickable { onDay(key) }
                            .semantics { contentDescription = "${CalendarLogic.dayTitle(key)}, ${jobs.size} jobs${if (openings.isNotEmpty()) ", ${CalendarLogic.openingsLabel(openings.size)}" else ""}" }
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(dayNumber.toString(), style = ZType.caption.copy(fontSize = 11.sp, fontWeight = if (key == today) FontWeight.Bold else FontWeight.Medium), color = if (isPast) colors.inkFaint else colors.ink)
                            // A partly blocked day (one window) or someone off: a quiet dot.
                            if (someBlocked && !wholeDay) Box(Modifier.size(5.dp).clip(CircleShape).background(colors.danger))
                            if (anyOff) Box(Modifier.size(5.dp).clip(CircleShape).background(colors.inkMuted))
                        }
                        var shown = 0
                        for (job in jobs.take(perCell)) { JobChip(job, wide); shown++ }
                        for (move in moves.take(perCell - shown)) { GhostChip("→${CalendarChips.chipLabel(move.window, move.jobCode)}"); shown++ }
                        for (row in tentative.take(maxOf(0, perCell - shown))) { GhostChip(CalendarChips.chipLabel(row.window, row.jobCode, tentative = true)); shown++ }
                        val overflow = jobs.size + moves.size + tentative.size - shown
                        if (overflow > 0) ZMono("+$overflow")
                        if (openings.isNotEmpty()) {
                            Text(if (wide) CalendarLogic.openingsLabel(openings.size) else "${openings.size} open", style = ZType.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold), color = colors.link, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/// A month-cell job: the lead's face + window initial + code. Jobs still
/// waiting on a crew, a start, or overdue to set off get the warning tint.
@Composable
private fun JobChip(job: CalendarJob, wide: Boolean) {
    val colors = ZTheme.colors
    val warn = CalendarLogic.warns(job)
    val lead = job.lead
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp).clip(RoundedCornerShape(4.dp))
            .background(if (warn) colors.warningSoft else colors.navy)
            .then(if (warn) Modifier.border(1.dp, if (job.overdue) colors.danger else colors.warning, RoundedCornerShape(4.dp)) else Modifier)
            .padding(horizontal = 2.dp, vertical = 1.5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (lead != null) ZPhotoAvatar(lead.photoUrl, lead.name.take(1), if (wide) 14.dp else 11.dp)
        Text(
            if (wide) "${CalendarLogic.windowTag(job.window).take(1)} · ${job.code ?: "—"}" else CalendarChips.chipLabel(job.window, job.code),
            style = ZType.mono.copy(fontSize = if (wide) 10.sp else 8.sp, letterSpacing = 0.sp),
            color = if (warn) colors.ink else colors.onBrand, maxLines = 1, overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun GhostChip(label: String) {
    val colors = ZTheme.colors
    Text(
        label, style = ZType.mono.copy(fontSize = 8.sp, letterSpacing = 0.sp), color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Clip,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp).dashedBorder(colors.inkMuted).padding(horizontal = 3.dp, vertical = 1.5.dp),
    )
}

/// A list row for one job: window + code, title, lead's face, flag.
@Composable
fun CalendarJobRow(job: CalendarJob, onClick: () -> Unit) {
    ZCard(padding = ZSpacing.sm, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
            CrewFaces(job, 28.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ZBodyStrong(job.title, maxLines = 1)
                ZCaption(listOfNotNull(job.code, CalendarLogic.slotLine(job.date, job.window), job.lead?.name).joinToString(" · "), maxLines = 1)
            }
            val flag = CalendarLogic.flag(job)
            if (flag != null) ZBadge(flag.label, flag.tone) else JobStatusBadge(job.status)
        }
    }
}

/// The crew's faces, lead first (overlapping circles); "?" when nobody's on it.
@Composable
fun CrewFaces(job: CalendarJob, size: Dp) {
    if (job.crew.isEmpty()) { ZAvatar("?", size = size); return }
    Box(modifier = Modifier.width(size + (size * 0.55f) * (minOf(job.crew.size, 3) - 1))) {
        job.crew.take(3).forEachIndexed { index, tech ->
            Box(Modifier.padding(start = size * 0.55f * index).clip(CircleShape).border(1.5.dp, ZTheme.colors.surface, CircleShape)) {
                ZPhotoAvatar(tech.photoUrl, tech.name.take(1), size)
            }
        }
    }
}

// Week board

/// Large screens: rows = team members (+ Unassigned), columns = the 7
/// days; cells list that tech's jobs; time off shades the cell "Off";
/// headers show each window's load against the cap.
@Composable
private fun WeekBoardGrid(calendar: CompanyCalendar, start: LocalDate, filter: TechFilter, today: String, onDay: (String) -> Unit, onJob: (Int) -> Unit) {
    val colors = ZTheme.colors
    val days = CalendarLogic.weekDays(start)
    val rows = CalendarLogic.boardRows(calendar, filter)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Spacer(Modifier.width(96.dp))
            for (day in days) {
                val key = CalendarLogic.dateKey(day)
                DayHeader(calendar, day, key, today, Modifier.weight(1f)) { onDay(key) }
            }
        }
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(modifier = Modifier.width(96.dp).heightIn(min = 64.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (row.member != null) ZPhotoAvatar(row.member.photoUrl, row.member.display.take(1), 24.dp) else ZAvatar("?", size = 24.dp)
                    Text(row.label, style = ZType.caption, color = colors.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                for (day in days) {
                    val key = CalendarLogic.dateKey(day)
                    val off = row.member?.let { CalendarLogic.offLabel(calendar, it.id, key) }
                    val jobs = row.jobsByDate[key].orEmpty()
                    Column(
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (off != null) colors.surfaceSunken else if (!CalendarLogic.isWorkingDay(calendar, day)) colors.surfaceAlt else colors.surface)
                            .border(1.dp, colors.borderSoft, RoundedCornerShape(6.dp))
                            .clickable { onDay(key) }
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        off?.let { Text(it, style = ZType.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold), color = colors.inkMuted, maxLines = 1) }
                        for (job in jobs) BoardChip(job) { onJob(job.id) }
                    }
                }
            }
        }
        if (rows.isEmpty()) ZCaption("No one to show for this filter.")
    }
}

@Composable
private fun DayHeader(calendar: CompanyCalendar, day: LocalDate, key: String, today: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = ZTheme.colors
    val openings = CalendarLogic.openingsOn(calendar, key, today)
    val full = CalendarLogic.dayHasFullWindow(calendar, key)
    Column(
        modifier = modifier.clip(RoundedCornerShape(6.dp)).then(if (key == today) Modifier.border(1.dp, colors.navy, RoundedCornerShape(6.dp)) else Modifier)
            .background(if (CalendarLogic.wholeDayBlock(calendar, key) != null) colors.dangerSoft else Color.Transparent)
            .clickable(onClick = onClick).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(CalendarLogic.dayShort(day), style = ZType.caption.copy(fontWeight = FontWeight.SemiBold), color = if (key < today) colors.inkFaint else colors.ink, maxLines = 1)
        CalendarLogic.dayLoadLabel(calendar, key)?.let { Text(it, style = ZType.mono.copy(fontSize = 8.sp, letterSpacing = 0.sp), color = if (full) colors.danger else colors.inkMuted, maxLines = 1) }
        if (openings.isNotEmpty()) Text(CalendarLogic.openingsLabel(openings.size), style = ZType.caption.copy(fontSize = 10.sp), color = colors.link, maxLines = 1)
    }
}

/// A board cell entry: window tag + code (warning-tinted when it needs action).
@Composable
private fun BoardChip(job: CalendarJob, onClick: () -> Unit) {
    val colors = ZTheme.colors
    val warn = CalendarLogic.warns(job)
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(if (warn) colors.warningSoft else colors.navy)
            .then(if (warn) Modifier.border(1.dp, if (job.overdue) colors.danger else colors.warning, RoundedCornerShape(4.dp)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(CalendarLogic.windowTag(job.window), style = ZType.caption.copy(fontSize = 9.sp), color = if (warn) colors.inkSoft else colors.onBrand.copy(alpha = 0.8f), maxLines = 1)
        Text(job.code ?: job.title, style = ZType.mono.copy(fontSize = 9.sp, letterSpacing = 0.sp), color = if (warn) colors.ink else colors.onBrand, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/// Phones: the same board as one card per day, grouped by tech.
@Composable
private fun WeekBoardList(calendar: CompanyCalendar, start: LocalDate, filter: TechFilter, today: String, onDay: (String) -> Unit, onJob: (Int) -> Unit) {
    val colors = ZTheme.colors
    val rows = CalendarLogic.boardRows(calendar, filter)
    for (day in CalendarLogic.weekDays(start)) {
        val key = CalendarLogic.dateKey(day)
        val openings = CalendarLogic.openingsOn(calendar, key, today)
        ZCard(padding = ZSpacing.sm) {
            Row(modifier = Modifier.fillMaxWidth().clickable { onDay(key) }, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ZBodyStrong(CalendarLogic.dayTitle(key) + if (key == today) " · Today" else "", color = if (key < today) colors.inkMuted else null)
                    ZCaption(listOfNotNull(CalendarLogic.dayStatus(calendar, key), CalendarLogic.dayLoadLabel(calendar, key)).joinToString(" · "))
                }
                if (openings.isNotEmpty()) ZBadge(CalendarLogic.openingsLabel(openings.size), com.zivett.app.design.ZTone.INFO)
                Icon(Icons.Filled.ChevronRight, contentDescription = "Open day", tint = colors.inkMuted)
            }
            var any = false
            for (row in rows) {
                val jobs = row.jobsByDate[key].orEmpty()
                val off = row.member?.let { CalendarLogic.offLabel(calendar, it.id, key) }
                if (jobs.isEmpty() && off == null) continue
                any = true
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (row.member != null) ZPhotoAvatar(row.member.photoUrl, row.member.display.take(1), 22.dp) else ZAvatar("?", size = 22.dp)
                    ZCaption(row.label, color = colors.ink)
                    off?.let { ZBadge(it) }
                }
                for (job in jobs) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { onJob(job.id) }.padding(start = 28.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ZMono(CalendarLogic.windowTag(job.window))
                        ZCaption(listOfNotNull(job.code, job.title).joinToString(" · "), color = colors.ink, maxLines = 1, modifier = Modifier.weight(1f))
                        CalendarLogic.flag(job)?.let { ZBadge(it.label, it.tone) }
                    }
                }
            }
            if (!any) ZCaption("Nothing booked")
        }
    }
}
