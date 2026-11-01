package com.zivett.app.features.company

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.input.KeyboardType
import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.design.ZTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.zivett.app.features.shared.LocalNavBadges
import com.zivett.app.features.shared.NavBadgesModel
import com.zivett.app.features.shared.ViewingNavBadge
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/// A "New … job near you" tap parks the job's code here; the feed opens
/// its quote composer once loaded (web `?job=CODE`). Consumed once.
object PendingOpportunity {
    var code: String? by mutableStateOf(null)
}

class OpportunitiesModel(private val client: ApiClient) {
    var state by mutableStateOf<Loadable<OpportunitiesResponse>>(Loadable.Loading)
    var toast by mutableStateOf<String?>(null)
    var actingOnId by mutableStateOf<Int?>(null)
    var startingPayoutSetup by mutableStateOf(false)

    suspend fun load() { state = state.reloaded { client.send(CompanyEndpoints.opportunities()) } }

    /// The composer's in-place rate change; null = saved (the feed's rate map is swapped).
    suspend fun saveRate(categoryId: Int, cents: Int): String? = when (val outcome = CompanyRates.save(client, categoryId, cents)) {
        is CompanyRates.Outcome.Saved -> { state.value?.let { state = Loadable.Loaded(it.copy(hourlyRates = outcome.rates)) }; null }
        is CompanyRates.Outcome.Failed -> outcome.message
    }

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

    /// null = the composer closes (sent, or the job is gone); non-null =
    /// an inline error and the composer — with everything typed — stays.
    suspend fun submitQuote(job: Job, body: QuoteBody): String? {
        actingOnId = job.id
        try {
            client.send(CompanyEndpoints.quote(job.id, body))
            state.value?.let { state = Loadable.Loaded(it.copy(opportunities = it.opportunities.filter { o -> o.id != job.id })) }
            toast = "Quote sent to ZiVETT for ${job.code ?: "the job"} — track it under My quotes"
            // Refetch so the job reappears under "Quoted by your team".
            runCatching { load() }
            return null
        } catch (error: ApiError) {
            if (error is ApiError.Validation && error.errors.errors.isNotEmpty()) {
                return error.errors.first("line_items") ?: error.errors.first("valid_until") ?: error.errors.first("proposed_date") ?: error.errors.first("estimated_hours") ?: error.errors.first("crew_size") ?: error.errors.first("message") ?: error.errors.message
            }
            // Only a 409 (quotes closed / already quoted) or a 404 means
            // the job is gone — drop the card. Everything else — offline,
            // a 5xx, a 429, the bodyless 422s ("Set up payouts first", a
            // feed cooldown, no rate for the trade) — used to drop it too,
            // taking a quotable job and the drafted quote with it.
            if (error is ApiError.Conflict || error is ApiError.NotFound) {
                state.value?.let { state = Loadable.Loaded(it.copy(opportunities = it.opportunities.filter { o -> o.id != job.id })) }
                toast = error.userMessage
                return null
            }
            return error.userMessage
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
    // Opening the feed clears its "new since you looked" tab badge.
    ViewingNavBadge(NavBadgesModel.OPPORTUNITIES)
    // A live notification (a teammate's quote, a new booking) refetches —
    // event-driven, like the web's `lastReceived` watch.
    val liveEvents = LocalNavBadges.current?.liveEvents ?: 0
    LaunchedEffect(liveEvents) { if (liveEvents > 0) model.load() }
    // A push for one job: open its composer once the feed has it.
    val pendingCode = PendingOpportunity.code
    val loadedFeed = model.state.value
    LaunchedEffect(pendingCode, loadedFeed) {
        val code = pendingCode ?: return@LaunchedEffect
        val feed = loadedFeed ?: return@LaunchedEffect
        PendingOpportunity.code = null
        feed.opportunities.firstOrNull { it.code == code }?.let { quoting = it }
    }
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
                    // Nothing ships capped today, so this never renders — and if a cap
                    // ever does, it states the fact only: the app never mentions
                    // memberships (see PARITY.md, App Store 3.1.1 / Play payments).
                    feed.leads.limit?.let { limit ->
                        val remaining = feed.leads.remaining ?: 0
                        ZBanner(if (remaining == 0) "You've used all $limit of your leads this month. New leads open at the start of next month." else "$remaining of $limit leads left this month — each quote you send uses one.", tone = if (remaining == 0) ZTone.DANGER else ZTone.WARNING)
                    }
                    if (feed.opportunities.isEmpty() && feed.cooldownUntil == null) ZEmptyState(Icons.Outlined.AutoAwesome, if (feed.quoted.isNotEmpty()) "No new opportunities right now" else "No opportunities right now", "New jobs appear here the moment customers book. Check back soon.")
                    for (job in feed.opportunities) {
                        OpportunityCard(job, feed.commissionBps, busy = model.actingOnId == job.id, quote = { quoting = job }, decline = { scope.launch { model.decline(job) } })
                    }
                    if (feed.quoted.isNotEmpty()) QuotedByTeam(feed.quoted, environment.session.user?.id) { openCompanyTarget(nav, com.zivett.app.core.push.PushTarget.Quotes) }
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }

    // Rates are an org-admin edit (the endpoint's gate) — members see no Change.
    val isAdmin = environment.session.user?.organizationRole == OrganizationRole.ADMIN
    quoting?.let { job ->
        val feed = model.state.value
        QuoteComposerSheet(
            job = job, rateCents = feed?.hourlyRates?.get(job.category.id.toString()), commissionBps = feed?.commissionBps ?: 1500, existing = null,
            payoutsReady = feed?.payoutsReady ?: true,
            setupPayouts = { quoting = null; scope.launch { model.payoutOnboardingUrl()?.let { sentToStripe = openUrl(context, it) } } },
            saveRate = if (isAdmin) ({ cents -> model.saveRate(job.category.id, cents) }) else null,
            onDismiss = { quoting = null },
        ) { body -> model.submitQuote(job, body) }
    }
}

/// Jobs a teammate already quoted: one quote per company, so they're off
/// the list above — but never silently gone (web review #20). Who sent
/// it, for how much, where it sits; a tap opens My quotes.
@Composable
private fun QuotedByTeam(quotes: List<Quote>, userId: Int?, openQuotes: () -> Unit) {
    val now = java.time.Instant.now()
    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs), modifier = Modifier.padding(top = ZSpacing.sm)) {
        ZHeadline("Quoted by your team")
        ZCaption("Your company has quoted these — one quote per job. Revise or withdraw it from My quotes.")
        for (quote in quotes) {
            ZCard(onClick = openQuotes) {
                ZBodyStrong(quote.job?.title ?: "Job", maxLines = 2)
                ZBody("Quoted by ${CompanyQuotesLogic.quotedBy(quote, userId)} — ${Money.format(quote.amountCents)}", tone = ZTextTone.SOFT)
                ZCaption(listOfNotNull(CompanyQuotesLogic.waitingState(quote), quote.createdAt?.let { CompanyQuotesLogic.sentAgo(it, now) }, quote.job?.code).joinToString(" · "), tone = ZTextTone.FAINT)
            }
        }
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

/// The composer's in-place rate change (`PATCH /api/company/rates/{id}`):
/// the refreshed rate map on success, else the message to show beside the
/// input. The host model swaps the map into its state, so the composer's
/// total reprices live.
object CompanyRates {
    sealed interface Outcome {
        data class Saved(val rates: Map<String, Int>) : Outcome
        data class Failed(val message: String) : Outcome
    }

    /// "120", "120.50", "120,50" → cents; null when it isn't a positive amount.
    fun parseDollars(text: String): Int? = text.trim().replace(',', '.').toDoubleOrNull()?.let { (it * 100).roundToInt() }?.takeIf { it > 0 }

    suspend fun save(client: ApiClient, categoryId: Int, cents: Int): Outcome = try {
        Outcome.Saved(client.send(CompanyEndpoints.updateRate(categoryId, cents)).hourlyRates)
    } catch (error: ApiError) {
        Outcome.Failed(if (error is ApiError.Validation) error.errors.first("hourly_rate_cents") ?: error.errors.message else error.userMessage)
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Outcome.Failed(error.userMessage)
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
    /// Changes this trade's rate in place (null = saved, else the error);
    /// null hides "Change" — members can't edit rates (org admins only).
    saveRate: (suspend (Int) -> String?)? = null,
    onDismiss: () -> Unit, submit: suspend (QuoteBody) -> String?,
) {
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    var lines by remember { mutableStateOf(QuoteLineLogic.drafts(existing, rateCents)) }
    var pricing by remember { mutableStateOf(QuotePricingKind.of(existing?.pricingType) ?: QuotePricingKind.FIXED) }
    var exclusions by remember { mutableStateOf(existing?.exclusions ?: "") }
    var days by remember { mutableStateOf(existing?.estimatedDays?.toString() ?: "") }
    var validUntil by remember { mutableStateOf(existing?.validUntil) }
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
    // "Change" edits the rate RIGHT HERE instead of sending the pro to the
    // Passport and losing the quote (field audit #13).
    var editingRate by remember { mutableStateOf(false) }
    var rateDraft by remember { mutableStateOf("") }
    var rateError by remember { mutableStateOf<String?>(null) }
    var savingRate by remember { mutableStateOf(false) }
    fun startRateEdit() {
        rateDraft = rateCents?.let { if (it % 100 == 0) (it / 100).toString() else "%.2f".format(java.util.Locale.US, it / 100.0) } ?: ""
        rateError = null; editingRate = true
    }

    val offeredWindows: List<CompanyWindow> = job.companyWindows ?: (job.availabilityWindows ?: emptyList()).map { CompanyWindow(it.date, it.window, true, 0) }
    val needsProposal = job.mode == "scheduled" && offeredWindows.isNotEmpty()
    val proposalMissing = needsProposal && (proposedKey == null || proposedKey == "flexible")
    val totalCents = QuoteLineLogic.total(lines)
    val problem = QuoteLineLogic.problem(lines, hours, proposalMissing)
    val canSubmit = !sending && problem == null && rateCents != null
    // A labour row follows hours × crew until someone types its quantity.
    val personHours = hours * crew
    LaunchedEffect(personHours) {
        lines = lines.map { if (it.followsTime) it.copy(quantity = if (personHours > 0) QuoteLineLogic.quantityText(personHours) else "") else it }
    }
    // A rate saved here reprices rows still following it.
    LaunchedEffect(rateCents) {
        if (rateCents != null) lines = lines.map { if (it.kind == QuoteLineKind.LABOUR && it.followsTime) it.copy(unit = QuoteLineLogic.dollarsText(rateCents)) else it }
    }

    QuotePage(onDismiss = onDismiss, title = if (existing == null) "Send your quote to ZiVETT" else "Revise your quote") {
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

        if (editingRate && saveRate != null) {
            // In-place rate edit: the quote below stays as typed.
            ZCard(padding = ZSpacing.sm) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ZBodyStrong("Your ${job.category.name} rate", modifier = Modifier.weight(1f))
                    ZTextField("", rateDraft, { rateDraft = it; rateError = null }, modifier = Modifier.width(110.dp), placeholder = "120", keyboardType = KeyboardType.Decimal,
                        corner = null)
                    ZCaption("/hr", tone = ZTextTone.SOFT)
                }
                rateError?.let { ZCaption(it, color = colors.danger) }
                    ?: ZCaption("Applies to all your ${job.category.name.lowercase()} quotes from now on — quotes already sent keep their price.", tone = ZTextTone.FAINT)
                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                    Spacer(Modifier.weight(1f))
                    ZButton("Cancel", style = ZButtonStyle.GHOST, compact = true, fullWidth = false, enabled = !savingRate) { editingRate = false }
                    ZButton("Save rate", compact = true, fullWidth = false, loading = savingRate) {
                        val cents = CompanyRates.parseDollars(rateDraft)
                        if (cents == null) { rateError = "Enter your hourly rate."; return@ZButton }
                        scope.launch {
                            savingRate = true
                            val failure = saveRate(cents)
                            savingRate = false
                            if (failure == null) editingRate = false else rateError = failure
                        }
                    }
                }
            }
        }

        if (rateCents != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ZHeadline("Your price", modifier = Modifier.weight(1f))
                ZCaption("${job.category.name} rate ${Money.format(rateCents)}/hr")
                if (saveRate != null && !editingRate) ZTextAction("Change") { startRateEdit() }
            }
            ZHeadline("Line items")
            lines.forEach { line ->
                key(line.id) {
                    QuoteLineEditor(
                        line,
                        change = { updated -> lines = lines.map { if (it.id == line.id) updated else it } },
                        remove = { lines = lines.filterNot { it.id == line.id } },
                    )
                }
            }
            ZButton("+ Add line item", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { lines = lines + QuoteLineDraft() }
            Stepper("Estimated hours", if (hours == 0.0) "—" else hours.hoursLabel(), down = { hours = maxOf(0.0, ((hours - 0.5) * 2).roundToInt() / 2.0) }, up = { hours = minOf(100.0, maxOf(0.5, ((hours + 0.5) * 2).roundToInt() / 2.0)) })
            Stepper("Crew", crew.toString(), down = { crew = maxOf(1, crew - 1) }, up = { crew = minOf(20, crew + 1) })
            ZCard(padding = ZSpacing.sm) {
                MoneyRow("Quote total", totalCents)
                MoneyRow("Commission (${CompanyPresentation.percent(commissionBps)})", -CompanyPresentation.commission(totalCents, commissionBps))
                ZDivider()
                MoneyRow("You keep", totalCents - CompanyPresentation.commission(totalCents, commissionBps), strong = true, color = colors.success)
            }
        } else {
            ZBanner("Set your ${job.category.name} hourly rate${if (saveRate != null) "" else " on your Passport"} first — quotes are priced from it.", tone = ZTone.WARNING)
            if (saveRate != null && !editingRate) ZTextAction("Set your ${job.category.name.lowercase()} rate") { startRateEdit() }
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

        ZHeadline("How firm is this price?")
        QuotePricingKind.entries.forEach { kind ->
            val chosen = pricing == kind
            Row(
                modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(ZRadius.card)).background(colors.surface)
                    .border(1.dp, if (chosen) colors.navy else colors.border, androidx.compose.foundation.shape.RoundedCornerShape(ZRadius.card))
                    .clickable { pricing = kind }.padding(ZSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs),
            ) {
                androidx.compose.material3.RadioButton(selected = chosen, onClick = { pricing = kind })
                Column(modifier = Modifier.weight(1f)) {
                    ZBodyStrong(kind.label)
                    ZCaption(kind.detail)
                }
            }
        }
        ZTextField("Days on site (optional)", days, { days = it.filter(Char::isDigit) }, keyboardType = KeyboardType.Number)
        ZLabel("Valid until")
        ZCaption(validUntil ?: "14 days from today")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(7L, 14L, 30L).forEach { count ->
                ZButton("$count days", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { validUntil = QuoteLineLogic.dateText(count) }
            }
        }
        ZTextArea("What's not included (optional)", exclusions, { exclusions = it }, placeholder = "Drywall repair, painting, hauling the old unit away…", minLines = 3)
        ZTextArea("Note to the customer (optional)", message, { message = it }, placeholder = "Anything that helps them choose you", minLines = 3)
        ZCaption("The booker compares quotes and accepts one — acceptance assigns you the job and unlocks the exact address and access notes.", tone = ZTextTone.FAINT)

        // Payouts unready: the primary action IS going to set them up.
        if (!payoutsReady && setupPayouts != null) {
            ZButton("Set up payouts →", style = ZButtonStyle.SUCCESS, onClick = setupPayouts)
        } else {
            ZButton(problem ?: if (existing == null) "Send to ZiVETT" else "Save changes", style = ZButtonStyle.SUCCESS, loading = sending, enabled = canSubmit) {
                scope.launch {
                    sending = true; error = null
                    var date: String? = null; var window: String? = null
                    // Instant jobs forbid a proposal server-side — never send one.
                    val key = proposedKey
                    if (key != null && key != "flexible" && job.mode != "instant") {
                        val parts = key.split("|")
                        date = parts.firstOrNull(); window = parts.getOrNull(1)
                    }
                    val result = submit(
                        QuoteBody(
                            hours, crew, message.ifEmpty { null }, date, window,
                            lineItems = QuoteLineLogic.body(lines),
                            pricingType = pricing.value,
                            exclusions = exclusions.ifEmpty { null },
                            estimatedDays = days.toIntOrNull(),
                            validUntil = validUntil,
                        ),
                    )
                    sending = false
                    if (result != null) error = result else onDismiss()
                }
            }
        }
    }
}

/// A full screen rather than a sheet: a quote is too much to build in one.
@Composable
private fun QuotePage(onDismiss: () -> Unit, title: String, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(ZTheme.colors.cream).statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = ZSpacing.gutter).padding(bottom = ZSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(ZSpacing.md),
        ) {
            ZTextAction("Cancel", onClick = onDismiss)
            ZTitle(title)
            content()
        }
    }
}

@Composable
private fun QuoteLineEditor(line: QuoteLineDraft, change: (QuoteLineDraft) -> Unit, remove: () -> Unit) {
    val colors = ZTheme.colors
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(ZRadius.card)
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.border, shape).padding(ZSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            ZTextAction("${line.kind.label} ▾") { menuOpen = true }
            androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                QuoteLineKind.entries.forEach { kind ->
                    androidx.compose.material3.DropdownMenuItem(text = { Text(kind.label) }, onClick = { change(line.copy(kind = kind)); menuOpen = false })
                }
            }
        }
        ZTextField("", line.description, { change(line.copy(description = it)) }, placeholder = "Describe this line", corner = null)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ZTextField(if (line.kind == QuoteLineKind.LABOUR) "Hours" else "Qty", line.quantity, { change(line.copy(quantity = it, followsTime = false)) }, modifier = Modifier.width(76.dp), keyboardType = KeyboardType.Decimal)
            ZTextField("Price $", line.unit, { change(line.copy(unit = it)) }, modifier = Modifier.width(96.dp), keyboardType = KeyboardType.Decimal)
            Spacer(Modifier.weight(1f))
            val cents = QuoteLineLogic.cents(line)
            ZBodyStrong((if (cents < 0) "−" else "") + Money.format(kotlin.math.abs(cents)))
            IconButton(onClick = remove, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, contentDescription = "Remove this line", tint = colors.inkMuted) }
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
