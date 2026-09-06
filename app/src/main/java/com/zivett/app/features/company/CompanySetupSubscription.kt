package com.zivett.app.features.company

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zivett.app.app.Areas
import com.zivett.app.app.CredentialGuideRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.OrgProfileRoute
import com.zivett.app.app.PassportRoute
import com.zivett.app.app.SubscriptionRoute
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.auth.AuthSession
import com.zivett.app.core.models.BillingContext
import com.zivett.app.core.models.BusinessEndpoints
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.CompanyOrganizationResponse
import com.zivett.app.core.models.CompanySetup
import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.models.SubscriptionResponse
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.payments.StripeBridge
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZCheckLine
import com.zivett.app.design.ZDisplay
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZIconTile
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZMonoBody
import com.zivett.app.design.ZMonoLarge
import com.zivett.app.design.ZNavRow
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZPlanTag
import com.zivett.app.design.ZRadius
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSectionHeader
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.features.shared.LocalNav
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class CompanySetupModel(private val client: ApiClient) {
    var state by mutableStateOf<Loadable<CompanyOrganizationResponse>>(Loadable.Loading)
    var toast by mutableStateOf<String?>(null)
    var submitting by mutableStateOf(false)
    var justSubmitted by mutableStateOf(false)

    suspend fun load() { state = state.reloaded { client.send(CompanyEndpoints.organization()) } }

    suspend fun submit(session: AuthSession) {
        submitting = true
        try {
            state = Loadable.Loaded(client.send(CompanyEndpoints.submitOrganization()))
            justSubmitted = true
            // The shell keys off organization.submitted_at.
            runCatching { session.refreshUser() }
        } catch (e: Exception) { toast = e.userMessage } finally { submitting = false }
    }

    companion object {
        /// What still blocks submission, named — the web wizard's "Needs
        /// attention" line. Empty = complete, submit unlocks.
        fun missingSummary(steps: CompanySetup.Steps): List<String> {
            val missing = mutableListOf<String>()
            missing += steps.profile.missing ?: (if (steps.profile.complete) emptyList() else listOf("business profile"))
            missing += steps.details.missing ?: (if (steps.details.complete) emptyList() else listOf("services & rates"))
            if (!steps.credentials.complete) {
                val uploaded = steps.credentials.uploaded ?: 0
                val total = steps.credentials.total ?: 4
                missing += "${total - uploaded} of $total required documents"
            }
            return missing
        }
    }
}

/// The company setup flow: profile → services & rates → credentials →
/// plan → review & submit. Each step is a screen it links out to; this
/// page is the checklist + submit, like the web's review step.
@Composable
fun CompanySetupScreen(onBack: (() -> Unit)?) {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val model = remember { CompanySetupModel(environment.client) }
    LaunchedEffect(model) { model.load() }
    androidx.lifecycle.compose.LifecycleResumeEffect(model) { scope.launch { model.load() }; onPauseOrDispose { } }

    Column {
        ZTopBar("Setup", onBack = onBack)
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { response ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        if (model.justSubmitted) {
                            Icon(Icons.Filled.Verified, contentDescription = null, tint = colors.brandGold, modifier = Modifier.padding(top = ZSpacing.xl).size(40.dp))
                            ZMono("Application submitted")
                            ZDisplay("Your application is in review")
                            ZBody("Our team checks every company before it goes live — that review is what the ZiVETT badge means to bookers. You'll hear back by notification; approval opens the job feed.", tone = ZTextTone.SOFT)
                        } else {
                            ZPageTitle("Set up your business", "This is your application to become a Verified Pro. Finish the four steps and submit — our team takes it from there.")
                            SetupChecklist(response.setup)
                            ZCard(padding = 0.dp) {
                                ZNavRow("Business profile", "Name, address, phone — what customers see", onClick = { nav.navigate(OrgProfileRoute(Areas.COMPANY)) }) { ZIconTile(Icons.Outlined.Business) }
                                ZDivider(Modifier.padding(start = 60.dp))
                                ZNavRow("Services & rates", "Trades, hourly rates, radius, availability", onClick = { nav.navigate(PassportRoute) }) { ZIconTile(Icons.Outlined.Build) }
                                ZDivider(Modifier.padding(start = 60.dp))
                                ZNavRow("Credentials", "Photo ID, license, insurance, registration", onClick = { nav.navigate(PassportRoute) }) { ZIconTile(Icons.Outlined.UploadFile) }
                                ZDivider(Modifier.padding(start = 60.dp))
                                ZNavRow("What each document needs", "What a good upload shows, per document", onClick = { nav.navigate(CredentialGuideRoute) }) { ZIconTile(Icons.Outlined.Checklist) }
                                ZDivider(Modifier.padding(start = 60.dp))
                                ZNavRow("Plan", response.setup.steps.plan.planName?.let { "$it${if (response.setup.steps.plan.confirmed == true) "" else " (default)"}" } ?: "Choose your tier", onClick = { nav.navigate(SubscriptionRoute(Areas.COMPANY)) }) { ZIconTile(Icons.Outlined.CreditCard) }
                            }
                            // Submit is gated on completeness, like the web review step.
                            val missing = CompanySetupModel.missingSummary(response.setup.steps)
                            if (missing.isNotEmpty()) ZBanner("Needs attention — ${missing.joinToString(", ")}.", tone = ZTone.WARNING)
                            if (environment.session.user?.organizationRole == OrganizationRole.ADMIN) {
                                ZButton("Submit application", style = ZButtonStyle.SUCCESS, loading = model.submitting, enabled = missing.isEmpty()) { scope.launch { model.submit(environment.session) } }
                            } else ZCaption("Only organization admins can submit the application.")
                        }
                        Spacer(Modifier.padding(ZSpacing.lg))
                    }
                }
            }
        }
    }
}

/// The per-document upload guide (`fieldGuide.credentials` on the web).
@Composable
fun CredentialGuideScreen(onBack: () -> Unit) {
    Column {
        ZTopBar("Document guide", onBack = onBack)
        ZScreen {
            ZPageTitle("Document guide", "What our review team looks for in each upload — get these right and approval is fast.")
            for (guide in CredentialFieldGuide.entries) {
                ZCard {
                    ZBodyStrong(guide.title)
                    ZCaption(guide.purpose)
                    ZLabel("A good upload shows")
                    for (item in guide.checklist) ZCheckLine(item)
                    ZCaption(guide.formats, tone = ZTextTone.FAINT)
                    guide.fallback?.let { ZCaption(it, tone = ZTextTone.FAINT) }
                }
            }
            Spacer(Modifier.padding(ZSpacing.lg))
        }
    }
}

/// Which shelf the screen sells — company tiers or the business
/// membership. Same contract machine and payload shape server-side.
enum class SubscriptionArea {
    COMPANY, BUSINESS;

    fun show(): ApiRequest<SubscriptionResponse> = if (this == COMPANY) CompanyEndpoints.subscription() else BusinessEndpoints.subscription()
    fun update(planId: Int, interval: String) = if (this == COMPANY) CompanyEndpoints.updateSubscription(planId, interval) else BusinessEndpoints.updateSubscription(planId, interval)
    fun cancelPending() = if (this == COMPANY) CompanyEndpoints.cancelPendingSubscription() else BusinessEndpoints.cancelPendingSubscription()
    fun billingContext() = if (this == COMPANY) CompanyEndpoints.orgBillingContext() else BusinessEndpoints.orgBillingContext()
    fun saveBillingCard(setupIntentId: String) = if (this == COMPANY) CompanyEndpoints.saveOrgBillingCard(setupIntentId) else BusinessEndpoints.saveOrgBillingCard(setupIntentId)
}

class SubscriptionModel(private val client: ApiClient, val area: SubscriptionArea = SubscriptionArea.COMPANY, private val context: android.content.Context? = null) {
    /// The confirmation the change deserves: a scheduled downgrade leaves
    /// the cards looking unchanged, so the outcome has to say itself.
    data class ChangeResult(val planKey: String, val planName: String, val interval: String, val scheduled: Boolean)

    var state by mutableStateOf<Loadable<SubscriptionResponse>>(Loadable.Loading)
    /// The org's billing card context (stripe driver) — the standalone panel reads the saved card off it.
    var billing by mutableStateOf<BillingContext?>(null)
    var toast by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var cardBusy by mutableStateOf(false)
    var changeResult by mutableStateOf<ChangeResult?>(null)

    suspend fun load() {
        state = state.reloaded { client.send(area.show()) }
        billing = runCatching { client.send(area.billingContext()) }.getOrNull()
    }

    /// View or replace the card on file, outside any purchase — the web's Billing-card panel.
    suspend fun updateCard() {
        val context = context ?: return
        val loaded = billing ?: runCatching { client.send(area.billingContext()) }.getOrNull() ?: return
        cardBusy = true
        try {
            val key = loaded.publishableKey; val secret = loaded.clientSecret
            if (key == null || secret == null) { toast = "Card setup isn't available right now — try again in a moment."; return }
            StripeBridge.configure(context, key)
            val intentId = StripeBridge.collectCard(secret) ?: return
            client.send(area.saveBillingCard(intentId))
            toast = "Billing card saved"
            // A fresh SetupIntent for the next edit.
            billing = runCatching { client.send(area.billingContext()) }.getOrNull()
        } catch (e: Exception) { toast = e.userMessage } finally { cardBusy = false }
    }

    /// One gesture, minimal friction: choosing a PAID plan with no org
    /// billing card on file collects the card via PaymentSheet right
    /// here — saving it to billing AND committing the plan in one go.
    suspend fun choose(plan: SubscriptionResponse.Plan, interval: String) {
        busy = true
        try {
            if (!plan.isFree) {
                val billingContext = client.send(area.billingContext())
                if (billingContext.driver == "stripe" && billingContext.savedCard == null) {
                    val key = billingContext.publishableKey; val secret = billingContext.clientSecret; val context = context
                    if (key == null || secret == null || context == null) { toast = "Card setup isn't available right now — try again in a moment."; return }
                    StripeBridge.configure(context, key)
                    val intentId = StripeBridge.collectCard(secret) ?: return // cancelled — nothing committed
                    client.send(area.saveBillingCard(intentId))
                    billing = runCatching { client.send(area.billingContext()) }.getOrNull()
                }
            }
            val response = client.send(area.update(plan.id, interval))
            state = Loadable.Loaded(response)
            changeResult = ChangeResult(plan.key, plan.name, interval, scheduled = response.subscription.pending?.planId == plan.id)
        } catch (e: Exception) { toast = e.userMessage } finally { busy = false }
    }

    suspend fun cancelPending() {
        busy = true
        try { state = Loadable.Loaded(client.send(area.cancelPending())); changeResult = null; toast = "Scheduled change cancelled" } catch (e: Exception) { toast = e.userMessage } finally { busy = false }
    }
}

private fun tagLabel(key: String): String? = when (key) { "pro", "elite" -> key.uppercase(); "business_premium" -> "PREMIUM"; else -> null }

private fun chargeStatus(status: String): Pair<String, ZTone> = when (status) {
    "paid" -> "Paid" to ZTone.SUCCESS
    "pending" -> "Due" to ZTone.WARNING
    "waived" -> "Waived" to ZTone.NEUTRAL
    else -> status.replaceFirstChar { it.uppercase() } to ZTone.NEUTRAL
}

/// The yearly toggle's "Save N%" hint, derived from the real prices.
private fun yearlySavingsPercent(plans: List<SubscriptionResponse.Plan>): Int? =
    plans.filter { it.priceCents > 0 && it.yearlyPriceCents > 0 }.map { ((1 - it.yearlyPriceCents.toDouble() / (it.priceCents * 12)) * 100).roundToInt() }.maxOrNull()

@Composable
fun SubscriptionScreen(area: String, onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val shelf = if (area == Areas.BUSINESS) SubscriptionArea.BUSINESS else SubscriptionArea.COMPANY
    val model = remember(shelf) { SubscriptionModel(environment.client, shelf, context) }
    var yearly by remember { mutableStateOf(false) }
    val canManage = environment.session.user?.organizationRole == OrganizationRole.ADMIN
    LaunchedEffect(model) { model.load() }

    Column {
        ZTopBar("Subscription", onBack = onBack)
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { response ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        ZPageTitle("Subscription")
                        response.plans.firstOrNull { it.id == response.subscription.planId }?.let { current ->
                            ZCard {
                                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically) { ZHeadline(current.name); tagLabel(current.key)?.let { ZPlanTag(it) } }
                                ZBody(if (current.isFree) "Free" else if (response.subscription.interval == "yearly" || current.priceCents == 0) "${Money.format(current.yearlyPriceCents)}/year, billed yearly" else "${Money.format(current.priceCents)}/month, billed monthly", tone = ZTextTone.SOFT)
                                val start = response.subscription.termStartedAt; val end = response.subscription.termEndsAt
                                if (start != null && end != null) {
                                    ZCaption("Current term: ${Dates.short(start)} — ${Dates.short(end)}")
                                    response.subscription.renewalCents?.let { ZCaption("Renews automatically for another 12 months: ${Money.format(it)}") }
                                }
                                response.subscription.pending?.let { pending ->
                                    ZBanner("Scheduled: moves to ${pending.planName ?: "a new plan"}${pending.interval?.let { " (billed $it)" } ?: ""} on ${response.subscription.termEndsAt?.let { Dates.short(it) } ?: "renewal"}.", tone = ZTone.INFO)
                                    if (canManage) ZButton("Keep ${current.name}", style = ZButtonStyle.OUTLINE, compact = true, loading = model.busy, fullWidth = false) { scope.launch { model.cancelPending() } }
                                }
                            }
                        }

                        // The billing card subscriptions charge (stripe driver) — visible and replaceable outside any purchase.
                        model.billing?.takeIf { it.driver == "stripe" }?.let { billing ->
                            ZCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ZHeadline("Billing card", modifier = Modifier.weight(1f))
                                    if (canManage) ZTextAction(if (billing.savedCard == null) "Add billing card" else "Use a different card", enabled = !model.cardBusy) { scope.launch { model.updateCard() } }
                                }
                                val card = billing.savedCard
                                when {
                                    model.cardBusy -> ZSpinner()
                                    card != null -> Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.CreditCard, contentDescription = null, tint = colors.ink); ZBody("${(card.brand ?: "Card").replaceFirstChar { it.uppercase() }} •••• ${card.last4 ?: ""}") }
                                    else -> ZCaption(if (shelf == SubscriptionArea.COMPANY) "Add a card so your plan can bill — every tier is a paid contract." else "Add a card so Premium can bill — Basic stays free, no card needed.")
                                }
                                if (billing.publishableKey?.startsWith("pk_test_") == true) ZCaption("Test mode — use card 4242 4242 4242 4242 with any future expiry and CVC.", tone = ZTextTone.FAINT)
                            }
                        }

                        ZCaption("Every plan runs as a 12-month contract. Upgrades start a new term right away; moving to a lower tier or changing how you're billed takes effect at your renewal date.")
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(selected = !yearly, onClick = { yearly = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Monthly") }
                            SegmentedButton(selected = yearly, onClick = { yearly = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Yearly") }
                        }
                        yearlySavingsPercent(response.plans)?.let { ZCaption("Pay yearly and save $it% — ten months' price for the full year.", tone = ZTextTone.SOFT) }

                        for (plan in response.plans) {
                            val isCurrent = SubscriptionPresentation.isCurrent(plan, response.subscription, yearly)
                            val isPending = response.subscription.pending?.planId == plan.id
                            ZCard(modifier = if (isCurrent) Modifier.border(2.dp, colors.navy, RoundedCornerShape(ZRadius.card)) else Modifier) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ZHeadline(plan.name, modifier = Modifier.weight(1f))
                                    if (isCurrent) ZBadge("Current plan", ZTone.SUCCESS) else if (plan.id == response.subscription.planId) ZBadge("Billed ${response.subscription.interval}", ZTone.NEUTRAL)
                                    if (isPending) ZBadge("Scheduled", ZTone.INFO)
                                }
                                ZMonoLarge(if (plan.isFree) "Free" else if (plan.priceCents == 0 || yearly) "${Money.format(plan.yearlyPriceCents)}/yr" else "${Money.format(plan.priceCents)}/mo")
                                if (yearly && plan.priceCents > 0 && plan.priceCents * 12 > plan.yearlyPriceCents) ZCaption("Save ${Money.format(plan.priceCents * 12 - plan.yearlyPriceCents)} a year", tone = ZTextTone.SOFT)
                                plan.blurb?.let { ZCaption(it) }
                                plan.commissionBps?.let { ZCaption("${CompanyPresentation.percent(it)} commission — only on completed jobs", tone = ZTextTone.SOFT) }
                                for (feature in plan.features ?: emptyList()) ZCheckLine(feature)
                                if (!isCurrent) {
                                    ZButton(SubscriptionPresentation.actionLabel(plan, response.plans, response.subscription, yearly), style = ZButtonStyle.OUTLINE, compact = true, loading = model.busy, fullWidth = false, enabled = canManage && !isPending) {
                                        scope.launch { model.choose(plan, SubscriptionPresentation.intervalFor(plan, yearly)) }
                                    }
                                }
                            }
                        }
                        if (!canManage) ZCaption("Only organization admins can change the subscription.")
                        if (shelf == SubscriptionArea.COMPANY && response.subscription.featured == true) ZBodyStrong("✓ Your company is currently featured on the ZiVETT homepage.", color = colors.success)

                        response.charges?.takeIf { it.isNotEmpty() }?.let { charges ->
                            ZSectionHeader("Billing history")
                            for (charge in charges) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        ZBody(charge.plan ?: "Subscription")
                                        val s = charge.periodStart; val e = charge.periodEnd
                                        if (s != null && e != null) ZCaption("${Dates.short(s)} – ${Dates.short(e)}", tone = ZTextTone.FAINT)
                                    }
                                    val (label, tone) = chargeStatus(charge.status)
                                    ZBadge(label, tone)
                                    ZMonoBody(Money.format(charge.amountCents))
                                }
                            }
                        }
                        response.changes?.takeIf { it.isNotEmpty() }?.let { changes ->
                            ZSectionHeader("Plan history")
                            for (change in changes) ZCaption("${(change.from ?: "Basic").replaceFirstChar { it.uppercase() }} → ${change.to.replaceFirstChar { it.uppercase() }}${change.interval?.let { " · billed $it" } ?: ""}${change.at?.let { " · ${Dates.short(it)}" } ?: ""}")
                        }
                        Spacer(Modifier.padding(ZSpacing.lg))
                    }
                }
            }
        }
    }

    model.changeResult?.let { result -> ChangeResultSheet(model, result, shelf) }
}

/// "Plan upgraded" vs "Change scheduled" — a scheduled downgrade leaves
/// the plan cards looking unchanged, so the outcome states itself, with
/// a way out of a scheduled move.
@Composable
private fun ChangeResultSheet(model: SubscriptionModel, result: SubscriptionModel.ChangeResult, shelf: SubscriptionArea) {
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val renewal = model.state.value?.subscription?.termEndsAt
    val currentName = model.state.value?.let { r -> r.plans.firstOrNull { it.id == r.subscription.planId }?.name }
    ZSheet(onDismiss = { model.changeResult = null }) {
        Icon(if (result.scheduled) Icons.Outlined.EventAvailable else Icons.Filled.Verified, contentDescription = null, tint = colors.brandGold, modifier = Modifier.size(40.dp))
        ZMono(if (result.scheduled) "Locked in for renewal" else "Active now")
        ZDisplay(if (result.scheduled) "${result.planName} starts ${renewal?.let { Dates.short(it) } ?: "at your renewal"}" else "You're on ${result.planName}")
        ZBody(if (result.scheduled) "You keep everything ${currentName ?: "your current plan"} includes until then, and you can cancel the switch any time before it lands." else SubscriptionPresentation.upgradeMessage(result.planKey, result.planName, result.interval, shelf == SubscriptionArea.COMPANY), tone = ZTextTone.SOFT)
        ZButton("Done") { model.changeResult = null }
        if (result.scheduled) ZButton("Keep ${currentName ?: "current plan"}", style = ZButtonStyle.OUTLINE, loading = model.busy) { scope.launch { model.cancelPending() } }
    }
}
