package com.zivett.app.features.company

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AutoAwesome
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zivett.app.app.Areas
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.CompanyWindow
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.OpportunitiesResponse
import com.zivett.app.core.models.Quote
import com.zivett.app.core.models.QuoteBody
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
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZFlowRow
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZMonoLarge
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZPlanTag
import com.zivett.app.design.ZRadius
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextArea
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTitle
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZType
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.customer.jobs.hoursLabel
import com.zivett.app.features.customer.jobs.kmLabel
import com.zivett.app.features.shared.CategoryChip
import com.zivett.app.features.shared.IntakeChips
import com.zivett.app.features.shared.LocalNav
import com.zivett.app.features.shared.MoneyRow
import com.zivett.app.features.shared.PhotoGrid
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class OpportunitiesModel(private val client: ApiClient) {
    var state by mutableStateOf<Loadable<OpportunitiesResponse>>(Loadable.Loading)
    var toast by mutableStateOf<String?>(null)
    var actingOnId by mutableStateOf<Int?>(null)
    var startingPayoutSetup by mutableStateOf(false)

    suspend fun load() { state = state.reloaded { client.send(CompanyEndpoints.opportunities()) } }

    /// Stripe-hosted Connect onboarding — the composer's "Set up payouts" road.
    suspend fun payoutOnboardingUrl(): String? {
        startingPayoutSetup = true
        try { return client.send(CompanyEndpoints.stripeOnboardingLink()).url } catch (_: Exception) { toast = "Could not start payout setup. Please try again." } finally { startingPayoutSetup = false }
        return null
    }

    suspend fun decline(job: Job) {
        actingOnId = job.id
        try {
            client.send(CompanyEndpoints.decline(job.id))
            state.value?.let { state = Loadable.Loaded(it.copy(opportunities = it.opportunities.filter { o -> o.id != job.id })) }
        } catch (_: Exception) { toast = "Could not decline ${job.code ?: "that job"}. Please try again." } finally { actingOnId = null }
    }

    /// null = success (card removed); non-null = inline error for the composer.
    suspend fun submitQuote(job: Job, body: QuoteBody): String? {
        actingOnId = job.id
        try {
            client.send(CompanyEndpoints.quote(job.id, body))
            state.value?.let { state = Loadable.Loaded(it.copy(opportunities = it.opportunities.filter { o -> o.id != job.id })) }
            toast = "Quote sent to ZiVETT for ${job.code ?: "the job"} — track it under My quotes"
            return null
        } catch (error: ApiError) {
            if (error is ApiError.Validation && error.errors.errors.isNotEmpty()) {
                return error.errors.first("proposed_date") ?: error.errors.first("estimated_hours") ?: error.errors.first("crew_size") ?: error.errors.first("message") ?: error.errors.message
            }
            // 409 / bodyless 422: the job is gone — drop the card.
            state.value?.let { state = Loadable.Loaded(it.copy(opportunities = it.opportunities.filter { o -> o.id != job.id })) }
            toast = error.userMessage
            return null
        } catch (error: Exception) {
            return error.userMessage
        } finally {
            actingOnId = null
        }
    }
}

@Composable
fun OpportunitiesScreen() {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember { OpportunitiesModel(environment.client) }
    var quoting by remember { mutableStateOf<Job?>(null) }
    var sentToStripe by remember { mutableStateOf(false) }
    LaunchedEffect(model) { model.load() }
    LifecycleResumeEffect(sentToStripe) {
        if (sentToStripe) { sentToStripe = false; scope.launch { model.load() } }
        onPauseOrDispose { }
    }

    ZToastBox(model.toast, { model.toast = null }) {
        ZScreen(onRefresh = { model.load() }) {
            ZLoadable(model.state, retry = { scope.launch { model.load() } }) { feed ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    ZPageTitle("Opportunities")
                    feed.cooldownUntil?.let { ZBanner("Your feed is paused after recent withdrawals. New opportunities reopen ${Dates.shortTime(it)}. Withdrawals also lower your ranking — completed jobs repair it.", tone = ZTone.DANGER) }
                    // No tier ships capped today, so this never renders — and if one
                    // ever does, it states the fact only: no "upgrade" nudge and no
                    // link (the app never steers toward a plan).
                    feed.leads.limit?.let { limit ->
                        val remaining = feed.leads.remaining ?: 0
                        ZBanner(if (remaining == 0) "You've used all $limit of your plan's leads this month. New leads open at the start of next month." else "$remaining of $limit leads left this month — each quote you send uses one.", tone = if (remaining == 0) ZTone.DANGER else ZTone.WARNING)
                    }
                    if (feed.opportunities.isEmpty() && feed.cooldownUntil == null) ZEmptyState(Icons.Outlined.AutoAwesome, "No opportunities right now", "New jobs appear here the moment customers book. Check back soon.")
                    for (job in feed.opportunities) {
                        OpportunityCard(job, feed.commissionBps, busy = model.actingOnId == job.id, quote = { quoting = job }, decline = { scope.launch { model.decline(job) } })
                    }
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }

    quoting?.let { job ->
        val feed = model.state.value
        QuoteComposerSheet(
            job = job, rateCents = feed?.hourlyRates?.get(job.category.id.toString()), commissionBps = feed?.commissionBps ?: 1500, existing = null,
            payoutsReady = feed?.payoutsReady ?: true,
            setupPayouts = { quoting = null; scope.launch { model.payoutOnboardingUrl()?.let { sentToStripe = true; openUrl(context, it) } } },
            onDismiss = { quoting = null },
        ) { body -> model.submitQuote(job, body) }
    }
}

@Composable
fun OpportunityCard(job: Job, commissionBps: Int, busy: Boolean, quote: () -> Unit, decline: () -> Unit) {
    val colors = ZTheme.colors
    var expanded by remember { mutableStateOf(false) }
    ZCard {
        Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                CategoryChip(job.category)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ZFlowRow(spacing = 4.dp) {
                        val mode = JobPresentation.modeMeta(job.mode)
                        ZBadge(mode.label, mode.tone)
                        // Business Premium's perk made visible: pinned to the top of the feed.
                        if (job.priority == true) ZPlanTag("PRIORITY")
                        CompanyPresentation.urgencyBadge(job.urgency)?.let { ZBadge(it.label, it.tone) }
                        if (job.bookerType == "business") ZBadge("Property manager", ZTone.INFO)
                    }
                    ZBodyStrong(job.title)
                }
            }
            Text(listOfNotNull(job.code, job.address, job.distanceKm?.let { "${it.kmLabel()} km away" }, job.scheduledDate?.let { JobPresentation.windowSlot(it, job.scheduledWindow) }).joinToString(" · "), style = ZType.mono.copy(letterSpacing = 0.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = colors.inkMuted)
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                ZMono("Payout")
                ZBodyStrong("Your quote", color = colors.success)
                ZCaption("minus ${CompanyPresentation.percent(commissionBps)} commission", tone = ZTextTone.FAINT)
            }
            job.intakeAnswers?.takeIf { it.isNotEmpty() }?.let { IntakeChips(it) }
            job.issue?.let { issue ->
                ZBody(issue, tone = ZTextTone.SOFT, maxLines = if (expanded) Int.MAX_VALUE else 3)
                if (issue.length > 220) ZTextAction(if (expanded) "Show less" else "Read full description") { expanded = !expanded }
            }
            job.photos?.takeIf { it.isNotEmpty() }?.let { PhotoGrid(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                ZButton("Decline", style = ZButtonStyle.SOFT, compact = true, fullWidth = false, enabled = !busy, onClick = decline)
                ZButton("Quote this job", style = ZButtonStyle.SUCCESS, compact = true, enabled = !busy, modifier = Modifier.weight(1f), onClick = quote)
            }
        }
    }
}

/// The quote composer (`QuoteComposer.vue`): hours × crew × your rate,
/// live money box, proposed arrival from the booker's windows.
@Composable
fun QuoteComposerSheet(
    job: Job, rateCents: Int?, commissionBps: Int, existing: Quote?,
    /// False only on the stripe driver before Connect onboarding — the
    /// primary action becomes going to set payouts up.
    payoutsReady: Boolean = true, setupPayouts: (() -> Unit)? = null,
    onDismiss: () -> Unit, submit: suspend (QuoteBody) -> String?,
) {
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    var hours by remember { mutableStateOf(existing?.estimatedHours ?: 0.0) }
    var crew by remember { mutableStateOf(existing?.crewSize ?: 1) }
    var message by remember { mutableStateOf(existing?.message ?: "") }
    var proposedKey by remember {
        mutableStateOf(
            when {
                existing?.proposedDate != null -> "${existing.proposedDate}|${existing.proposedWindow ?: ""}"
                existing != null && job.mode != "scheduled" -> "flexible"
                else -> null
            },
        )
    }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val offeredWindows: List<CompanyWindow> = job.companyWindows ?: (job.availabilityWindows ?: emptyList()).map { CompanyWindow(it.date, it.window, true, 0) }
    val needsProposal = job.mode == "scheduled" && offeredWindows.isNotEmpty()
    val proposalMissing = needsProposal && (proposedKey == null || proposedKey == "flexible")
    val totalCents = rateCents?.let { CompanyPresentation.quoteTotal(it, hours, crew) } ?: 0
    val canSubmit = !sending && hours >= 0.5 && !proposalMissing && rateCents != null

    ZSheet(onDismiss = onDismiss, title = if (existing == null) "Send your quote to ZiVETT" else "Revise your quote") {
        // What the booker told us — the structured facts stay visible while pricing.
        ZCard(padding = ZSpacing.sm) {
            ZBodyStrong(job.title)
            ZCaption(listOfNotNull(job.code, job.address, job.distanceKm?.let { "${it.kmLabel()} km away" }).joinToString(" · "))
            job.intakeAnswers?.takeIf { it.isNotEmpty() }?.let { IntakeChips(it) }
            job.issue?.takeIf { it.isNotEmpty() }?.let { ZCaption("“$it”", tone = ZTextTone.SOFT) }
        }
        error?.let { ZBanner(it, tone = ZTone.DANGER) }

        if (!payoutsReady) {
            Column(modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(ZRadius.card)).background(colors.warningSoft).padding(ZSpacing.sm), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ZBodyStrong("One step before you can quote: set up payouts.")
                ZCaption("Payouts are how you get paid — when a booker accepts your quote and the job is done, ZiVETT sends the money to your bank account. Banking details are entered securely with Stripe and take a few minutes; ZiVETT never sees them.")
            }
        }

        if (rateCents != null) {
            Row(verticalAlignment = Alignment.CenterVertically) { ZHeadline("Your price", modifier = Modifier.weight(1f)); ZCaption("${job.category.name} rate ${Money.format(rateCents)}/hr") }
            existing?.hourlyRateCents?.takeIf { it != rateCents }?.let { old ->
                ZBanner("Your rate changed since this quote went out (${Money.format(old)}/hr → ${Money.format(rateCents)}/hr) — saving reprices at the new rate.", tone = ZTone.INFO)
            }
            Stepper("Estimated hours", if (hours == 0.0) "—" else hours.hoursLabel(), down = { hours = maxOf(0.0, ((hours - 0.5) * 2).roundToInt() / 2.0) }, up = { hours = minOf(100.0, maxOf(0.5, ((hours + 0.5) * 2).roundToInt() / 2.0)) })
            Stepper("Crew", crew.toString(), down = { crew = maxOf(1, crew - 1) }, up = { crew = minOf(20, crew + 1) })
            ZCard(padding = ZSpacing.sm) {
                if (hours >= 0.5) {
                    MoneyRow("$crew × ${hours.hoursLabel()}h × ${Money.format(rateCents)}", totalCents)
                    MoneyRow("Commission (${CompanyPresentation.percent(commissionBps)})", -CompanyPresentation.commission(totalCents, commissionBps))
                    ZDivider()
                    MoneyRow("You keep", totalCents - CompanyPresentation.commission(totalCents, commissionBps), strong = true, color = colors.success)
                } else ZCaption("Set the hours and your total prices itself — rate × hours × crew.")
            }
        } else {
            ZBanner("Set your ${job.category.name} hourly rate on your Passport first — quotes are priced from it.", tone = ZTone.WARNING)
        }

        if (offeredWindows.isNotEmpty()) {
            ZHeadline("When would you arrive?${if (job.mode == "scheduled") "" else " (optional)"}")
            ZCaption("The booker offered these windows — your quote proposes one.")
            ZFlowRow(spacing = 6.dp) {
                for (window in offeredWindows) {
                    val key = "${window.date}|${window.window}"
                    val selected = proposedKey == key
                    Text(
                        JobPresentation.windowSlot(window.date, window.window) + (if (window.booked > 0) " · ${window.booked} booked" else ""),
                        style = ZType.label, color = if (selected) colors.onBrand else if (window.available) colors.ink else colors.inkFaint,
                        modifier = Modifier.clip(CircleShape).background(if (selected) colors.navy else colors.surface).border(1.dp, if (selected) colors.navy else colors.borderStrong, CircleShape).clickable(enabled = window.available) { proposedKey = if (selected) null else key }.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
                if (job.mode != "scheduled") {
                    val flexible = proposedKey == "flexible"
                    Text("Flexible — arrange after acceptance", style = ZType.label, color = if (flexible) colors.onBrand else colors.inkSoft,
                        modifier = Modifier.clip(CircleShape).background(if (flexible) colors.navy else colors.surface).border(1.dp, if (flexible) colors.navy else colors.borderStrong, CircleShape).clickable { proposedKey = if (flexible) null else "flexible" }.padding(horizontal = 10.dp, vertical = 7.dp))
                }
            }
        }

        ZTextArea("What's included (optional)", message, { message = it }, placeholder = "Materials, timeline, what the price covers…", minLines = 3)
        ZCaption("The booker compares quotes and accepts one — acceptance assigns you the job and unlocks the exact address and access notes.", tone = ZTextTone.FAINT)

        // Payouts unready: the primary action IS going to set them up.
        if (!payoutsReady && setupPayouts != null) {
            ZButton("Set up payouts →", style = ZButtonStyle.SUCCESS, onClick = setupPayouts)
        } else {
            ZButton(if (proposalMissing) "Pick an arrival window" else if (existing == null) "Send to ZiVETT" else "Save changes", style = ZButtonStyle.SUCCESS, loading = sending, enabled = canSubmit) {
                scope.launch {
                    sending = true; error = null
                    var date: String? = null; var window: String? = null
                    // Instant jobs forbid a proposal server-side — never send one.
                    val key = proposedKey
                    if (key != null && key != "flexible" && job.mode != "instant") {
                        val parts = key.split("|")
                        date = parts.firstOrNull(); window = parts.getOrNull(1)
                    }
                    val result = submit(QuoteBody(hours, crew, message.ifEmpty { null }, date, window))
                    sending = false
                    if (result != null) error = result else onDismiss()
                }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, down: () -> Unit, up: () -> Unit) {
    val colors = ZTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
        ZLabel(label, modifier = Modifier.weight(1f))
        IconButton(onClick = down, modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.surfaceSunken)) { Icon(Icons.Filled.Remove, contentDescription = "Less", tint = colors.ink) }
        Box(Modifier.widthIn(min = 52.dp), contentAlignment = Alignment.Center) { ZMonoLarge(value) }
        IconButton(onClick = up, modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.surfaceSunken)) { Icon(Icons.Filled.Add, contentDescription = "More", tint = colors.ink) }
    }
}

@Suppress("unused")
private val keepAlign: TextAlign = TextAlign.Start
@Suppress("unused")
private val keepTitle: @Composable () -> Unit = { ZTitle("") }
