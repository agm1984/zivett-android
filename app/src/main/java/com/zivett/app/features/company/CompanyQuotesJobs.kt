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
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.CompanyQuotesResponse
import com.zivett.app.core.models.Invoice
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.Quote
import com.zivett.app.core.models.QuoteBody
import com.zivett.app.core.models.QuoteStatus
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
import com.zivett.app.design.ZMonoLarge
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
            toast = error.userMessage
            return null
        } catch (error: Exception) { return error.userMessage }
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
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val model = remember { CompanyQuotesModel(environment.client) }
    var revising by remember { mutableStateOf<Quote?>(null) }
    var withdrawing by remember { mutableStateOf<Quote?>(null) }
    LaunchedEffect(model) { model.load() }

    fun badge(status: QuoteStatus) = when (status) {
        QuoteStatus.PENDING -> "Pending" to ZTone.INFO
        QuoteStatus.ACCEPTED -> "Accepted" to ZTone.SUCCESS
        QuoteStatus.DECLINED -> "Declined" to ZTone.NEUTRAL
    }

    ZToastBox(model.toast, { model.toast = null }) {
        ZScreen(onRefresh = { model.load() }) {
            ZLoadable(model.state, retry = { scope.launch { model.load() } }) { response ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    ZPageTitle("My quotes")
                    if (response.quotes.isEmpty()) ZEmptyState(Icons.Outlined.Description, "No quotes yet", "Every job in Opportunities takes a quote (your hourly rate × estimated hours × crew), and they land here.")
                    for (quote in response.quotes) {
                        ZCard {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ZBodyStrong(quote.job?.title ?: "Job", maxLines = 1)
                                    Text(quote.job?.code ?: "", style = ZType.mono.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified, fontWeight = FontWeight.Normal), color = colors.inkMuted)
                                }
                                val (label, tone) = badge(quote.status)
                                ZBadge(label, tone)
                            }
                            Row(verticalAlignment = Alignment.Bottom) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ZMonoLarge(Money.format(quote.amountCents), color = colors.navy)
                                    val rate = quote.hourlyRateCents; val hours = quote.estimatedHours; val crew = quote.crewSize
                                    if (rate != null && hours != null && crew != null) ZCaption("$crew × ${hours.hoursLabel()}h × ${Money.format(rate)}/hr")
                                }
                                quote.createdAt?.let { ZCaption(Dates.monthDay(it), tone = ZTextTone.FAINT) }
                            }
                            if (quote.status == QuoteStatus.PENDING) {
                                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                                    ZButton("Revise", style = ZButtonStyle.SOFT, compact = true, fullWidth = false) { revising = quote }
                                    ZButton("Withdraw", style = ZButtonStyle.GHOST, compact = true, fullWidth = false) { withdrawing = quote }
                                }
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
            QuoteComposerSheet(job, model.state.value?.hourlyRates?.get(job.category.id.toString()), model.state.value?.commissionBps ?: 1500, quote, onDismiss = { revising = null }) { body -> model.revise(quote, body) }
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

/// The composer takes a Job; hydrate one from the quote's job ref.
fun quoteJobAsJob(quote: Quote): Job? {
    val ref = quote.job ?: return null
    val category = ref.category ?: return null
    return Job(
        id = ref.id, code = ref.code, title = ref.title, issue = ref.issue, intakeAnswers = ref.intakeAnswers,
        mode = ref.mode, availabilityWindows = ref.availabilityWindows, status = JobStatus.SUBMITTED,
        estimateMin = ref.estimateMin, estimateMax = ref.estimateMax, category = category, companyWindows = ref.companyWindows,
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
}

@Composable
fun CompanyActiveJobsScreen() {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val model = remember { CompanyJobsModel(environment.client) }
    var completed by remember { mutableStateOf(false) }
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
                                    else -> ZCaption("Unassigned", color = colors.warning)
                                }
                                // Withdraw deliberately does NOT ride these rows — a destructive act never
                                // sits beside the button techs tap all day. It lives on the job detail screen.
                                val next = CompanyPresentation.nextStage(job.status)
                                if (next != null) ZButton("Mark ${next.label.lowercase()}", compact = true, loading = model.busyId == job.id, fullWidth = false) { scope.launch { model.advance(job) } }
                                else if (job.status == JobStatus.INVOICED) ZCaption("Awaiting payment")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.padding(ZSpacing.lg))
        }
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
