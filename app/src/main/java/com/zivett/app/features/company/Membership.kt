package com.zivett.app.features.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zivett.app.app.Areas
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.BusinessEndpoints
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.SubscriptionResponse
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZPlanTag
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import kotlinx.coroutines.launch

/// Which shelf the org is on — company tiers or the business
/// membership. Same payload shape server-side.
enum class SubscriptionArea {
    COMPANY, BUSINESS;

    fun show(): ApiRequest<SubscriptionResponse> = if (this == COMPANY) CompanyEndpoints.subscription() else BusinessEndpoints.subscription()
}

class MembershipModel(private val client: ApiClient, val area: SubscriptionArea = SubscriptionArea.COMPANY) {
    var state by mutableStateOf<Loadable<SubscriptionResponse>>(Loadable.Loading)

    suspend fun load() { state = state.reloaded { client.send(area.show()) } }
}

/// PRO / ELITE / PREMIUM — what a plan key wears as its tag.
private fun tagLabel(key: String): String? = when (key) { "pro", "elite" -> key.uppercase(); "business_premium" -> "PREMIUM"; else -> null }

/// Read-only: the membership the organization already has. The app
/// REFLECTS the account and never sells — no plan shelf, no prices, no
/// card entry, no change actions, and nothing that points anywhere to
/// buy. A plan is a digital service consumed in the app, so App Store
/// 3.1.1 and Play's payments policy allow it to be sold in-app through
/// store billing or not at all, and both forbid steering to an outside
/// purchase (the 2026-09 review rejections were this screen's old plan
/// shelf). Memberships are chosen and changed on the web account; the
/// platform tells people that by email, never from inside the app.
@Composable
fun MembershipScreen(area: String, onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val shelf = if (area == Areas.BUSINESS) SubscriptionArea.BUSINESS else SubscriptionArea.COMPANY
    val model = remember(shelf) { MembershipModel(environment.client, shelf) }
    LaunchedEffect(model) { model.load() }

    Column {
        ZTopBar("Membership", onBack = onBack)
        ZScreen(onRefresh = { model.load() }) {
            ZLoadable(model.state, retry = { scope.launch { model.load() } }) { response ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    ZPageTitle("Membership")
                    val current = response.plans.firstOrNull { it.id == response.subscription.planId }
                    ZCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                            ZHeadline(current?.name ?: response.subscription.planKey.replaceFirstChar { it.uppercase() })
                            tagLabel(current?.key ?: response.subscription.planKey)?.let { ZPlanTag(it) }
                        }
                        val start = response.subscription.termStartedAt; val end = response.subscription.termEndsAt
                        if (start != null && end != null) {
                            ZBody("Current term: ${Dates.short(start)} — ${Dates.short(end)}", tone = ZTextTone.SOFT)
                            ZCaption("Billed ${response.subscription.interval}.")
                        } else {
                            ZBody("Your organization's current membership.", tone = ZTextTone.SOFT)
                        }
                        response.subscription.pending?.let { pending ->
                            ZBanner("Scheduled: moves to ${pending.planName ?: "a new plan"}${pending.interval?.let { " (billed $it)" } ?: ""} on ${response.subscription.termEndsAt?.let { Dates.short(it) } ?: "renewal"}.", tone = ZTone.INFO)
                        }
                    }
                    if (shelf == SubscriptionArea.COMPANY && response.subscription.featured == true) ZBodyStrong("✓ Your company is currently featured on the ZiVETT homepage.", color = colors.success)
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }
}
