package com.zivett.app.features.customer.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zivett.app.core.models.AssignedTech
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.initialsOf
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZPhotoAvatar
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZStars
import com.zivett.app.design.ZTextArea
import com.zivett.app.design.ZTextTone
import kotlinx.coroutines.launch

/// The review reminder's quick rating sheet (web `QuickReviewModal.vue`,
/// 2026-11-21). The server's one-time "How did X do?" push / bell row
/// (`Notify::reviewRequested`, 24 h after a booker paid without rating)
/// carries `review: 1`; the job page opens this over the job when the job
/// qualifies. Pure rules here, tested in `ReviewReminderTest`.
object QuickReview {
    const val COMMENT_MAX = 1000
    const val MAX_FACES = 3

    /// Closed (paid) and still unrated. Already rated → just the job.
    fun qualifies(job: Job): Boolean =
        job.isReviewable && job.review == null && (job.status in JobStatus.closed || job.closedAt != null)

    /// Who did the work: the crew, else the assigned tech — up to three.
    fun faces(job: Job): List<AssignedTech> = job.crewMembers.take(MAX_FACES)

    /// The comment box's label for a picked rating; null until a star is.
    fun commentLabel(rating: Int): String? = when (rating) {
        1 -> "What went wrong?"
        2, 3 -> "What could have gone better?"
        4, 5 -> "What did they do well?"
        else -> null
    }

    /// "Plumbing · Z-93U4H3".
    fun context(job: Job): String = listOfNotNull(job.category.name, job.code).joinToString(" · ")
}

@Composable
fun QuickReviewSheet(job: Job, model: JobDetailModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rating by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }
    val companyName = job.company?.name ?: "your pro"
    ZSheet(onDismiss = onDismiss, title = "How did $companyName do?", dismissable = !model.busy) {
        val faces = QuickReview.faces(job)
        if (faces.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                for (tech in faces) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ZPhotoAvatar(tech.photoUrl, initialsOf(tech.name), 48.dp)
                        ZCaption(tech.name, maxLines = 1)
                    }
                }
            }
        }
        ZCaption(QuickReview.context(job), tone = ZTextTone.FAINT)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ZStars(rating, starSize = 44.dp) { rating = it }
        }
        QuickReview.commentLabel(rating)?.let { label ->
            ZTextArea(label, comment, { comment = it.take(QuickReview.COMMENT_MAX) }, placeholder = "Optional", minLines = 3)
        }
        ZButton("Submit review", loading = model.busy, enabled = rating >= 1) {
            scope.launch {
                if (model.submitReview(rating, comment)) {
                    onDismiss()
                    model.refresh()
                }
            }
        }
        ZButton("Not now", style = ZButtonStyle.OUTLINE, enabled = !model.busy, onClick = onDismiss)
    }
}
