package com.zivett.app.features.business

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.zivett.app.app.Areas
import com.zivett.app.app.BookRoute
import com.zivett.app.app.BusinessSetupRoute
import com.zivett.app.app.JobDetailRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.PropertiesRoute
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.BusinessDashboard
import com.zivett.app.core.models.BusinessEndpoints
import com.zivett.app.core.models.BusinessSetup
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMonoBody
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSectionHeader
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZStatTile
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTone
import com.zivett.app.features.customer.book.BookingWizardEngine
import com.zivett.app.features.customer.book.ResumeBookingBanner
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.shared.LocalNav
import kotlinx.coroutines.launch

class BusinessHomeModel(private val client: ApiClient) {
    var state by mutableStateOf<Loadable<BusinessDashboard>>(Loadable.Loading)
    suspend fun load() { state = state.reloaded { client.send(BusinessEndpoints.dashboard()) } }
}

private const val NUDGE_PREFS = "zivett.business"
private const val NUDGE_KEY = "rp.business.setupNudgeDismissed"

/// Business overview: setup nudge, stats, properties, open requests.
@Composable
fun BusinessHomeScreen() {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val model = remember { BusinessHomeModel(environment.client) }
    var nudgeDismissed by remember { mutableStateOf(context.getSharedPreferences(NUDGE_PREFS, Context.MODE_PRIVATE).getBoolean(NUDGE_KEY, false)) }
    LaunchedEffect(model) { model.load() }
    androidx.lifecycle.compose.LifecycleResumeEffect(model) { scope.launch { model.load() }; onPauseOrDispose { } }

    fun setupMissing(setup: BusinessSetup): List<String> {
        val missing = mutableListOf<String>()
        if (!setup.steps.profile.complete) {
            val fields = (setup.steps.profile.missing ?: emptyList()).joinToString(", ")
            missing += if (fields.isEmpty()) "your business profile" else "your business profile ($fields)"
        }
        if (!setup.steps.properties.complete) missing += "your first property"
        return missing
    }

    ZScreen(onRefresh = { model.load() }) {
        ZLoadable(model.state, retry = { scope.launch { model.load() } }) { dashboard ->
            Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                ZPageTitle("Overview", environment.session.user?.organization?.name)
                ResumeBookingBanner(BookingWizardEngine.Area.BUSINESS)

                val missing = setupMissing(dashboard.setup)
                if (!nudgeDismissed && missing.isNotEmpty()) {
                    ZCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ZBodyStrong("Finish setting up", modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                nudgeDismissed = true
                                context.getSharedPreferences(NUDGE_PREFS, Context.MODE_PRIVATE).edit().putBoolean(NUDGE_KEY, true).apply()
                                // Dismissing = the web's "Skip for now": stamp setup_completed_at
                                // server-side (best-effort, admins only) so every client stops nudging.
                                if (environment.session.user?.organizationRole == OrganizationRole.ADMIN) scope.launch { runCatching { environment.client.send(BusinessEndpoints.completeSetup()) } }
                            }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = colors.inkFaint, modifier = Modifier.size(14.dp)) }
                        }
                        ZCaption("Still to do: ${missing.joinToString(" and ")}.")
                        ZTextAction("Resume setup") { nav.navigate(BusinessSetupRoute) }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                    ZStatTile(dashboard.stats.properties.toString(), "Properties", Modifier.weight(1f))
                    ZStatTile(dashboard.stats.openRequests.toString(), "Open requests", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                    ZStatTile(Money.format(dashboard.stats.spendMonth), "Spend this month", Modifier.weight(1f))
                    ZStatTile(dashboard.stats.jobsYtd.toString(), "Jobs YTD", Modifier.weight(1f))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZSectionHeader("Open requests", modifier = Modifier.weight(1f))
                    ZTextAction("+ New request") { nav.navigate(BookRoute(Areas.BUSINESS)) }
                }
                if (dashboard.requests.isEmpty()) ZEmptyState(Icons.Outlined.Work, "No open requests", "Book a repair and it will show up here with its live status.")
                for (job in dashboard.requests) BusinessRequestRow(job) { nav.navigate(JobDetailRoute(job.id, Areas.BUSINESS)) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZSectionHeader("Properties", modifier = Modifier.weight(1f))
                    ZTextAction("Manage") { nav.navigate(PropertiesRoute) }
                }
                for (property in dashboard.properties.take(4)) {
                    ZCard(padding = ZSpacing.sm) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                ZBodyStrong(property.name)
                                ZCaption(listOfNotNull(property.kind, property.address).joinToString(" · "), maxLines = 1)
                            }
                            property.openJobs?.takeIf { it > 0 }?.let { ZBadge("$it open", ZTone.DANGER) }
                        }
                    }
                }
                Spacer(Modifier.padding(ZSpacing.lg))
            }
        }
    }
}

@Composable
fun BusinessRequestRow(job: Job, onClick: () -> Unit) {
    ZCard(padding = ZSpacing.sm, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZBodyStrong(job.title, maxLines = 1, modifier = Modifier.weight(1f))
            val display = JobPresentation.display(job)
            ZBadge(display.label, display.tone)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZCaption(listOfNotNull(job.code, job.property?.name ?: job.address, job.unit).joinToString(" · "), maxLines = 1, modifier = Modifier.weight(1f))
            ZMonoBody(JobPresentation.priceLabel(job) ?: "—", weight = FontWeight.SemiBold)
        }
        job.tenant?.let { ZCaption("Tenant: $it", tone = ZTextTone.FAINT) }
    }
}

/// The organization's whole request ledger.
@Composable
fun BusinessRequestsScreen() {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Loadable<List<Job>>>(Loadable.Loading) }
    suspend fun load() { state = state.reloaded { environment.client.send(BusinessEndpoints.requests()).jobs } }
    LaunchedEffect(Unit) { load() }

    ZScreen(onRefresh = { load() }) {
        ZLoadable(state, retry = { scope.launch { load() } }) { jobs ->
            Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                ResumeBookingBanner(BookingWizardEngine.Area.BUSINESS)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) { ZPageTitle("Requests") }
                    ZTextAction("+ New") { nav.navigate(BookRoute(Areas.BUSINESS)) }
                }
                if (jobs.isEmpty()) ZEmptyState(Icons.Outlined.Work, "No requests yet", "Book your first repair and it will show up here.")
                for (job in jobs) BusinessRequestRow(job) { nav.navigate(JobDetailRoute(job.id, Areas.BUSINESS)) }
                Spacer(Modifier.padding(ZSpacing.lg))
            }
        }
    }
}

@Suppress("unused")
private val keepFill: Modifier = Modifier.fillMaxWidth()
