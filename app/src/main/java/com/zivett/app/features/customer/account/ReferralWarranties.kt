package com.zivett.app.features.customer.account

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zivett.app.app.Areas
import com.zivett.app.app.JobDetailRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.CustomerEndpoints
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobArea
import com.zivett.app.core.models.Referral
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZQRCode
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSectionHeader
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZStatTile
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.design.ZType
import com.zivett.app.features.company.Dates
import com.zivett.app.features.customer.jobs.JobDetailModel
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.customer.jobs.WarrantyClaimSheet
import com.zivett.app.features.referrals.BankedCreditsPanel
import com.zivett.app.features.shared.LocalNav
import kotlinx.coroutines.launch
import java.time.Instant

/// Android's share sheet for a link + message.
fun shareText(context: android.content.Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    context.startActivity(Intent.createChooser(intent, null))
}

@Composable
fun ReferralScreen(onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    var state by remember { mutableStateOf<Loadable<Referral>>(Loadable.Loading) }
    suspend fun load() { state = state.reloaded { environment.client.send(CustomerEndpoints.referral()) } }
    LaunchedEffect(Unit) { load() }

    fun discountCopy(discount: Referral.Discount) = if (discount.mode == "flat") Money.format(discount.cents) else "${discount.bps / 100}%"

    Column {
        ZTopBar("Referral", onBack = onBack)
        ZScreen {
            ZLoadable(state, retry = { scope.launch { load() } }) { referral ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    ZPageTitle("Refer a friend", "They get ${discountCopy(referral.discount)} off their first job. You earn ${Money.format(referral.rewardCents)} when it completes.")
                    ZCard {
                        Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                            ZLabel("Your code")
                            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.Top) {
                                // Scannable in person — opens signup with the code attached.
                                ZQRCode(referral.link, 104.dp)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(referral.code, style = ZType.money.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp), color = colors.navy)
                                    ZCaption(referral.link, maxLines = 2)
                                }
                            }
                            ZButton("Share your link") {
                                shareText(context, "Book a verified pro on ZiVETT — use my code ${referral.code} for ${discountCopy(referral.discount)} off. ${referral.link}")
                            }
                        }
                    }
                    ZSectionHeader("Your rewards")
                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                        ZStatTile(referral.stats.friendsJoined.toString(), "Friends joined", Modifier.weight(1f))
                        ZStatTile(Money.format(referral.stats.rewardsEarnedCents), "Earned", Modifier.weight(1f))
                        ZStatTile(Money.format(referral.stats.availableCents), "Available", Modifier.weight(1f))
                    }
                    // The wallet: credits spend one per job, so what's
                    // banked and what spends next are different numbers.
                    BankedCreditsPanel(referral.stats.bankedCount, referral.stats.availableCents, referral.stats.nextCreditCents, "comes off your next accepted quote", "job")
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }
    @Suppress("unused") val keep = Icons.Outlined.Share
}

@Composable
fun WarrantiesScreen(area: JobArea, onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    var state by remember { mutableStateOf<Loadable<List<Job>>>(Loadable.Loading) }
    // The web's list-driven claim: open one right from the row.
    var claiming by remember { mutableStateOf<Job?>(null) }
    var showPast by remember { mutableStateOf(false) }
    val areaKey = if (area.kind == JobArea.Kind.BUSINESS) Areas.BUSINESS else Areas.CUSTOMER

    suspend fun load() { state = state.reloaded { environment.client.send(area.warranties()).jobs } }
    LaunchedEffect(Unit) { load() }

    // Split on the end date, not the status — a lapsed job the hourly
    // expiry sweep hasn't flipped yet belongs with the past ones.
    fun isActive(job: Job): Boolean = job.warrantyEndsAt?.isAfter(Instant.now()) == true

    Column {
        ZTopBar("Warranties", onBack = onBack)
        ZScreen {
            ZLoadable(state, retry = { scope.launch { load() } }) { jobs ->
                val active = jobs.filter(::isActive)
                val past = jobs.filter { !isActive(it) }
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    ZPageTitle("Warranties", "Completed work is covered for a short window. Something not right? Open a claim from the job.")
                    if (active.isEmpty()) ZEmptyState(Icons.Outlined.VerifiedUser, "No active warranties", "Every job comes with a workmanship warranty — it activates here once the invoice is paid.")
                    for (job in active) {
                        ZCard(onClick = { nav.navigate(JobDetailRoute(job.id, areaKey)) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ZBodyStrong(job.title, maxLines = 1)
                                    val context = if (area.kind == JobArea.Kind.BUSINESS) listOfNotNull(job.company?.name, job.property?.name, job.unit) else listOfNotNull(job.code, job.company?.name)
                                    ZCaption(context.joinToString(" · "))
                                    job.warrantyEndsAt?.let { ZCaption("Covered until ${Dates.short(it)}", tone = ZTextTone.SOFT) }
                                }
                                val claim = job.disputes?.firstOrNull { it.kind == "warranty_claim" }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (claim != null) {
                                        val meta = JobPresentation.disputeStatusMeta(claim.status)
                                        ZBadge(meta.label, meta.tone)
                                        ZCaption(claim.code ?: "", tone = ZTextTone.FAINT)
                                    } else {
                                        ZBadge("Active", ZTone.SUCCESS)
                                        ZTextAction("Open a claim") { claiming = job }
                                    }
                                }
                            }
                        }
                    }
                    if (past.isNotEmpty()) {
                        Row(modifier = Modifier.clickable { showPast = !showPast }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(if (showPast) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(18.dp))
                            ZBodyStrong("Past warranties (${past.size})", color = colors.inkMuted)
                        }
                        AnimatedVisibility(showPast) {
                            ZCard {
                                past.forEachIndexed { i, job ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            ZBodyStrong(job.title, maxLines = 1)
                                            val context = if (area.kind == JobArea.Kind.BUSINESS) listOfNotNull(job.company?.name, job.property?.name, job.unit) else listOfNotNull(job.company?.name)
                                            ZCaption(context.joinToString(" · "))
                                        }
                                        job.warrantyEndsAt?.let { ZCaption("Ended ${Dates.short(it)}", tone = ZTextTone.SOFT) }
                                    }
                                    if (i < past.size - 1) ZDivider()
                                }
                            }
                        }
                    }
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }

    claiming?.let { job ->
        val model = remember(job.id) { JobDetailModel(job.id, environment.client, area) }
        WarrantyClaimSheet(model) { claiming = null; scope.launch { load() } }
    }
}

@Suppress("unused")
private val keepFill: Modifier = Modifier.fillMaxWidth()
