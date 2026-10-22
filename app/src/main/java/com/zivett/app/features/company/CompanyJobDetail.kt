package com.zivett.app.features.company

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zivett.app.app.Areas
import com.zivett.app.app.CompanyInvoiceRoute
import com.zivett.app.app.ConversationRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.ProfileRoute
import com.zivett.app.app.TeamRoute
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.media.PhotoImport
import com.zivett.app.core.models.AssignmentRoster
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.Dispute
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobArea
import com.zivett.app.core.models.JobPhoto
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.models.noFieldTechs
import com.zivett.app.core.models.nobodyAssignable
import com.zivett.app.core.models.nextUp
import com.zivett.app.core.models.tooFewAssignable
import com.zivett.app.core.models.togglingCrew
import com.zivett.app.core.models.canAutoPick
import com.zivett.app.core.models.autoPick
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
import com.zivett.app.design.ZChoiceTile
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZHeroPanel
import com.zivett.app.design.ZIconTile
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZMonoBody
import com.zivett.app.design.ZMonoLarge
import com.zivett.app.design.ZNavRow
import com.zivett.app.design.ZPhotoAvatar
import com.zivett.app.design.ZRadius
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSelectableRow
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextArea
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTitle
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.design.ZType
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.shared.IntakeChips
import com.zivett.app.features.shared.JobStatusBadge
import com.zivett.app.features.shared.JobTrackingMap
import com.zivett.app.features.shared.LiveJobEffect
import com.zivett.app.features.shared.LocalNav
import com.zivett.app.features.shared.PhotoGrid
import com.zivett.app.features.shared.SupportCaseCards
import com.zivett.app.features.shared.TimelineStepper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CompanyJobDetailModel(val jobId: Int, private val client: ApiClient) {
    var state by mutableStateOf<Loadable<Job>>(Loadable.Loading)
    var toast by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    // The dispatcher's assign/reassign picker (org admins only — the
    // server enforces it). Accepting a quote auto-assigns a field tech,
    // so this is mostly for reassigning or for teams nobody could be
    // auto-picked from.
    var roster by mutableStateOf<Loadable<AssignmentRoster>>(Loadable.Loading)

    val job: Job? get() = state.value

    suspend fun load() { state = state.reloaded { client.send(CompanyEndpoints.job(jobId)).job } }

    /// A live ping's refetch; skipped mid-action (the response wins).
    suspend fun refresh() { if (!busy) load() }

    /// True when the server refused en route for want of the quote's
    /// crew (`crew_incomplete`) — the screen opens the picker; its message
    /// says how many more.
    suspend fun advance(): Boolean {
        busy = true
        try { state = Loadable.Loaded(client.send(CompanyEndpoints.advance(jobId)).job) } catch (e: Exception) {
            toast = e.userMessage
            return (e as? ApiError.Validation)?.errors?.code == CREW_INCOMPLETE
        } finally { busy = false }
        return false
    }

    suspend fun withdraw(): Boolean {
        busy = true
        try { client.send(CompanyEndpoints.withdraw(jobId)); return true } catch (e: Exception) { toast = e.userMessage } finally { busy = false }
        return false
    }

    suspend fun loadRoster() { roster = roster.reloaded { client.send(CompanyEndpoints.assignment(jobId)) } }

    /// Staffs the job: `userIds` is the whole crew, lead first — one id on
    /// a one-tech job.
    suspend fun assign(userIds: List<Int>): Boolean {
        busy = true
        try {
            val result = client.send(CompanyEndpoints.assign(jobId, userIds))
            state = Loadable.Loaded(result.job)
            roster = Loadable.Loaded(AssignmentRoster(result.job.assignedTech?.id, result.members, result.job.crewMembers.map { it.id }, result.job.crewSize))
            return true
        } catch (e: ApiError) { toast = e.first("user_ids") ?: e.first("user_ids.0") ?: e.userMessage } catch (e: Exception) { toast = e.userMessage } finally { busy = false }
        return false
    }

    companion object {
        const val CREW_INCOMPLETE = "crew_incomplete"
    }

    /// Legacy completed-but-uninvoiced jobs (completion auto-issues now).
    suspend fun sendInvoice() {
        busy = true
        try {
            val invoice = client.send(CompanyEndpoints.invoiceJob(jobId)).invoice
            job?.let { state = Loadable.Loaded(it.copy(invoice = invoice)) }
            toast = "Invoice sent to the booker."
        } catch (e: Exception) { toast = e.userMessage } finally { busy = false }
    }

    suspend fun uploadEvidence(kind: String, photos: List<ByteArray>) {
        if (photos.isEmpty()) return
        busy = true
        try {
            val added = client.send(CompanyEndpoints.uploadPhotos(jobId, kind, photos)).photos
            job?.let { state = Loadable.Loaded(it.copy(photos = (it.photos ?: emptyList()) + added)) }
        } catch (e: ApiError) { toast = e.first("photos") ?: e.userMessage } catch (e: Exception) { toast = e.userMessage } finally { busy = false }
    }

    suspend fun deletePhoto(photo: JobPhoto) {
        runCatching { client.send(CompanyEndpoints.deletePhoto(photo.id)) }
        job?.let { state = Loadable.Loaded(it.copy(photos = it.photos?.filter { p -> p.id != photo.id })) }
    }

    suspend fun proposeChangeOrder(label: String, amountCents: Int, materials: Boolean = false): Boolean {
        busy = true
        try {
            val order = client.send(CompanyEndpoints.changeOrder(jobId, label, amountCents, materials)).changeOrder
            job?.let { state = Loadable.Loaded(it.copy(changeOrders = (it.changeOrders ?: emptyList()) + order)) }
            toast = "Change order sent to the booker for approval"
            return true
        } catch (e: ApiError) { toast = e.first("label") ?: e.first("amount_cents") ?: e.userMessage } catch (_: Exception) { toast = "Could not propose that change. Please try again." } finally { busy = false }
        return false
    }

    /// Grafts the new case into the job so its card shows straight away
    /// (the live ping's refetch would get there, but not instantly).
    suspend fun report(kind: String, body: String): Dispute? {
        busy = true
        try {
            val dispute = client.send(CompanyEndpoints.report(jobId, kind, body)).dispute
            job?.let { state = Loadable.Loaded(it.copy(disputes = it.disputes.orEmpty().filter { d -> d.id != dispute.id } + dispute)) }
            return dispute
        } catch (e: Exception) { toast = e.userMessage } finally { busy = false }
        return null
    }

    suspend fun reportLocation(lat: Double, lng: Double): Boolean {
        try { client.send(CompanyEndpoints.reportLocation(jobId, lat, lng)); return true }
        catch (_: ApiError.Unauthenticated) { return false }
        catch (_: ApiError.Forbidden) { return false }
        catch (_: Exception) { return true } // a rejected glitch point shouldn't stop sharing
    }
}

/// Foreground live-location loop: LocationManager while en route, one
/// POST per 8 s, stops on deny/401/403 — mirrors the web's watchPosition.
class LiveLocationSharer(private val context: Context) {
    var sharing by mutableStateOf(false)
    var notice by mutableStateOf<String?>(null)
    private var lastSent = 0L
    private var send: (suspend (Double, Double) -> Boolean)? = null
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listener = LocationListener { location -> onLocation(location) }

    val hasPermission: Boolean get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun start(send: suspend (Double, Double) -> Boolean) {
        this.send = send
        if (!hasPermission) { notice = "Location permission denied — sharing stopped."; return }
        val provider = if (Build.VERSION.SDK_INT >= 31 && manager.allProviders.contains(LocationManager.FUSED_PROVIDER)) LocationManager.FUSED_PROVIDER else LocationManager.GPS_PROVIDER
        try {
            manager.requestLocationUpdates(provider, 4000L, 5f, listener)
            sharing = true
            notice = null
        } catch (_: SecurityException) {
            notice = "Location permission denied — sharing stopped."
        }
    }

    fun stop() {
        manager.removeUpdates(listener)
        sharing = false
    }

    private fun onLocation(location: Location) {
        if (!sharing || System.currentTimeMillis() - lastSent < 8000) return
        lastSent = System.currentTimeMillis()
        scope.launch {
            if (send?.invoke(location.latitude, location.longitude) == false) { stop(); notice = "Location sharing stopped." }
        }
    }
}

sealed interface CompanyJobSheet { object ChangeOrder : CompanyJobSheet; object Report : CompanyJobSheet; object Withdraw : CompanyJobSheet; object Assign : CompanyJobSheet }

@Composable
fun CompanyJobDetailScreen(jobId: Int, openAssign: Boolean = false, onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val model = remember(jobId) { CompanyJobDetailModel(jobId, environment.client) }
    val sharer = remember { LiveLocationSharer(context) }
    var sheet by remember { mutableStateOf<CompanyJobSheet?>(null) }
    val myId = environment.session.user?.id
    val isAdmin = environment.session.user?.organizationRole == OrganizationRole.ADMIN

    // The Jobs list opens the job with `openAssign` when its "Mark en
    // route" needed a tech — land straight on the picker.
    LaunchedEffect(model) {
        model.load()
        if (openAssign && isAdmin && model.job?.let { it.crewMissing > 0 && it.status in CompanyPresentation.assignable } == true) sheet = CompanyJobSheet.Assign
    }
    DisposableEffect(Unit) { onDispose { sharer.stop() } }
    // "Only while using the app": backgrounding the app (Home, lock,
    // another app) stops reporting — the OS only blocks it on Android 10+,
    // and minSdk is 26. Coming back resumes if the job is still en route.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var resumeOnStart = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (sharer.sharing) { resumeOnStart = true; sharer.stop() }
                Lifecycle.Event.ON_START -> if (resumeOnStart) {
                    resumeOnStart = false
                    if (model.job?.status == JobStatus.EN_ROUTE && sharer.hasPermission) sharer.start { lat, lng -> model.reportLocation(lat, lng) }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LiveJobEffect(jobId) { model.refresh() }
    // A live refetch can move the job off EnRoute (arrived from another
    // device, cancelled by the booker) — stop broadcasting with it.
    val status = model.job?.status
    LaunchedEffect(status) { if (status != null && status != JobStatus.EN_ROUTE && sharer.sharing) sharer.stop() }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) sharer.start { lat, lng -> model.reportLocation(lat, lng) } else sharer.notice = "Location permission denied — sharing stopped."
    }
    fun startSharing() {
        if (sharer.hasPermission) sharer.start { lat, lng -> model.reportLocation(lat, lng) } else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /// Advance, then sync location sharing to the new status: hitting
    /// EnRoute auto-starts the loop — but ONLY when the signed-in user IS
    /// the assigned tech (the crew's lead — one breadcrumb per job).
    /// Leaving EnRoute stops it.
    fun advance() {
        // Going en route needs the whole crew. Short of it (or with nobody
        // and no photo to self-assign with), the server would refuse — open
        // the picker.
        val current = model.job
        if (current != null && CompanyPresentation.needsTechBeforeEnRoute(current, environment.session.user)) {
            sheet = CompanyJobSheet.Assign
            return
        }
        scope.launch {
            // Refused for want of a crew the app didn't know was short (a
            // stale page): the toast says how many; admins get the picker.
            if (model.advance() && isAdmin) { sheet = CompanyJobSheet.Assign; return@launch }
            val job = model.job ?: return@launch
            if (job.status == JobStatus.EN_ROUTE && myId != null && job.assignedTech?.id == myId && !sharer.sharing) { startSharing(); model.toast = null }
            else if (job.status != JobStatus.EN_ROUTE && sharer.sharing) sharer.stop()
        }
    }

    val beforePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { uris -> if (uris.isNotEmpty()) scope.launch { model.uploadEvidence("before", PhotoImport.jpegData(context, uris)) } }
    val afterPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { uris -> if (uris.isNotEmpty()) scope.launch { model.uploadEvidence("after", PhotoImport.jpegData(context, uris)) } }

    Column {
        ZTopBar(model.job?.code ?: "Job", onBack = onBack)
        ZToastBox(model.toast ?: sharer.notice, { model.toast = null; sharer.notice = null }) {
            ZScreen(onRefresh = { model.load() }) {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { job ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        ZHeroPanel {
                            JobStatusBadge(job.status)
                            Text(job.title, style = ZType.title, color = Color.White)
                            Text(listOfNotNull(job.code, JobPresentation.modeMeta(job.mode).label, job.address, job.unit, job.scheduledDate?.let { JobPresentation.windowSlot(it, job.scheduledWindow) }).joinToString(" · "), style = ZType.caption, color = Color.White.copy(alpha = 0.75f))
                        }

                        // Open support cases lead — they're why the payout is frozen.
                        SupportCaseCards(job, JobPresentation.CaseSide.COMPANY)

                        // Who's on it — the crew the customer is told to expect, lead first.
                        val crewSize = job.crewSize ?: 1
                        val isCrew = crewSize > 1
                        val canAssign = isAdmin && job.status in CompanyPresentation.assignable
                        ZCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ZMono(if (isCrew) "Assigned crew" else "Assigned tech", modifier = Modifier.weight(1f))
                                if (canAssign && job.crewMembers.isNotEmpty()) ZTextAction(if (isCrew) "Change crew" else "Reassign") { sheet = CompanyJobSheet.Assign }
                            }
                            if (isCrew) ZCaption("Your quote was for ${CompanyPresentation.technicians(crewSize)}. The customer sees each name and photo.")
                            job.crewMembers.forEachIndexed { index, tech ->
                                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                    ZPhotoAvatar(tech.photoUrl, tech.name.take(1), 44.dp)
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                                            ZBodyStrong(tech.name + if (tech.id == myId) " (you)" else "")
                                            if (isCrew && index == 0) ZBadge("Lead")
                                        }
                                        if (!isCrew) ZCaption("The customer sees this name and photo.")
                                    }
                                }
                            }
                            // Acceptance usually staffs the job — when it couldn't, say so loudly.
                            val missing = job.crewMissing
                            if (missing > 0) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.tile)).background(colors.tone(ZTone.WARNING).let { (_, bg) -> bg }).padding(ZSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(ZSpacing.xs),
                                ) {
                                    ZBodyStrong(if (job.crewMembers.isEmpty()) "No tech assigned yet" else "$missing more ${if (missing == 1) "technician" else "technicians"} needed")
                                    ZCaption(
                                        if (isCrew) "The quote was for ${CompanyPresentation.technicians(crewSize)}, so this job needs all of them assigned before it can go en route."
                                        else "The customer sees the name and photo of who's coming, so this job needs a tech before it can go en route." +
                                            if (!isAdmin) " Going en route assigns it to you." else "",
                                        tone = ZTextTone.SOFT,
                                    )
                                    if (canAssign) ZButton(if (isCrew) "Assign the crew" else "Assign a tech", compact = true, fullWidth = false) { sheet = CompanyJobSheet.Assign }
                                }
                            }
                        }

                        // Status + actions
                        ZCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ZHeadline("Job status", modifier = Modifier.weight(1f))
                                CompanyPresentation.nextStage(job.status)?.let { next ->
                                    ZButton("Mark ${next.label.lowercase()}", style = ZButtonStyle.SUCCESS, compact = true, loading = model.busy, fullWidth = false) { advance() }
                                }
                            }
                            TimelineStepper(JobPresentation.timeline(job.status))
                        }

                        if (job.status == JobStatus.EN_ROUTE) {
                            ZCard {
                                ZBodyStrong("Live location")
                                ZCaption("Share your position so the customer can watch you arrive — keep the app open while driving.")
                                ZButton(if (sharer.sharing) "Sharing — tap to stop" else "Share live location", style = if (sharer.sharing) ZButtonStyle.SUCCESS else ZButtonStyle.OUTLINE, compact = true, fullWidth = false) {
                                    if (sharer.sharing) sharer.stop() else startSharing()
                                }
                            }
                        }

                        // The same breadcrumb the booker watches — the dispatcher's view of the crew's progress.
                        if (job.lat != null && job.status in setOf(JobStatus.EN_ROUTE, JobStatus.ARRIVED)) {
                            JobTrackingMap(job, JobArea.companyThread, locationRequest = { CompanyEndpoints.location(it) }, modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(ZRadius.panel)))
                        }

                        // Directions + customer
                        ZCard {
                            ZMono("Customer")
                            ZBodyStrong(job.customerName ?: "Customer")
                            ZCaption(listOfNotNull(job.address, job.tenant).joinToString(" · "))
                            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                                if (job.lat != null && job.lng != null) ZButton("Get directions", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { openUrl(context, "https://www.google.com/maps/dir/?api=1&destination=${job.lat},${job.lng}") }
                                ZButton("Report a problem", style = ZButtonStyle.GHOST, compact = true, fullWidth = false) { sheet = CompanyJobSheet.Report }
                            }
                        }

                        ZCard(padding = 0.dp) {
                            ZNavRow("Messages", "Chat with the booker about this job", onClick = { nav.navigate(ConversationRoute(job.id, Areas.COMPANY, job.customerName ?: "Customer", job.title)) }) { ZIconTile(Icons.AutoMirrored.Outlined.Chat) }
                        }

                        // Booker's brief
                        ZCard {
                            ZMono("What the booker told us")
                            job.intakeAnswers?.takeIf { it.isNotEmpty() }?.let { IntakeChips(it) }
                            job.issue?.let { ZBody(it) }
                            val issuePhotos = (job.photos ?: emptyList()).filter { it.kind == "issue" }
                            if (issuePhotos.isNotEmpty()) { ZMono("Photos from the booker"); PhotoGrid(issuePhotos) }
                            job.accessNotes?.takeIf { it.isNotEmpty() }?.let { ZMono("Access notes"); ZBody(it, tone = ZTextTone.SOFT) }
                        }

                        // Work evidence
                        ZCard {
                            ZHeadline("Work evidence")
                            ZCaption("Before/after photos protect you in disputes and build your passport record.")
                            EvidenceSection("Before", "before", job, model) { beforePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            EvidenceSection("After", "after", job, model) { afterPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        }

                        // Money
                        val invoice = job.invoice
                        val accepted = job.acceptedQuote
                        if (invoice != null) {
                            ZCard {
                                Row(verticalAlignment = Alignment.CenterVertically) { ZMono("Invoice", modifier = Modifier.weight(1f)); val meta = JobPresentation.invoiceStatusMeta(invoice.status); ZBadge(meta.label, meta.tone) }
                                ZMonoLarge(Money.format(invoice.totalCents))
                                invoice.number?.let { ZTextAction(it) { nav.navigate(CompanyInvoiceRoute(invoice.id)) } }
                            }
                        } else if (accepted != null) {
                            ZCard {
                                ZMono("Agreed price")
                                ZMonoLarge(Money.format(accepted.amountCents))
                                ZCaption("Your accepted quote — scope or price changes go through a change order.")
                                // Legacy escape hatch: pre-auto-invoice jobs can sit Completed-but-uninvoiced.
                                if (job.status == JobStatus.COMPLETED) ZButton("Send invoice", compact = true, loading = model.busy, fullWidth = false) { scope.launch { model.sendInvoice() } }
                            }
                        }

                        // Change orders
                        val changeOrdersOpen = job.status in setOf(JobStatus.ACCEPTED, JobStatus.EN_ROUTE, JobStatus.ARRIVED, JobStatus.IN_PROGRESS, JobStatus.COMPLETED) && job.invoice == null
                        val orders = job.changeOrders ?: emptyList()
                        if (orders.isNotEmpty() || changeOrdersOpen) {
                            ZCard {
                                ZHeadline("Change orders")
                                for (order in orders) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                                        ZBody(order.label ?: "Scope change", modifier = Modifier.weight(1f))
                                        ZBadge(order.status, if (order.status == "approved") ZTone.SUCCESS else if (order.status == "declined") ZTone.DANGER else ZTone.WARNING)
                                        ZMonoBody(Money.format(order.amountCents), weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    }
                                }
                                if (changeOrdersOpen) {
                                    ZButton("Propose a change", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { sheet = CompanyJobSheet.ChangeOrder }
                                    ZCaption("The booker approves in-app before it bills. Use a negative amount to descope.")
                                }
                            }
                        }

                        // Danger zone — the one destructive act, kept at the very bottom.
                        if (job.status in CompanyPresentation.withdrawable) {
                            Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs), modifier = Modifier.padding(top = ZSpacing.lg)) {
                                ZDivider()
                                ZCaption("Can't do this job? Withdrawing returns it to the marketplace and affects your reliability record.")
                                ZTextAction("Withdraw from this job", color = colors.danger) { sheet = CompanyJobSheet.Withdraw }
                            }
                        }
                        Spacer(Modifier.padding(ZSpacing.lg))
                    }
                }
            }
        }
    }

    when (sheet) {
        CompanyJobSheet.ChangeOrder -> ChangeOrderSheet(model) { sheet = null }
        CompanyJobSheet.Report -> CompanyReportSheet(model) { sheet = null }
        CompanyJobSheet.Assign -> AssignTechSheet(model) { sheet = null }
        CompanyJobSheet.Withdraw -> model.job?.let { job -> CompanyWithdrawSheet(job, model, onDismiss = { sheet = null }, done = { sheet = null; onBack() }) }
        null -> Unit
    }
}

@Composable
private fun EvidenceSection(title: String, kind: String, job: Job, model: CompanyJobDetailModel, pick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val photos = (job.photos ?: emptyList()).filter { it.kind == kind }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ZMono(title)
        PhotoGrid(photos, onDelete = { photo -> scope.launch { model.deletePhoto(photo) } })
        if (job.status != JobStatus.CANCELLED && photos.size < 8) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = ZTheme.colors.link)
                ZTextAction("Add ${title.lowercase()} photos", enabled = !model.busy, onClick = pick)
            }
        }
    }
}

@Composable
private fun ChangeOrderSheet(model: CompanyJobDetailModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var descope by remember { mutableStateOf(false) }
    var materials by remember { mutableStateOf(false) }
    val cents = Money.parseCents(amount) ?: 0
    val amountCents = if (descope) -cents else cents
    ZSheet(onDismiss = onDismiss, title = "Propose a change") {
        ZTextField("Change description", label, { label = it }, placeholder = "Replace shut-off valve", capitalization = KeyboardCapitalization.Sentences)
        ZTextField("Amount ($)", amount, { amount = it }, placeholder = "0.00", keyboardType = KeyboardType.Decimal)
        ZCheckbox(descope, { descope = it }, text = "This reduces the price (descope)")
        ZCheckbox(materials, { materials = it }, text = "Pass-through materials at cost (no commission taken)")
        ZCaption("The booker approves in-app before it bills.")
        ZButton("Propose", loading = model.busy, enabled = label.isNotEmpty() && amountCents != 0) { scope.launch { if (model.proposeChangeOrder(label, amountCents, materials)) onDismiss() } }
    }
}

@Composable
private fun CompanyReportSheet(model: CompanyJobDetailModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("conduct") }
    var body by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf<Dispute?>(null) }
    val kinds = listOf("conduct" to "Conduct or behaviour", "quality" to "Job or site issue", "safety" to "Safety concern")
    ZSheet(onDismiss = onDismiss) {
        val done = opened
        if (done != null) {
            ZMono("Case opened")
            ZTitle("${done.code ?: "Case"} — support will follow up")
            ZBody("Our team can see the job and your report. We'll follow up by notification.", tone = ZTextTone.SOFT)
            ZButton("Done", onClick = onDismiss)
        } else {
            ZTitle("Report a problem")
            ZBanner("If anyone is in immediate danger, call 911 first. Safety reports go straight to our team with top priority.", tone = ZTone.DANGER)
            for ((key, label) in kinds) ZChoiceTile(label.take(2).uppercase(), label, "", selected = kind == key, tone = if (key == "safety") ZTone.DANGER else ZTone.WARNING) { kind = key }
            ZTextArea("What happened?", body, { body = it }, placeholder = "Describe what happened, when, and who was involved.")
            ZButton("Send report", style = ZButtonStyle.DANGER, loading = model.busy, enabled = body.isNotBlank()) { scope.launch { opened = model.report(kind, body) } }
        }
    }
}

@Composable
private fun CompanyWithdrawSheet(job: Job, model: CompanyJobDetailModel, onDismiss: () -> Unit, done: () -> Unit) {
    val scope = rememberCoroutineScope()
    ZSheet(onDismiss = onDismiss, title = "Withdraw from ${job.code ?: "this job"}?") {
        ZBody("The job goes back to the marketplace and the booker is notified that you stepped away.", tone = ZTextTone.SOFT)
        ZBanner("ZiVETT records every withdrawal on your reliability record. A second withdrawal within 30 days pauses your opportunities feed for 24 hours. Taking this booker's work off-platform breaches the marketplace terms.", tone = ZTone.WARNING)
        ZButton("Withdraw", style = ZButtonStyle.DANGER, loading = model.busy) { scope.launch { if (model.withdraw()) done() } }
        ZButton("Keep the job", style = ZButtonStyle.OUTLINE, onClick = onDismiss)
    }
}

/// The dispatcher's picker: field techs first, then office staff, then
/// teammates who can't be assigned, each in suggested-fairness order
/// (fewest open jobs, longest since last assignment) — "Auto-assign
/// next" takes the first field tech, but the dispatcher always chooses.
/// A crew job (the quote priced more than one tech) is picked, then
/// saved: exactly `crewSize` people, the first picked leads.
@Composable
private fun AssignTechSheet(model: CompanyJobDetailModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val nav = LocalNav.current
    var assigning by remember { mutableStateOf<Int?>(null) }
    // The crew being picked, lead first — seeded from the roster once it loads.
    var selection by remember { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(model) { model.loadRoster() }
    fun assign(userIds: List<Int>) {
        if (assigning != null) return
        assigning = userIds.first()
        scope.launch { val ok = model.assign(userIds); assigning = null; if (ok) onDismiss() }
    }
    val crewSize = model.job?.crewSize ?: model.roster.value?.crewSize ?: 1
    val isCrew = crewSize > 1
    val technicians = CompanyPresentation.technicians(crewSize)
    ZSheet(onDismiss = onDismiss, title = if (isCrew) "Assign $technicians" else "Who's taking this job?") {
        ZBody(
            if (isCrew) "Pick $technicians — the quote's crew. The first person you pick leads, and the customer sees their photo first."
            else "Suggested order — fewest open jobs first, then longest since their last one. The booker sees the assigned tech's name and photo.",
            tone = ZTextTone.SOFT,
        )
        ZLoadable(model.roster, retry = { scope.launch { model.loadRoster() } }) { roster ->
            val picked = selection ?: roster.crewUserIds.ifEmpty { model.job?.crewMembers?.map { it.id }.orEmpty() }
            Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                if (isCrew) {
                    if (roster.canAutoPick(picked, crewSize)) ZButton("Auto-pick ${if (picked.isEmpty()) "the crew" else "the rest"}", style = ZButtonStyle.OUTLINE) { selection = roster.autoPick(picked, crewSize) }
                } else roster.nextUp()?.let { next -> ZButton("Auto-assign next — ${next.name}", loading = assigning == next.id) { assign(listOf(next.id)) } }
                // Nobody pickable is almost always "nobody has a photo yet"
                // — usually a solo owner who signed up without one.
                if (roster.nobodyAssignable) {
                    ZBanner("Nobody on your team has a profile photo yet, and customers see the photo of who's coming. Add your photo (My account) to take this job yourself, or add teammates' photos from the Team page.", tone = ZTone.WARNING)
                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                        ZTextAction("My account") { onDismiss(); nav.navigate(ProfileRoute(Areas.COMPANY)) }
                        ZTextAction("Team page") { onDismiss(); nav.navigate(TeamRoute(Areas.COMPANY)) }
                    }
                } else if (isCrew && roster.tooFewAssignable(crewSize)) {
                    ZBanner("This job needs $technicians, but fewer teammates than that have a profile photo. Add photos from the Team page so the whole crew can be assigned.", tone = ZTone.WARNING)
                    ZTextAction("Team page") { onDismiss(); nav.navigate(TeamRoute(Areas.COMPANY)) }
                } else if (roster.noFieldTechs) {
                    ZBanner("Nobody is set to take jobs, so nothing is auto-assigned. Pick someone below, or turn on “Takes jobs” for your techs on the Team page.", tone = ZTone.WARNING)
                    ZTextAction("Team page") { onDismiss(); nav.navigate(TeamRoute(Areas.COMPANY)) }
                }
                for (member in roster.members) {
                    val slot = picked.indexOf(member.id)
                    ZSelectableRow(
                        selected = if (isCrew) slot >= 0 else member.assigned,
                        enabled = member.assignable && assigning == null && (isCrew || !member.assigned),
                        onClick = { if (isCrew) selection = picked.togglingCrew(member.id, crewSize) else assign(listOf(member.id)) },
                    ) {
                        ZPhotoAvatar(member.photoUrl, member.name.take(1), 40.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            ZBodyStrong(member.name)
                            ZCaption(if (member.needsPhoto) "Needs a profile photo before they can be assigned" else "${member.openJobs} open ${if (member.openJobs == 1) "job" else "jobs"}")
                        }
                        if (isCrew && slot == 0) ZBadge("Lead", ZTone.SUCCESS)
                        else if (isCrew && slot > 0) ZBadge("Crew", ZTone.SUCCESS)
                        else if (!isCrew && member.assigned) ZBadge("Assigned", ZTone.SUCCESS)
                        else if (assigning == member.id) ZSpinner(size = 18.dp)
                        else if (member.assignable && !member.takesJobs) ZBadge("Office")
                    }
                }
                if (isCrew) {
                    ZDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                        ZCaption("${picked.size} of $crewSize picked", modifier = Modifier.weight(1f))
                        ZButton("Cancel", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false, onClick = onDismiss)
                        ZButton("Assign crew", compact = true, fullWidth = false, loading = assigning != null, enabled = picked.size == crewSize) { assign(picked) }
                    }
                }
            }
        }
    }
}
