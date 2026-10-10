package com.zivett.app.features.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zivett.app.core.models.Job
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextTone
import com.zivett.app.features.customer.jobs.JobPresentation

/// Port of `SupportCaseCard.vue`: every open case on a job, for either
/// party's job page — what was reported, where support has it, and
/// what's paused while it's open. Leads the page (they're why Pay &
/// close / the payout won't move). Live updates come free: a case
/// opening or moving pings `job.updated`, which refetches the job.
@Composable
fun SupportCaseCards(job: Job, side: JobPresentation.CaseSide) {
    for (dispute in JobPresentation.openCases(job)) {
        ZCard {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                val meta = JobPresentation.disputeStatusMeta(dispute.status)
                ZBadge(meta.label, meta.tone)
                ZHeadline(JobPresentation.caseTitle(dispute), modifier = Modifier.weight(1f))
            }
            // The complaint text only rides for its author.
            dispute.body?.takeIf { it.isNotBlank() }?.let { ZBody("“$it”", tone = ZTextTone.SOFT) }
            ZCaption(JobPresentation.caseReporterLine(dispute, side), tone = ZTextTone.SOFT)
            Row(modifier = Modifier.padding(vertical = 4.dp)) { StepperRows(JobPresentation.caseSteps(dispute)) }
            ZCaption(JobPresentation.caseNextStep(dispute, side), tone = ZTextTone.SOFT)
        }
    }
}
