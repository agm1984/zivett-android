package com.zivett.app.features.customer.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zivett.app.app.Areas
import com.zivett.app.app.ConversationRoute
import com.zivett.app.app.InvoiceDetailRoute
import com.zivett.app.app.JobDetailRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.Invoice
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.Quote
import com.zivett.app.core.payments.PaymentCardSection
import com.zivett.app.design.ZActionBand
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZColors
import com.zivett.app.design.ZRadius
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZType
import com.zivett.app.features.customer.account.PayInvoiceModel
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.customer.jobs.hoursLabel
import com.zivett.app.features.shared.LocalNav
import com.zivett.app.features.shared.ProRow
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch

/// The charcoal/green panel every hero opens with (`HeroShell.vue`): one
/// wrapper so radius, padding, and the gold glow stay identical.
@Composable
fun HeroShellCard(green: Boolean = false, center: Boolean = false, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val gradient = if (green) Brush.linearGradient(listOf(ZTheme.colors.success, ZColors.fixedGold)) else Brush.linearGradient(listOf(ZColors.fixedNavyDeep, ZColors.fixedNavy))
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.panel)).background(gradient)) {
        if (!green) Box(Modifier.fillMaxWidth().height(200.dp).background(Brush.radialGradient(listOf(ZColors.fixedGold.copy(alpha = 0.25f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(900f, 0f), radius = 450f)))
        Column(modifier = Modifier.fillMaxWidth().padding(ZSpacing.lg), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start, content = content)
    }
}

// Quotes hero (`HeroQuotes.vue`)

/// Quotes are in and a decision is owed — comparing them IS the screen.
@Composable
fun HeroQuotesCard(job: Job) {
    val colors = ZTheme.colors
    val quotes = job.pendingQuotes
    val bestId = HomeHeroLogic.recommendedQuoteId(quotes)
    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
        HeroShellCard {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(ZRadius.tile)).background(ZColors.fixedGold.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Bookmark, contentDescription = null, tint = Color.White)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (quotes.size == 1) "Your quote is ready" else "Your ${quotes.size} quotes are ready", style = ZType.headline.copy(fontSize = 19.sp, fontWeight = FontWeight.ExtraBold), color = Color.White)
                    Text("${job.title} · prepared by ZiVETT", style = ZType.caption.copy(fontSize = 13.5.sp), color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                }
            }
        }
        // One trust statement for the whole set — curation is ZiVETT's promise.
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.card)).background(colors.infoSoft).padding(ZSpacing.sm), horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = colors.info, modifier = Modifier.size(16.dp))
            Text("Every option below is from a pro ZiVETT has verified — identity, licence and insurance checked. Contact details are shared as soon as you accept.", style = ZType.caption.copy(fontSize = 12.5.sp), color = colors.ink)
        }
        quotes.forEachIndexed { index, quote -> QuoteOptionCard(job, quote, index, quote.id == bestId) }
    }
}

@Composable
private fun QuoteOptionCard(job: Job, quote: Quote, index: Int, best: Boolean) {
    val nav = LocalNav.current
    val colors = ZTheme.colors
    Box(modifier = Modifier.padding(top = if (best) 10.dp else 0.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.panel)).background(colors.surface).border(if (best) 2.dp else 1.dp, if (best) colors.star else colors.border, RoundedCornerShape(ZRadius.panel)).padding(ZSpacing.md),
        ) {
            Text("OPTION ${index + 1}", style = ZType.mono, color = colors.inkMuted)
            Text(Money.format(quote.amountCents), style = ZType.money.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold), color = colors.navyDeep.takeIf { !colors.isDark } ?: colors.ink, modifier = Modifier.padding(top = 5.dp))
            val rate = quote.hourlyRateCents; val hours = quote.estimatedHours; val crew = quote.crewSize
            if (rate != null && hours != null && crew != null) Text("$crew ${if (crew == 1) "person" else "people"} × ${hours.hoursLabel()}h × ${Money.format(rate)}/hr", style = ZType.mono.copy(letterSpacing = 0.sp, fontWeight = FontWeight.Normal, fontSize = 12.5.sp), color = colors.inkMuted, modifier = Modifier.padding(top = 2.dp))
            quote.proposedDate?.let { Text("Can arrive ${JobPresentation.windowSlot(it, quote.proposedWindow)}", style = ZType.caption, color = colors.inkSoft, modifier = Modifier.padding(top = 4.dp)) }
            Text(HomeHeroLogic.proLine(quote), style = ZType.caption.copy(fontSize = 12.sp), color = colors.inkMuted, modifier = Modifier.padding(top = ZSpacing.xs))
            quote.message?.takeIf { it.isNotEmpty() }?.let {
                Text("“$it”", style = ZType.caption.copy(fontSize = 12.5.sp), color = colors.inkSoft, modifier = Modifier.padding(top = ZSpacing.sm).fillMaxWidth().clip(RoundedCornerShape(ZRadius.card)).background(colors.surfaceSunken).padding(ZSpacing.sm))
            }
            Box(
                modifier = Modifier.padding(top = ZSpacing.sm).fillMaxWidth().clip(RoundedCornerShape(ZRadius.button)).background(if (best) colors.navy else colors.brandGold.copy(alpha = 0.15f)).clickable { nav.navigate(JobDetailRoute(job.id, Areas.CUSTOMER)) }.padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Review & accept — ${Money.format(quote.amountCents)}", style = ZType.bodyStrong, color = if (best) colors.onBrand else colors.navyDeep.takeIf { !colors.isDark } ?: colors.ink) }
        }
        if (best) {
            Text("ZIVETT RECOMMENDS", style = ZType.mono.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp), color = ZColors.fixedNavyDeep, modifier = Modifier.offset(x = ZSpacing.md, y = (-10).dp).clip(RoundedCornerShape(6.dp)).background(colors.star).padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

// Invoice hero (`HeroInvoice.vue`)

/// The invoice becomes the hero the moment it's issued — itemized, tip
/// inline, one button. Reuses `PayInvoiceModel` (card capture + 3DS
/// included) so the pay rules live once.
@Composable
fun HeroInvoiceCard(invoice: Invoice, onPaid: suspend () -> Unit) {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val model = remember(invoice.id) { PayInvoiceModel(invoice, environment.client, context = context) }
    LaunchedEffect(model) { model.load() }
    val cancellation = HomeHeroLogic.isCancellationFee(invoice)
    val overdue = invoice.status == "overdue"

    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.panel)).background(colors.surface).border(1.dp, if (cancellation) colors.danger.copy(alpha = 0.3f) else colors.border, RoundedCornerShape(ZRadius.panel))) {
        // Header band: number + title, DUE/OVERDUE stamp.
        Row(modifier = Modifier.fillMaxWidth().background(if (cancellation) colors.danger else ZColors.fixedNavyDeep).padding(ZSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${if (cancellation) "CANCELLATION FEE" else "INVOICE"} · ${invoice.number ?: ""}", style = ZType.mono.copy(letterSpacing = 0.sp, fontWeight = FontWeight.Normal, fontSize = 12.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = Color.White.copy(alpha = 0.65f), modifier = Modifier.clickable { nav.navigate(InvoiceDetailRoute(invoice.id, Areas.CUSTOMER)) })
                Text(invoice.job?.title ?: "Invoice", style = ZType.bodyStrong, color = Color.White, maxLines = 1)
            }
            Text(if (overdue) "OVERDUE" else "DUE NOW", style = ZType.mono.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp, fontSize = 12.sp), color = if (overdue) colors.danger else ZColors.fixedNavyDeep, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (overdue) Color.White else colors.star).padding(horizontal = 10.dp, vertical = 4.dp))
        }

        Column(modifier = Modifier.padding(ZSpacing.md)) {
            if (cancellation) {
                Text("This covers the pro's time after they'd committed to your job — no work is included.", style = ZType.caption.copy(fontSize = 12.5.sp), color = colors.danger, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.card)).background(colors.dangerSoft).padding(ZSpacing.sm))
            }
            for (item in invoice.lineItems ?: emptyList()) HeroMoneyRow(item.label, item.amount)
            // Fee + GST ride above the total; pre-fee invoices skip the block entirely.
            if (invoice.customerFeeBps != null) {
                invoice.customerFeeCents?.takeIf { it > 0 }?.let { HeroMoneyRow("Trust & support fee", it) }
                val gst = (invoice.gstCents ?: 0) + (invoice.customerFeeGstCents ?: 0)
                if (gst > 0) HeroMoneyRow(invoice.gstBps?.let { "GST (${it / 100.0}%)" } ?: "GST", gst)
                invoice.pstCents?.takeIf { it > 0 }?.let { HeroMoneyRow(invoice.pstBps?.let { b -> "PST (${b / 100.0}%)" } ?: "PST", it) }
            }
            invoice.referralCreditCents?.takeIf { it > 0 }?.let { credit ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("Referral credit", style = ZType.caption.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = colors.success, modifier = Modifier.weight(1f))
                    Text("−${Money.format(credit)}", style = ZType.monoBody.copy(fontWeight = FontWeight.SemiBold), color = colors.success)
                }
            }
            if (!cancellation) {
                ZTextField("Tip your pro (optional)", model.customTip, { model.customTip = it }, modifier = Modifier.padding(top = ZSpacing.xs), placeholder = "0.00", error = model.fieldErrors["tip_cents"], keyboardType = KeyboardType.Decimal, corner = { ZCaption("100% goes to your pro") })
            }
            Box(Modifier.fillMaxWidth().padding(vertical = ZSpacing.sm).height(1.dp).background(colors.borderStrong.copy(alpha = 0.6f)))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("Total due", style = ZType.headline.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold), color = colors.ink, modifier = Modifier.weight(1f))
                Text(Money.format(model.totalCents), style = ZType.money.copy(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold), color = colors.navyDeep.takeIf { !colors.isDark } ?: colors.ink)
            }
            model.error?.let { Text(it, style = ZType.caption.copy(fontSize = 12.5.sp), color = colors.danger, modifier = Modifier.padding(top = ZSpacing.xs)) }
            // The card that will be charged, changeable right here — a
            // declined charge leads it with the bank's message. (This hero
            // only offered a card when NONE was on file, so a declined
            // saved card had no way out.)
            PaymentCardSection(model.card, declined = model.declined, modifier = Modifier.padding(top = ZSpacing.sm)) { model.declined = null }
            if (!cancellation) Text("Paying closes the job — or it settles automatically 48 hours after invoicing.", style = ZType.caption.copy(fontSize = 12.sp), color = colors.inkMuted, modifier = Modifier.padding(top = ZSpacing.sm))
        }

        ZActionBand("Pay ${Money.format(model.totalCents)}", loading = model.paying, enabled = model.canPay, flushBottom = true, radius = ZRadius.panel) {
            scope.launch { if (model.pay() != null) onPaid() }
        }
    }
}

@Composable
private fun HeroMoneyRow(label: String, cents: Int) {
    val colors = ZTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = ZType.caption.copy(fontSize = 14.sp), color = colors.inkSoft, modifier = Modifier.weight(1f))
        Text(Money.format(cents), style = ZType.monoBody.copy(fontWeight = FontWeight.SemiBold), color = colors.ink)
    }
}

// Pending hero (`HeroPending.vue`)

/// The dead zone between "submitted" and "a pro accepted": visible
/// motion, a plain-language checklist, and a way out that isn't buried.
@Composable
fun HeroPendingCard(job: Job, onCancel: () -> Unit) {
    val colors = ZTheme.colors
    var confirming by remember { mutableStateOf(false) }
    val quoteMode = job.mode == "quote"
    val ping = rememberInfiniteTransition(label = "ping")
    val pingScale by ping.animateFloat(0.7f, 1.25f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "ping-scale")
    val pingAlpha by ping.animateFloat(0.8f, 0f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "ping-alpha")

    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
        HeroShellCard(center = true) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                Box(Modifier.size(96.dp).scale(pingScale).alpha(pingAlpha).clip(CircleShape).background(ZColors.fixedGold.copy(alpha = 0.5f)))
                Box(Modifier.size(56.dp).clip(CircleShape).background(ZColors.fixedGold), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Build, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
            Text(if (quoteMode) "Collecting your quotes…" else "Finding your pro…", style = ZType.title.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = Color.White, modifier = Modifier.padding(top = ZSpacing.md))
            Text(ChecklistLogic.pendingExpectation(quoteMode), style = ZType.caption.copy(fontSize = 14.sp), color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = ZSpacing.xs))
        }

        StatusChecklist(ChecklistLogic.pendingSteps(quoteMode))

        // The request summary — what the system is working from.
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.panel)).background(colors.surface).border(1.dp, colors.border, RoundedCornerShape(ZRadius.panel)).padding(ZSpacing.md), verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
            Text("YOUR REQUEST", style = ZType.mono, color = colors.inkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(ZRadius.tile)).background(ZColors.parse(job.category.bgColor) ?: colors.infoSoft), contentAlignment = Alignment.Center) {
                    Icon(CategoryIcon.icon(job.category.icon), contentDescription = null, tint = ZColors.parse(job.category.fgColor) ?: colors.info, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ZBodyStrong(job.title, maxLines = 1)
                    ZCaption(listOfNotNull(JobPresentation.modeMeta(job.mode).label, job.address).joinToString(" · "), maxLines = 1)
                }
                if (job.urgency == "asap") ZBadge("Urgent", ZTone.DANGER)
            }
        }

        // Cancelling is free at this point, and hiding that would be a dark pattern. Quiet, not absent.
        Text("Cancel this request", style = ZType.label.copy(fontSize = 13.5.sp), color = colors.danger, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { confirming = true }.padding(vertical = 12.dp))
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Cancel this request?") },
            text = { Text("No pro has been assigned yet, so cancelling is free.") },
            confirmButton = { TextButton(onClick = { confirming = false; onCancel() }) { Text("Cancel request", color = colors.danger) } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Keep looking") } },
        )
    }
}

// Progress hero (`HeroProgress.vue`)

/// The pro is on-site and working — a calm live checklist replaces the
/// map, so the customer never wonders what's happening in their house.
@Composable
fun HeroProgressCard(job: Job) {
    val nav = LocalNav.current
    val colors = ZTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
        HeroShellCard(green = true) {
            Row(modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                Text(if (job.status == JobStatus.COMPLETED) "FINISHING UP" else "WORK IN PROGRESS", style = ZType.mono.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp), color = Color.White)
            }
            Text(ChecklistLogic.progressHeadline(job.status), style = ZType.title.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = Color.White, modifier = Modifier.padding(top = ZSpacing.sm))
            Text(ChecklistLogic.progressBlurb(job.status, job.company?.name, job.title), style = ZType.caption.copy(fontSize = 14.sp), color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 4.dp))
        }
        StatusChecklist(ChecklistLogic.progressSteps(job.status))
        job.company?.let { company ->
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.panel)).background(colors.surface).border(1.dp, colors.border, RoundedCornerShape(ZRadius.panel)).padding(ZSpacing.md), verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                ProRow(company, trailingLine = "On-site now")
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.button)).background(colors.brandGold.copy(alpha = 0.15f)).clickable { nav.navigate(ConversationRoute(job.id, Areas.CUSTOMER, company.name, job.title)) }.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                    Text("Message", style = ZType.bodyStrong, color = colors.navyDeep.takeIf { !colors.isDark } ?: colors.ink)
                }
            }
        }
    }
}

/// The rendered checklist: gold ✓ for done, pulsing charcoal for
/// current, quiet grey for not-yet.
@Composable
fun StatusChecklist(steps: List<ChecklistLogic.Step>) {
    val colors = ZTheme.colors
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulse.animateFloat(1f, 0.55f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse-alpha")
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.panel)).background(colors.surface).border(1.dp, colors.border, RoundedCornerShape(ZRadius.panel)).padding(horizontal = ZSpacing.md)) {
        steps.forEachIndexed { index, step ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                when (step.state) {
                    ChecklistLogic.StepState.DONE -> Box(Modifier.size(24.dp).clip(CircleShape).background(colors.brandGold), contentAlignment = Alignment.Center) { Text("✓", style = ZType.label, color = colors.navyDeep) }
                    ChecklistLogic.StepState.CURRENT -> Box(Modifier.size(24.dp).alpha(pulseAlpha).clip(CircleShape).background(colors.navy))
                    ChecklistLogic.StepState.TODO -> Box(Modifier.size(24.dp).clip(CircleShape).background(colors.border))
                }
                Text(
                    step.label,
                    style = ZType.body.copy(fontSize = 14.5.sp, fontWeight = when (step.state) { ChecklistLogic.StepState.CURRENT -> FontWeight.Bold; ChecklistLogic.StepState.DONE -> FontWeight.SemiBold; else -> FontWeight.Medium }),
                    color = when (step.state) { ChecklistLogic.StepState.CURRENT -> colors.ink; ChecklistLogic.StepState.DONE -> colors.inkSoft; else -> colors.inkFaint },
                    modifier = Modifier.weight(1f),
                )
                step.time?.let { Text(it, style = ZType.mono.copy(letterSpacing = 0.sp, fontWeight = FontWeight.Normal, fontSize = 12.sp), color = colors.inkFaint) }
            }
            if (index < steps.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderSoft))
        }
    }
}
