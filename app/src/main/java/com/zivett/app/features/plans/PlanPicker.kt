package com.zivett.app.features.plans

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.models.SubscriptionResponse
import com.zivett.app.core.store.PlayStore
import com.zivett.app.core.store.StoreBillingPlan
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZCheckLine
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTitle
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.features.customer.account.LegalLinks
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/// One plan model per screen, backed by the live Play store.
@Composable
fun rememberPlanPickerModel(area: String): PlanPickerModel {
    val environment = LocalAppEnvironment.current
    val model = remember(area) { PlanPickerModel(area, environment.client, PlayStore) }
    LaunchedEffect(model) { model.load() }
    return model
}

/// The plan screen as its own page — More → Plan (company), Account →
/// Plan (business), the company setup checklist's Plan row. The business
/// setup wizard embeds `PlanPickerContent` as its first step instead.
@Composable
fun PlanPickerScreen(area: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val model = rememberPlanPickerModel(area)
    Column {
        ZTopBar("Plan", onBack = onBack)
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                PlanPickerContent(model)
                Spacer(Modifier.padding(ZSpacing.lg))
            }
        }
    }
    // Back from Google Play's subscription page: a change there reaches
    // the server by notification, so re-read.
    LifecycleResumeEffect(model) { scope.launch { model.load() }; onPauseOrDispose { } }
}

/// The plan shelf, shared by the page and the business setup wizard:
/// plan cards with Google Play prices, or — when a paid plan is already
/// running elsewhere — the plan itself, read-only. `onContinue` turns on
/// the wizard's Continue, enabled once a plan is chosen. The host owns
/// the toast (`model.toast`).
@Composable
fun PlanPickerContent(model: PlanPickerModel, onContinue: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    ZLoadable(model.state, retry = { scope.launch { model.load() } }) { response ->
        Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
            ZPageTitle("Choose your plan", subtitle(model.area))
            model.notice?.let { ZBanner(it, tone = ZTone.SUCCESS) }

            val canManage = response.subscription.canManage
            when (PlanShelf.mode(response)) {
                PlanShelf.Mode.BILLED_ELSEWHERE -> CurrentPlanCard(response, storeBilled = false)
                PlanShelf.Mode.STORE_BILLED -> {
                    CurrentPlanCard(response, storeBilled = true)
                    if (canManage) ZLabel("Change plan")
                    Shelf(model, canManage, switching = true)
                }
                PlanShelf.Mode.SHELF -> {
                    if (!canManage) ZBanner("Only organization admins can change the plan.", tone = ZTone.INFO)
                    Shelf(model, canManage, switching = false)
                }
            }

            if (onContinue != null) {
                val ready = PlanShelf.hasChosen(response)
                ZButton("Continue", enabled = ready, onClick = onContinue)
                if (!ready) ZCaption("Choose a plan to continue.")
            }

            if (canManage) {
                ZButton("Restore purchases", style = ZButtonStyle.GHOST, loading = model.restoring) { scope.launch { model.restore() } }
            }

            Legal()
        }
    }
}

private fun subtitle(area: String) = when (area) {
    "business" -> "Basic is free. Premium lowers your fees and puts your requests first."
    else -> "Pick how your business shows up on ZiVETT. Higher tiers keep more of every job."
}

private val longDate = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
private fun Instant.long(): String = atZone(ZoneId.systemDefault()).format(longDate)

// The running plan (Play-billed, or read-only).

@Composable
private fun CurrentPlanCard(response: SubscriptionResponse, storeBilled: Boolean) {
    val context = LocalContext.current
    val plan = PlanShelf.currentPlan(response)
    val store = response.subscription.store
    ZCard {
        ZLabel("Your plan")
        ZTitle(plan?.name ?: "Your plan")
        if (storeBilled) {
            store?.expiresAt?.let { ZCaption(if (store.autoRenew) "Renews ${it.long()}" else "Ends ${it.long()}") }
            if (store?.status == "billing_retry") {
                ZCaption("Google Play couldn't renew your plan. Update your payment method in Google Play to keep it.", tone = ZTextTone.INK)
            }
            Features(plan)
            if (response.subscription.canManage) {
                ZButton("Manage in Google Play", style = ZButtonStyle.OUTLINE) {
                    val url = PlanShelf.manageUrl(response.googlePlay?.packageName ?: context.packageName, store?.productId)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                ZCaption("Cancel or update your payment method in Google Play.")
            }
        } else {
            response.subscription.termEndsAt?.let { ZCaption("Your current term runs until ${it.long()}.") }
            Features(plan)
            ZCaption("This plan can't be changed in the app while its term is running.")
        }
    }
}

// The shelf.

@Composable
private fun Shelf(model: PlanPickerModel, canManage: Boolean, switching: Boolean) {
    if (model.storeUnavailable) {
        ZBanner("Google Play isn't responding right now, so paid plans can't be bought. Pull to refresh to try again.", tone = ZTone.WARNING)
    }
    for (card in model.cards) PlanCard(model, card, canManage, switching)
}

@Composable
private fun PlanCard(model: PlanPickerModel, card: PlanShelf.Card, canManage: Boolean, switching: Boolean) {
    val scope = rememberCoroutineScope()
    ZCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZTitle(card.plan.name, modifier = Modifier.weight(1f))
            if (card.isCurrent) ZBadge("Current plan", ZTone.SUCCESS)
        }
        card.plan.blurb?.let { ZCaption(it) }
        Features(card.plan)

        when {
            card.plan.isFree -> {
                ZBodyStrong("Free")
                ZCaption("Nothing to pay")
                // A Play-billed org drops to a free plan by cancelling in
                // Google Play, not by picking it here.
                if (canManage && !card.isCurrent && !switching) {
                    ZButton("Choose ${card.plan.name}", style = ZButtonStyle.OUTLINE, loading = model.working == "${card.plan.id}-free") {
                        scope.launch { model.chooseFree(card) }
                    }
                }
            }
            !card.available -> ZCaption("Not available right now.")
            else -> for (option in card.options) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = ZSpacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZBodyStrong(option.price, modifier = Modifier.weight(1f))
                        if (option.isCurrent) ZBadge("Yours", ZTone.SUCCESS)
                        else if (canManage && (switching || !card.isCurrent)) {
                            val label = when {
                                switching -> "Switch"
                                option.billing == StoreBillingPlan.MONTHLY -> "Pay monthly"
                                else -> "Pay yearly"
                            }
                            ZButton(
                                label,
                                // The first option (monthly, or a yearly-only plan's one price) is filled.
                                style = if (option == card.options.first() && !switching) ZButtonStyle.PRIMARY else ZButtonStyle.OUTLINE,
                                compact = true,
                                fullWidth = false,
                                loading = model.working == "${card.plan.id}-${option.billing.basePlanId}",
                            ) { scope.launch { model.buy(card, option.billing) } }
                        }
                    }
                    ZCaption(option.detail)
                }
            }
        }
    }
}

@Composable
private fun Features(plan: SubscriptionResponse.Plan?) {
    plan?.features?.forEach { ZCheckLine(it, tone = ZTextTone.INK) }
}

// Subscription disclosure (Google Play's subscriptions policy: price,
// period, auto-renewal, how to cancel — beside the purchase buttons).

@Composable
private fun Legal() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs), modifier = Modifier.padding(top = ZSpacing.sm)) {
        ZCaption(
            "Plans are subscriptions that renew automatically until cancelled — yearly plans each year, monthly plans each month. Payment is charged to your Google Play account when you confirm the purchase. Cancel any time in Google Play › Payments & subscriptions › Subscriptions; your plan stays active until the end of the period you've paid for.",
            tone = ZTextTone.FAINT,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
            ZCaption("Terms of Service", tone = ZTextTone.LINK, modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.terms))) })
            ZCaption("Privacy Policy", tone = ZTextTone.LINK, modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.privacy))) })
        }
    }
}
