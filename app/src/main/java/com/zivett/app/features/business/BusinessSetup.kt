package com.zivett.app.features.business

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Business
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zivett.app.app.Areas
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.OrgProfileRoute
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.auth.AuthSession
import com.zivett.app.core.models.BusinessEndpoints
import com.zivett.app.core.models.BusinessSetup
import com.zivett.app.core.models.SubscriptionResponse
import com.zivett.app.core.models.Team
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
import com.zivett.app.design.ZCheckLine
import com.zivett.app.design.ZDisplay
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZIconTile
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZNavRow
import com.zivett.app.design.ZRadius
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.design.ZType
import com.zivett.app.features.company.SubscriptionArea
import com.zivett.app.features.company.SubscriptionModel
import com.zivett.app.features.company.SubscriptionPresentation
import com.zivett.app.features.shared.LocalNav
import kotlinx.coroutines.launch

class BusinessSetupModel(private val client: ApiClient) {
    var state by mutableStateOf<Loadable<BusinessSetup>>(Loadable.Loading)
    var step by mutableStateOf(BusinessSetupSteps.Step.PROFILE)
    var finished by mutableStateOf(false)
    var completing by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    private var landed = false

    suspend fun load() {
        state = state.reloaded { client.send(BusinessEndpoints.organization()).setup }
        val setup = state.value
        if (!landed && setup != null) { step = BusinessSetupSteps.firstIncomplete(setup); landed = true }
    }

    /// Silent re-learn after a save/add elsewhere — the stepper reads
    /// completeness from the setup payload, so it has to move with the data.
    suspend fun refresh() {
        runCatching { client.send(BusinessEndpoints.organization()).setup }.getOrNull()?.let { state = Loadable.Loaded(it) }
    }

    fun next() { step = BusinessSetupSteps.Step.entries.getOrNull(step.index + 1) ?: step }
    fun back() { step = BusinessSetupSteps.Step.entries.getOrNull(step.index - 1) ?: step }
    fun jump(target: BusinessSetupSteps.Step) { val setup = state.value ?: return; if (BusinessSetupSteps.canJump(target, step, setup)) step = target }

    /// Skip and finish both stamp `setup_completed_at` — the wizard is a
    /// helper, not a gate, and it should never nag twice. The session
    /// refresh is what lifts the shell's gate.
    suspend fun complete(session: AuthSession): Boolean {
        completing = true
        try { client.send(BusinessEndpoints.completeSetup()); runCatching { session.refreshUser() }; return true } catch (e: Exception) { toast = e.userMessage } finally { completing = false }
        return false
    }
}

/// The business setup wizard: profile → properties → team → plan, the
/// web's `/business/setup` flow. Pushed full-screen to fresh org admins
/// from the shell and from the overview's "Resume setup" for skippers.
@Composable
fun BusinessSetupScreen(onDismiss: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember { BusinessSetupModel(environment.client) }
    LaunchedEffect(model) { model.load() }
    // Fires again when a pushed editor (the profile form) pops — re-learn completeness.
    LifecycleResumeEffect(model) { scope.launch { model.refresh() }; onPauseOrDispose { } }

    fun skip() { scope.launch { if (model.complete(environment.session)) onDismiss() } }

    Column {
        ZTopBar("Set up your account", onBack = onDismiss)
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { setup ->
                    if (model.finished) {
                        Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                            Icon(Icons.Filled.Verified, contentDescription = null, tint = ZTheme.colors.brandGold, modifier = Modifier.padding(top = ZSpacing.xl).size(40.dp))
                            ZMono("Setup complete")
                            ZDisplay("You're all set — welcome to ZiVETT")
                            ZBody("Your team can now book vetted pros for any property, compare quotes, and keep every repair's paper trail in one place.", tone = ZTextTone.SOFT)
                            ZButton("Book your first request", onClick = onDismiss)
                            ZButton("Go to your overview", style = ZButtonStyle.OUTLINE, onClick = onDismiss)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                            Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                                ZDisplay("Every property covered, with ZiVETT")
                                ZBody("Book verified pros across your whole portfolio — tenant requests, compared quotes, and every repair's paper trail in one place.", tone = ZTextTone.SOFT)
                            }
                            Stepper(model, setup)
                            BusinessSetupSteps.whatsLeft(model.step, setup)?.let { ZCaption("Needs attention — $it", color = ZTheme.colors.danger) }
                            when (model.step) {
                                BusinessSetupSteps.Step.PROFILE -> ProfileStep(model, ::skip)
                                BusinessSetupSteps.Step.PROPERTIES -> PropertiesStep(model, ::skip)
                                BusinessSetupSteps.Step.TEAM -> TeamStep(model)
                                BusinessSetupSteps.Step.PLAN -> PlanStep(model, ::skip) { scope.launch { if (model.complete(environment.session)) model.finished = true } }
                            }
                            Spacer(Modifier.padding(ZSpacing.lg))
                        }
                    }
                }
            }
        }
    }
}

/// The truth-meter stepper: checks for done, a red ring + count for
/// skipped-incomplete, tappable under the same rule Continue uses.
@Composable
private fun Stepper(model: BusinessSetupModel, setup: BusinessSetup) {
    val colors = ZTheme.colors
    Row(modifier = Modifier.fillMaxWidth()) {
        for (step in BusinessSetupSteps.Step.entries) {
            val status = BusinessSetupSteps.status(step, model.step, setup)
            Column(modifier = Modifier.weight(1f).clickable { model.jump(step) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    when (status) {
                        BusinessSetupSteps.Status.DONE -> Box(Modifier.size(24.dp).clip(CircleShape).background(colors.success), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(12.dp)) }
                        BusinessSetupSteps.Status.CURRENT -> Box(Modifier.size(24.dp).clip(CircleShape).background(colors.navy), contentAlignment = Alignment.Center) { Box(Modifier.size(7.dp).clip(CircleShape).background(colors.onBrand)) }
                        BusinessSetupSteps.Status.WARN -> Box(Modifier.size(24.dp).border(2.dp, colors.danger, CircleShape), contentAlignment = Alignment.Center) { Text(maxOf(BusinessSetupSteps.completeness(step, setup).missing.size, 1).toString(), style = ZType.label.copy(fontSize = 11.sp), color = colors.danger) }
                        BusinessSetupSteps.Status.TODO -> Box(Modifier.size(24.dp).border(2.dp, colors.borderStrong, CircleShape))
                    }
                }
                Text(step.title, style = ZType.caption.copy(fontSize = 11.sp, fontWeight = if (status == BusinessSetupSteps.Status.CURRENT) FontWeight.Bold else FontWeight.Medium), color = if (status == BusinessSetupSteps.Status.CURRENT) colors.ink else colors.inkMuted, textAlign = TextAlign.Center)
            }
        }
    }
}

/* Step 1 — the org profile. The form itself is the shared editor
   (pushed, it saves on its own); Continue re-checks completeness. */
@Composable
private fun ProfileStep(model: BusinessSetupModel, skip: () -> Unit) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var tried by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val missing = model.state.value?.let { BusinessSetupSteps.completeness(BusinessSetupSteps.Step.PROFILE, it).missing } ?: emptyList()
    ZCard {
        ZHeadline("Tell us about your organization")
        ZCaption("Right now your account is named after you. This is the organization your team and the pros working your requests will see — you can change any of it later.")
        if (tried && missing.isNotEmpty()) ZBanner("Still needed before you can continue: ${missing.joinToString(", ")}.", tone = ZTone.DANGER)
        ZNavRow("Business profile", if (missing.isEmpty()) "Complete — edit anytime" else "Still needed: ${missing.joinToString(", ")}", onClick = { nav.navigate(OrgProfileRoute(Areas.BUSINESS)) }) { ZIconTile(Icons.Outlined.Business) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
        ZButton("Skip for now", style = ZButtonStyle.GHOST, loading = model.completing, fullWidth = false, onClick = skip)
        Spacer(Modifier.weight(1f))
        ZButton(if (checking) "Checking…" else "Continue", loading = checking, fullWidth = false) {
            scope.launch {
                checking = true; model.refresh(); checking = false
                val setup = model.state.value
                if (setup != null && BusinessSetupSteps.completeness(BusinessSetupSteps.Step.PROFILE, setup).complete) model.next() else tried = true
            }
        }
    }
}

/* Step 2 — properties. One is enough; the wizard's Where step walls an
   empty portfolio later, so skipping is allowed but the stepper stays honest. */
@Composable
private fun PropertiesStep(model: BusinessSetupModel, skip: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val properties = remember { PropertiesModel(environment.client) }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(properties) { properties.load() }
    ZCard {
        ZHeadline("Add your properties")
        ZCaption("Every repair request starts from a property. Add the ones you manage — one is enough to get going, and you can always add more later.")
        for (property in properties.state.value ?: emptyList()) {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(18.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) { ZBodyStrong(property.name, maxLines = 1); ZCaption(listOfNotNull(property.kind, property.address).joinToString(" · "), maxLines = 1) }
                ZTextAction("Remove") { scope.launch { properties.delete(property); model.refresh() } }
            }
        }
        ZButton("+ Add property", style = ZButtonStyle.SOFT) { adding = true }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
        ZButton("Back", style = ZButtonStyle.OUTLINE, fullWidth = false) { model.back() }
        ZButton("Skip for now", style = ZButtonStyle.GHOST, loading = model.completing, fullWidth = false, onClick = skip)
        Spacer(Modifier.weight(1f))
        ZButton("Continue", fullWidth = false) { model.next() }
    }
    if (adding) PropertyFormSheet(properties, null) { adding = false; scope.launch { properties.load(); model.refresh() } }
}

/* Step 3 — invite the team. Genuinely optional. */
@Composable
private fun TeamStep(model: BusinessSetupModel) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var inviting by remember { mutableStateOf(false) }
    var invitations by remember { mutableStateOf<List<Team.Invitation>>(emptyList()) }
    suspend fun loadInvitations() { invitations = runCatching { environment.client.send(BusinessEndpoints.team()) }.getOrNull()?.invitations?.filter { it.status == "pending" } ?: emptyList() }
    LaunchedEffect(Unit) { loadInvitations() }
    ZCard {
        ZHeadline("Invite your team")
        ZCaption("Teammates can raise and track repair requests across your whole portfolio. This step is optional — you can invite people from the Team page anytime.")
        ZTextField("Teammate's email", email, { email = it }, placeholder = "colleague@yourcompany.com", error = error, keyboardType = KeyboardType.Email)
        ZButton("Send invite", style = ZButtonStyle.SOFT, loading = inviting, fullWidth = false, enabled = email.isNotBlank()) {
            scope.launch {
                inviting = true; error = null
                try { environment.client.send(BusinessEndpoints.invite(email.trim())); email = ""; loadInvitations() }
                catch (e: ApiError) { error = e.first("email") ?: e.userMessage } catch (e: Exception) { error = e.userMessage } finally { inviting = false }
            }
        }
        for (invitation in invitations) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) { ZBodyStrong(invitation.email, maxLines = 1, modifier = Modifier.weight(1f)); ZBadge("Invited", ZTone.INFO) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
        ZButton("Back", style = ZButtonStyle.OUTLINE, fullWidth = false) { model.back() }
        Spacer(Modifier.weight(1f))
        ZButton("Continue", fullWidth = false) { model.next() }
    }
}

/* Step 4 — the membership pick, the wizard's last step. Basic is free,
   so completing here is choosing, not paying; a Premium pick collects the
   org billing card first (SubscriptionModel owns that price-gated flow). */
@Composable
private fun PlanStep(model: BusinessSetupModel, skip: () -> Unit, finish: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val subscription = remember { SubscriptionModel(environment.client, SubscriptionArea.BUSINESS, context) }
    var yearly by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SubscriptionResponse.Plan?>(null) }
    var tried by remember { mutableStateOf(false) }
    LaunchedEffect(subscription) { subscription.load() }
    val alreadyChosen = model.state.value?.let { BusinessSetupSteps.completeness(BusinessSetupSteps.Step.PLAN, it).complete } ?: false

    ZToastBox(subscription.toast, { subscription.toast = null }) {
        Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
            ZCard {
                ZHeadline("Pick your membership")
                ZCaption("Basic is free. Premium drops the trust & support fee, waives emergency call-out fees, and puts your jobs in front of pros first. Both run as 12-month terms — you can upgrade any time, and move down at renewal from your Subscription page.")
                if (tried && selected == null && !alreadyChosen) ZBanner("Pick a membership to finish — Basic is free, and you can change any time.", tone = ZTone.DANGER)
                val response = subscription.state.value
                if (response != null) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = !yearly, onClick = { yearly = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Monthly") }
                        SegmentedButton(selected = yearly, onClick = { yearly = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Yearly") }
                    }
                    for (plan in response.plans) {
                        val isSelected = selected?.id == plan.id
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.tile)).background(colors.surface).border(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.navy else colors.borderStrong, RoundedCornerShape(ZRadius.tile)).clickable { selected = plan }.padding(ZSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ZBodyStrong(plan.name, modifier = Modifier.weight(1f))
                                ZMono(if (plan.isFree) "Free" else if (yearly || plan.priceCents == 0) "${Money.format(plan.yearlyPriceCents)}/yr" else "${Money.format(plan.priceCents)}/mo")
                            }
                            plan.blurb?.let { ZCaption(it) }
                            for (feature in plan.features ?: emptyList()) ZCheckLine(feature)
                        }
                    }
                } else Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ZSpinner() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                ZButton("Back", style = ZButtonStyle.OUTLINE, fullWidth = false) { model.back() }
                ZButton("Skip for now", style = ZButtonStyle.GHOST, loading = model.completing, fullWidth = false, onClick = skip)
                Spacer(Modifier.weight(1f))
                ZButton("Finish setup", loading = subscription.busy || model.completing, fullWidth = false) {
                    scope.launch {
                        val plan = selected
                        if (plan == null) { if (alreadyChosen) finish() else tried = true; return@launch }
                        // choose() collects the org billing card first when the pick is paid and no card is on file.
                        subscription.choose(plan, SubscriptionPresentation.intervalFor(plan, yearly))
                        if (subscription.changeResult != null) { subscription.changeResult = null; model.refresh(); finish() }
                    }
                }
            }
        }
    }
}
