package com.zivett.app.features.company

import com.zivett.app.core.models.CompanyCalendar
import com.zivett.app.core.models.CompanyCalendar.CalendarJob
import com.zivett.app.core.models.RescheduleSlots
import com.zivett.app.core.models.WeeklyBlock
import com.zivett.app.design.ZTone
import com.zivett.app.features.customer.jobs.JobPresentation
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/// The calendar's pure rules — what the month grid, the week board, the
/// day sheet and the move picker derive from the payload. Kept free of
/// Compose so it's unit-tested (`CalendarDispatchTest`).
object CalendarLogic {
    /// `Availability::WINDOWS`, in day order.
    val windows: List<String> = listOf("morning", "afternoon", "evening")

    /// Sort key: instant ("Now", null window) first, then the windows.
    fun windowOrder(window: String?): Int = if (window == null) 0 else windows.indexOf(window).let { if (it < 0) windows.size + 1 else it + 1 }

    fun dateKey(date: LocalDate): String = String.format(Locale.US, "%04d-%02d-%02d", date.year, date.monthValue, date.dayOfMonth)
    fun parse(date: String): LocalDate? = runCatching { LocalDate.parse(date) }.getOrNull()
    fun monthKey(month: YearMonth): String = String.format(Locale.US, "%04d-%02d", month.year, month.monthValue)
    fun parseMonth(month: String): YearMonth? = runCatching { YearMonth.parse(month) }.getOrNull()

    /// The Monday that starts `date`'s week (the server's `week=` range).
    fun weekStart(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)
    fun weekDays(start: LocalDate): List<LocalDate> = (0L until 7L).map { start.plusDays(it) }

    private val weekTitle = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    /// "Oct 20 – 26" / "Oct 27 – Nov 2".
    fun weekLabel(start: LocalDate): String {
        val end = start.plusDays(6)
        val tail = if (end.month == start.month) end.dayOfMonth.toString() else weekTitle.format(end)
        return "${weekTitle.format(start)} – $tail"
    }

    private val dayTitle = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)
    private val dayShort = DateTimeFormatter.ofPattern("EEE d", Locale.US)
    fun dayTitle(date: String): String = parse(date)?.let { dayTitle.format(it) } ?: date
    fun dayShort(date: LocalDate): String = dayShort.format(date)

    /// "Morning" / "Afternoon" / "Evening", or "Now" for instant work.
    fun windowTag(window: String?): String = CalendarChips.tag(window)

    // Load + capacity

    /// The board/day label for one window: "1 of 2" under a cap, else
    /// "2 booked" (or nothing booked).
    fun loadLabel(count: Int, capacity: Int?): String = when {
        capacity != null -> "$count of $capacity"
        count == 0 -> "Nothing booked"
        else -> "$count booked"
    }

    fun isFull(count: Int, capacity: Int?): Boolean = capacity != null && count >= capacity

    fun load(calendar: CompanyCalendar, date: String, window: String): Int = calendar.load["$date|$window"] ?: 0

    /// The week board's day header under a cap — "M 1/2 · A 0/2 · E 2/2";
    /// null without a cap (there's nothing to measure against).
    fun dayLoadLabel(calendar: CompanyCalendar, date: String): String? {
        val capacity = calendar.windowCapacity ?: return null
        return windows.joinToString(" · ") { "${windowTag(it).take(1)} ${load(calendar, date, it)}/$capacity" }
    }

    /// Any window at or over the cap that day.
    fun dayHasFullWindow(calendar: CompanyCalendar, date: String): Boolean =
        windows.any { isFull(load(calendar, date, it), calendar.windowCapacity) }

    // Flags

    data class Flag(val label: String, val tone: ZTone)

    /// What still stands between a job and the pro leaving — the warning
    /// on its chip. Overdue outranks the staffing gaps.
    fun flag(job: CalendarJob): Flag? = when {
        job.overdue -> Flag("Should have set off", ZTone.DANGER)
        job.needsCrew -> Flag(if (job.crew.isEmpty()) "No tech" else "Needs crew", ZTone.WARNING)
        job.needsStart -> Flag("Not started", ZTone.WARNING)
        else -> null
    }

    fun warns(job: CalendarJob): Boolean = job.overdue || job.needsCrew || job.needsStart

    // Team filter (admins): a tick per person + "No tech yet"

    /// Who's shown: everyone not `hidden`, plus jobs with nobody on them
    /// unless `hideUnassigned`. Busy placeholders (an own job you may not
    /// open) always show — they say a slot is taken.
    data class TechFilter(val hidden: Set<Int> = emptySet(), val hideUnassigned: Boolean = false) {
        val everyone: Boolean get() = hidden.isEmpty() && !hideUnassigned

        fun shows(memberId: Int): Boolean = memberId !in hidden

        fun toggled(memberId: Int): TechFilter = copy(hidden = if (memberId in hidden) hidden - memberId else hidden + memberId)
        fun toggledUnassigned(): TechFilter = copy(hideUnassigned = !hideUnassigned)

        /// "Only" one person (the web sidebar's shortcut).
        fun only(memberId: Int, team: List<CompanyCalendar.CalendarMember>): TechFilter =
            TechFilter(hidden = team.map { it.id }.filter { it != memberId }.toSet(), hideUnassigned = true)

        companion object {
            val Everyone = TechFilter()
        }
    }

    fun matches(job: CalendarJob, filter: TechFilter): Boolean = when {
        job.busy -> true
        job.crew.isEmpty() -> !filter.hideUnassigned
        else -> job.crew.any { filter.shows(it.id) }
    }

    fun filtered(jobs: List<CalendarJob>, filter: TechFilter): List<CalendarJob> = jobs.filter { matches(it, filter) }

    /// A day's visits, ZiVETT and own, in the order they'll happen: placed
    /// times first by their start, then window order.
    fun jobsOn(calendar: CompanyCalendar, date: String, filter: TechFilter = TechFilter.Everyone): List<CalendarJob> =
        calendar.entries.filter { it.date == date && matches(it, filter) }.sortedWith(visitOrder)

    /// Instant ("Now") first, then by the visit's start minute (span), then
    /// window order — the web's agenda sort.
    val visitOrder: Comparator<CalendarJob> = compareBy({ if (it.window == null && !it.isOwn) 0 else 1 }, { it.span?.firstOrNull() ?: windowStart(it.window) }, { windowOrder(it.window) })

    /// `TechSchedule::WINDOWS` start minute; unknown windows sort last.
    fun windowStart(window: String?): Int = when (window) {
        "morning" -> 8 * 60
        "afternoon" -> 12 * 60
        "evening" -> 17 * 60
        null -> 0
        else -> 24 * 60
    }

    // Colours: one per team member, jobs wear their lead's

    /// Index into the tech palette for `userId` — team order, cycling; a
    /// lead outside the visible roster (a member's payload carries only
    /// themselves) falls back to their id. Null = nobody (grey).
    fun colorIndex(team: List<CompanyCalendar.CalendarMember>, userId: Int?, size: Int = 10): Int? {
        userId ?: return null
        val at = team.indexOfFirst { it.id == userId }
        return if (at >= 0) at % size else Math.floorMod(userId, size)
    }

    fun leadColorIndex(calendar: CompanyCalendar, job: CalendarJob, size: Int = 10): Int? = colorIndex(calendar.team, job.lead?.id, size)

    // Time of day

    /// "9:30 am" when a time is planned (arrival or own-job start), else
    /// the window tag ("Morning", "Now").
    fun timeTag(job: CalendarJob): String = job.clockTime?.let { MyDayLogic.formatClock(it) } ?: windowTag(job.window)

    /// The list rows' when line: "Thu, Nov 12 · Morning · around 9:30 am".
    fun whenLine(job: CalendarJob): String {
        val slot = slotLine(job.date, job.window)
        val time = job.clockTime?.let { MyDayLogic.formatClock(it) } ?: return slot
        return if (job.isOwn) "$slot · $time" else "$slot · around $time"
    }

    // Week board

    /// One row of the dispatch board: a team member (null = No tech yet)
    /// and their jobs per date. A crew job sits on every crew member's row.
    data class BoardRow(val member: CompanyCalendar.CalendarMember?, val jobsByDate: Map<String, List<CalendarJob>>) {
        val label: String get() = member?.display ?: "No tech yet"
    }

    fun boardRows(calendar: CompanyCalendar, filter: TechFilter = TechFilter.Everyone): List<BoardRow> {
        val rows = mutableListOf<BoardRow>()
        val placed = calendar.entries.filter { !it.busy }
        for (member in calendar.team) {
            if (!filter.shows(member.id)) continue
            val mine = placed.filter { job -> job.crew.any { it.id == member.id } }
            rows += BoardRow(member, mine.groupBy { it.date }.mapValues { (_, list) -> list.sortedWith(visitOrder) })
        }
        if (!filter.hideUnassigned) {
            val unassigned = placed.filter { it.crew.isEmpty() }
            // Only when that range has work with nobody on it (web review #13).
            if (unassigned.isNotEmpty()) {
                rows += BoardRow(null, unassigned.groupBy { it.date }.mapValues { (_, list) -> list.sortedWith(visitOrder) })
            }
        }
        return rows
    }

    // List view (the next fortnight)

    const val LIST_DAYS = 14

    /// "Nov 12 – 25" / "Nov 26 – Dec 9".
    fun rangeLabel(start: LocalDate, days: Int = LIST_DAYS): String {
        val end = start.plusDays(days - 1L)
        val tail = if (end.month == start.month) end.dayOfMonth.toString() else weekTitle.format(end)
        return "${weekTitle.format(start)} – $tail"
    }

    /// The days in the range that have something on them, each with its
    /// visits in order.
    fun agenda(calendar: CompanyCalendar, start: LocalDate, filter: TechFilter, days: Int = LIST_DAYS): List<Pair<String, List<CalendarJob>>> =
        (0L until days).map { dateKey(start.plusDays(it)) }
            .map { date -> date to jobsOn(calendar, date, filter) }
            .filter { (_, jobs) -> jobs.isNotEmpty() }

    // Time off

    fun timeOffOn(calendar: CompanyCalendar, date: String): List<CompanyCalendar.TimeOff> = calendar.timeOff.filter { it.date == date }

    /// "Off" for the whole day, "Off mornings" style for one window, null when working.
    fun offLabel(calendar: CompanyCalendar, userId: Int, date: String): String? {
        val rows = calendar.timeOff.filter { it.userId == userId && it.date == date }
        if (rows.isEmpty()) return null
        if (rows.any { it.window == null }) return "Off"
        return "Off " + rows.mapNotNull { it.window }.sortedBy { windowOrder(it) }.joinToString(", ") { windowTag(it).lowercase() }
    }

    fun timeOffLabel(row: CompanyCalendar.TimeOff): String = row.window?.let { "${windowTag(it)} off" } ?: "All day"

    // Day availability

    fun isWorkingDay(calendar: CompanyCalendar, date: LocalDate): Boolean = calendar.workingDays?.contains(date.dayOfWeek.value) ?: true

    fun weeklyBlocked(blocks: List<WeeklyBlock>?, weekday: Int, window: String): Boolean = blocks.orEmpty().any { it.weekday == weekday && it.window == window }

    sealed interface WindowState {
        data object Open : WindowState
        data class Full(val count: Int, val capacity: Int) : WindowState
        /// A one-off block on this date — reopenable from the day sheet.
        data class BlockedDate(val id: Int) : WindowState
        /// The whole day is blocked (one row).
        data class BlockedDay(val id: Int) : WindowState
        /// The weekly rule ("no Friday evenings") — edited in Settings.
        data object Weekly : WindowState
        data object DayOff : WindowState
    }

    fun windowState(calendar: CompanyCalendar, date: String, window: String): WindowState {
        val day = parse(date)
        if (day != null && !isWorkingDay(calendar, day)) return WindowState.DayOff
        val rows = calendar.blockedDates.filter { it.date == date }
        rows.firstOrNull { it.window == null }?.let { return WindowState.BlockedDay(it.id) }
        rows.firstOrNull { it.window == window }?.let { return WindowState.BlockedDate(it.id) }
        if (day != null && weeklyBlocked(calendar.blockedWindows, day.dayOfWeek.value, window)) return WindowState.Weekly
        val count = load(calendar, date, window)
        val capacity = calendar.windowCapacity
        if (capacity != null && count >= capacity) return WindowState.Full(count, capacity)
        return WindowState.Open
    }

    fun windowStateLabel(state: WindowState): String = when (state) {
        WindowState.Open -> "Open"
        is WindowState.Full -> "Full"
        is WindowState.BlockedDate -> "Blocked"
        is WindowState.BlockedDay -> "Blocked"
        WindowState.Weekly -> "Blocked weekly"
        WindowState.DayOff -> "Day off"
    }

    fun wholeDayBlock(calendar: CompanyCalendar, date: String): Int? = calendar.blockedDates.firstOrNull { it.date == date && it.window == null }?.id
    fun anyBlock(calendar: CompanyCalendar, date: String): Boolean = calendar.blockedDates.any { it.date == date }

    /// The day sheet's one-line status.
    fun dayStatus(calendar: CompanyCalendar, date: String): String {
        val day = parse(date)
        if (day != null && !isWorkingDay(calendar, day)) return "Not a working day"
        if (wholeDayBlock(calendar, date) != null) return "Blocked all day"
        val closed = windows.filter { windowState(calendar, date, it) !in setOf(WindowState.Open) }
        return when {
            closed.isEmpty() -> "Open for work"
            closed.size == windows.size -> "No open windows"
            else -> "Open " + windows.filter { it !in closed }.joinToString(", ") { windowTag(it).lowercase() }
        }
    }

    // Openings

    /// Feed jobs wanting a slot that day — future days only (today counts).
    fun openingsOn(calendar: CompanyCalendar, date: String, today: String): List<CompanyCalendar.Opening> =
        if (date < today) emptyList() else calendar.openings[date].orEmpty()

    fun openingsLabel(count: Int): String = if (count == 1) "1 open job" else "$count open jobs"

    // Map legs

    data class Stop(val number: Int, val job: CalendarJob, val lat: Double, val lng: Double)
    data class Leg(val from: String, val to: String, val km: Double)

    /// The day's located jobs in window order, numbered from 1.
    fun stops(jobs: List<CalendarJob>): List<Stop> =
        jobs.sortedWith(visitOrder)
            .filter { !it.busy && it.lat != null && it.lng != null }
            .mapIndexed { i, job -> Stop(i + 1, job, job.lat!!, job.lng!!) }

    /// Straight-line legs: base → stop 1 → stop 2 …; without a base the
    /// route starts at stop 1.
    fun legs(base: CompanyCalendar.Base?, stops: List<Stop>): List<Leg> {
        val points = buildList {
            base?.let { add(Triple(it.label ?: "Base", it.lat, it.lng)) }
            stops.forEach { add(Triple(stopLabel(it), it.lat, it.lng)) }
        }
        return points.zipWithNext { a, b -> Leg(a.first, b.first, distanceKm(a.second, a.third, b.second, b.third)) }
    }

    fun stopLabel(stop: Stop): String = "${stop.number}. ${stop.job.code ?: stop.job.title}"

    fun totalKm(legs: List<Leg>): Double = legs.sumOf { it.km }

    /// Great-circle distance (haversine), km.
    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    /// "650 m" under a kilometre, else "4.2 km".
    fun distanceLabel(km: Double): String =
        if (km < 1) "${(km * 1000 / 10).roundToInt() * 10} m" else String.format(Locale.US, "%.1f km", km)

    // Settings

    /// All seven days = null (the server's "every day").
    fun workingDaysBody(selected: Set<Int>): List<Int>? = if ((1..7).all { it in selected }) null else selected.filter { it in 1..7 }.sorted()

    fun toggleWeekly(blocks: List<WeeklyBlock>, weekday: Int, window: String): List<WeeklyBlock> =
        if (weeklyBlocked(blocks, weekday, window)) blocks.filterNot { it.weekday == weekday && it.window == window }
        else (blocks + WeeklyBlock(weekday, window)).sortedWith(compareBy({ it.weekday }, { windowOrder(it.window) }))

    sealed interface Capacity {
        data class Valid(val value: Int?) : Capacity
        data object Invalid : Capacity
    }

    /// Blank = no limit; otherwise a whole number 1–50.
    fun parseCapacity(text: String): Capacity {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Capacity.Valid(null)
        val value = trimmed.toIntOrNull() ?: return Capacity.Invalid
        return if (value in 1..50) Capacity.Valid(value) else Capacity.Invalid
    }

    // Move picker

    fun slotReason(reason: String?): String? = when (reason) {
        "blocked" -> "Blocked"
        "day_off" -> "Day off"
        "full" -> "Full"
        null -> null
        else -> reason.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    /// "1 of 2" under a cap, "1 booked" otherwise, blank when empty.
    fun slotLoad(slot: RescheduleSlots.Slot): String? = when {
        slot.capacity != null -> "${slot.booked} of ${slot.capacity}"
        slot.booked > 0 -> "${slot.booked} booked"
        else -> null
    }

    /// A slot can be picked when it's open and isn't where the visit already is.
    fun pickable(slot: RescheduleSlots.Slot): Boolean = slot.open && !slot.current

    fun slotsByDate(slots: List<RescheduleSlots.Slot>): List<Pair<String, List<RescheduleSlots.Slot>>> =
        slots.groupBy { it.date }.entries.sortedBy { it.key }.map { (date, rows) -> date to rows.sortedBy { windowOrder(it.window) } }

    /// "Sat, Oct 25 · Morning".
    fun slotLine(date: String?, window: String?): String = date?.let { JobPresentation.windowSlot(it, window) } ?: "the agreed time"
}
