package com.zivett.app.features.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.zivett.app.app.CompanyInvoiceRoute
import com.zivett.app.app.CompanyJobRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.AssignmentRoster
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.CompanyQuotesResponse
import com.zivett.app.core.models.Invoice
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.Quote
import com.zivett.app.core.models.QuoteBody
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMonoBody
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZType
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.customer.jobs.hoursLabel
import com.zivett.app.features.shared.JobStatusBadge
import com.zivett.app.features.shared.LocalNav
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.TextUnit
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZTextField
import com.zivett.app.features.shared.PhotoGrid
import java.time.Instant

class CompanyQuotesModel(private val client: ApiClient) {
    var state by mutableStateOf<Loadable<CompanyQuotesResponse>>(Loadable.Loading)
    var toast by mutableStateOf<String?>(null)

    suspend fun load() { state = state.reloaded { client.send(CompanyEndpoints.quotes()) } }

    suspend fun revise(quote: Quote, body: QuoteBody): String? {
        try {
            val updated = client.send(CompanyEndpoints.updateQuote(quote.id, body)).quote
            state.value?.let { state = Loadable.Loaded(it.copy(quotes = it.quotes.map { q -> if (q.id == quote.id) updated else q })) }
            toast = "Quote for ${quote.job?.code ?: "the job"} updated"
            return null
        } catch (error: ApiError) {
            if (error is ApiError.Validation && error.errors.errors.isNotEmpty()) return error.errors.first("proposed_date") ?: error.errors.first("estimated_hours") ?: error.errors.first("crew_size") ?: error.errors.message
            // Into the composer, which stays open: a toast + null closed it
            // as though the revision had been sent, losing the edit.
            return error.userMessage
        } catch (error: Exception) { return error.userMessage }
    }

    /// The composer's in-place rate change; null = saved (the rate map is swapped).
    suspend fun saveRate(categoryId: Int, cents: Int): String? = when (val outcome = CompanyRates.save(client, categoryId, cents)) {
        is CompanyRates.Outcome.Saved -> { state.value?.let { state = Loadable.Loaded(it.copy(hourlyRates = outcome.rates)) }; null }
        is CompanyRates.Outcome.Failed -> outcome.message
    }

    suspend fun withdraw(quote: Quote) {
        try {
            client.send(CompanyEndpoints.withdrawQuote(quote.id))
            state.value?.let { state = Loadable.Loaded(it.copy(quotes = it.quotes.filter { q -> q.id != quote.id })) }
            toast = "Quote for ${quote.job?.code ?: "the job"} withdrawn — the job is back in your feed"
        } catch (error: Exception) { toast = error.userMessage }
    }
}

@Composable
fun CompanyQuotesScreen() {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val model = remember { CompanyQuotesModel(environment.client) }
    var revising by remember { mutableStateOf<Quote?>(null) }
    var withdrawing by remember { mutableStateOf<Quote?>(null) }
    var filter by remember { mutableStateOf(CompanyQuotesLogic.Filter.ALL) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(model) { model.load() }

    ZToastBox(model.toast, { model.toast = null }) {
        ZScreen(onRefresh = { model.load() }) {
            ZLoadable(model.state, retry = { scope.launch { model.load() } }) { response ->
                val now = Instant.now()
                val waiting = CompanyQuotesLogic.waiting(response.quotes)
                val settled = CompanyQuotesLogic.past(response.quotes, CompanyQuotesLogic.Filter.ALL, "")
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    ZPageTitle("My quotes", subtitle = CompanyQuotesLogic.summary(response.quotes, now))
                    if (response.quotes.isEmpty()) {
                        ZEmptyState(Icons.Outlined.Description, "No quotes yet", "Every job in Opportunities takes a quote (your hourly rate × estimated hours × crew), and they land here.")
                    } else {
                        ZHeadline("Waiting on a decision")
                        if (waiting.isEmpty()) ZEmptyState(Icons.Outlined.Description, "Nothing waiting on a decision", "Quote a job in Opportunities and it waits here until the booker decides.")
                        for (quote in waiting) {
                            WaitingQuoteCard(quote, response.commissionBps, now, onRevise = { revising = quote }, onWithdraw = { withdrawing = quote })
                        }

                        ZHeadline("Past quotes", modifier = Modifier.padding(top = ZSpacing.sm))
                        if (settled.isEmpty()) {
                            ZCaption("Quotes the booker has decided on land here — won, not chosen, or the job was cancelled.")
                        } else {
                            val filters = CompanyQuotesLogic.Filter.entries
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                filters.forEachIndexed { index, option ->
                                    SegmentedButton(selected = filter == option, onClick = { filter = option }, shape = SegmentedButtonDefaults.itemShape(index, filters.size)) { Text(option.label) }
                                }
                            }
                            ZTextField("", query, { query = it }, placeholder = "Search by title or code", imeAction = ImeAction.Search)
                            val shown = CompanyQuotesLogic.past(response.quotes, filter, query)
                            if (shown.isEmpty()) ZCaption("No past quotes match.")
                            for (quote in shown) {
                                val won = CompanyQuotesLogic.outcome(quote) == CompanyQuotesLogic.Outcome.WON
                                val jobId = quote.job?.id
                                PastQuoteRow(quote, onClick = if (won && jobId != null) ({ nav.navigate(CompanyJobRoute(jobId)) }) else null)
                            }
                        }
                    }
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }

    revising?.let { quote ->
        quoteJobAsJob(quote)?.let { job ->
            val isAdmin = LocalAppEnvironment.current.session.user?.organizationRole == OrganizationRole.ADMIN
            QuoteComposerSheet(job, model.state.value?.hourlyRates?.get(job.category.id.toString()), model.state.value?.commissionBps ?: 1500, quote,
                saveRate = if (isAdmin) ({ cents -> model.saveRate(job.category.id, cents) }) else null,
                onDismiss = { revising = null }) { body -> model.revise(quote, body) }
        }
    }
    withdrawing?.let { quote ->
        AlertDialog(
            onDismissRequest = { withdrawing = null },
            title = { Text("Withdraw this quote?") },
            text = { Text("Your ${Money.format(quote.amountCents)} quote on ${quote.job?.title ?: "this job"} (${quote.job?.code ?: ""}) comes off the table — the booker will no longer see or be able to accept it.") },
            confirmButton = { TextButton(onClick = { withdrawing = null; scope.launch { model.withdraw(quote) } }) { Text("Withdraw quote", color = colors.danger) } },
            dismissButton = { TextButton(onClick = { withdrawing = null }) { Text("Keep the quote") } },
        )
    }
}

/// The job code — the one monospace thing on the screen (people copy it).
@Composable
private fun QuoteCode(code: String) {
    Text(code, style = ZType.mono.copy(letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.Normal), color = ZTheme.colors.inkMuted)
}

private val money = ZType.headline.copy(fontFeatureSettings = "tnum")

/// A pending quote: what was quoted, what the company keeps, when it would
/// arrive, where it stands. Revise is the one button (only before release);
/// Withdraw sits behind More with its confirm.
@Composable
private fun WaitingQuoteCard(quote: Quote, commissionBps: Int, now: Instant, onRevise: () -> Unit, onWithdraw: () -> Unit) {
    val colors = ZTheme.colors
    ZCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ZBodyStrong(quote.job?.title ?: "Job", maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    quote.job?.category?.name?.let { ZCaption(it) }
                    if (quote.job?.category != null && quote.job.code != null) ZCaption("·")
                    quote.job?.code?.let { QuoteCode(it) }
                }
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = colors.inkMuted) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Withdraw quote", color = colors.danger) }, onClick = { menu = false; onWithdraw() })
                }
            }
        }
        quote.job?.photos?.takeIf { it.isNotEmpty() }?.let { PhotoGrid(it.take(4), thumbSize = 56.dp) }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
            Text(Money.format(quote.amountCents), style = money, color = colors.ink)
            ZCaption("You keep ≈ ${Money.format(CompanyQuotesLogic.youKeepCents(quote.amountCents, commissionBps))}", modifier = Modifier.padding(bottom = 2.dp))
        }
        val rate = quote.hourlyRateCents; val hours = quote.estimatedHours; val crew = quote.crewSize
        if (rate != null && hours != null && crew != null) ZCaption("$crew × ${hours.hoursLabel()}h × ${Money.format(rate)}/hr", tone = ZTextTone.FAINT)
        quote.proposedDate?.let { ZCaption("Can arrive ${JobPresentation.windowSlot(it, quote.proposedWindow)}", tone = ZTextTone.SOFT) }
        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            ZBadge(CompanyQuotesLogic.waitingState(quote), if (quote.job?.quotesReleased == true) ZTone.INFO else ZTone.NEUTRAL)
            quote.createdAt?.let { ZCaption(CompanyQuotesLogic.sentAgo(it, now), tone = ZTextTone.FAINT) }
        }
        if (CompanyQuotesLogic.canRevise(quote)) {
            ZButton("Revise", compact = true, fullWidth = false, onClick = onRevise)
        }
    }
}

/// A settled quote — compact; only a won row opens its job.
@Composable
private fun PastQuoteRow(quote: Quote, onClick: (() -> Unit)?) {
    val outcome = CompanyQuotesLogic.outcome(quote) ?: return
    ZCard(padding = ZSpacing.sm, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ZBodyStrong(quote.job?.title ?: "Job", maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    quote.job?.code?.let { QuoteCode(it) }
                    quote.decidedAt?.let { ZCaption("· ${Dates.monthDay(it)}") }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(Money.format(quote.amountCents), style = ZType.bodyStrong.copy(fontFeatureSettings = "tnum"), color = ZTheme.colors.ink)
                ZBadge(outcome.label, if (outcome == CompanyQuotesLogic.Outcome.WON) ZTone.SUCCESS else ZTone.NEUTRAL)
            }
        }
    }
}

/// The composer takes a Job; hydrate one from the quote's job ref.
fun quoteJobAsJob(quote: Quote): Job? {
    val ref = quote.job ?: return null
    val category = ref.category ?: return null
    return Job(
        id = ref.id, code = ref.code, title = ref.title, issue = ref.issue, intakeAnswers = ref.intakeAnswers,
        mode = ref.mode, availabilityWindows = ref.availabilityWindows, status = JobStatus.SUBMITTED,
        estimateMin = ref.estimateMin, estimateMax = ref.estimateMax, category = category, companyWindows = ref.companyWindows,
        photos = ref.photos,
    )
}

class CompanyJobsModel(private val client: ApiClient) {
    var state by mutableStateOf<Loadable<List<Job>>>(Loadable.Loading)
    var toast by mutableStateOf<String?>(null)
    var busyId by mutableStateOf<Int?>(null)
    /// The Completed tab: billed history is the invoices list (the web's `?tab=completed`).
    var invoices by mutableStateOf<Loadable<List<Invoice>>>(Loadable.Loading)

    suspend fun load() { state = state.reloaded { client.send(CompanyEndpoints.jobs()).jobs } }
    suspend fun loadInvoices() { invoices = invoices.reloaded { client.send(CompanyEndpoints.invoices()).invoices } }

    /// Refusals (`not_started`, `not_on_crew`, `crew_incomplete`) carry a
    /// message that says what's missing — it's shown as is.
    suspend fun advance(job: Job) {
        busyId = job.id
        try {
            val updated = client.send(CompanyEndpoints.advance(job.id)).job
            state.value?.let { jobs ->
                if (updated.status == JobStatus.INVOICED) {
                    state = Loadable.Loaded(jobs.filter { it.id != job.id })
                    toast = "${updated.code ?: "Job"} complete — invoice sent to the booker automatically"
                    invoices = Loadable.Loading
                } else state = Loadable.Loaded(jobs.map { if (it.id == job.id) updated else it })
            }
        } catch (error: ApiError) { toast = error.userMessage } catch (_: Exception) { toast = "Could not update ${job.code ?: "that job"}. Please try again." } finally { busyId = null }
    }

    /// The Start job picker's roster — for whichever row opened it.
    var roster by mutableStateOf<Loadable<AssignmentRoster>>(Loadable.Loading)

    suspend fun loadRoster(jobId: Int) { roster = roster.reloaded { client.send(CompanyEndpoints.assignment(jobId)) } }

    /// "Start job" from the row: confirm who's going, then swap the row.
    suspend fun start(job: Job, userIds: List<Int>): Boolean {
        try {
            val result = client.send(CompanyEndpoints.start(job.id, userIds))
            state.value?.let { jobs -> state = Loadable.Loaded(jobs.map { if (it.id == job.id) result.job else it }) }
            toast = CompanyPresentation.startedToast(result.job.code, userIds.size)
            return true
        } catch (error: ApiError) { toast = error.first("user_ids") ?: error.first("user_ids.0") ?: error.userMessage } catch (_: Exception) { toast = "Could not start the job. Please try again." }
        return false
    }
}

@Composable
fun CompanyActiveJobsScreen() {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val model = remember { CompanyJobsModel(environment.client) }
    var completed by remember { mutableStateOf(false) }
    // The row whose Start job picker is open.
    var starting by remember { mutableStateOf<Job?>(null) }
    val myId = environment.session.user?.id
    LaunchedEffect(model) { model.load() }
    LaunchedEffect(completed) { if (completed && model.invoices.value == null) model.loadInvoices() }

    ZToastBox(model.toast, { model.toast = null }) {
        ZScreen(onRefresh = { if (completed) model.loadInvoices() else model.load() }) {
            ZPageTitle("Jobs")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !completed, onClick = { completed = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Active") }
                SegmentedButton(selected = completed, onClick = { completed = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Completed") }
            }
            if (completed) {
                ZLoadable(model.invoices, retry = { scope.launch { model.loadInvoices() } }) { invoices ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        if (invoices.isEmpty()) ZEmptyState(Icons.Outlined.Verified, "No completed jobs yet", "Finished jobs land here with their invoices — completing a job bills the booker automatically.")
                        for (invoice in invoices) CompanyInvoiceRow(invoice) { nav.navigate(CompanyInvoiceRoute(invoice.id)) }
                    }
                }
            } else {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { jobs ->
                    // Mine first (a tech opens this screen to see where THEY are going), then the rest.
                    val ordered = jobs.sortedByDescending { it.isAssigned(myId) }
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        if (jobs.isEmpty()) ZEmptyState(Icons.Outlined.Work, "No active jobs", "Quote an opportunity and it will land here once accepted, ready to progress through to completion.")
                        for (job in ordered) {
                            ZCard(onClick = { nav.navigate(CompanyJobRoute(job.id)) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) { ZBodyStrong(job.title, maxLines = 1, modifier = Modifier.weight(1f)); JobStatusBadge(job.status) }
                                ZCaption(listOfNotNull(job.code, job.address).joinToString(" · "), maxLines = 1)
                                // Who's on it — "you" is the row a tech scans for.
                                when {
                                    job.isAssigned(myId) -> ZCaption("Assigned to you", color = colors.success)
                                    job.assignedTech != null -> ZCaption("Assigned to ${job.assignedTech.name}")
                                    // Acceptance normally assigns a field tech; this is the exception.
                                    else -> ZBadge("No tech", ZTone.WARNING)
                                }
                                // Short of the quote's crew — it can't go en route yet.
                                if (job.assignedTech != null && job.crewMissing > 0) ZBadge("Needs ${job.crewMissing} more", ZTone.WARNING)
                                // Withdraw deliberately does NOT ride these rows — a destructive act never
                                // sits beside the button techs tap all day. It lives on the job detail screen.
                                // The row's primary action (`companyJobAction`): an admin STARTS a won
                                // job — the crew picker opens right here — and only a tech on the crew
                                // then marks it en route.
                                when (val action = CompanyPresentation.jobAction(job, environment.session.user)) {
                                    CompanyPresentation.JobAction.Start -> ZButton("Start job", compact = true, fullWidth = false) { model.roster = Loadable.Loading; starting = job }
                                    is CompanyPresentation.JobAction.Advance -> ZButton(action.label, compact = true, loading = model.busyId == job.id, fullWidth = false) { scope.launch { model.advance(job) } }
                                    is CompanyPresentation.JobAction.Waiting -> ZCaption(action.label)
                                    null -> if (job.status == JobStatus.INVOICED) ZCaption("Awaiting payment")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.padding(ZSpacing.lg))
        }
    }
    starting?.let { job ->
        CrewPickerSheet(job, starting = true, roster = model.roster, loadRoster = { model.loadRoster(job.id) }, save = { ids -> model.start(job, ids) }) { starting = null }
    }
}

/// One invoice row — shared by the Invoices screen and the jobs screen's Completed tab.
@Composable
fun CompanyInvoiceRow(invoice: Invoice, onClick: () -> Unit) {
    ZCard(padding = ZSpacing.sm, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ZBodyStrong(invoice.job?.title ?: invoice.number ?: "Invoice", maxLines = 1)
                ZCaption(listOfNotNull(invoice.number, invoice.job?.customer).joinToString(" · "))
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ZMonoBody(Money.format(invoice.totalCents), weight = FontWeight.Bold)
                val meta = JobPresentation.invoiceStatusMeta(invoice.status)
                ZBadge(meta.label, meta.tone)
            }
        }
    }
}
