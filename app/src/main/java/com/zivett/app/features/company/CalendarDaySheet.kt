package com.zivett.app.features.company

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zivett.app.app.CompanyQuotesRoute
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.CompanyCalendar
import com.zivett.app.core.models.CompanyCalendar.CalendarJob
import com.zivett.app.core.models.WeeklyBlock
import com.zivett.app.core.network.ApiClient
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZFlowRow
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZPhotoAvatar
import com.zivett.app.design.ZRadius
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZType
import com.zivett.app.features.company.CalendarLogic.WindowState
import com.zivett.app.features.shared.DayRouteMap
import com.zivett.app.features.shared.JobStatusBadge
import com.zivett.app.features.shared.LocalNav
import com.zivett.app.features.shared.RouteStop
import kotlinx.coroutines.launch

/// A destructive-ish change waiting on the inline confirm step.
private data class PendingConfirm(val title: String, val body: String, val action: String, val danger: Boolean, val run: suspend () -> Unit)

/// Everything about one date: status + per-window load, the route map,
/// the jobs (Start / Move), pending moves, tentative quotes, the feed jobs
/// that want the day, who's off, and (admins) the blocks.
@Composable
fun CalendarDaySheet(
    date: String,
    calendar: CompanyCalendar,
    model: CompanyCalendarModel,
    me: Int?,
    today: String,
    onDismiss: () -> Unit,
    onOpenJob: (Int) -> Unit,
    onMove: (CalendarJob) -> Unit,
    onStart: (CalendarJob) -> Unit,
    onQuote: (CompanyCalendar.Opening) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val nav = LocalNav.current
    val colors = ZTheme.colors
    var confirm by remember(date) { mutableStateOf<PendingConfirm?>(null) }
    val manage = calendar.canManage
    val upcoming = date >= today
    val jobs = CalendarLogic.jobsOn(calendar, date)
    val moves = calendar.moves.filter { it.date == date }
    val tentative = calendar.tentative.orEmpty().filter { it.date == date }
    val openings = if (calendar.canQuote) CalendarLogic.openingsOn(calendar, date, today) else emptyList()
    val off = CalendarLogic.timeOffOn(calendar, date)
    val stops = CalendarLogic.stops(jobs)
    val legs = CalendarLogic.legs(calendar.base, stops)

    ZSheet(onDismiss = onDismiss, title = CalendarLogic.dayTitle(date), dismissable = !model.busy) {
        ZCaption(CalendarLogic.dayStatus(calendar, date), tone = ZTextTone.SOFT)

        // The inline confirm step — it replaces the controls while it's up.
        confirm?.let { pending ->
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.tile)).background(colors.tone(if (pending.danger) ZTone.DANGER else ZTone.INFO).second).padding(ZSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ZSpacing.xs),
            ) {
                ZBodyStrong(pending.title)
                ZCaption(pending.body, tone = ZTextTone.SOFT)
                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                    ZButton("Cancel", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false, enabled = !model.busy) { confirm = null }
                    ZButton(pending.action, style = if (pending.danger) ZButtonStyle.DANGER else ZButtonStyle.PRIMARY, compact = true, fullWidth = false, loading = model.busy) {
                        scope.launch { pending.run(); confirm = null }
                    }
                }
            }
        }

        // Windows: state + load against the cap; admins block/reopen one window.
        ZCard(padding = ZSpacing.sm) {
            for (window in CalendarLogic.windows) {
                val state = CalendarLogic.windowState(calendar, date, window)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        ZBodyStrong(com.zivett.app.features.customer.jobs.JobPresentation.windowLabel(window))
                        ZCaption(CalendarLogic.loadLabel(CalendarLogic.load(calendar, date, window), calendar.windowCapacity))
                    }
                    ZBadge(CalendarLogic.windowStateLabel(state), when (state) {
                        WindowState.Open -> ZTone.SUCCESS
                        is WindowState.Full -> ZTone.WARNING
                        WindowState.DayOff -> ZTone.NEUTRAL
                        else -> ZTone.DANGER
                    })
                    if (manage && upcoming && confirm == null) {
                        when (state) {
                            WindowState.Open, is WindowState.Full -> ZTextAction("Block") {
                                confirm = PendingConfirm("Block the ${CalendarLogic.windowTag(window).lowercase()}?", "New jobs and quote acceptances can't land in this window. Jobs already booked in it stay put.", "Block window", danger = true) { model.blockWindow(date, window) }
                            }
                            is WindowState.BlockedDate -> ZTextAction("Reopen") {
                                val row = calendar.blockedDates.first { it.id == state.id }
                                confirm = PendingConfirm("Reopen the ${CalendarLogic.windowTag(window).lowercase()}?", "This window takes new work again.", "Reopen", danger = false) { model.unblock(row) }
                            }
                            else -> Unit
                        }
                    }
                }
            }
            if (manage && upcoming && confirm == null) {
                ZDivider()
                val wholeDay = CalendarLogic.wholeDayBlock(calendar, date)
                if (CalendarLogic.anyBlock(calendar, date)) {
                    ZButton(if (wholeDay != null) "Reopen day" else "Reopen every window", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) {
                        confirm = PendingConfirm("Reopen ${CalendarLogic.dayTitle(date)}?", "Every block on this date is removed and it takes new work again.", "Reopen day", danger = false) { model.reopenDay(date) }
                    }
                }
                if (wholeDay == null) {
                    ZButton("Block the whole day", style = ZButtonStyle.GHOST, compact = true, fullWidth = false) {
                        confirm = PendingConfirm(
                            "Block ${CalendarLogic.dayTitle(date)}?",
                            if (jobs.isNotEmpty()) "You have ${jobs.size} confirmed ${if (jobs.size == 1) "job" else "jobs"} this day — blocking does NOT cancel or move them. Scheduled jobs that day leave your feed, and quotes proposing it can't be accepted at it."
                            else "Scheduled jobs that day leave your feed, and quotes proposing it can no longer be accepted at it.",
                            "Block day", danger = true,
                        ) { model.blockDay(date) }
                    }
                }
            }
        }

        // The route: base → stop 1 → stop 2 …, straight lines.
        if (stops.isNotEmpty()) {
            DayRouteMap(
                base = calendar.base?.let { it.lat to it.lng }, baseLabel = calendar.base?.label,
                stops = stops.map { RouteStop(it.number, it.lat, it.lng, CalendarLogic.stopLabel(it), CalendarLogic.warns(it.job)) },
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(ZRadius.panel)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (leg in legs) ZCaption("${leg.from} → ${leg.to} · ${CalendarLogic.distanceLabel(leg.km)}")
                if (legs.size > 1) ZCaption("About ${CalendarLogic.distanceLabel(CalendarLogic.totalKm(legs))} in a straight line", tone = ZTextTone.SOFT)
                if (calendar.base == null) ZCaption("Add your business address on your profile to start the route from your base.", tone = ZTextTone.FAINT)
            }
        }

        // Jobs
        ZHeadline(if (jobs.isEmpty()) "No jobs this day" else "Jobs (${jobs.size})")
        val numbers = stops.associate { it.job.id to it.number }
        for (job in jobs) {
            ZCard(padding = ZSpacing.sm, onClick = { onOpenJob(job.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                    numbers[job.id]?.let { StopNumber(it) }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ZBodyStrong(job.title, maxLines = 1)
                        ZCaption(listOfNotNull(CalendarLogic.windowTag(job.window), job.code).joinToString(" · "))
                    }
                    JobStatusBadge(job.status)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                    CrewFaces(job, 26.dp)
                    ZCaption(if (job.crew.isEmpty()) "Nobody assigned" else CompanyPresentation.nameList(job.crew.map { it.name }), modifier = Modifier.weight(1f), maxLines = 1)
                    CalendarLogic.flag(job)?.let { ZBadge(it.label, it.tone) }
                }
                if (manage && (job.needsStart || job.movable)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                        if (job.needsStart) ZButton("Start job", style = ZButtonStyle.SUCCESS, compact = true, fullWidth = false) { onStart(job) }
                        if (job.movable) ZButton("Move", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { onMove(job) }
                    }
                }
            }
        }

        if (moves.isNotEmpty()) {
            ZMono("Waiting on the booker")
            for (move in moves) {
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.tile)).dashedBorder(colors.inkMuted, ZRadius.tile).padding(ZSpacing.sm), horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ZBodyStrong("${move.jobCode ?: "A visit"} → ${CalendarLogic.windowTag(move.window)}")
                        ZCaption("Moving from ${CalendarLogic.slotLine(move.fromDate, move.fromWindow)}${move.title?.let { " · $it" } ?: ""}")
                    }
                    ZBadge("Pending", ZTone.INFO)
                }
            }
        }

        if (tentative.isNotEmpty()) {
            ZMono("Proposed in your quotes")
            for (row in tentative) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.tile)).dashedBorder(colors.inkMuted, ZRadius.tile).clickable { onDismiss(); nav.navigate(CompanyQuotesRoute) }.padding(ZSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ZBodyStrong(listOfNotNull(row.jobCode, row.title).joinToString(" · "), maxLines = 1)
                        ZCaption("${CalendarLogic.windowTag(row.window)} · ${com.zivett.app.core.Money.format(row.amountCents)} · not booked yet")
                    }
                }
            }
        }

        if (openings.isNotEmpty()) {
            ZHeadline("Jobs that want this day")
            ZCaption("Open requests offering a window you have free.")
            for (opening in openings) {
                ZCard(padding = ZSpacing.sm) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            ZBodyStrong(opening.title.ifEmpty { opening.code ?: "Open job" }, maxLines = 1)
                            ZCaption(listOfNotNull(opening.code, opening.windows.sortedBy { CalendarLogic.windowOrder(it) }.joinToString(", ") { CalendarLogic.windowTag(it) }.ifEmpty { null }, opening.distanceKm?.let { CalendarLogic.distanceLabel(it) + " away" }).joinToString(" · "))
                        }
                        ZButton("Quote", compact = true, fullWidth = false) { onQuote(opening) }
                    }
                }
            }
        }

        TimeOffSection(date, calendar, model, me, upcoming)
    }
}

@Composable
private fun StopNumber(number: Int) {
    val colors = ZTheme.colors
    Box(Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape).background(colors.navy), contentAlignment = Alignment.Center) {
        Text(number.toString(), style = ZType.caption.copy(fontWeight = FontWeight.Bold), color = colors.onBrand)
    }
}

/// Who's off this day, with remove (admins: anyone's; a member: their
/// own) and add (admins pick any team member; a member books themselves).
@Composable
private fun TimeOffSection(date: String, calendar: CompanyCalendar, model: CompanyCalendarModel, me: Int?, upcoming: Boolean) {
    val scope = rememberCoroutineScope()
    val off = CalendarLogic.timeOffOn(calendar, date)
    val choosable = if (calendar.canManage) calendar.team else calendar.team.filter { it.id == me }
    var who by remember(date) { mutableStateOf(choosable.singleOrNull()?.id) }
    var window by remember(date) { mutableStateOf<String?>(null) }
    var adding by remember(date) { mutableStateOf(false) }

    ZHeadline("Time off")
    if (off.isEmpty()) ZCaption("Nobody is off.")
    for (row in off) {
        val member = calendar.team.firstOrNull { it.id == row.userId }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
            ZPhotoAvatar(member?.photoUrl, (member?.display ?: row.name ?: "?").take(1), 28.dp)
            Column(modifier = Modifier.weight(1f)) {
                ZBodyStrong(member?.display ?: row.name ?: "Team member")
                ZCaption(CalendarLogic.timeOffLabel(row))
            }
            if (calendar.canManage || row.userId == me) ZTextAction("Remove", enabled = !model.busy) { scope.launch { model.removeTimeOff(row) } }
        }
    }
    if (!upcoming || choosable.isEmpty()) return
    if (!adding) {
        ZButton(if (calendar.canManage) "Add time off" else "I'm off this day", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { adding = true }
        return
    }
    ZCard(padding = ZSpacing.sm) {
        if (calendar.canManage) {
            ZMono("Who")
            ZFlowRow {
                for (member in choosable) FilterChip(selected = who == member.id, onClick = { who = member.id }, label = { Text(member.display) }, leadingIcon = { ZPhotoAvatar(member.photoUrl, member.display.take(1), 20.dp) })
            }
        }
        ZMono("When")
        ZFlowRow {
            FilterChip(selected = window == null, onClick = { window = null }, label = { Text("All day") })
            for (option in CalendarLogic.windows) FilterChip(selected = window == option, onClick = { window = option }, label = { Text(CalendarLogic.windowTag(option)) })
        }
        ZCaption("Time off steers auto-assignment away from them — it never closes your calendar.")
        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
            ZButton("Cancel", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { adding = false }
            val member = choosable.firstOrNull { it.id == who }
            ZButton("Add", compact = true, fullWidth = false, loading = model.busy, enabled = member != null) {
                if (member != null) scope.launch { if (model.addTimeOff(member.id, member.display, date, window)) adding = false }
            }
        }
    }
}

/// Admins: working days, weekly window blocks (a 7 × 3 grid), and a cap
/// on jobs per window (blank = no limit).
@Composable
fun CalendarSettingsSheet(calendar: CompanyCalendar, model: CompanyCalendarModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    var days by remember { mutableStateOf(calendar.workingDays?.toSet() ?: (1..7).toSet()) }
    var weekly by remember { mutableStateOf(calendar.blockedWindows.orEmpty()) }
    var capacity by remember { mutableStateOf(calendar.windowCapacity?.toString() ?: "") }
    val parsed = CalendarLogic.parseCapacity(capacity)

    ZSheet(onDismiss = onDismiss, title = "Calendar settings", dismissable = !model.busy) {
        ZHeadline("Working days")
        ZCaption("Scheduled jobs on other days stay out of your feed.")
        ZFlowRow {
            for (day in 1..7) FilterChip(selected = day in days, onClick = { days = if (day in days) days - day else days + day }, label = { Text(names[day - 1]) })
        }
        if (days.isEmpty()) ZBanner("Pick at least one working day.", tone = ZTone.WARNING)

        ZHeadline("Weekly blocks")
        ZCaption("Close a window every week — say, no evenings on Fridays. Tap to block or open.")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(44.dp))
                for (window in CalendarLogic.windows) ZMono(CalendarLogic.windowTag(window), modifier = Modifier.weight(1f))
            }
            for (day in 1..7) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ZCaption(names[day - 1], modifier = Modifier.width(44.dp), color = if (day in days) colors.ink else colors.inkFaint)
                    for (window in CalendarLogic.windows) {
                        val blocked = CalendarLogic.weeklyBlocked(weekly, day, window)
                        val working = day in days
                        Box(
                            modifier = Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(6.dp))
                                .background(if (!working) colors.surfaceSunken else if (blocked) colors.dangerSoft else colors.surface)
                                .border(1.dp, if (blocked && working) colors.danger else colors.border, RoundedCornerShape(6.dp))
                                .clickable(enabled = working, role = Role.Checkbox) { weekly = CalendarLogic.toggleWeekly(weekly, day, window) }
                                .semantics { contentDescription = "${names[day - 1]} ${CalendarLogic.windowTag(window)}: ${if (!working) "day off" else if (blocked) "blocked" else "open"}" },
                            contentAlignment = Alignment.Center,
                        ) {
                            ZCaption(if (!working) "Off" else if (blocked) "Blocked" else "Open", color = if (blocked && working) colors.danger else if (!working) colors.inkFaint else colors.inkMuted)
                        }
                    }
                }
            }
        }

        ZHeadline("Jobs per window")
        ZTextField("Most jobs in one window", capacity, { capacity = it.filter(Char::isDigit).take(2) }, placeholder = "No limit", keyboardType = KeyboardType.Number,
            error = if (parsed is CalendarLogic.Capacity.Invalid) "Enter 1 to 50, or leave it blank for no limit." else null)
        ZCaption("A full window stops taking new work across your team — the feed, quotes, acceptances and moves. Leave blank for no limit.")

        ZButton("Save", loading = model.busy, enabled = days.isNotEmpty() && parsed is CalendarLogic.Capacity.Valid) {
            val cap = (parsed as? CalendarLogic.Capacity.Valid)?.value
            scope.launch { if (model.saveSettings(days, weekly.filter { it.weekday in days }, cap)) onDismiss() }
        }
    }
}

/// "Start job" from the calendar: the job page's own crew picker over
/// the full job, so the rules (exactly the quote's crew, lead first)
/// stay in one place.
@Composable
fun StartFromCalendar(jobId: Int, client: ApiClient, onDismiss: () -> Unit, onStarted: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val model = remember(jobId) { CompanyJobDetailModel(jobId, client) }
    LaunchedEffect(model) { model.load() }
    val job = model.job
    if (job == null) {
        ZSheet(onDismiss = onDismiss, title = "Start job") {
            when (val state = model.state) {
                is Loadable.Failed -> { ZBody(state.message, tone = ZTextTone.SOFT); ZButton("Try again") { scope.launch { model.load() } } }
                else -> ZSpinner()
            }
        }
        return
    }
    CrewPickerSheet(job, starting = true, roster = model.roster, loadRoster = { model.loadRoster() }, save = { ids ->
        val ok = model.start(ids)
        if (ok) onStarted(model.toast ?: CompanyPresentation.startedToast(job.code, ids.size)) else model.toast?.let { onStarted(it) }
        ok
    }, onDismiss = onDismiss)
}

/// "Quote" on a day's opening: the feed's own composer for that job.
@Composable
fun QuoteFromCalendar(jobId: Int, client: ApiClient, isAdmin: Boolean, onDismiss: () -> Unit, onSent: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val model = remember(jobId) { OpportunitiesModel(client) }
    LaunchedEffect(model) { model.load() }
    val feed = model.state.value
    val job = feed?.opportunities?.firstOrNull { it.id == jobId }
    if (feed == null || job == null) {
        ZSheet(onDismiss = onDismiss, title = "Send a quote") {
            when (val state = model.state) {
                Loadable.Loading -> ZSpinner()
                is Loadable.Failed -> { ZBody(state.message, tone = ZTextTone.SOFT); ZButton("Try again") { scope.launch { model.load() } } }
                is Loadable.Loaded -> { ZBody("This job is no longer open to quotes — it may have been quoted, released or booked.", tone = ZTextTone.SOFT); ZButton("Back to the day", style = ZButtonStyle.OUTLINE, onClick = onDismiss) }
            }
        }
        return
    }
    QuoteComposerSheet(
        job = job, rateCents = feed.hourlyRates[job.category.id.toString()], commissionBps = feed.commissionBps, existing = null,
        payoutsReady = feed.payoutsReady ?: true,
        setupPayouts = { onDismiss(); scope.launch { model.payoutOnboardingUrl()?.let { openUrl(context, it) } } },
        saveRate = if (isAdmin) ({ cents -> model.saveRate(job.category.id, cents) }) else null,
        onDismiss = onDismiss,
    ) { body ->
        val error = model.submitQuote(job, body)
        if (error == null) onSent(model.toast ?: "Quote sent")
        error
    }
}
