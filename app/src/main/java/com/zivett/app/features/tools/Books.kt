package com.zivett.app.features.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.media.PhotoImport
import com.zivett.app.core.models.BooksEndpoints
import com.zivett.app.core.models.PayrollShiftBody
import com.zivett.app.core.models.PayrollSheet
import com.zivett.app.core.models.ProExpense
import com.zivett.app.core.models.ProExpensesResponse
import com.zivett.app.core.models.ProReport
import com.zivett.app.core.models.TeamShift
import com.zivett.app.core.models.Timesheet
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZDateField
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZDropdown
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSegmented
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZStatTile
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTimeField
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.features.company.ShiftForm
import com.zivett.app.features.company.TeamClockLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object BooksLogic {
    private val monthName = DateTimeFormatter.ofPattern("MMM yyyy", Locale.CANADA)

    /// "Nov 2026" from "2026-11".
    fun month(key: String): String = runCatching { YearMonth.parse(key).format(monthName) }.getOrDefault(key)

    /// The bar's share of the largest month, 0..1.
    fun share(value: Int, max: Int): Float = if (max <= 0) 0f else (value.coerceAtLeast(0).toFloat() / max).coerceIn(0f, 1f)

    /// "Nov 1 – Nov 30, 2026".
    fun period(from: String, to: String): String = "${ToolsFormat.day(from).substringBefore(",")} – ${ToolsFormat.day(to)}"

    fun hours(minutes: Int): String = TeamClockLogic.durationOrZero(minutes)

    /// Today when it falls in the period, else the period's first day.
    fun dayInPeriod(start: String, end: String, today: LocalDate): LocalDate {
        val first = LocalDate.parse(start); val last = LocalDate.parse(end)
        return if (today.isBefore(first) || today.isAfter(last)) first else today
    }

    val ranges = listOf("last_12_months" to "Last 12 months", "this_year" to "This year", "last_year" to "Last year")
}

// ── Expenses ────────────────────────────────────────────────────────

class ExpensesModel(private val client: ApiClient) {
    var state by mutableStateOf<Loadable<ProExpensesResponse>>(Loadable.Loading)
    var from by mutableStateOf<String?>(null)
    var to by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)

    suspend fun load() {
        state = state.reloaded { client.send(BooksEndpoints.expenses(from, to)) }
        state.value?.let { from = it.from; to = it.to }
    }

    /// Steps a whole month back or forward.
    suspend fun step(months: Long) {
        val start = from?.let { LocalDate.parse(it) } ?: LocalDate.now().withDayOfMonth(1)
        val next = start.withDayOfMonth(1).plusMonths(months)
        from = next.toString(); to = next.plusMonths(1).minusDays(1).toString()
        state = Loadable.Loading
        load()
    }
}

/// Expenses that aren't one job's cost (web `Expenses.vue`): this month by
/// default, by category, with job costs listed read-only. Export is web-only.
@Composable
fun ExpensesScreen(nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember { ExpensesModel(environment.client) }
    LaunchedEffect(model) { model.load() }
    var editing by remember { mutableStateOf<ProExpense?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column {
        ZTopBar("Expenses", onBack = { nav.back() })
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                ZPageTitle("Expenses", "What the business spent that isn't one job's cost: fuel, tools, insurance, advertising.")
                ZButton("Add an expense") { adding = true }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZTextAction("Previous") { scope.launch { model.step(-1) } }
                    ZBodyStrong(model.from?.let { f -> model.to?.let { BooksLogic.period(f, it) } } ?: "This month", Modifier.weight(1f).padding(horizontal = ZSpacing.sm))
                    ZTextAction("Next") { scope.launch { model.step(1) } }
                }
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { data ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                            ZStatTile(Money.format(data.totals.amountCents), "Spent", Modifier.weight(1f))
                            ZStatTile(Money.format(data.totals.gstCents), "GST paid", Modifier.weight(1f))
                        }
                        if (data.byCategory.isNotEmpty()) ZCard {
                            ZHeadline("By category")
                            for (row in data.byCategory) Row { ZCaption(row.label, Modifier.weight(1f)); ZBody(Money.format(row.amountCents)) }
                        }
                        if (data.expenses.isEmpty()) ZEmptyState(Icons.Outlined.ReceiptLong, "No expenses this period", "Add what you spend so Reports can show what's left.")
                        for (expense in data.expenses) ZCard(onClick = { editing = expense }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                                expense.receipt?.let { AsyncImage(model = it.thumbUrl ?: it.url, contentDescription = "Receipt", modifier = Modifier.height(44.dp).clip(RoundedCornerShape(6.dp))) }
                                Column(Modifier.weight(1f)) {
                                    ZBodyStrong(expense.description, maxLines = 1)
                                    ZCaption("${ToolsFormat.day(expense.spentOn)} · ${expense.categoryLabel ?: expense.category}")
                                }
                                ZBodyStrong(Money.format(expense.amountCents))
                            }
                        }
                        if (data.jobCosts.isNotEmpty()) ZCard {
                            ZHeadline("Costs on jobs")
                            ZCaption("Recorded on each job. They already count there and in Reports.")
                            for (cost in data.jobCosts) ClickRow({ cost.jobId?.let { nav.job(it) } }) {
                                Row { ZCaption("${ToolsFormat.day(cost.spentOn)} · ${cost.description}${cost.jobCode?.let { " · $it" } ?: ""}", Modifier.weight(1f)); ZCaption(Money.format(cost.amountCents)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.padding(ZSpacing.md))
            }
        }
    }

    val categories = model.state.value?.categories.orEmpty()
    if (adding || editing != null) ExpenseSheet(editing, categories, environment.client, onDismiss = { adding = false; editing = null }) { message ->
        model.toast = message; scope.launch { model.load() }
    }
}

@Composable
private fun ExpenseSheet(editing: ProExpense?, categories: List<com.zivett.app.core.models.LabelValue>, client: ApiClient, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var date by remember { mutableStateOf(editing?.spentOn ?: LocalDate.now().toString()) }
    var category by remember { mutableStateOf(editing?.category ?: "") }
    var description by remember { mutableStateOf(editing?.description.orEmpty()) }
    var amount by remember { mutableStateOf(editing?.let { com.zivett.app.features.company.QuoteLineLogic.dollarsText(it.amountCents) }.orEmpty()) }
    var gst by remember { mutableStateOf(editing?.gstCents?.let { com.zivett.app.features.company.QuoteLineLogic.dollarsText(it) }.orEmpty()) }
    var receipt by remember { mutableStateOf<ByteArray?>(null) }
    var removeReceipt by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { receipt = PhotoImport.jpegData(context, listOf(uri)).firstOrNull(); removeReceipt = false }
    }
    ZSheet(onDismiss, title = if (editing != null) "Edit expense" else "Add an expense", dismissable = !saving) {
        ZDateField("Date paid", date, { date = it }, error = errors["spent_on"])
        ZDropdown("Category", category, categories.map { it.value to it.label }, placeholder = "Choose one", error = errors["category"]) { category = it }
        ZTextField("What it was for", description, { description = it.take(160) }, placeholder = "Fuel for the van", error = errors["description"])
        ZTextField("Paid ($)", amount, { amount = it }, keyboardType = KeyboardType.Decimal, error = errors["amount_cents"])
        ZTextField("GST paid ($, optional)", gst, { gst = it }, keyboardType = KeyboardType.Decimal, error = errors["gst_cents"])
        val hasReceipt = receipt != null || (editing?.receipt != null && !removeReceipt)
        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
            ZTextAction(if (hasReceipt) "Replace the receipt photo" else "Add a receipt photo") { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            if (hasReceipt) ZTextAction("Remove it", color = ZTheme.colors.inkSoft) { receipt = null; removeReceipt = editing?.receipt != null }
        }
        if (receipt != null) ZCaption("Receipt photo ready.")
        ZButton(if (editing != null) "Save expense" else "Add expense", loading = saving, enabled = category.isNotBlank() && description.isNotBlank() && (Money.parseCents(amount) ?: 0) > 0) {
            saving = true; errors = emptyMap()
            scope.launch {
                try {
                    client.send(BooksEndpoints.saveExpense(editing?.id, date, category, description.trim(), Money.parseCents(amount) ?: 0, gst.takeIf { it.isNotBlank() }?.let { Money.parseCents(it) }, receipt, removeReceipt))
                    onDismiss(); onSaved(if (editing != null) "Expense saved." else "Expense added.")
                } catch (e: CancellationException) { throw e } catch (e: ApiError.Validation) { errors = e.errors.firstMessages } catch (e: Exception) { errors = mapOf("description" to e.userMessage) } finally { saving = false }
            }
        }
        if (editing != null) ZButton("Delete expense", style = ZButtonStyle.GHOST) { confirmDelete = true }
    }
    if (confirmDelete && editing != null) ToolsConfirm("Delete this expense?", "It comes off your books and Reports. This can't be undone.", "Delete expense", danger = true, onDismiss = { confirmDelete = false }) {
        scope.launch { runCatching { client.send(BooksEndpoints.deleteExpense(editing.id)) }.onSuccess { onDismiss(); onSaved("Expense deleted.") } }
    }
}

// ── Time on jobs ────────────────────────────────────────────────────

/// Who worked how long on own jobs, a week at a time (web `Timesheets.vue`,
/// org admins). Export is web-only.
@Composable
fun TimesheetsScreen(nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    var monday by remember { mutableStateOf(LocalDate.now().with(java.time.DayOfWeek.MONDAY)) }
    var state by remember { mutableStateOf<Loadable<Timesheet>>(Loadable.Loading) }
    suspend fun load() { state = state.reloaded { environment.client.send(BooksEndpoints.timesheets(monday.toString(), monday.plusDays(6).toString())) } }
    LaunchedEffect(monday) { state = Loadable.Loading; load() }

    Column {
        ZTopBar("Time on jobs", onBack = { nav.back() })
        ZScreen(onRefresh = { load() }) {
            ZPageTitle("Time on jobs", "Who worked how long on your own jobs, from the job timers and time added on each job.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZTextAction("Previous") { monday = monday.minusWeeks(1) }
                ZBodyStrong(BooksLogic.period(monday.toString(), monday.plusDays(6).toString()), Modifier.weight(1f).padding(horizontal = ZSpacing.sm))
                ZTextAction("Next") { monday = monday.plusWeeks(1) }
            }
            ZLoadable(state, retry = { scope.launch { load() } }) { sheet ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    ZCaption("${BooksLogic.hours(sheet.totalMinutes)} in all.")
                    if (sheet.people.isEmpty()) ZCaption("No time on jobs this week.")
                    for (person in sheet.people) ZCard {
                        Row { ZHeadline(person.user?.name ?: "Not assigned", Modifier.weight(1f)); ZBodyStrong(BooksLogic.hours(person.totalMinutes)) }
                        for (entry in person.entries) ClickRow({ entry.job?.id?.let { nav.job(it) } }) {
                            Row {
                                ZCaption(listOfNotNull(ToolsFormat.day(entry.workedOn), entry.job?.code, entry.job?.title, entry.note).joinToString(" · "), Modifier.weight(1f))
                                ZCaption(BooksLogic.hours(entry.minutes))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.padding(ZSpacing.md))
        }
    }
}

// ── Payroll ─────────────────────────────────────────────────────────

class PayrollModel(private val client: ApiClient) {
    var date by mutableStateOf<String?>(null)
    var state by mutableStateOf<Loadable<PayrollSheet>>(Loadable.Loading)
    var busy by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)

    suspend fun load() { state = state.reloaded { client.send(BooksEndpoints.payroll(date)) } }

    suspend fun go(next: String?) { date = next; state = Loadable.Loading; load() }

    private suspend fun act(success: String, operation: suspend () -> PayrollSheet): Boolean {
        busy = true; error = null
        return try { state = Loadable.Loaded(operation()); toast = success; true }
        catch (e: CancellationException) { throw e } catch (e: ApiError.Validation) { error = e.errors.firstMessages.values.firstOrNull() ?: e.errors.message; false }
        catch (e: Exception) { error = e.userMessage; toast = e.userMessage; false } finally { busy = false }
    }

    suspend fun approve(start: String) = act("Approved. These shifts are locked.") { client.send(BooksEndpoints.approve(start)) }
    suspend fun reopen(start: String) = act("Reopened for changes.") { client.send(BooksEndpoints.reopen(start)) }
    suspend fun saveShift(id: Int?, body: PayrollShiftBody) = act(if (id == null) "Shift added." else "Shift saved.") {
        if (id == null) client.send(BooksEndpoints.addShift(body)) else client.send(BooksEndpoints.editShift(id, body))
    }
    suspend fun deleteShift(id: Int) = act("Shift deleted.") { client.send(BooksEndpoints.deleteShift(id)) }
}

/// The team clock's pay period (web `Payroll.vue`, org admins, payroll on):
/// paid hours split regular / overtime, gross pay, shifts to review, and
/// approve / reopen. NOT a payroll run: no tax or remittances. Export and
/// pay settings are web-only.
@Composable
fun PayrollScreen(nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember { PayrollModel(environment.client) }
    LaunchedEffect(model) { model.load() }
    var editing by remember { mutableStateOf<Pair<Int, ShiftForm>?>(null) }

    Column {
        ZTopBar("Payroll", onBack = { nav.back() })
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                ZPageTitle("Payroll", "Your team's paid hours for the pay period, from the team clock. It isn't a payroll run: no tax, CPP, EI or remittances.")
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { sheet ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ZTextAction("Previous") { scope.launch { model.go(sheet.previous) } }
                            ZBodyStrong(BooksLogic.period(sheet.period.start, sheet.period.end), Modifier.weight(1f).padding(horizontal = ZSpacing.sm))
                            ZTextAction("Next") { scope.launch { model.go(sheet.next) } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                            ZStatTile(BooksLogic.hours(sheet.totals.paidMinutes), "Paid", Modifier.weight(1f))
                            ZStatTile(BooksLogic.hours(sheet.totals.overtimeMinutes), "Overtime", Modifier.weight(1f))
                            ZStatTile(sheet.totals.grossCents?.let { Money.format(it) } ?: "Not set", "Gross", Modifier.weight(1f))
                        }
                        if (sheet.totals.review > 0) ZBanner("${ToolsCopy.plural(sheet.totals.review, "shift")} to review: started by work, closed for them, added or edited.", tone = ZTone.WARNING)
                        sheet.totals.missingRates?.takeIf { it > 0 }?.let { ZCaption("${ToolsCopy.plural(it, "person")} with hours has no pay rate yet. Set rates on the web.") }
                        model.error?.let { ZBanner(it, tone = ZTone.DANGER) }
                        val allApproved = sheet.people.filter { it.totals.paidMinutes > 0 }.let { it.isNotEmpty() && it.all { p -> p.approved } }
                        if (allApproved) ZButton("Reopen this period", style = ZButtonStyle.OUTLINE, loading = model.busy) { scope.launch { model.reopen(sheet.period.start) } }
                        else ZButton("Approve this period", loading = model.busy, enabled = sheet.people.none { it.open }) { scope.launch { model.approve(sheet.period.start) } }
                        if (sheet.people.any { it.open }) ZCaption("Someone is still clocked in. Approve once everyone has ended their day.")
                        for (person in sheet.people) ZCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                                ZHeadline(person.user.name, Modifier.weight(1f))
                                if (person.approved) ZBadge("Approved", ZTone.SUCCESS)
                                if (person.review > 0) ZBadge("${person.review} to review", ZTone.WARNING)
                                if (person.open) ZBadge("On the clock", ZTone.INFO)
                            }
                            ZCaption(listOfNotNull(
                                "Paid ${BooksLogic.hours(person.totals.paidMinutes)}",
                                if (person.totals.overtimeMinutes > 0) "1.5x ${BooksLogic.hours(person.totals.overtimeMinutes)}" else null,
                                if (person.totals.doubleMinutes > 0) "2x ${BooksLogic.hours(person.totals.doubleMinutes)}" else null,
                                person.pay?.totalCents?.let { "gross ${Money.format(it)}" },
                            ).joinToString(" · "))
                            for (day in person.days) {
                                ZDivider()
                                ZLabel(ToolsFormat.day(day.date) + if (day.jobMinutes > 0) " · ${BooksLogic.hours(day.jobMinutes)} on jobs" else "")
                                for (shift in day.shifts) ClickRow({ if (!shift.approved) editing = person.user.id to ShiftForm.fixing(shift) }) {
                                    Row {
                                        ZCaption(TeamClockLogic.shiftTimes(shift) + (shift.note?.let { " · $it" } ?: ""), Modifier.weight(1f))
                                        if (shift.needsReview) ZBadge("Review", ZTone.WARNING)
                                        ZCaption(BooksLogic.hours(shift.paidMinutes))
                                    }
                                }
                            }
                            if (!person.approved) ZTextAction("Add a shift") { editing = person.user.id to ShiftForm.adding(BooksLogic.dayInPeriod(sheet.period.start, sheet.period.end, LocalDate.now())) }
                        }
                    }
                }
                Spacer(Modifier.padding(ZSpacing.md))
            }
        }
    }

    editing?.let { (userId, form) -> PayrollShiftSheet(userId, form, model, onDismiss = { editing = null }) }
}


@Composable
private fun PayrollShiftSheet(userId: Int, initial: ShiftForm, model: PayrollModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(initial) }
    var deleting by remember { mutableStateOf(false) }
    ZSheet(onDismiss, title = if (form.shiftId != null) "Edit shift" else "Add a shift", dismissable = !model.busy) {
        ZDateField("Day", form.day.toString(), { form = form.copy(day = LocalDate.parse(it)) })
        ZTimeField("Start", "%02d:%02d".format(form.start.hour, form.start.minute), { it.split(":").takeIf { p -> p.size == 2 }?.let { (h, m) -> form = form.copy(start = LocalTime.of(h.toInt(), m.toInt())) } })
        ZTimeField("End", form.end?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "", { text -> form = form.copy(end = text.split(":").takeIf { it.size == 2 }?.let { (h, m) -> LocalTime.of(h.toInt(), m.toInt()) }) }, placeholder = "Still on the clock")
        ZTextField("Unpaid break (minutes)", form.breakText, { form = form.copy(breakText = it) }, keyboardType = KeyboardType.Number)
        ZTextField("Note (optional)", form.note, { form = form.copy(note = it.take(300)) })
        form.paidLine()?.let { ZCaption(it) }
        model.error?.let { ZBanner(it, tone = ZTone.DANGER) }
        ZButton("Save shift", loading = model.busy, enabled = form.end != null || form.stillOpen) {
            val body = form.body(null)
            scope.launch { if (model.saveShift(form.shiftId, PayrollShiftBody(body.startedAt, body.endedAt, body.breakMinutes, body.note, if (form.shiftId == null) userId else null))) onDismiss() }
        }
        if (form.shiftId != null) ZButton("Delete shift", style = ZButtonStyle.GHOST) { deleting = true }
    }
    if (deleting) form.shiftId?.let { id -> ToolsConfirm("Delete this shift?", "Its hours come off the pay period.", "Delete shift", danger = true, onDismiss = { deleting = false }) { scope.launch { if (model.deleteShift(id)) onDismiss() } } }
}

// ── Reports ─────────────────────────────────────────────────────────

/// Reports on the company's own work (web `Reports.vue`, Pro and Elite):
/// invoiced, collected, costs and what's left by month, quotes won, days
/// to pay, top customers and trades.
@Composable
fun ReportsScreen(nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    var range by remember { mutableStateOf("last_12_months") }
    var state by remember { mutableStateOf<Loadable<ProReport>>(Loadable.Loading) }
    suspend fun load() { state = state.reloaded { environment.client.send(BooksEndpoints.reports(range)) } }
    LaunchedEffect(range) { state = Loadable.Loading; load() }

    Column {
        ZTopBar("Reports", onBack = { nav.back() })
        ZScreen(onRefresh = { load() }) {
            ZPageTitle("Reports", "How your own work is going. ZiVETT jobs aren't counted here.")
            ZLoadable(state, retry = { scope.launch { load() } }) { report ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    if (!report.allowed) {
                        ZCard {
                            ZHeadline("Reports come with a higher plan")
                            ZBody(report.upgrade?.let { "Reports are part of the ${it.name} plan." } ?: "Reports aren't part of your plan.")
                            if (report.upgrade != null) ZButton("See plans") { nav.plan() }
                        }
                        return@Column
                    }
                    ZSegmented(BooksLogic.ranges, range) { range = it }
                    val totals = report.totals
                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                        ZStatTile(Money.format(totals.invoicedCents), "Invoiced", Modifier.weight(1f))
                        ZStatTile(Money.format(totals.collectedCents), "Collected", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                        ZStatTile(Money.format(totals.costsCents), "Costs", Modifier.weight(1f))
                        ZStatTile(Money.format(totals.leftCents), "Left", Modifier.weight(1f))
                    }
                    ZCaption("${Money.format(totals.owedCents)} still owed to you. Collected is ${ToolsCopy.PRO_REPORTED.lowercase()}.")
                    ZCard {
                        ZHeadline("Invoiced by month")
                        val max = report.months.maxOfOrNull { it.invoicedCents } ?: 0
                        for (month in report.months) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                                ZCaption(BooksLogic.month(month.month), Modifier.weight(0.3f))
                                Box(Modifier.weight(0.45f).height(10.dp)) {
                                    Box(Modifier.fillMaxWidth(BooksLogic.share(month.invoicedCents, max)).height(10.dp).clip(RoundedCornerShape(3.dp)).background(ZTheme.colors.navy))
                                }
                                ZCaption(Money.format(month.invoicedCents), Modifier.weight(0.25f))
                            }
                        }
                    }
                    report.quotes?.let { q ->
                        ZCard {
                            ZHeadline("Quotes")
                            ZBody("${Money.count(q.sent)} sent · ${Money.count(q.won)} won · ${Money.count(q.lost)} lost · ${Money.count(q.open)} open")
                            q.winRate?.let { ZCaption("You win $it% of the quotes customers answer.") }
                        }
                    }
                    report.daysToPay?.let { ZCard { ZHeadline("Days to pay"); ZBody("Customers pay in ${it.median} days, typically (${ToolsCopy.plural(it.invoices, "paid invoice")}).") } }
                    if (report.topCustomers.isNotEmpty()) ZCard {
                        ZHeadline("Top customers")
                        for (c in report.topCustomers) ClickRow({ c.id?.let { nav.customer(it) } }) { Row { ZCaption(c.name ?: "Customer", Modifier.weight(1f)); ZCaption(Money.format(c.invoicedCents)) } }
                    }
                    if (report.trades.isNotEmpty()) ZCard {
                        ZHeadline("Jobs by trade")
                        for (t in report.trades) Row { ZCaption(t.name, Modifier.weight(1f)); ZCaption(ToolsCopy.plural(t.jobs, "job")) }
                    }
                }
            }
            Spacer(Modifier.padding(ZSpacing.md))
        }
    }
}

@Suppress("unused")
private fun keep(shift: TeamShift) = shift
@Suppress("unused")
private val keepTone = ZTextTone.SOFT
