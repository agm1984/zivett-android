package com.zivett.app.features.customer.jobs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zivett.app.app.Areas
import com.zivett.app.app.ConversationRoute
import com.zivett.app.app.InvoiceDetailRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Money
import com.zivett.app.core.media.PhotoImport
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobArea
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.Quote
import com.zivett.app.design.ZActionBand
import com.zivett.app.design.ZAvatar
import com.zivett.app.design.ZAvatarShape
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZHeroPanel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZMonoLarge
import com.zivett.app.design.ZPhotoAvatar
import com.zivett.app.design.ZPlanTag
import com.zivett.app.design.ZRadius
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZStars
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.design.ZType
import com.zivett.app.features.company.Dates
import com.zivett.app.features.shared.InvoiceBreakdown
import com.zivett.app.features.shared.IntakeChips
import com.zivett.app.features.shared.JobTrackingMap
import com.zivett.app.features.shared.LiveJobEffect
import com.zivett.app.features.shared.LocalNav
import com.zivett.app.features.shared.PhotoGrid
import com.zivett.app.features.shared.SupportCaseCards
import com.zivett.app.features.shared.TimelineStepper
import com.zivett.app.features.shared.VerificationBadges
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed interface JobSheet {
    data class Accept(val quote: Quote) : JobSheet
    object Cancel : JobSheet
    object Report : JobSheet
    object Claim : JobSheet
    object ReviewSheetKey : JobSheet
    object Pay : JobSheet
    data class Reviews(val id: Int, val name: String) : JobSheet
}

@Composable
fun JobDetailScreen(jobId: Int, area: JobArea, onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember(jobId) { JobDetailModel(jobId, environment.client, area, context) }
    var sheet by remember { mutableStateOf<JobSheet?>(null) }
    var reviewNudged by remember { mutableStateOf(false) }

    LaunchedEffect(model) { model.load() }
    LiveJobEffect(jobId) { model.refresh() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { uris ->
        if (uris.isNotEmpty()) scope.launch { model.upload(PhotoImport.jpegData(context, uris)) }
    }

    // Payment and rating are one flow, not two errands (the web scrolls
    // into the review form after closing): when the pay sheet goes away
    // after a successful close, carry the booker straight into the review — once.
    fun dismissSheet() {
        val was = sheet
        sheet = null
        if (was == JobSheet.Pay && model.justClosed && !reviewNudged && model.job?.review == null) {
            reviewNudged = true
            sheet = JobSheet.ReviewSheetKey
        }
    }

    Column {
        ZTopBar(model.job?.code ?: "Job", onBack = onBack)
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { job ->
                    JobDetailContent(job, model, area, onSheet = { sheet = it }, onAddPhotos = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                }
                Spacer(Modifier.padding(ZSpacing.xl))
            }
        }
    }

    when (val current = sheet) {
        is JobSheet.Accept -> model.job?.let { job -> AcceptQuoteSheet(job, current.quote, model, onDismiss = ::dismissSheet) }
        JobSheet.Cancel -> model.job?.let { job -> CancelJobSheet(job, model, onDismiss = ::dismissSheet) }
        JobSheet.Report -> ReportProblemSheet(model, onDismiss = ::dismissSheet)
        JobSheet.Claim -> WarrantyClaimSheet(model, onDismiss = ::dismissSheet)
        JobSheet.ReviewSheetKey -> ReviewSheet(model, onDismiss = ::dismissSheet)
        JobSheet.Pay -> model.job?.invoice?.let { invoice -> PayAndCloseSheet(invoice, model, onDismiss = ::dismissSheet) }
        is JobSheet.Reviews -> CompanyReviewsSheet(current.id, current.name, onDismiss = ::dismissSheet)
        null -> Unit
    }
}

@Composable
private fun JobDetailContent(job: Job, model: JobDetailModel, area: JobArea, onSheet: (JobSheet) -> Unit, onAddPhotos: () -> Unit) {
    val phase = JobPresentation.phase(job)
    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
        JobHeader(job, area)

        // Open support cases lead everything (web's order -1 card) —
        // they're why "Pay & close" would refuse.
        SupportCaseCards(job, JobPresentation.CaseSide.BOOKER)

        // The pro asked to move the visit — a decision, so it leads.
        RescheduleCard(job, model)

        if (job.lat != null && job.status in setOf(JobStatus.EN_ROUTE, JobStatus.ARRIVED)) {
            JobTrackingMap(job, area, modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(ZRadius.panel)))
        }

        // Ordering mirrors `mainOrder` on the web: what matters most
        // for this phase leads.
        when (phase) {
            JobPresentation.Phase.DECIDING -> { QuotesCard(job, area, onSheet); PriceCard(job); StatusCard(job, area); ProCard(job, area, onSheet); DetailsCard(job, model, onAddPhotos) }
            JobPresentation.Phase.WORKING -> { StatusCard(job, area); ProCard(job, area, onSheet); PropertyCard(job, area); ChangeOrders(job, model); PriceCard(job); DetailsCard(job, model, onAddPhotos) }
            JobPresentation.Phase.SETTLING -> { InvoiceCard(job, model, area, onSheet); ReviewCard(job, onSheet); StatusCard(job, area); ProCard(job, area, onSheet); ChangeOrders(job, model); EvidenceCard(job); DetailsCard(job, model, onAddPhotos) }
            JobPresentation.Phase.RECORD -> { StatusCard(job, area); InvoiceCard(job, model, area, onSheet); ReviewCard(job, onSheet); EvidenceCard(job); ProCard(job, area, onSheet); DetailsCard(job, model, onAddPhotos) }
        }

        ManageRow(job, area, onSheet)
    }
}

@Composable
private fun JobHeader(job: Job, area: JobArea) {
    ZHeroPanel {
        val display = JobPresentation.display(job)
        ZBadge(display.label, display.tone)
        Text(job.title, style = ZType.title, color = Color.White)
        val context = if (area.kind == JobArea.Kind.BUSINESS) listOfNotNull(job.property?.name ?: job.address, job.unit, job.tenant) else listOfNotNull(job.address)
        var line = (listOfNotNull(job.code, JobPresentation.modeMeta(job.mode).label) + context).joinToString(" · ")
        job.scheduledDate?.let { line += " · ${JobPresentation.windowSlot(it, job.scheduledWindow)}" }
        Text(line, style = ZType.caption, color = Color.White.copy(alpha = 0.75f))
    }
}

@Composable
private fun PropertyCard(job: Job, area: JobArea) {
    if (area.kind != JobArea.Kind.BUSINESS) return
    val property = job.property ?: return
    ZCard {
        ZMono("Property")
        ZBodyStrong(property.name)
        ZCaption(listOfNotNull(job.address, job.unit).joinToString(" · "))
        job.tenant?.let { ZCaption("Tenant: $it", tone = ZTextTone.SOFT) }
    }
}

@Composable
private fun StatusCard(job: Job, area: JobArea) {
    val colors = ZTheme.colors
    if (job.status == JobStatus.CANCELLED) {
        ZCard {
            ZBodyStrong("This ${area.noun} was cancelled.", color = colors.danger)
            job.invoice?.takeIf { !it.isPaid }?.let { ZCaption("A cancellation fee of ${Money.format(it.amountDueCents)} is due.") }
        }
    } else {
        ZCard {
            ZHeadline("Job status")
            TimelineStepper(JobPresentation.timeline(job.status))
        }
    }
}

@Composable
private fun QuotesCard(job: Job, area: JobArea, onSheet: (JobSheet) -> Unit) {
    val open = if (job.company == null) job.pendingQuotes else emptyList()
    if (open.isEmpty()) return
    ZCard {
        ZHeadline("Your quotes from ZiVETT (${open.size})")
        ZCaption("Each option is from a pro we've verified — identity, licence and insurance checked. Contact details are shared as soon as you accept.")
        open.forEachIndexed { i, quote ->
            val company = quote.company
            val showReviews: (() -> Unit)? = if (company?.id != null) ({ onSheet(JobSheet.Reviews(company.id, company.name)) }) else null
            QuoteRow(i + 1, quote, showReviews) { onSheet(JobSheet.Accept(quote)) }
        }
        // "Accept — $150" reads like a checkout button; say plainly that
        // accepting isn't paying (Damon's review #40).
        ZCaption("You won't be charged when you accept — you pay once the ${if (area.kind == JobArea.Kind.BUSINESS) "work" else "job"} is done and you close it.")
    }
}

@Composable
private fun PriceCard(job: Job) {
    if (job.invoice != null) return
    ZCard {
        val accepted = job.acceptedQuote
        // Labelled for what it holds (field audit #21: a bare "Price"
        // over "ZiVETT is on it" read as broken).
        ZMono(if (accepted != null) "Your agreed price" else "Quotes")
        val label = JobPresentation.priceLabel(job)
        when {
            accepted != null -> {
                ZMonoLarge(Money.format(accepted.amountCents))
                ZCaption("Quoted price, agreed with your pro", tone = ZTextTone.SOFT)
                com.zivett.app.features.shared.QuoteBreakdown(accepted)
            }
            label != null && job.quotesReleasedAt != null -> {
                ZMonoLarge(label)
                ZCaption("Accept a quote to lock in your price", tone = ZTextTone.SOFT)
            }
            else -> {
                ZBodyStrong("ZiVETT is on it")
                // Progress the booker can feel: the notified-pro count
                // when there is one. Zero supply stays generic.
                val matched = job.matchedPros
                if (matched != null && matched > 0) ZCaption("$matched verified pro${if (matched == 1) "" else "s"} in your area ${if (matched == 1) "has" else "have"} been notified. We hand-check every quote — you'll hear from us the moment yours are ready.")
                else ZCaption("We're collecting quotes from verified pros and hand-checking each one — you'll hear from us the moment yours are ready.")
            }
        }
    }
}

@Composable
private fun ProCard(job: Job, area: JobArea, onSheet: (JobSheet) -> Unit) {
    val nav = LocalNav.current
    val colors = ZTheme.colors
    ZCard {
        ZMono("Your pro")
        val company = job.company
        if (company != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                ZAvatar(company.initials, ZAvatarShape.COMPANY, 48.dp)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ZBodyStrong(company.name)
                        company.plan?.takeIf { it.isNotEmpty() }?.let { ZPlanTag(it) }
                        Icon(Icons.Filled.Check, contentDescription = "Verified", tint = colors.brandGold, modifier = Modifier.padding(0.dp))
                    }
                    // The rating opens the actual reviews — a number nobody
                    // can read behind it isn't a trust signal.
                    val rating = company.rating
                    if (rating != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            ZStars(rating.roundToInt())
                            val text = "${rating.oneDecimal()} · ${company.count ?: 0} reviews"
                            if (company.id != null) ZTextAction(text) { onSheet(JobSheet.Reviews(company.id, company.name)) } else ZCaption(text)
                        }
                    } else ZCaption("Verified pro")
                }
            }
            // Who's actually coming — the trust signal, as soon as the org
            // assigns someone. A crew job lists everyone, lead first.
            val crew = job.crewMembers
            crew.forEachIndexed { index, tech ->
                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    ZPhotoAvatar(tech.photoUrl, tech.name.take(1), 40.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ZBodyStrong(tech.name)
                        ZCaption(JobPresentation.crewCaption(index, crew.size))
                    }
                }
            }
            job.scheduledDate?.takeIf { job.status in setOf(JobStatus.ACCEPTED, JobStatus.EN_ROUTE, JobStatus.ARRIVED) }?.let {
                ZBanner("Arriving ${JobPresentation.arrivalLabel(it, job.scheduledWindow)}", tone = ZTone.INFO)
            }
            ZCaption("Identity, license & insurance verified by ZiVETT.")
            if (job.status != JobStatus.CANCELLED) {
                ZButton("Message ${company.name}") {
                    nav.navigate(ConversationRoute(job.id, if (area.kind == JobArea.Kind.BUSINESS) Areas.BUSINESS else Areas.CUSTOMER, company.name, job.title, readOnly = job.status in setOf(JobStatus.WARRANTY_EXPIRED, JobStatus.CANCELLED)))
                }
            }
        } else {
            ZBody("Matching you with a verified pro…", tone = ZTextTone.SOFT)
        }
    }
}

@Composable
private fun DetailsCard(job: Job, model: JobDetailModel, onAddPhotos: () -> Unit) {
    val scope = rememberCoroutineScope()
    val issuePhotos = (job.photos ?: emptyList()).filter { it.kind == "issue" }
    val canAdd = job.status != JobStatus.CANCELLED && job.closedAt == null && issuePhotos.size < 8
    ZCard {
        ZMono("Issue")
        job.intakeAnswers?.takeIf { it.isNotEmpty() }?.let { IntakeChips(it) }
        job.issue?.let { ZBody(it) }
        ZMono("Photos", modifier = Modifier.padding(top = 4.dp))
        if (issuePhotos.isEmpty()) ZCaption("This job has no photos yet — pros quote faster (and more accurately) when they can see the problem.")
        PhotoGrid(issuePhotos, onDelete = { photo -> scope.launch { model.deletePhoto(photo) } })
        if (canAdd) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = ZTheme.colors.link)
                ZTextAction(if (model.busy) "Uploading…" else "Add photos", enabled = !model.busy, onClick = onAddPhotos)
            }
        }
        job.accessNotes?.takeIf { it.isNotEmpty() }?.let {
            ZMono("Access notes", modifier = Modifier.padding(top = 4.dp))
            ZBody(it, tone = ZTextTone.SOFT)
        }
    }
}

@Composable
private fun EvidenceCard(job: Job) {
    val before = (job.photos ?: emptyList()).filter { it.kind == "before" }
    val after = (job.photos ?: emptyList()).filter { it.kind == "after" }
    if (before.isEmpty() && after.isEmpty()) return
    ZCard {
        ZHeadline("Work evidence")
        ZCaption("Before/after photos from your pro — part of this job's permanent record.")
        if (before.isNotEmpty()) { ZMono("Before"); PhotoGrid(before) }
        if (after.isNotEmpty()) { ZMono("After"); PhotoGrid(after) }
    }
}

@Composable
private fun InvoiceCard(job: Job, model: JobDetailModel, area: JobArea, onSheet: (JobSheet) -> Unit) {
    val nav = LocalNav.current
    val invoice = job.invoice ?: return
    if (model.justClosed) {
        ZCard {
            ZHeadline("${job.code ?: "Job"} is closed — thanks for confirming")
            ZCaption("${Money.format(invoice.amountDueCents)} went to ${job.company?.name ?: "your pro"}. Your workmanship warranty starts now — if anything's off, report it from this page.")
        }
        return
    }
    ZCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZHeadline("Invoice", modifier = Modifier.weight(1f))
            val meta = JobPresentation.invoiceStatusMeta(invoice.status)
            ZBadge(meta.label, meta.tone)
        }
        InvoiceBreakdown(invoice)
        ZTextAction(invoice.number ?: "View invoice") { nav.navigate(InvoiceDetailRoute(invoice.id, if (area.kind == JobArea.Kind.BUSINESS) Areas.BUSINESS else Areas.CUSTOMER)) }
        // An open case freezes closure server-side (JobClosure::held):
        // Pay & close would do nothing and the auto-release date isn't
        // coming, so say what IS happening instead.
        val openCase = JobPresentation.openCases(job).firstOrNull()
        if (!invoice.isPaid && job.status != JobStatus.CANCELLED && openCase != null) {
            ZBanner(JobPresentation.paymentPausedCopy(openCase), tone = ZTone.WARNING)
        } else if (!invoice.isPaid && job.status != JobStatus.CANCELLED) {
            ZActionBand("Pay & close — ${Money.format(invoice.amountDueCents)}", loading = model.busy) { onSheet(JobSheet.Pay) }
            job.autoCloseAt?.let { ZCaption("Payment releases automatically on ${Dates.shortTime(it)} unless you report an issue first.") }
            // Pay first, then rate — the review card appears only once
            // the job is closed (JobStatus.reviewable).
            ZCaption("Once it's paid, you can rate ${job.company?.name ?: "your pro"}.")
        }
    }
}

@Composable
private fun ReviewCard(job: Job, onSheet: (JobSheet) -> Unit) {
    if (!job.isReviewable) return
    ZCard {
        ZHeadline("Your review")
        val review = job.review
        if (review != null) {
            ZStars(review.rating)
            review.comment?.takeIf { it.isNotEmpty() }?.let { ZBody(it, tone = ZTextTone.SOFT) }
            ZButton("Edit review", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { onSheet(JobSheet.ReviewSheetKey) }
        } else {
            ZCaption("How did the visit go?")
            ZButton("Write a review", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { onSheet(JobSheet.ReviewSheetKey) }
        }
    }
}

/// "{company} asked to move your visit": from → to, their reason, and the
/// booker's two answers. Cancelling is fee-free while it's pending.
@Composable
private fun RescheduleCard(job: Job, model: JobDetailModel) {
    val request = job.rescheduleRequest ?: return
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    ZCard {
        ZBadge("Decide", ZTone.WARNING)
        ZHeadline("${job.company?.name ?: "Your pro"} asked to move your visit")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                ZMono("From", modifier = Modifier.padding(top = 2.dp))
                Text(JobPresentation.reschedulePoint(request.fromDate ?: job.scheduledDate, request.fromWindow ?: job.scheduledWindow), style = ZType.body.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough), color = colors.inkMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                ZMono("To", modifier = Modifier.padding(top = 2.dp))
                ZBodyStrong(JobPresentation.reschedulePoint(request.date, request.window))
            }
        }
        request.reason?.takeIf { it.isNotBlank() }?.let { Text("“$it”", style = ZType.body.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = colors.inkSoft) }
        ZButton("Accept new time", style = ZButtonStyle.SUCCESS, loading = model.busy) { scope.launch { model.decideReschedule(accept = true) } }
        ZButton("Keep original time", style = ZButtonStyle.OUTLINE, enabled = !model.busy) { scope.launch { model.decideReschedule(accept = false) } }
        ZCaption("Cancelling is free while you decide.")
    }
}

@Composable
private fun ChangeOrders(job: Job, model: JobDetailModel) {
    val scope = rememberCoroutineScope()
    val orders = job.changeOrders?.takeIf { it.isNotEmpty() } ?: return
    ZCard {
        ZHeadline("Change orders")
        for (order in orders) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ZBodyStrong(order.label ?: "Scope change")
                    ZCaption(Money.format(order.amountCents), tone = ZTextTone.SOFT)
                }
                if (order.isProposed) {
                    ZButton("Decline", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { scope.launch { model.decideChangeOrder(order, approve = false) } }
                    ZButton("Approve", style = ZButtonStyle.SUCCESS, compact = true, fullWidth = false) { scope.launch { model.decideChangeOrder(order, approve = true) } }
                } else {
                    ZBadge(order.status, if (order.status == "approved") ZTone.SUCCESS else ZTone.DANGER)
                }
            }
        }
        ZCaption("Approved changes bill with the final invoice; declined ones never do.")
    }
}

@Composable
private fun ManageRow(job: Job, area: JobArea, onSheet: (JobSheet) -> Unit) {
    if (job.status == JobStatus.CANCELLED || !(job.isCancellable || job.company != null)) return
    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.sm), modifier = Modifier.padding(top = ZSpacing.sm)) {
        ZCaption(JobPresentation.cancelManageCopy(job))
        com.zivett.app.design.ZFlowRow(spacing = ZSpacing.xs) {
            if (job.company != null) ZButton(if (JobPresentation.openCases(job).isNotEmpty()) "Report another problem" else "Report a problem", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { onSheet(JobSheet.Report) }
            if (job.status == JobStatus.WARRANTY_ACTIVE && (job.disputes ?: emptyList()).none { it.kind == "warranty_claim" && it.status !in setOf("resolved", "declined") }) {
                ZButton("Open a warranty claim", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { onSheet(JobSheet.Claim) }
            }
            if (job.isCancellable) ZButton("Cancel this ${area.noun}", style = ZButtonStyle.GHOST, compact = true, fullWidth = false) { onSheet(JobSheet.Cancel) }
        }
    }
}

/// One quote option on the job page. The top-ranked option (curation →
/// tier → price) carries the gold action band; the alternatives keep a
/// quiet accept. One gold action per screen.
@Composable
fun QuoteRow(index: Int, quote: Quote, showReviews: (() -> Unit)?, accept: () -> Unit) {
    val colors = ZTheme.colors
    val top = index == 1
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.tile)).background(colors.surfaceAlt).border(1.dp, colors.border, RoundedCornerShape(ZRadius.tile))) {
        Column(modifier = Modifier.padding(ZSpacing.sm), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { ZMono("Option $index", modifier = Modifier.weight(1f)); ZMonoLarge(Money.format(quote.amountCents)) }
            ZBodyStrong(JobPresentation.quoteCompanyName(quote))
            // What ZiVETT checked about this pro (ID, insurance, licence,
            // WorkSafeBC, background check…).
            VerificationBadges(quote.company?.verification)
            if (showReviews != null && quote.company?.rating != null) ZTextAction(JobPresentation.proLine(quote.company), onClick = showReviews) else ZCaption(JobPresentation.proLine(quote.company))
            com.zivett.app.features.shared.QuoteBreakdown(quote)
            if (quote.expired == true) ZCaption("This quote has expired, so it can't be accepted. Contact ZiVETT support and we'll get you a fresh price.", color = colors.danger)
            else if (!top) ZButton("Accept — ${Money.format(quote.amountCents)}", style = ZButtonStyle.OUTLINE, compact = true, onClick = accept)
        }
        if (top && quote.expired != true) ZActionBand("Accept this quote — ${Money.format(quote.amountCents)}", flushBottom = true, radius = ZRadius.tile, onClick = accept)
    }
}
