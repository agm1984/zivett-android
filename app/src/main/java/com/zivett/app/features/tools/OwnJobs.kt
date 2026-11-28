package com.zivett.app.features.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.media.PhotoImport
import com.zivett.app.core.models.CostBody
import com.zivett.app.core.models.JobChangeBody
import com.zivett.app.core.models.OwnJobStatus
import com.zivett.app.core.models.ProJob
import com.zivett.app.core.models.ProJobBody
import com.zivett.app.core.models.ProJobContext
import com.zivett.app.core.models.ProJobEndpoints
import com.zivett.app.core.models.ProJobFormat
import com.zivett.app.core.models.ProJobsResponse
import com.zivett.app.core.models.ProQuoteEndpoints
import com.zivett.app.core.models.RosterMember
import com.zivett.app.core.models.TimeBody
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
import com.zivett.app.design.ZCheckbox
import com.zivett.app.design.ZDateField
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZDropdown
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZErrorState
import com.zivett.app.design.ZFilterPills
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZHeroPanel
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZPhotoAvatar
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSegmented
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextArea
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTimeField
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.design.ZType
import com.zivett.app.features.company.MyDayLogic
import com.zivett.app.features.company.OwnJobLogic
import com.zivett.app.features.company.QuoteLineLogic
import com.zivett.app.features.shared.LocalNavBadges
import com.zivett.app.features.shared.PhotoGrid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

object OwnJobsLogic {
    /// The job's one forward step (web JobDetail `action`).
    fun action(job: ProJob): String? = when (job.status) {
        OwnJobStatus.SCHEDULED -> "start"
        OwnJobStatus.IN_PROGRESS -> "complete"
        OwnJobStatus.COMPLETED -> when {
            job.invoices.isEmpty() -> "invoice"
            job.invoices.any { it.status == "draft" } -> "issue"
            job.invoices.any { it.status in OWED } -> "email_invoice"
            else -> null
        }
        else -> null
    }

    val OWED = setOf("unpaid", "partially_paid", "overdue")

    fun nextStep(job: ProJob): String = when (job.status) {
        OwnJobStatus.SCHEDULED -> "Start it when you get there, or mark it completed if it's done."
        OwnJobStatus.IN_PROGRESS -> "Mark it completed when the work is done."
        OwnJobStatus.COMPLETED -> if (job.invoices.isNotEmpty()) "Done and invoiced." else "Done. Invoice the customer for it."
        else -> "Cancelled. It no longer fills a slot on your calendar."
    }

    /// "Quoted $525.00 · costs $80.00 · $445.00 left · $89.00 an hour".
    fun moneyLines(money: ProJob.Money): List<Pair<String, String>> = listOfNotNull(
        money.priceCents?.let { (when (money.priceBasis) { "invoiced" -> "Invoiced"; "quoted" -> "Quoted"; else -> "Price" }) to Money.format(it) },
        ("Costs" to Money.format(money.costsCents)),
        money.leftCents?.let { "Left after costs" to Money.format(it) },
        money.perHourCents?.let { "Per hour worked" to Money.format(it) },
    )

    /// The title a job scheduled from a quote starts with: its first line
    /// that isn't labour or a discount.
    fun titleFromQuote(lines: List<com.zivett.app.core.models.ProLine>): String =
        (lines.firstOrNull { it.type !in setOf("labour", "discount") } ?: lines.firstOrNull())?.description.orEmpty()
}

// ── List (the Jobs tab) ─────────────────────────────────────────────

class OwnJobsModel(private val client: ApiClient) {
    var status by mutableStateOf("active")
    var query by mutableStateOf("")
    var state by mutableStateOf<Loadable<ProJobsResponse>>(Loadable.Loading)
    var loadingMore by mutableStateOf(false)
    private var ticket = 0
    private var page = 1

    suspend fun load() {
        val mine = ++ticket
        page = 1
        try {
            val response = client.send(ProJobEndpoints.list(status, query))
            if (mine == ticket) state = Loadable.Loaded(response)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (mine == ticket && state !is Loadable.Loaded) state = Loadable.Failed(e.userMessage)
        }
    }

    suspend fun more() {
        val current = state.value ?: return
        if (!current.hasMore || loadingMore) return
        loadingMore = true
        try { val next = client.send(ProJobEndpoints.list(status, query, page + 1)); page += 1; state = Loadable.Loaded(next.copy(jobs = current.jobs + next.jobs)) }
        catch (e: CancellationException) { throw e } catch (_: Exception) {} finally { loadingMore = false }
    }
}

/// The company's own jobs (web `Jobs.vue`).
@Composable
fun OwnJobsScreen(nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember { OwnJobsModel(environment.client) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(model) {
        ToolsPendingFilter.take({ ToolsPendingFilter.jobs }, { ToolsPendingFilter.jobs = null })?.let { model.status = it }
        model.load()
    }
    val live = LocalNavBadges.current?.liveEvents ?: 0
    LaunchedEffect(live) { if (live > 0) model.load() }
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(model) { if (resumed) scope.launch { model.load() }; resumed = true; onPauseOrDispose { } }

    ZScreen(onRefresh = { model.load() }) {
        ZPageTitle("Jobs", "Work for your own customers, on your calendar beside your ZiVETT jobs.")
        ZButton("New job") { nav.newJob() }
        val counts = model.state.value?.counts
        ZFilterPills(ProJobFormat.filters.map { (key, label) -> Triple(key, label, counts?.get(key)) }, model.status) { model.status = it; scope.launch { model.load() } }
        ZTextField("Search", model.query, { model.query = it; searchJob?.cancel(); searchJob = scope.launch { delay(300); model.load() } }, placeholder = "Job, code or customer")
        when (val state = model.state) {
            Loadable.Loading -> ZSpinner()
            is Loadable.Failed -> ZErrorState("Could not load your jobs.") { scope.launch { model.load() } }
            is Loadable.Loaded -> {
                if (state.loaded.jobs.isEmpty()) ZEmptyState(Icons.Outlined.Work, "No jobs here", "Jobs you book for your own customers show here.")
                for (job in state.loaded.jobs) ZCard(onClick = { nav.job(job.id) }) {
                    JobLine(job, showCustomer = true)
                    if (job.crew.isNotEmpty()) ZCaption(job.crew.joinToString(", ") { it.name }, maxLines = 1)
                    else if (job.status == OwnJobStatus.SCHEDULED) ZBadge("No one on it yet", ZTone.WARNING)
                }
                if (state.loaded.hasMore) ZButton("Show more", style = ZButtonStyle.OUTLINE, loading = model.loadingMore) { scope.launch { model.more() } }
            }
        }
        Spacer(Modifier.padding(ZSpacing.md))
    }
}

// ── Detail (the office's view) ──────────────────────────────────────

class ProJobModel(val jobId: Int, private val client: ApiClient) {
    var state by mutableStateOf<Loadable<ProJob>>(Loadable.Loading)
    var busy by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)
    var roster by mutableStateOf<List<RosterMember>?>(null)

    val job: ProJob? get() = state.value

    suspend fun load() { state = state.reloaded { client.send(ProJobEndpoints.show(jobId)).job } }
    suspend fun refresh() { if (busy == null) load() }

    suspend fun act(key: String, success: String?, operation: suspend () -> ProJob): Boolean {
        if (busy != null) return false
        busy = key
        return try { state = Loadable.Loaded(operation()); success?.let { toast = it }; true }
        catch (e: CancellationException) { throw e } catch (e: Exception) { toast = e.userMessage; false } finally { busy = null }
    }

    suspend fun setStatus(status: String, reason: String? = null) = act(status, when (status) {
        OwnJobStatus.IN_PROGRESS -> "Job started."
        OwnJobStatus.COMPLETED -> "Marked completed."
        else -> "Job cancelled."
    }) { client.send(ProJobEndpoints.status(jobId, status, reason)).job }

    suspend fun saveNotes(notes: String) = act("notes", "Saved.") { client.send(ProJobEndpoints.record(jobId, notes.trim().ifBlank { null })).job }
    suspend fun addPhoto(kind: String, jpeg: ByteArray) = act("photo", "Photo added.") { client.send(ProJobEndpoints.addPhoto(jobId, kind, jpeg)).job }
    suspend fun deletePhoto(id: Int) = act("photo", "Photo removed.") { client.send(ProJobEndpoints.deletePhoto(jobId, id)).job }
    suspend fun addTime(body: TimeBody) = act("time", "Time added.") { client.send(ProJobEndpoints.addTime(jobId, body)).job }
    suspend fun deleteTime(id: Int) = act("time", "Time removed.") { client.send(ProJobEndpoints.deleteTime(jobId, id)).job }
    suspend fun addCost(body: CostBody) = act("cost", "Cost added.") { client.send(ProJobEndpoints.addCost(jobId, body)).job }
    suspend fun deleteCost(id: Int) = act("cost", "Cost removed.") { client.send(ProJobEndpoints.deleteCost(jobId, id)).job }
    suspend fun addChange(body: JobChangeBody) = act("change", "Change added.") { client.send(ProJobEndpoints.addChange(jobId, body)).job }
    suspend fun deleteChange(id: Int) = act("change", "Change removed.") { client.send(ProJobEndpoints.deleteChange(jobId, id)).job }
    suspend fun stopSeries() = act("series", "This job no longer repeats. Visits already done or under way are kept.") { client.send(ProJobEndpoints.stopSeries(jobId)).job }
    suspend fun seriesCrew() = act("series", "The crew is on every visit still to come.") { client.send(ProJobEndpoints.seriesCrew(jobId)).job }

    suspend fun loadRoster() { roster = runCatching { client.send(ProJobEndpoints.crew(jobId)).members }.getOrElse { toast = it.userMessage; emptyList() } }
    suspend fun setCrew(ids: List<Int>) = act("crew", if (ids.isEmpty()) "Everyone is off this job." else "Crew saved.") {
        client.send(ProJobEndpoints.setCrew(jobId, ids)).also { roster = it.members }.job
    }
}

/// One own job as the office sees it (web `business/JobDetail.vue`): the
/// status's step, the customer and where, the crew, the price and changes,
/// the record (what was done, photos, time, costs), the money, a repeating
/// series and the invoices. Cancelling sits in the menu with its confirm.
@Composable
fun ProJobScreen(jobId: Int, nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val model = remember(jobId) { ProJobModel(jobId, environment.client) }
    val isAdmin = environment.session.user?.organizationRole == com.zivett.app.core.models.OrganizationRole.ADMIN
    LaunchedEffect(model) { model.load() }
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(model) { if (resumed) scope.launch { model.refresh() }; resumed = true; onPauseOrDispose { } }
    val live = LocalNavBadges.current?.liveEvents ?: 0
    LaunchedEffect(live) { if (live > 0) model.refresh() }
    var menu by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    var crew by remember { mutableStateOf(false) }
    var addingTime by remember { mutableStateOf(false) }
    var addingCost by remember { mutableStateOf(false) }
    var addingChange by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    var photoKind by remember { mutableStateOf("before") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { PhotoImport.jpegData(context, listOf(uri)).firstOrNull()?.let { model.addPhoto(photoKind, it) } }
    }
    val job = model.job

    Column {
        ZTopBar(job?.code ?: "Job", onBack = { nav.back() }, actions = {
            if (job != null && job.isOpen) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; nav.editJob(job.id) })
                    DropdownMenuItem(text = { Text("Cancel job…", color = com.zivett.app.design.ZTheme.colors.danger) }, onClick = { menu = false; cancelling = true })
                }
            }
        })
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { j ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        ZHeroPanel {
                            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                                val (label, tone) = ToolsFormat.jobStatus(j.status)
                                ZBadge(label, tone)
                                if (j.repeats) ZBadge("Repeats")
                            }
                            Text(j.title.ifBlank { "Job" }, style = ZType.title, color = Color.White)
                            Text(listOfNotNull(j.code, j.customer?.name, ToolsFormat.jobWhen(j.scheduledDate, j.startTime, j.scheduledWindow)).joinToString(" · "), style = ZType.caption, color = Color.White.copy(alpha = 0.75f))
                        }

                        ZCard {
                            ZHeadline("Job status")
                            ZCaption(OwnJobsLogic.nextStep(j), tone = ZTextTone.SOFT)
                            j.completedAt?.takeIf { j.status == OwnJobStatus.COMPLETED }?.let { ZCaption("Completed ${ToolsFormat.day(it)}.") }
                            j.cancelReason?.takeIf { j.status == OwnJobStatus.CANCELLED }?.let { ZCaption("Reason: $it") }
                            when (OwnJobsLogic.action(j)) {
                                "start" -> {
                                    ZButton("Start job", loading = model.busy == OwnJobStatus.IN_PROGRESS) { scope.launch { model.setStatus(OwnJobStatus.IN_PROGRESS) } }
                                    ZButton("Mark it completed", style = ZButtonStyle.OUTLINE, enabled = model.busy == null) { scope.launch { model.setStatus(OwnJobStatus.COMPLETED) } }
                                }
                                "complete" -> ZButton("Mark completed", loading = model.busy == OwnJobStatus.COMPLETED) { scope.launch { model.setStatus(OwnJobStatus.COMPLETED) } }
                                "invoice" -> ZButton("Create invoice") { nav.newInvoice(jobId = j.id) }
                                "issue" -> ZButton("Finish and issue invoice") { j.invoices.firstOrNull { it.status == "draft" }?.let { nav.invoice(it.id) } }
                                "email_invoice" -> ZButton("Email invoice to ${j.customerFirstName}") { j.invoices.firstOrNull { it.status in OwnJobsLogic.OWED }?.let { nav.invoice(it.id) } }
                            }
                            j.reminderSentAt?.let { ZCaption("Reminder emailed to ${j.customerFirstName} ${ToolsFormat.day(it)}.") }
                        }

                        ZCard {
                            ZHeadline("Customer")
                            j.customer?.id?.let { id -> ZTextAction(j.customer.name ?: "Customer") { nav.customer(id) } } ?: ZBodyStrong(j.customer?.name ?: "Customer")
                            j.customer?.phone?.let { ToolsContact(it, phone = true) }
                            j.customer?.email?.let { ToolsContact(it, phone = false) }
                            val where = listOfNotNull(j.addressUnit?.takeIf { it.isNotBlank() }?.let { CustomerDetailLogic.unit(it) }, j.address).joinToString(", ")
                            if (where.isNotBlank()) ZBody(where) else ZCaption("No address on this job.")
                            MyDayLogic.directionsUrl(j.lat, j.lng, j.address)?.let { url -> ZTextAction("Directions") { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) } } }
                            j.description?.takeIf { it.isNotBlank() }?.let { ZLabel("Notes"); ZBody(it, tone = ZTextTone.SOFT) }
                        }

                        CrewCard(j, isAdmin, onEdit = { crew = true; scope.launch { model.loadRoster() } }, model)

                        PriceCard(j, nav, onAddChange = { addingChange = true }, model)

                        j.series?.let { series ->
                            ZCard {
                                ZHeadline("Repeats")
                                ZBody(listOfNotNull(series.label, series.endsOn?.let { "until ${ToolsFormat.day(it)}" }).joinToString(" ").ifBlank { "Repeats" } + if (!series.active) ". Stopped." else ".")
                                if (series.upcoming.isNotEmpty()) {
                                    ZLabel("Coming up")
                                    for (visit in series.upcoming) ClickRow({ nav.job(visit.id) }) { ZBody(ToolsFormat.jobWhen(visit.scheduledDate, null, visit.scheduledWindow)) }
                                    if (series.upcomingCount > series.upcoming.size) ZCaption("And ${series.upcomingCount - series.upcoming.size} more.")
                                }
                                if (series.active && isAdmin) {
                                    ZButton("Put this crew on every visit to come", style = ZButtonStyle.OUTLINE, loading = model.busy == "series") { scope.launch { model.seriesCrew() } }
                                    ZButton("Stop repeating", style = ZButtonStyle.GHOST) { stopping = true }
                                }
                            }
                        }

                        RecordCard(j, model, onAddTime = { addingTime = true }, onPhoto = { kind -> photoKind = kind; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })

                        j.money?.let { money ->
                            ZCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ZHeadline("Money", Modifier.weight(1f))
                                    ZTextAction("Add a cost") { addingCost = true }
                                }
                                for ((label, value) in OwnJobsLogic.moneyLines(money)) Row { ZCaption(label, Modifier.weight(1f)); ZBody(value) }
                                for (cost in money.costs) Row(verticalAlignment = Alignment.CenterVertically) {
                                    ZCaption(cost.description, Modifier.weight(1f))
                                    ZCaption(Money.format(cost.amountCents))
                                    ZTextAction("Remove", color = com.zivett.app.design.ZTheme.colors.inkSoft, enabled = model.busy == null) { scope.launch { model.deleteCost(cost.id) } }
                                }
                                ZCaption("Only your team sees this.")
                            }
                        }

                        if (j.invoices.isNotEmpty()) {
                            ZCard {
                                ZHeadline(if (j.invoices.size == 1) "Invoice" else "Invoices")
                                for (invoice in j.invoices) ClickRow({ nav.invoice(invoice.id) }) {
                                    val (label, tone) = ToolsFormat.invoiceStatus(invoice.status ?: "draft")
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                                        com.zivett.app.design.ZMonoBody(invoice.number ?: "Draft"); ZBadge(label, tone)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.padding(ZSpacing.md))
                    }
                }
            }
        }
    }

    if (cancelling) CancelJobSheet(model.busy == OwnJobStatus.CANCELLED, onDismiss = { cancelling = false }) { reason ->
        scope.launch { if (model.setStatus(OwnJobStatus.CANCELLED, reason.ifBlank { null })) cancelling = false }
    }
    if (crew && job != null) OwnCrewSheet(job, model) { crew = false }
    if (addingTime && job != null) TimeSheet(job, model) { addingTime = false }
    if (addingCost) CostSheet(model) { addingCost = false }
    if (addingChange) ChangeSheet(model) { addingChange = false }
    if (stopping) ToolsConfirm("Stop this job repeating?", "Visits still to come are cancelled. Visits already done or under way are kept.", "Stop repeating", danger = true, dismiss = "Keep repeating", onDismiss = { stopping = false }) {
        scope.launch { model.stopSeries() }
    }
}

@Composable
private fun CrewCard(job: ProJob, isAdmin: Boolean, onEdit: () -> Unit, model: ProJobModel) {
    ZCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZHeadline(if (job.crew.size > 1) "Crew" else "On this job", Modifier.weight(1f))
            if (isAdmin && job.isOpen) ZTextAction(if (job.crew.isEmpty()) "Assign" else "Change", enabled = model.busy == null, onClick = onEdit)
        }
        if (job.crew.isEmpty()) ZCaption("No one on it yet.")
        job.crew.forEachIndexed { index, member ->
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                ZPhotoAvatar(member.photoUrl, com.zivett.app.core.models.initialsOf(member.name), 32.dp)
                ZBodyStrong(member.name, Modifier.weight(1f))
                if (member.inactive) ZBadge("No longer on the team", ZTone.WARNING)
                if (job.crew.size > 1 && index == 0) ZBadge("Lead")
            }
        }
    }
}

@Composable
private fun PriceCard(job: ProJob, nav: ToolsNav, onAddChange: () -> Unit, model: ProJobModel) {
    val scope = rememberCoroutineScope()
    ZCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZHeadline("Price", Modifier.weight(1f))
            if (job.changesLocked == null) ZTextAction("Add a change", onClick = onAddChange)
        }
        job.quote?.let { quote -> ClickRow({ nav.quote(quote.id) }) { ZBody("From quote ${quote.number ?: ""} · ${Money.format(quote.subtotalCents)} before tax") } }
        job.lineItems?.takeIf { it.isNotEmpty() && job.quote == null }?.forEach { line ->
            Row { ZCaption(line.description, Modifier.weight(1f)); ZCaption(Money.format(line.amountCents)) }
        }
        for (change in job.changes) {
            ZDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    ZBodyStrong(change.label)
                    ZCaption(listOfNotNull(change.createdBy, ToolsFormat.day(change.createdAt), change.note).joinToString(" · "))
                }
                ZBody((if (change.amountCents < 0) "−" else "+") + Money.format(kotlin.math.abs(change.amountCents)))
            }
            if (job.changesLocked == null) ZTextAction("Remove", color = com.zivett.app.design.ZTheme.colors.inkSoft, enabled = model.busy == null) { scope.launch { model.deleteChange(change.id) } }
        }
        ZDivider()
        Row { ZBodyStrong("Total before tax", Modifier.weight(1f)); ZBodyStrong(job.totalPriceCents?.let { Money.format(it) } ?: "Not set") }
        job.changesLocked?.let { ZCaption(it) }
    }
}

@Composable
private fun RecordCard(job: ProJob, model: ProJobModel, onAddTime: () -> Unit, onPhoto: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var notes by remember(job.workNotes) { mutableStateOf(job.workNotes.orEmpty()) }
    ZCard {
        ZHeadline("What was done")
        ZTextArea("", notes, { notes = it.take(5000) }, placeholder = "What you did, parts used, anything for next time", minLines = 3)
        if (notes != job.workNotes.orEmpty()) ZButton("Save", style = ZButtonStyle.OUTLINE, loading = model.busy == "notes") { scope.launch { model.saveNotes(notes) } }
    }
    ZCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZHeadline("Photos", Modifier.weight(1f))
            ZTextAction("Before") { onPhoto("before") }
            Spacer(Modifier.padding(horizontal = 6.dp))
            ZTextAction("After") { onPhoto("after") }
        }
        if (job.photos.isEmpty()) ZCaption("No photos yet. Add before and after photos for your records.")
        else PhotoGrid(job.photos, onDelete = { photo -> scope.launch { model.deletePhoto(photo.id) } })
        if (model.busy == "photo") ZCaption("Uploading…")
    }
    ZCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZHeadline("Time", Modifier.weight(1f))
            if (job.time.isNotEmpty()) ZCaption(OwnJobLogic.minutes(job.time.sumOf { it.minutes }))
            Spacer(Modifier.padding(horizontal = 6.dp))
            ZTextAction("Add time", onClick = onAddTime)
        }
        if (job.time.isEmpty()) ZCaption("No time logged yet. Starting the job or the job timer logs time here.")
        for (entry in job.time) Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ZBodyStrong(entry.user?.name ?: "Teammate", maxLines = 1)
                ZCaption(listOfNotNull(entry.workedOn?.let { ToolsFormat.day(it) }, entry.note?.takeIf { it.isNotBlank() }).joinToString(" · "), maxLines = 2)
            }
            if (entry.running) ZBadge("Timer running", ZTone.SUCCESS)
            ZCaption(OwnJobLogic.minutes(entry.minutes))
            if (entry.removable && !entry.running) ZTextAction("Remove", color = com.zivett.app.design.ZTheme.colors.inkSoft, enabled = model.busy == null) { scope.launch { model.deleteTime(entry.id) } }
        }
    }
}

@Composable
private fun CancelJobSheet(saving: Boolean, onDismiss: () -> Unit, onCancel: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    ZSheet(onDismiss, title = "Cancel this job?", dismissable = !saving) {
        ZBody("It comes off your calendar and the crew's day. The customer isn't told.")
        ZTextArea("Reason (optional, for your records)", reason, { reason = it.take(500) }, minLines = 2)
        ZButton("Cancel job", style = ZButtonStyle.DANGER, loading = saving) { onCancel(reason.trim()) }
        ZButton("Keep the job", style = ZButtonStyle.GHOST, onClick = onDismiss)
    }
}

/// Pick the crew, lead first (web `OwnJobCrewCard`'s picker): tap to add or
/// take off; the first picked leads. Leavers show picked, to take off.
@Composable
private fun OwnCrewSheet(job: ProJob, model: ProJobModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(job.crew.map { it.id }) }
    ZSheet(onDismiss, title = "Who's on this job?", dismissable = model.busy == null) {
        val roster = model.roster
        if (roster == null) ZCaption("Loading the team…")
        else {
            ZCaption("Tap people to put them on the job. The first one you pick leads.")
            for (member in roster) {
                val on = member.id in picked
                ClickRow({ if (on) picked = picked - member.id else if (!member.inactive) picked = picked + member.id }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        ZPhotoAvatar(member.photoUrl, com.zivett.app.core.models.initialsOf(member.name), 32.dp)
                        Column(Modifier.weight(1f)) {
                            ZBodyStrong(member.name)
                            val notes = listOfNotNull(
                                if (member.inactive) "No longer on the team. Tap to take them off" else null,
                                if (!member.takesJobs) "Office" else null,
                                if (member.off) "Off that day" else null,
                                if (member.busy.isNotEmpty()) "Busy then" else null,
                                "${member.openJobs} open jobs",
                            )
                            ZCaption(notes.joinToString(" · "))
                        }
                        if (on) ZBadge(if (picked.firstOrNull() == member.id) "Lead" else "On it", ZTone.SUCCESS)
                    }
                }
            }
            ZButton(if (picked.isEmpty()) "Take everyone off" else "Save crew", loading = model.busy == "crew") { scope.launch { if (model.setCrew(picked)) onDismiss() } }
        }
    }
}

@Composable
private fun TimeSheet(job: ProJob, model: ProJobModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var hours by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var who by remember { mutableStateOf("") }
    ZSheet(onDismiss, title = "Add time", dismissable = model.busy == null) {
        if (job.crew.isNotEmpty()) ZDropdown("Who", who, listOf("" to "Me") + job.crew.map { it.id.toString() to it.name }, placeholder = "Me") { who = it }
        ZDateField("Day", date, { date = it })
        ZTextField("Hours", hours, { hours = it }, placeholder = "1.5", keyboardType = KeyboardType.Decimal)
        ZTextField("Note (optional)", note, { note = it.take(160) })
        val value = hours.trim().replace(',', '.').toDoubleOrNull()
        ZButton("Add time", loading = model.busy == "time", enabled = value != null && value > 0 && date.isNotBlank()) {
            scope.launch { if (model.addTime(TimeBody(date, value ?: 0.0, note.trim().ifBlank { null }, who.toIntOrNull()))) onDismiss() }
        }
    }
}

@Composable
private fun CostSheet(model: ProJobModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    ZSheet(onDismiss, title = "Add a cost", dismissable = model.busy == null) {
        ZTextField("What it was", description, { description = it.take(160) }, placeholder = "Parts from the supply house")
        ZTextField("Amount ($)", amount, { amount = it }, keyboardType = KeyboardType.Decimal)
        ZCaption("What this job cost you. Only your team sees it.")
        val cents = Money.parseCents(amount)
        ZButton("Add cost", loading = model.busy == "cost", enabled = description.isNotBlank() && (cents ?: 0) > 0) {
            scope.launch { if (model.addCost(CostBody(description.trim(), cents ?: 0))) onDismiss() }
        }
    }
}

/// Work added to or taken off a job after booking (ProJobChanges).
@Composable
private fun ChangeSheet(model: ProJobModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf(listOf(DocumentLogic.blankLine())) }
    ZSheet(onDismiss, title = "Add a change", dismissable = model.busy == null) {
        ZTextField("What changed", label, { label = it.take(120) }, placeholder = "Extra outlet in the garage")
        lines.forEachIndexed { index, line ->
            com.zivett.app.features.company.QuoteLineEditor(line, { next -> lines = lines.toMutableList().also { it[index] = next } }, { lines = lines.filterIndexed { i, _ -> i != index } }, changeOrder = true)
        }
        ZButton("Add a line", style = ZButtonStyle.OUTLINE) { lines = lines + DocumentLogic.blankLine() }
        ZTextField("Why (optional)", note, { note = it.take(500) })
        ZCaption("Total ${Money.format(QuoteLineLogic.total(lines))}. It's added to the job's price and billed on its invoice.")
        ZButton("Add change", loading = model.busy == "change", enabled = label.isNotBlank() && !DocumentLogic.incomplete(lines)) {
            scope.launch { if (model.addChange(JobChangeBody(label.trim(), note.trim().ifBlank { null }, QuoteLineLogic.body(lines)))) onDismiss() }
        }
    }
}

// ── Editor ──────────────────────────────────────────────────────────

class ProJobEditorModel(val jobId: Int?, private val client: ApiClient) {
    val customer = DocumentCustomerState(client)
    var context by mutableStateOf<ProJobContext?>(null)
    var existing by mutableStateOf<ProJob?>(null)
    var quote by mutableStateOf<com.zivett.app.core.models.ProDocument?>(null)
    var loadError by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
    var categoryId by mutableStateOf<Int?>(null)
    var description by mutableStateOf("")
    var date by mutableStateOf("")
    /// window | time
    var timing by mutableStateOf("window")
    var window by mutableStateOf("morning")
    var startTime by mutableStateOf("")
    var hours by mutableStateOf("")
    var lines by mutableStateOf<List<com.zivett.app.features.company.QuoteLineDraft>>(emptyList())
    var price by mutableStateOf("")
    var repeat by mutableStateOf("")
    var repeatUntil by mutableStateOf("")
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
    var message by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
        private set

    /// Priced by its quote: the lines aren't edited here.
    val fromQuote: Boolean get() = quote != null || existing?.quote != null

    suspend fun load(customerId: Int?, quoteId: Int?, presetDate: String?) {
        try {
            context = client.send(ProJobEndpoints.context())
            if (jobId != null) {
                val job = client.send(ProJobEndpoints.show(jobId)).job
                existing = job
                job.customer?.id?.let { customer.pick(it, keepAddress = job.proCustomerAddressId) }
                customer.addressId = job.proCustomerAddressId
                title = job.title; categoryId = job.category?.id; description = job.description.orEmpty()
                date = job.scheduledDate.orEmpty()
                timing = if (job.startTime != null) "time" else "window"
                window = job.scheduledWindow ?: "morning"; startTime = job.startTime.orEmpty()
                hours = job.estimatedHours?.let { QuoteLineLogic.quantityText(it) }.orEmpty()
                lines = job.lineItems?.takeIf { it.isNotEmpty() }?.let { DocumentLogic.drafts(it) }
                    ?: job.priceCents?.let { listOf(com.zivett.app.features.company.QuoteLineDraft(kind = com.zivett.app.features.company.QuoteLineKind.FLAT, description = job.title, quantity = "1", unit = QuoteLineLogic.dollarsText(it))) }
                    ?: emptyList()
            } else if (quoteId != null) {
                val q = client.send(ProQuoteEndpoints.show(quoteId)).quote
                quote = q
                q.customer?.id?.let { customer.pick(it, keepAddress = q.proCustomerAddressId) }
                customer.addressId = q.proCustomerAddressId
                title = OwnJobsLogic.titleFromQuote(q.lineItems)
            } else if (customerId != null) customer.pick(customerId)
            if (jobId == null && presetDate != null) date = presetDate
            loadError = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) { loadError = e.userMessage }
    }

    suspend fun save(): com.zivett.app.core.models.ProJob? {
        errors = emptyMap(); message = null
        if (date.isNotBlank() && timing == "time" && startTime.isBlank()) { errors = mapOf("start_time" to "Pick a start time, or choose a time of day instead."); message = "Check the highlighted fields."; return null }
        val creating = jobId == null
        if (creating && quote == null && customer.customer == null) { errors = mapOf("pro_customer_id" to "Choose who this job is for."); return null }
        val repeating = creating && repeat.isNotBlank() && date.isNotBlank()
        val priced = lines.filter { it.description.isNotBlank() }
        val body = ProJobBody(
            quoteId = if (creating) quote?.id else null,
            proCustomerId = if (creating && quote == null) customer.customer?.id else null,
            proCustomerAddressId = customer.addressId,
            serviceCategoryId = categoryId,
            title = title.trim(),
            description = description.trim().ifBlank { null },
            scheduledDate = date.ifBlank { null },
            scheduledWindow = if (date.isNotBlank() && timing == "window") window else null,
            startTime = if (date.isNotBlank() && timing == "time") startTime.ifBlank { null } else null,
            estimatedHours = hours.trim().replace(',', '.').toDoubleOrNull(),
            priceCents = if (!fromQuote && repeating) Money.parseCents(price) else null,
            lineItems = if (!fromQuote && !repeating && priced.isNotEmpty()) QuoteLineLogic.body(lines) else null,
            repeat = if (repeating) ProJobBody.Repeat(repeat, repeatUntil.ifBlank { null }) else null,
        )
        saving = true
        return try {
            if (creating) client.send(ProJobEndpoints.create(body)).job else client.send(ProJobEndpoints.update(jobId!!, body)).job
        } catch (e: CancellationException) { throw e } catch (e: ApiError.Validation) {
            errors = e.errors.firstMessages; message = if (errors.isNotEmpty()) "Check the highlighted fields." else e.errors.message; null
        } catch (e: Exception) { message = e.userMessage; null } finally { saving = false }
    }
}

/// New / edit own job (web `JobEditor.vue`).
@Composable
fun ProJobEditorScreen(jobId: Int?, customerId: Int?, quoteId: Int?, date: String?, nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember(jobId) { ProJobEditorModel(jobId, environment.client) }
    LaunchedEffect(model) { model.load(customerId, quoteId, date) }
    val ctx = model.context

    Column {
        ZTopBar(if (jobId != null) "Edit job" else "New job", onBack = { nav.back() })
        ZScreen {
            ZCaption("For one of your own customers. It goes on your calendar beside your ZiVETT jobs.")
            when {
                model.loadError != null -> ZErrorState(model.loadError.orEmpty()) { scope.launch { model.load(customerId, quoteId, date) } }
                ctx == null -> ZSpinner()
                else -> {
                    DocumentCustomerCard(model.customer, model.errors["pro_customer_id"], model.errors["pro_customer_address_id"], locked = jobId != null || model.quote != null)
                    ZCard {
                        ZHeadline("The job")
                        ZTextField("Title", model.title, { model.title = it.take(160) }, placeholder = "Replace hot water tank", error = model.errors["title"])
                        if (ctx.categories.isNotEmpty()) ZDropdown("Trade (optional)", model.categoryId?.toString() ?: "", listOf("" to "None") + ctx.categories.map { it.id.toString() to it.name }, placeholder = "None") { model.categoryId = it.toIntOrNull() }
                        ZTextArea("Notes (optional)", model.description, { model.description = it.take(5000) }, placeholder = "What to bring, access, anything the customer told you", minLines = 2)
                    }
                    ZCard {
                        ZHeadline("On your calendar")
                        ZDateField("Day", model.date, { model.date = it }, error = model.errors["scheduled_date"], placeholder = "Not booked yet", clearable = true)
                        if (model.date.isNotBlank()) {
                            ZSegmented(listOf("window" to "Time of day", "time" to "Start time"), model.timing) { model.timing = it }
                            if (model.timing == "window") ZSegmented(listOf("morning" to "Morning", "afternoon" to "Afternoon", "evening" to "Evening"), model.window) { model.window = it }
                            else ZTimeField("Start time", model.startTime, { model.startTime = it }, error = model.errors["start_time"])
                        }
                        ZTextField("Hours", model.hours, { model.hours = it }, placeholder = "2", keyboardType = KeyboardType.Decimal, error = model.errors["estimated_hours"])
                        ZCaption("How long will it take? Only for planning your day. The customer doesn't see it.")
                        if (jobId == null && model.date.isNotBlank()) {
                            ZDropdown("Does it repeat?", model.repeat, listOf("" to "No, just this once") + ProJobFormat.frequencies, placeholder = "No, just this once") { model.repeat = it }
                            if (model.repeat.isNotBlank()) ZDateField("Until (optional)", model.repeatUntil, { model.repeatUntil = it }, clearable = true, placeholder = "Keeps going")
                        }
                    }
                    if (!model.fromQuote) {
                        if (jobId == null && model.repeat.isNotBlank() && model.date.isNotBlank()) {
                            ZCard {
                                ZTextField("Price per visit, before tax (optional)", model.price, { model.price = it }, placeholder = "0.00", keyboardType = KeyboardType.Decimal, error = model.errors["price_cents"])
                                ZCaption("Used when you bill completed visits. The customer doesn't see it until it's invoiced.")
                            }
                        } else {
                            ZLabel("Price (optional)")
                            DocumentLinesCard(model.lines, { model.lines = it }, model.errors.entries.firstOrNull { it.key.startsWith("line_items") }?.value)
                        }
                    } else ZCaption("Priced by its quote. Changes after booking go on the job page.")
                    model.message?.let { ZBanner(it, tone = ZTone.DANGER) }
                    ZButton(if (jobId != null) "Save changes" else "Book job", loading = model.saving, enabled = model.title.isNotBlank()) {
                        scope.launch { model.save()?.let { nav.back(); if (jobId == null) nav.job(it.id) } }
                    }
                    ZButton("Cancel", style = ZButtonStyle.GHOST) { nav.back() }
                }
            }
            Spacer(Modifier.padding(ZSpacing.md))
        }
    }
}

@Suppress("unused")
private val keepCheckbox: @Composable () -> Unit = { ZCheckbox(false, {}, "") }
