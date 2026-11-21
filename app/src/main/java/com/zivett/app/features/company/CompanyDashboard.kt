package com.zivett.app.features.company

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zivett.app.app.CompanySetupRoute
import com.zivett.app.app.AppEvents
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.OpportunitiesRoute
import com.zivett.app.app.PassportRoute
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.CompanyDashboard
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.CompanyReferral
import com.zivett.app.core.models.CompanySetup
import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZQRCode
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZStatTile
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZType
import com.zivett.app.features.customer.account.shareText
import com.zivett.app.features.customer.jobs.CompanyReviewsSheet
import com.zivett.app.features.customer.jobs.oneDecimal
import com.zivett.app.features.referrals.BankedCreditsPanel
import com.zivett.app.features.shared.LocalNav
import kotlinx.coroutines.launch

class CompanyDashboardModel(private val client: ApiClient) {
    var state by mutableStateOf<Loadable<CompanyDashboard>>(Loadable.Loading)
    var toast by mutableStateOf<String?>(null)
    var startingPayoutSetup by mutableStateOf(false)

    suspend fun load() { state = state.reloaded { client.send(CompanyEndpoints.dashboard()) } }

    suspend fun payoutOnboardingUrl(): String? {
        startingPayoutSetup = true
        try { return client.send(CompanyEndpoints.stripeOnboardingLink()).url } catch (e: Exception) { toast = e.userMessage } finally { startingPayoutSetup = false }
        return null
    }

    suspend fun refreshStripeStatus() {
        val ready = runCatching { client.send(CompanyEndpoints.refreshStripeStatus()) }.getOrNull()?.payoutsReady
        // Set up while the passport is still under review: nothing to
        // quote on until approval.
        val approved = state.value?.approved ?: true
        toast = if (ready == true) (if (approved) "Payouts are set up. You're ready to quote" else "Payouts are set up. You can quote as soon as you're approved") else "Stripe is still reviewing your details. This usually clears in a minute or two."
        load()
    }
}

/// Opens a web link (Stripe's hosted onboarding, directions) in the
/// browser. False when nothing on the device can — a phone with its
/// browser disabled used to crash here on ActivityNotFoundException.
fun openUrl(context: android.content.Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (_: android.content.ActivityNotFoundException) {
    android.widget.Toast.makeText(context, "No browser is available to open that link.", android.widget.Toast.LENGTH_LONG).show()
    false
}

@Composable
fun CompanyDashboardScreen() {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember { CompanyDashboardModel(environment.client) }
    var sentToStripe by remember { mutableStateOf(false) }
    var showingReviews by remember { mutableStateOf(false) }
    LaunchedEffect(model) { model.load() }
    // Back from Stripe's hosted onboarding in the browser → re-poll.
    LifecycleResumeEffect(sentToStripe) {
        // When the return link brought them back, the effect below does it.
        if (sentToStripe) { sentToStripe = false; if (!AppEvents.stripeReturned) scope.launch { model.refreshStripeStatus() } }
        onPauseOrDispose { }
    }
    // Stripe's return link (`return_to: "app"`) opened the app here —
    // works after a cold start too, where `sentToStripe` is long gone.
    // Consuming the flag re-keys this effect and would cancel it, so the
    // refresh runs on the screen's scope.
    val stripeReturned = AppEvents.stripeReturned
    LaunchedEffect(stripeReturned) {
        if (stripeReturned) { AppEvents.stripeReturned = false; scope.launch { model.refreshStripeStatus() } }
    }

    ZToastBox(model.toast, { model.toast = null }) {
        ZScreen(onRefresh = { model.load() }) {
            ZLoadable(model.state, retry = { scope.launch { model.load() } }) { dashboard ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    when {
                        dashboard.approved -> ApprovedContent(dashboard, model, onStripe = { url -> sentToStripe = openUrl(context, url) }, onReviews = { showingReviews = true })
                        dashboard.setup.status == "draft" -> {
                            ZPageTitle("Set up your business", "Finish your application and our team takes it from there.")
                            SetupChecklist(dashboard.setup)
                            ZButton("Continue setup") { nav.navigate(CompanySetupRoute) }
                        }
                        else -> SubmittedContent(dashboard, model, onStripe = { url -> sentToStripe = openUrl(context, url) })
                    }
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }

    if (showingReviews) environment.session.user?.organization?.let { org -> CompanyReviewsSheet(org.id, org.name) { showingReviews = false } }
}

/// Stripe payout setup. Offered from SUBMISSION (the server marks it
/// required then): under review it's the optional "while you wait" step,
/// so approval lets them quote at once; once approved it's the one thing
/// between them and quoting. Org admins only (the endpoints 403 members).
/// Web: PayoutSetupCard.
@Composable
private fun PayoutSetupCard(dashboard: CompanyDashboard, model: CompanyDashboardModel, onStripe: (String) -> Unit) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val isOrgAdmin = environment.session.user?.organizationRole == OrganizationRole.ADMIN
    if (!dashboard.stripePayouts.required || dashboard.stripePayouts.ready || !isOrgAdmin) return
    ZCard {
        if (dashboard.approved) {
            ZBodyStrong("Set up payouts to start quoting")
            ZCaption("Connect your bank through Stripe so accepted jobs can pay out to you. It takes a few minutes, and your details go to Stripe, never to ZiVETT.")
        } else {
            ZBodyStrong("Set up payouts while you wait")
            ZCaption("Connect your bank through Stripe now, and you can quote the moment your passport is approved. It takes a few minutes, and your details go to Stripe, never to ZiVETT.")
        }
        ZButton("Set up payouts", compact = true, loading = model.startingPayoutSetup, fullWidth = false) {
            scope.launch { model.payoutOnboardingUrl()?.let(onStripe) }
        }
    }
}

/// The post-submission stepper. The Payouts row is Stripe-only: payouts
/// can be set up during review, and quoting waits on it after approval.
internal fun applicationTimeline(uploaded: Int, total: Int, approvedDocs: Int, payouts: CompanyDashboard.StripePayouts): List<TimelineRow> = buildList {
    add(TimelineRow("Application submitted", "Your business profile is with our team.", TimelineRowState.DONE))
    add(TimelineRow("Credentials uploaded", "$uploaded of $total documents in (manage on your Passport page)", if (uploaded == total) TimelineRowState.DONE else TimelineRowState.CURRENT))
    add(TimelineRow("Passport review", if (approvedDocs > 0) "$approvedDocs of $total documents verified by our team." else "Identity, license & insurance verification by our team.", if (uploaded == total) TimelineRowState.CURRENT else TimelineRowState.TODO))
    if (payouts.required) {
        add(TimelineRow("Payouts", if (payouts.ready) "Stripe has verified your payout account." else "Needed before your first quote. You can set it up now.", if (payouts.ready) TimelineRowState.DONE else TimelineRowState.TODO))
    }
    add(TimelineRow("Approved & live", "Job opportunities unlock in your area.", TimelineRowState.TODO))
}

@Composable
private fun ApprovedContent(dashboard: CompanyDashboard, model: CompanyDashboardModel, onStripe: (String) -> Unit, onReviews: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val colors = ZTheme.colors
    ZPageTitle("Dashboard", environment.session.user?.organization?.name)

    PayoutSetupCard(dashboard, model, onStripe)

    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
        ZStatTile(dashboard.stats.opportunities.toString(), "New opportunities", Modifier.weight(1f))
        ZStatTile(dashboard.stats.activeJobs.toString(), "Active jobs", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
        // The rating tile opens the org's own reviews — the same sheet bookers see.
        ZStatTile(dashboard.stats.rating?.let { "${it.oneDecimal()}★" } ?: "None yet", "Customer rating", Modifier.weight(1f), onClick = onReviews)
        ZStatTile(Money.format(dashboard.stats.payoutPending), "Payout pending", Modifier.weight(1f))
    }

    ZCard(onClick = { nav.navigate(OpportunitiesRoute) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                ZBodyStrong("New opportunities")
                ZCaption("${dashboard.stats.opportunities} ${if (dashboard.stats.opportunities == 1) "job" else "jobs"} waiting for a pro right now.")
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.inkFaint)
        }
    }

    ZCard(onClick = { nav.navigate(PassportRoute) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ZBodyStrong("Verified Pro Passport")
                ZCaption("${environment.session.user?.organization?.name ?: "Your company"}: credentials verified and visible to customers.")
            }
            ZBadge("Verified", ZTone.SUCCESS)
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.inkFaint)
        }
    }

    CompanyReferralCard()
}

@Composable
private fun SubmittedContent(dashboard: CompanyDashboard, model: CompanyDashboardModel, onStripe: (String) -> Unit) {
    val setup = dashboard.setup
    val nav = LocalNav.current
    val colors = ZTheme.colors
    ZPageTitle("Your application")
    val docs = setup.steps.credentials.documents ?: emptyList()
    val rejected = docs.filter { it.status == "rejected" }
    if (rejected.isNotEmpty()) {
        ZCard {
            ZBodyStrong("Action needed on your documents", color = colors.danger)
            for (doc in rejected) ZCaption("${doc.label}: ${doc.rejectionReason ?: "needs another look"}")
            ZTextAction("Re-upload on your Passport") { nav.navigate(PassportRoute) }
        }
    }
    PayoutSetupCard(dashboard, model, onStripe)
    val uploaded = setup.steps.credentials.uploaded ?: 0
    val total = setup.steps.credentials.total ?: 4
    val approvedDocs = docs.count { it.status == "approved" }
    ZCard {
        Row(verticalAlignment = Alignment.CenterVertically) { ZHeadline("Under review", modifier = Modifier.weight(1f)); ZBadge("Pending review", ZTone.WARNING) }
        TimelineList(applicationTimeline(uploaded, total, approvedDocs, dashboard.stripePayouts))
    }
    ZCard {
        ZHeadline("Your documents")
        for (doc in docs) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZBody(doc.label, modifier = Modifier.weight(1f))
                val badge = CompanyPresentation.documentBadge(doc.status)
                ZBadge(badge.label, badge.tone)
            }
        }
        ZCaption("Need to update something? Upload a new version on your Passport page any time. Our team reviews the latest copy.")
    }
}

/// The 3-row draft checklist shared by the dashboard and setup review.
@Composable
fun SetupChecklist(setup: CompanySetup) {
    val colors = ZTheme.colors
    @Composable fun row(title: String, complete: Boolean, done: String, missing: List<String>?, detail: String? = null) {
        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.Top) {
            Icon(if (complete) Icons.Filled.CheckCircle else Icons.Filled.Error, contentDescription = null, tint = if (complete) colors.brandGold else colors.warning, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ZBodyStrong(title)
                if (complete || detail != null) ZCaption(detail ?: done)
                else if (!missing.isNullOrEmpty()) ZCaption("Still missing: ${missing.joinToString(", ")}")
            }
        }
    }
    ZCard {
        Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
            row("Business profile", setup.steps.profile.complete, "Name, address and phone are in.", setup.steps.profile.missing)
            row("Services & rates", setup.steps.details.complete, "Trades, rates, radius and availability are in.", setup.steps.details.missing)
            val docs = CredentialExpiry.credentialsDetail(setup.steps.credentials)
            row("Credentials", setup.steps.credentials.complete, docs, null, detail = docs)
        }
    }
}

enum class TimelineRowState { DONE, CURRENT, TODO }
data class TimelineRow(val title: String, val detail: String, val state: TimelineRowState)

@Composable
fun TimelineList(rows: List<TimelineRow>) {
    val colors = ZTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.Top) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(22.dp).clip(CircleShape).background(when (row.state) { TimelineRowState.DONE -> colors.brandGold; TimelineRowState.CURRENT -> colors.navy; TimelineRowState.TODO -> colors.surfaceSunken }),
                    contentAlignment = Alignment.Center,
                ) {
                    if (row.state == TimelineRowState.DONE) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.navyDeep, modifier = Modifier.size(12.dp))
                    else if (row.state == TimelineRowState.CURRENT) androidx.compose.foundation.layout.Box(Modifier.size(7.dp).clip(CircleShape).background(colors.onBrand))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.title, style = if (row.state == TimelineRowState.CURRENT) ZType.bodyStrong else ZType.body, color = if (row.state == TimelineRowState.TODO) colors.inkFaint else colors.ink)
                    ZCaption(row.detail)
                }
            }
        }
    }
}

/// Refer-a-pro card on the dashboard.
@Composable
fun CompanyReferralCard() {
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    val colors = ZTheme.colors
    var referral by remember { mutableStateOf<CompanyReferral?>(null) }
    LaunchedEffect(Unit) { referral = runCatching { environment.client.send(CompanyEndpoints.referral()) }.getOrNull() }
    val current = referral ?: return
    val reward = if (current.reward.mode == "percent") "${current.reward.bps / 100}% of their first invoice" else Money.format(current.reward.cents)
    ZCard {
        Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
            ZMono("Refer a pro")
            ZBody("Know a company that belongs on ZiVETT? Earn $reward when their first job is paid.")
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.Top) {
                // The QR is for showing another pro in person — scanning opens signup with the code.
                ZQRCode(current.link, 96.dp)
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                    Text(current.code, style = ZType.money.copy(fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp), color = colors.navy)
                    ZCaption("${current.stats.companiesJoined} ${if (current.stats.companiesJoined == 1) "company has" else "companies have"} joined${if (current.stats.rewardsEarnedCents > 0) " · ${Money.format(current.stats.rewardsEarnedCents)} earned all time" else ""}")
                }
            }
            ZButton("Share your link") { shareText(context, "Join ZiVETT as a verified pro. Use my code ${current.code}. ${current.link}") }
            // The wallet: credits ride out one invoice at a time, so the bank and the next credit differ.
            BankedCreditsPanel(current.stats.bankedCount, current.stats.pendingCents, current.stats.nextCreditCents, "tops up your next invoice's payout", "invoice")
        }
    }
}

@Suppress("unused")
private val keepFill: Modifier = Modifier.fillMaxWidth()
