package com.zivett.app.features.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.CompanyCalendar
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.Reminders
import com.zivett.app.core.models.ToolsEndpoints
import com.zivett.app.core.models.ToolsOverview
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZCheckLine
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZStatTile
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZToggleRow
import com.zivett.app.design.ZTone
import com.zivett.app.features.company.MyDayLogic
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.shared.LocalNavBadges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/// Wording shared across the business tools screens (web `lib/businessTools.js`).
object ToolsCopy {
    const val PRO_REPORTED = "Pro-reported"

    /// The customer-ownership promise (PRD TRU-01).
    val ownershipPromise = listOf(
        "Your customers are yours. ZiVETT never contacts them, markets to them or turns them into marketplace leads.",
        "They are never added to our advertising audiences, marketing lists or sales pipeline.",
        "No other Pro can see them, and they are never shown on the marketplace.",
        "We only email a customer when you press send, or as a reminder you switched on, always under your business name.",
        "You can download your whole list, or delete it, at any time.",
    )

    fun plural(count: Int, noun: String) = "${Money.count(count)} $noun${if (count == 1) "" else "s"}"
}

/// One line of the Overview's "Next 7 days": own and ZiVETT jobs in one run.
data class WeekRow(val key: String, val own: Boolean, val date: String, val whenText: String, val sort: String, val title: String, val detail: String?, val done: Boolean, val id: Int)

object ToolsOverviewLogic {
    private val windowRank = mapOf("morning" to 0, "afternoon" to 1, "evening" to 2)
    private val windowShort = mapOf("morning" to "Morning", "afternoon" to "Afternoon", "evening" to "Evening")
    private val dayLabel = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.CANADA)

    /// Seven days from `from`, only the days with work, each in visit order
    /// (web Overview `days`). Busy placeholders are dropped.
    fun week(calendar: CompanyCalendar, from: LocalDate, today: LocalDate): List<Pair<String, List<WeekRow>>> {
        val rows = calendar.jobs.map { job ->
            WeekRow("z-${job.id}", false, job.date, job.window?.let { windowShort[it] } ?: "Now", "${windowRank[job.window] ?: -1}", job.title, job.code, false, job.id)
        } + calendar.ownJobs.filter { !it.busy }.mapNotNull { job ->
            val id = job.id.intValue ?: return@mapNotNull null
            WeekRow(
                "o-$id", true, job.date,
                job.startTime?.let { MyDayLogic.formatClock(it) } ?: windowShort[job.window].orEmpty(),
                "${windowRank[job.window] ?: 0}${job.startTime.orEmpty()}",
                job.title.orEmpty(), job.customerName, job.status == "completed", id,
            )
        }
        return (0 until 7).mapNotNull { offset ->
            val date = from.plusDays(offset.toLong())
            val day = rows.filter { it.date == date.toString() }.sortedBy { it.sort }
            if (day.isEmpty()) null else (if (date == today) "Today" else date.format(dayLabel)) to day
        }
    }

    fun reminderRows(reminders: Reminders) = listOf(
        Triple(Reminders.APPOINTMENT, "Appointment reminders", "The day before a job, your customer gets an email with the day, time and address."),
        Triple(Reminders.QUOTE_FOLLOW_UP, "Quote follow-ups", "A quote with no answer after ${reminders.followUpDays} days is sent again, once, with its approval link."),
        Triple(Reminders.INVOICE_OVERDUE, "Overdue invoice reminders", "The day after an invoice is due, and once more a week later if it is still unpaid."),
    )
}

class ToolsOverviewModel(private val client: ApiClient, private val seesCalendar: Boolean) {
    var state by mutableStateOf<Loadable<ToolsOverview>>(Loadable.Loading)
    var week by mutableStateOf<CompanyCalendar?>(null)
    var weekFailed by mutableStateOf(false)
    var weekFrom: LocalDate = LocalDate.now()
    var savingReminder by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)

    suspend fun load() {
        state = state.reloaded { client.send(ToolsEndpoints.overview()) }
        if (seesCalendar) loadWeek()
    }

    suspend fun loadWeek() {
        weekFrom = LocalDate.now()
        try {
            week = client.send(CompanyEndpoints.rangeCalendar(weekFrom.toString(), 7)); weekFailed = false
        } catch (e: CancellationException) { throw e } catch (_: Exception) { weekFailed = true }
    }

    suspend fun setReminder(kind: String, on: Boolean) {
        val overview = state.value ?: return
        savingReminder = kind
        try {
            val reminders = client.send(ToolsEndpoints.setReminder(kind, on)).reminders
            // The PATCH answer has no `can_edit`; the person just edited, so they can.
            state = Loadable.Loaded(overview.copy(reminders = reminders.copy(canEdit = true)))
            toast = if (on) "Switched on. The next ones due go out automatically." else "Switched off."
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            toast = e.userMessage
        } finally { savingReminder = null }
    }
}

/// The Business tools home (web `pages/company/business/Overview.vue`): the
/// company's own figures, the next 7 days, the promise, the reminders and
/// the allowance. Each figure opens its list.
@Composable
fun ToolsOverviewScreen(nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val seesCalendar = environment.session.user?.canOpen("calendar") == true
    val model = remember { ToolsOverviewModel(environment.client, seesCalendar) }
    LaunchedEffect(model) { model.load() }
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(model) { if (resumed) scope.launch { model.load() }; resumed = true; onPauseOrDispose { } }
    val live = LocalNavBadges.current?.liveEvents ?: 0
    LaunchedEffect(live) { if (live > 0) model.load() }

    com.zivett.app.design.ZToastBox(model.toast, { model.toast = null }) {
        ZScreen(onRefresh = { model.load() }) {
            ZLoadable(model.state, retry = { scope.launch { model.load() } }) { overview ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    ZPageTitle("Your business", "Customers, quotes, jobs and invoices for the work you find yourself. ZiVETT takes 0% commission on it, and none of it is shared with the marketplace.")

                    ToolsQuickActions(nav)
                    val stats = overview.stats
                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                        ZStatTile(Money.count(stats.customers), "Customers", Modifier.weight(1f)) { nav.customers() }
                        ZStatTile(Money.count(stats.activeJobs), "Active jobs", Modifier.weight(1f)) { nav.jobs("active") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                        ZStatTile(Money.count(stats.pendingQuotes), "Quotes waiting", Modifier.weight(1f)) { nav.quotes("sent") }
                        ZStatTile(Money.format(stats.outstandingCents), "Owed to you", Modifier.weight(1f)) { nav.invoices("open") }
                    }
                    ZStatTile(Money.format(stats.paidThisMonthCents), "Paid this month · ${ToolsCopy.PRO_REPORTED}") { nav.invoices("paid") }
                    ZCaption("${ToolsCopy.plural(stats.outstandingInvoices, "unpaid invoice")}. Quotes waiting are sent and not expired.")

                    if (seesCalendar) WeekCard(model, nav)
                    PromiseCard()
                    overview.reminders?.let { RemindersCard(it, model, nav) }

                    ZCard {
                        ZHeadline("Your allowance")
                        overview.businessTools.plan?.let { ZLabel("$it plan") }
                        overview.businessTools.allowanceSentence?.let { ZBody(it) }
                        ZCaption("Invoices and recording payments are never limited, so you can always bill for work you've done.", tone = ZTextTone.SOFT)
                        ZCaption("Payments you record are marked Pro-reported: ZiVETT never holds or collects that money.", tone = ZTextTone.SOFT)
                    }
                    Spacer(Modifier.padding(ZSpacing.md))
                }
            }
        }
    }
}

@Composable
private fun WeekCard(model: ToolsOverviewModel, nav: ToolsNav) {
    val scope = rememberCoroutineScope()
    ZCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZHeadline("Next 7 days", Modifier.weight(1f))
            com.zivett.app.design.ZTextAction("Calendar") { nav.calendar() }
        }
        ZCaption("Your own jobs and your ZiVETT jobs, in one run.", tone = ZTextTone.SOFT)
        val week = model.week
        when {
            model.weekFailed -> Row(verticalAlignment = Alignment.CenterVertically) {
                ZCaption("Couldn't load your schedule.", Modifier.weight(1f))
                com.zivett.app.design.ZTextAction("Try again") { scope.launch { model.loadWeek() } }
            }
            week == null -> ZCaption("Loading…")
            else -> {
                val days = ToolsOverviewLogic.week(week, model.weekFrom, LocalDate.now())
                if (days.isEmpty()) ZCaption("Nothing booked this week.")
                for ((label, rows) in days) {
                    ZLabel(label)
                    for (row in rows) {
                        ZCard(padding = ZSpacing.sm, onClick = { if (row.own) nav.ownJob(row.id) else nav.zivettJob(row.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                                ZCaption(row.whenText, Modifier.width(76.dp), tone = ZTextTone.SOFT)
                                Column(Modifier.weight(1f)) {
                                    ZBodyStrong(row.title.ifBlank { "Job" }, maxLines = 1, tone = if (row.done) ZTextTone.MUTED else ZTextTone.INK)
                                    row.detail?.let { ZCaption(it, maxLines = 1) }
                                }
                                ZBadge(if (row.own) "Your own" else "ZiVETT", if (row.own) ZTone.INFO else ZTone.NEUTRAL)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PromiseCard() {
    ZCard {
        ZHeadline("Your customers stay yours")
        ZCaption("Our promise. It is part of the ZiVETT Pro Agreement.", tone = ZTextTone.SOFT)
        for (line in ToolsCopy.ownershipPromise) ZCheckLine(line, tone = ZTextTone.INK)
    }
}

@Composable
private fun RemindersCard(reminders: Reminders, model: ToolsOverviewModel, nav: ToolsNav) {
    val scope = rememberCoroutineScope()
    val rows = ToolsOverviewLogic.reminderRows(reminders)
    ZCard {
        ZHeadline("Automatic reminders")
        ZCaption("Emails ZiVETT sends your customers for you, under your business name. They only go to customers with an email on file.", tone = ZTextTone.SOFT)
        if (reminders.allowed) {
            if (reminders.defaulted) ZBanner("These are on for you. Turn off any you don't want, or leave out one customer from their page.", tone = ZTone.INFO)
            for ((key, label, description) in rows) {
                ZToggleRow(label, description, reminders.settings[key] ?: true, onCheckedChange = { on -> scope.launch { model.setReminder(key, on) } }, enabled = reminders.canEdit && model.savingReminder != key)
            }
            if (!reminders.canEdit) ZCaption("Only a company admin can change these.")
            else ZCaption("Sent between 9 am and 6 pm, and counted in your daily email limit. Quotes and invoices from before you switch one on are left alone if they're more than a few weeks old.")
        } else {
            for ((_, label, description) in rows) ZCaption("$label. $description", tone = ZTextTone.SOFT)
            reminders.upgrade?.let { upgrade ->
                ZBody("Reminders come with the ${upgrade.name} plan.")
                com.zivett.app.design.ZTextAction("See plans") { nav.plan() }
            }
        }
    }
}
