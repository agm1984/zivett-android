package com.zivett.app.features.customer.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Work
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
import androidx.compose.ui.unit.dp
import com.zivett.app.app.Areas
import com.zivett.app.app.JobDetailRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.CustomerEndpoints
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMonoBody
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTopBar
import com.zivett.app.design.ZType
import com.zivett.app.features.company.Dates
import com.zivett.app.features.customer.book.ResumeBookingBanner
import com.zivett.app.features.customer.book.BookingWizardEngine
import com.zivett.app.features.shared.CategoryChip
import com.zivett.app.features.shared.LocalNav
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch

/// "My jobs" — every job, live ones first, then history.
@Composable
fun JobsScreen() {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Loadable<List<Job>>>(Loadable.Loading) }
    var history by remember { mutableStateOf(false) }

    suspend fun load() { state = state.reloaded { environment.client.send(CustomerEndpoints.jobs()).jobs } }
    LaunchedEffect(Unit) { load() }

    Column {
        ZTopBar("Jobs")
        ZScreen(onRefresh = { load() }) {
            ZLoadable(state, retry = { scope.launch { load() } }) { jobs ->
                val shown = jobs.filter { (it.status in JobPresentation.historyStatuses) == history }
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    ResumeBookingBanner(BookingWizardEngine.Area.CUSTOMER)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = !history, onClick = { history = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Active") }
                        SegmentedButton(selected = history, onClick = { history = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("History") }
                    }
                    if (shown.isEmpty()) {
                        if (history) ZEmptyState(Icons.Outlined.History, "No past jobs yet", "Finished and cancelled jobs move here so you keep the record.")
                        else ZEmptyState(Icons.Outlined.Work, "Nothing on the go", "When you book a repair it will show up here with its live status.")
                    }
                    for (job in shown) JobRow(job) { nav.navigate(JobDetailRoute(job.id, Areas.CUSTOMER)) }
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }
}

@Composable
fun JobRow(job: Job, onClick: () -> Unit) {
    val colors = ZTheme.colors
    val settled = job.status in JobPresentation.historyStatuses
    ZCard(onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.Top) {
            CategoryChip(job.category)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ZBodyStrong(job.title, maxLines = 1)
                ZCaption(listOfNotNull(job.code, if (settled) job.company?.name else job.address).joinToString(" · "), maxLines = 1)
            }
            val display = JobPresentation.display(job)
            ZBadge(display.label, display.tone)
        }
        if (!settled) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (JobPresentation.isLive(job.status)) Box(Modifier.size(7.dp).clip(CircleShape).background(colors.brandGold))
                val color = when {
                    job.status == JobStatus.INVOICED -> colors.danger
                    job.company == null && job.pendingQuotes.isNotEmpty() -> colors.navyBright
                    else -> colors.success
                }
                Text(JobPresentation.liveLine(job), style = ZType.caption, color = color, modifier = Modifier.weight(1f))
                ZMonoBody(JobPresentation.priceLabel(job) ?: "—", weight = FontWeight.SemiBold)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZCaption(listOfNotNull(job.createdAt?.let { Dates.monthDay(it) }, job.company?.name).joinToString(" · "), modifier = Modifier.weight(1f))
                ZMonoBody(job.invoice?.let { Money.format(it.amountDueCents) } ?: "—", weight = FontWeight.SemiBold)
            }
        }
    }
}

@Suppress("unused")
private val keepTone: ZTextTone = ZTextTone.INK
