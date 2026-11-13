package com.zivett.app.features.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zivett.app.app.CompanyJobRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.OwnJobRoute
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.DayJob
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.MyDay
import com.zivett.app.core.models.MyDayEndpoints
import com.zivett.app.core.models.OwnJobStatus
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZFlowRow
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZBody
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.shared.LocalNav
import com.zivett.app.features.shared.LocalNavBadges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/// My day's pure rules (web `pages/company/MyDay.vue`) — the time lines,
/// the clock strip, the crew line and the directions link. Tested in
/// `MyDayTest`.
object MyDayLogic {
    /// "9:30 am" from "09:30" (web `formatClock`).
    fun formatClock(time: String?): String {
        val (hours, minutes) = time?.split(":")?.takeIf { it.size >= 2 }?.let { (h, m) -> (h.toIntOrNull() ?: return "") to (m.toIntOrNull() ?: return "") } ?: return ""
        val suffix = if (hours < 12) "am" else "pm"
        val hour = if (hours % 12 == 0) 12 else hours % 12
        return String.format(Locale.US, "%d:%02d %s", hour, minutes, suffix)
    }

    /// "09:30" from minutes past midnight.
    fun clock(minutes: Int): String = String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

    /// The card's when line: "9:30 am – 11:30 am" when the visit is placed
    /// at a time, "Around 9:30 am" with a time but no span, the window
    /// ("Morning (8am–12pm)") without one, else "Any time today".
    fun timeLine(job: DayJob, isToday: Boolean = true): String {
        val span = job.span?.takeIf { it.size == 2 }
        val time = job.time
        return when {
            time != null && span != null -> "${formatClock(clock(span[0]))} – ${formatClock(clock(span[1]))}"
            time != null -> "Around ${formatClock(time)}"
            job.window != null -> JobPresentation.windowLabel(job.window)
            else -> if (isToday) "Any time today" else "Any time"
        }
    }

    /// The badge: a ZiVETT job's status words, or an own job's.
    fun status(job: DayJob): JobPresentation.Meta {
        if (job.isOwn) return JobPresentation.Meta(OwnJobStatus.label(job.status), if (job.status == OwnJobStatus.COMPLETED) ZTone.SUCCESS else if (job.status == OwnJobStatus.IN_PROGRESS) ZTone.WARNING else ZTone.INFO)
        return JobStatus.entries.firstOrNull { it.wire == job.status }?.let { JobPresentation.meta(it) }
            ?: JobPresentation.Meta(job.status.replace('_', ' ').replaceFirstChar { it.uppercase() }, ZTone.NEUTRAL)
    }

    /// "J-1042 · Plumbing".
    fun codeLine(job: DayJob): String = listOfNotNull(job.code, job.trade).joinToString(" · ")

    /// "Suite 4, 1 Front St …" — the unit leads, as on a route sheet.
    fun addressLine(job: DayJob): String? = listOfNotNull(job.unit?.takeIf { it.isNotBlank() }, job.address?.takeIf { it.isNotBlank() }).joinToString(", ").ifEmpty { null }

    /// "With Mike R." / "With Mike R. and Ana P." — everyone else on it;
    /// null when it's just you.
    fun crewLine(job: DayJob, me: Int?): String? {
        val others = job.crew.filter { it.id != me }.map { it.name }
        return if (others.isEmpty()) null else "With ${CompanyPresentation.nameList(others)}"
    }

    /// Google Maps directions to the pin, else the typed address.
    fun directionsUrl(lat: Double?, lng: Double?, address: String?): String? = when {
        lat != null && lng != null -> "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng"
        !address.isNullOrBlank() -> "https://www.google.com/maps/dir/?api=1&destination=${URLEncoder.encode(address, "UTF-8")}"
        else -> null
    }

    /// "1 h 05 min" / "12 min".
    fun elapsed(from: Instant, now: Instant): String {
        val minutes = maxOf(0L, Duration.between(from, now).toMinutes())
        return if (minutes < 60) "$minutes min" else String.format(Locale.US, "%d h %02d min", minutes / 60, minutes % 60)
    }

    /// "Clocked in on J-1042 · Gutter clean".
    fun clockTitle(clock: MyDay.RunningClock): String =
        "Clocked in on " + listOfNotNull(clock.code, clock.title).joinToString(" · ").ifEmpty { "a job" }

    /// "since 9:12 am · 1 h 05 min".
    fun clockSince(clock: MyDay.RunningClock, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String? {
        val started = clock.startedAt ?: return null
        val at = DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(zone).format(started).lowercase(Locale.US)
        return "since $at · ${elapsed(started, now)}"
    }

    private val longDay = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)

    /// "Today" / "Tomorrow" / "Yesterday" / "Friday, November 13".
    fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> longDay.format(date)
    }

    /// The day's headline count: "3 jobs" / "1 job" / "Nothing booked".
    fun countLine(day: MyDay): String = when (day.jobs.size) {
        0 -> "Nothing booked"
        1 -> "1 job"
        else -> "${day.jobs.size} jobs"
    }

    /// Clock buttons ride open own jobs when the org has Business tools.
    fun showsClock(job: DayJob, day: MyDay): Boolean = job.isOwn && job.clock != null && day.canClock

    /// "On my way" goes through the job page, which starts live location
    /// sharing; every other step posts from here.
    fun opensJobToAdvance(next: DayJob.Next): Boolean = next.action == "advance" && next.status == JobStatus.EN_ROUTE.wire
}

/// `GET /api/company/my-day` — one person's day, stepping a day at a time.
/// Every button posts to the endpoint that owns the step, then re-reads
/// the day (the order and the next buttons are the server's).
class MyDayModel(private val client: ApiClient) {
    var state by mutableStateOf<Loadable<MyDay>>(Loadable.Loading)
    /// The day asked for; null = today on the server's clock.
    var date by mutableStateOf<LocalDate?>(null)
        private set
    /// The job a button is working on (its spinner).
    var working by mutableStateOf<String?>(null)
        private set
    var toast by mutableStateOf<String?>(null)

    val day: MyDay? get() = state.value

    suspend fun load() {
        state = state.reloaded { client.send(MyDayEndpoints.day(date?.let { CalendarLogic.dateKey(it) })) }
    }

    /// The loaded day, else what was asked for.
    private fun current(): LocalDate? = day?.date?.let { CalendarLogic.parse(it) } ?: date

    suspend fun shift(days: Long) {
        val from = current() ?: LocalDate.now()
        date = from.plusDays(days)
        state = Loadable.Loading
        load()
    }

    suspend fun goToday() {
        date = null
        state = Loadable.Loading
        load()
    }

    private fun key(job: DayJob) = "${job.kind}-${job.id}"
    fun isWorking(job: DayJob) = working == key(job)

    private suspend fun act(job: DayJob?, success: String?, operation: suspend () -> Unit) {
        if (working != null) return
        working = job?.let { key(it) } ?: "clock"
        try {
            operation()
            success?.let { toast = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast = e.userMessage
        } finally {
            working = null
        }
        load()
    }

    /// The ZiVETT job's next stage (arrived, start work, work complete).
    suspend fun advance(job: DayJob) = act(job, null) { client.send(CompanyEndpoints.advance(job.id)) }

    /// An own job's Start (clocks you in) or Finish (stops every clock).
    suspend fun ownStatus(job: DayJob, status: String) = act(job, if (status == OwnJobStatus.COMPLETED) "${job.code ?: job.title} finished" else null) {
        client.send(MyDayEndpoints.ownJobStatus(job.id, status))
    }

    suspend fun clockIn(job: DayJob) = act(job, "Clocked in on ${job.code ?: job.title}") { client.send(MyDayEndpoints.clockIn(job.id)) }
    suspend fun clockOut(jobId: Int, label: String?) = act(null, "Clocked out${label?.let { " of $it" } ?: ""}") { client.send(MyDayEndpoints.clockOut(jobId)) }
}

@Composable
fun MyDayScreen() {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val model = remember { MyDayModel(environment.client) }
    val me = environment.session.user?.id

    // Every return to the screen or the foreground refetches; so does any
    // live notification or push (a teammate started the job, a new job).
    LifecycleResumeEffect(model) {
        scope.launch { model.load() }
        onPauseOrDispose { }
    }
    val liveEvents = LocalNavBadges.current?.liveEvents ?: 0
    LaunchedEffect(liveEvents) { if (liveEvents > 0) model.load() }

    // The running clock's minutes tick while it's on screen.
    var now by remember { mutableStateOf(Instant.now()) }
    val running = model.day?.clock != null
    LaunchedEffect(running) { while (running) { now = Instant.now(); delay(30_000) } }

    ZToastBox(model.toast, { model.toast = null }) {
        ZScreen(onRefresh = { model.load() }) {
            val today = LocalDate.now()
            val shown = model.day?.date?.let { CalendarLogic.parse(it) } ?: model.date ?: today
            val isToday = model.day?.isToday ?: (shown == today)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scope.launch { model.shift(-1) } }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day", tint = colors.ink) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    ZHeadline(MyDayLogic.dayTitle(shown, today))
                    model.day?.let { ZCaption(if (isToday) MyDayLogic.countLine(it) else "${CalendarLogic.dayTitle(it.date)} · ${MyDayLogic.countLine(it)}") }
                }
                IconButton(onClick = { scope.launch { model.shift(1) } }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next day", tint = colors.ink) }
            }
            if (!isToday) ZTextAction("Back to today", modifier = Modifier.align(Alignment.CenterHorizontally)) { scope.launch { model.goToday() } }

            ZLoadable(model.state, retry = { scope.launch { model.load() } }) { day ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    day.clock?.let { clock -> ClockStrip(clock, now, model.working != null) { scope.launch { model.clockOut(clock.jobId, clock.code) } } }
                    if (day.jobs.isEmpty()) {
                        ZEmptyState(Icons.Outlined.Today, if (isToday) "Nothing on today" else "Nothing booked", "Jobs you're on show up here in the order you'll do them.")
                    }
                    for (job in day.jobs) {
                        DayJobCard(
                            job, day, me, isToday, working = model.isWorking(job), anyWorking = model.working != null,
                            onNext = { next ->
                                when {
                                    MyDayLogic.opensJobToAdvance(next) -> nav.navigate(CompanyJobRoute(job.id, advanceOnOpen = true))
                                    next.action == "advance" -> scope.launch { model.advance(job) }
                                    next.action == "start" -> nav.navigate(CompanyJobRoute(job.id, openStart = true))
                                    next.action == "own_status" -> next.status?.let { status -> scope.launch { model.ownStatus(job, status) } }
                                }
                            },
                            onClockIn = { scope.launch { model.clockIn(job) } },
                            onClockOut = { scope.launch { model.clockOut(job.id, job.code) } },
                            onCall = { phone -> CompanyPresentation.callUri(phone)?.let { openUrl(context, it) } },
                            onDirections = { MyDayLogic.directionsUrl(job.lat, job.lng, job.address)?.let { openUrl(context, it) } },
                            onOpen = { if (job.isOwn) nav.navigate(OwnJobRoute(job.id)) else nav.navigate(CompanyJobRoute(job.id)) },
                        )
                    }
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }
}

/// "Clocked in on J-1042 · Gutter clean — since 9:12 am · 1 h 05 min".
@Composable
private fun ClockStrip(clock: MyDay.RunningClock, now: Instant, busy: Boolean, onClockOut: () -> Unit) {
    val colors = ZTheme.colors
    ZCard(padding = ZSpacing.sm) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ZBodyStrong(MyDayLogic.clockTitle(clock), maxLines = 2)
                MyDayLogic.clockSince(clock, now)?.let { ZCaption(it, color = colors.success) }
            }
            ZButton("Clock out", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false, loading = busy, onClick = onClockOut)
        }
    }
}

@Composable
private fun DayJobCard(
    job: DayJob,
    day: MyDay,
    me: Int?,
    isToday: Boolean,
    working: Boolean,
    anyWorking: Boolean,
    onNext: (DayJob.Next) -> Unit,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onCall: (String) -> Unit,
    onDirections: () -> Unit,
    onOpen: () -> Unit,
) {
    ZCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
            ZBodyStrong(MyDayLogic.timeLine(job, isToday), modifier = Modifier.weight(1f))
            if (job.isOwn) ZBadge("Own job")
            val meta = MyDayLogic.status(job)
            ZBadge(meta.label, meta.tone)
        }
        ZHeadline(job.title.ifBlank { "Job" })
        MyDayLogic.codeLine(job).takeIf { it.isNotEmpty() }?.let { ZMono(it) }
        MyDayLogic.addressLine(job)?.let { ZBody(it) }
        job.accessNotes?.takeIf { it.isNotBlank() }?.let {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ZCaption("Getting in", tone = ZTextTone.LABEL)
                ZBody(it, tone = ZTextTone.SOFT)
            }
        }
        job.contact?.name?.let { name ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                ZCaption(name, color = ZTheme.colors.ink)
                job.contact.phone?.takeIf { CompanyPresentation.callUri(it) != null }?.let { phone -> ZTextAction(phone) { onCall(phone) } }
            }
        }
        MyDayLogic.crewLine(job, me)?.let { ZCaption(it) }

        // The one next thing to press, or why there isn't one.
        job.next?.let { next ->
            if (next.action == "waiting") ZCaption(next.label, tone = ZTextTone.SOFT)
            else ZButton(next.label, style = ZButtonStyle.SUCCESS, loading = working, enabled = !anyWorking || working) { onNext(next) }
        }
        ZFlowRow(spacing = ZSpacing.xs) {
            if (MyDayLogic.showsClock(job, day)) {
                if (job.clock?.running == true) ZButton("Clock out", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false, enabled = !anyWorking, onClick = onClockOut)
                else ZButton("Clock in", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false, enabled = !anyWorking, onClick = onClockIn)
            }
            if (MyDayLogic.directionsUrl(job.lat, job.lng, job.address) != null) ZButton("Directions", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false, onClick = onDirections)
            ZButton("Open job", style = ZButtonStyle.GHOST, compact = true, fullWidth = false, onClick = onOpen)
        }
    }
}

