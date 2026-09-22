package com.zivett.app.features.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.zivett.app.core.models.IntakeAnswer
import com.zivett.app.core.models.Invoice
import com.zivett.app.core.models.JobCategory
import com.zivett.app.core.models.JobPhoto
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.Money
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZChip
import com.zivett.app.design.ZColors
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZFlowRow
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZMonoBody
import com.zivett.app.design.ZMonoLarge
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZType
import com.zivett.app.features.customer.jobs.JobPresentation
import androidx.compose.ui.text.font.FontWeight
import com.zivett.app.features.customer.jobs.oneDecimal

/// The 9-stage vertical stepper on job pages.
@Composable
fun TimelineStepper(steps: List<JobPresentation.Step>) {
    val colors = ZTheme.colors
    Column {
        steps.forEachIndexed { i, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(
                            when (step.state) {
                                JobPresentation.StepState.DONE -> colors.brandGold
                                JobPresentation.StepState.CURRENT -> colors.navy
                                JobPresentation.StepState.TODO -> colors.surfaceSunken
                            },
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (step.state) {
                            JobPresentation.StepState.DONE -> Icon(Icons.Filled.Check, contentDescription = null, tint = colors.navyDeep, modifier = Modifier.size(12.dp))
                            JobPresentation.StepState.CURRENT -> Box(Modifier.size(7.dp).clip(CircleShape).background(colors.onBrand))
                            JobPresentation.StepState.TODO -> Unit
                        }
                    }
                    if (i < steps.size - 1) Box(Modifier.width(2.dp).height(18.dp).background(if (step.state == JobPresentation.StepState.DONE) colors.brandGold else colors.border))
                }
                Text(
                    step.title,
                    style = if (step.state == JobPresentation.StepState.CURRENT) ZType.bodyStrong else ZType.body,
                    color = if (step.state == JobPresentation.StepState.TODO) colors.inkFaint else colors.ink,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/// The mono category chip (JobRow, opportunity cards).
@Composable
fun CategoryChip(category: JobCategory, size: Int = 40) {
    val colors = ZTheme.colors
    Box(
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(ZColors.parse(category.bgColor) ?: colors.infoSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(category.code ?: category.name.take(2).uppercase(), style = ZType.monoBody.copy(fontWeight = FontWeight.Bold), color = ZColors.parse(category.fgColor) ?: colors.info)
    }
}

@Composable
fun JobStatusBadge(status: JobStatus) {
    val meta = JobPresentation.meta(status)
    ZBadge(meta.label, meta.tone)
}

/// Intake answers as chips (description answers as prose).
@Composable
fun IntakeChips(answers: List<IntakeAnswer>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (answer in answers) {
            if (answer.type == "description") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ZMono(answer.prompt)
                    ZBody(answer.answers.joinToString("\n"), tone = ZTextTone.SOFT)
                }
            } else {
                ZFlowRow { for (value in answer.answers) ZChip(value) }
            }
        }
    }
}

/// Thumbnail grid with a full-screen viewer and optional per-photo delete.
@Composable
fun PhotoGrid(photos: List<JobPhoto>, onDelete: ((JobPhoto) -> Unit)? = null, canDelete: (JobPhoto) -> Boolean = { it.mine }) {
    if (photos.isEmpty()) return
    var viewing by remember { mutableStateOf<JobPhoto?>(null) }
    val colors = ZTheme.colors

    ZFlowRow(spacing = 6.dp) {
        for (photo in photos) {
            Box(modifier = Modifier.size(90.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceSunken).clickable { viewing = photo }) {
                AsyncImage(model = photo.thumbUrl ?: photo.url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (onDelete != null && canDelete(photo)) {
                    IconButton(onClick = { onDelete(photo) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                        Icon(Icons.Filled.Cancel, contentDescription = "Remove photo", tint = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
        }
    }

    viewing?.let { photo ->
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(model = photo.url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                IconButton(onClick = { viewing = null }, modifier = Modifier.align(Alignment.TopEnd).padding(ZSpacing.md)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

/// Line items → service subtotal → booker fee → taxes → credits → total.
/// Mirrors the web's invoice card: rows hide when their bps is null
/// (pre-fee invoices).
@Composable
fun InvoiceBreakdown(invoice: Invoice) {
    val colors = ZTheme.colors
    ZCard {
        for (item in invoice.lineItems ?: emptyList()) MoneyRow(item.label, item.amount)
        ZDivider()
        MoneyRow("Service subtotal", invoice.totalCents, strong = true)
        val feeBps = invoice.customerFeeBps
        val fee = invoice.customerFeeCents
        if (feeBps != null && fee != null) MoneyRow("${invoice.feeLabel} (${JobPresentation.percent(feeBps)})", fee)
        val gst = invoice.gstCents
        if (gst != null && invoice.gstBps != null) MoneyRow("GST", gst + (invoice.customerFeeGstCents ?: 0))
        val pst = invoice.pstCents
        if (pst != null && invoice.pstBps != null && pst > 0) MoneyRow("PST", pst)
        invoice.tipCents?.takeIf { it > 0 }?.let { MoneyRow("Tip", it) }
        invoice.referralCreditCents?.takeIf { it > 0 }?.let { MoneyRow("Referral credit", -it, color = colors.success) }
        ZDivider()
        MoneyRow("Total", invoice.amountDueCents, strong = true, large = true)
        invoice.refundedCents?.takeIf { it > 0 }?.let { MoneyRow("Refunded", -it, color = colors.success) }
    }
}

@Composable
fun MoneyRow(label: String, cents: Int, strong: Boolean = false, large: Boolean = false, color: Color? = null) {
    val colors = ZTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = if (strong) ZType.bodyStrong else ZType.body, color = if (strong) colors.ink else colors.inkSoft, modifier = Modifier.weight(1f))
        val text = (if (cents < 0) "−" else "") + Money.format(kotlin.math.abs(cents))
        if (large) ZMonoLarge(text, color = color ?: colors.ink) else ZMonoBody(text, color = color ?: colors.ink, weight = if (strong) FontWeight.Bold else FontWeight.Medium)
    }
}

/// The pro identity row shared by job pages and the rich heroes.
@Composable
fun ProRow(company: com.zivett.app.core.models.CompanySummary, trailingLine: String? = null, onRating: (() -> Unit)? = null) {
    val colors = ZTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        com.zivett.app.design.ZAvatar(company.initials, com.zivett.app.design.ZAvatarShape.COMPANY, 48.dp)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ZBodyStrong(company.name)
                // Plan chip pulled (PARITY.md "plan chips") — the web renders
                // `ZPlanTag(company.plan)` here.
                Icon(Icons.Filled.Check, contentDescription = "Verified", tint = colors.brandGold, modifier = Modifier.size(14.dp))
            }
            val rating = company.rating
            val line = if (rating != null) "★ ${rating.oneDecimal()} · ${company.count ?: 0} ${if (company.count == 1) "review" else "reviews"}" else "No reviews yet"
            if (onRating != null && rating != null) com.zivett.app.design.ZCaption(line, tone = ZTextTone.LINK, modifier = Modifier.clickable(onClick = onRating))
            else com.zivett.app.design.ZCaption(line, tone = ZTextTone.SOFT)
            trailingLine?.let { com.zivett.app.design.ZCaption(it, tone = ZTextTone.SOFT) }
        }
    }
}

@Suppress("unused")
private val keepSpacer: @Composable () -> Unit = { Spacer(Modifier) }
