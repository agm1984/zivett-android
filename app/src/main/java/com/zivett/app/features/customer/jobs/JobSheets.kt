package com.zivett.app.features.customer.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.CreditCard
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.AvailabilityWindow
import com.zivett.app.core.models.BillingContext
import com.zivett.app.core.models.CustomerEndpoints
import com.zivett.app.core.models.Dispute
import com.zivett.app.core.models.Invoice
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.Quote
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.payments.StripeBridge
import com.zivett.app.core.payments.PaymentCardSection
import com.zivett.app.core.payments.PaymentCardModel
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZActionBand
import com.zivett.app.design.ZAvatar
import com.zivett.app.design.ZAvatarShape
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZChoiceTile
import com.zivett.app.design.ZDisplay
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZMonoLarge
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZStars
import com.zivett.app.design.ZTextArea
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTitle
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZType
import com.zivett.app.features.shared.InvoiceBreakdown
import com.zivett.app.features.shared.MoneyRow
import kotlinx.coroutines.launch

/// The accept modal: what you're agreeing to, the hold preview, the
/// arrival, then confirm. Handles the 409 schedule-conflict fallback and
/// the "You're booked" success state in place.
@Composable
fun AcceptQuoteSheet(job: Job, quote: Quote, model: JobDetailModel, onDismiss: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    var billing by remember { mutableStateOf<Loadable<BillingContext>>(Loadable.Loading) }
    var outcome by remember { mutableStateOf<JobDetailModel.AcceptOutcome?>(null) }
    // Native card capture: PaymentSheet confirms the SetupIntent, the
    // accept call pins its id (the web modal's PaymentElement flow).
    var setupIntentId by remember { mutableStateOf<String?>(null) }
    var collectingCard by remember { mutableStateOf(false) }
    var cardError by remember { mutableStateOf<String?>(null) }

    // A failed fetch is a FAILED state with a Retry. It used to fall back
    // to a made-up simulated context — on a real Stripe backend that read
    // "Test payments (simulated)" with Confirm enabled, and the accept
    // then 422'd for want of a card.
    suspend fun loadBilling() { billing = billing.reloaded { environment.client.send(CustomerEndpoints.billingContext()) } }
    LaunchedEffect(Unit) { model.acceptError = null; loadBilling() }

    val stripe = billing.value?.driver == "stripe"
    val canConfirm = !model.busy && ((billing.value?.canChargeWithoutCardForm ?: false) || setupIntentId != null)

    fun collectCard(billingContext: BillingContext) {
        val key = billingContext.publishableKey; val secret = billingContext.clientSecret
        if (key == null || secret == null) { cardError = "Card setup isn't available right now — try again in a moment."; return }
        collectingCard = true; cardError = null
        scope.launch {
            try {
                StripeBridge.configure(context, key)
                setupIntentId = StripeBridge.collectCard(secret)
            } catch (error: Exception) { cardError = error.userMessage } finally { collectingCard = false }
        }
    }

    // Pinned open while the accept is in flight — see `ZSheet`.
    ZSheet(onDismiss = onDismiss, dismissable = !model.busy) {
        when (val current = outcome) {
            is JobDetailModel.AcceptOutcome.Accepted -> {
                Icon(Icons.Filled.Verified, contentDescription = null, tint = colors.brandGold, modifier = Modifier.padding(0.dp))
                ZMono("Quote accepted")
                ZDisplay("Your ZiVETT Pro is on the way")
                ZBody(if (stripe) "Your card is saved for the ${Money.format(current.holdCents)} total — it's only charged once the work is done and you close the job." else "We placed a ${Money.format(current.holdCents)} hold on your card — you pay only when the job is done and you close it.", tone = ZTextTone.SOFT)
                val date = current.date
                if (date != null) ZBanner("Arriving ${JobPresentation.arrivalLabel(date, current.window)}", tone = ZTone.INFO)
                else ZCaption("No fixed arrival time — arrange timing in the message thread right on this page.")
                ZButton("Done", onClick = onDismiss)
            }
            is JobDetailModel.AcceptOutcome.ScheduleConflictOutcome -> {
                ZTitle("That arrival time was taken")
                if (current.validWindows.isEmpty()) {
                    ZBody("None of your offered windows work for them any more. Message them to arrange a time, or accept a different quote.", tone = ZTextTone.SOFT)
                    ZButton("Not now", style = ZButtonStyle.OUTLINE, onClick = onDismiss)
                } else {
                    ZBody("${JobPresentation.quoteCompanyName(quote)} can no longer make the time they proposed. These windows from your original offer still work for them — pick one to accept the quote:", tone = ZTextTone.SOFT)
                    model.acceptError?.let { ZBanner(it, tone = ZTone.DANGER) }
                    for (w in current.validWindows) {
                        ZButton(JobPresentation.windowSlot(w.date, w.window), style = ZButtonStyle.OUTLINE, loading = model.busy) {
                            scope.launch { outcome = model.acceptQuote(quote, w.date, w.window, setupIntentId) ?: outcome }
                        }
                    }
                }
            }
            null -> {
                ZTitle("Accept quote")
                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    ZAvatar(quote.company?.initials ?: "Z", ZAvatarShape.COMPANY, 44.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ZBodyStrong(JobPresentation.quoteCompanyName(quote))
                        ZCaption(JobPresentation.proLine(quote.company))
                    }
                }
                Text(Money.format(quote.amountCents), style = ZType.money, color = colors.ink)

                ZCard {
                    ZMono("Arrival")
                    val date = quote.proposedDate
                    if (date != null) {
                        ZBodyStrong(JobPresentation.arrivalLabel(date, quote.proposedWindow))
                        ZCaption("Confirmed the moment you accept — you'll see it on the job page.")
                    } else {
                        ZBody("No fixed arrival time — you'll arrange timing with ${JobPresentation.quoteCompanyName(quote)} in messages after accepting.", tone = ZTextTone.SOFT)
                    }
                }

                quote.authorizationPreview?.let { preview ->
                    ZCard {
                        MoneyRow("Quote", preview.subtotalCents ?: quote.amountCents)
                        preview.feeCents?.let { MoneyRow("Trust & support fee", it) }
                        preview.gstCents?.let { MoneyRow("GST${preview.gstBps?.let { b -> " (${JobPresentation.percent(b)})" } ?: ""}", it) }
                        preview.pstCents?.takeIf { it > 0 }?.let { MoneyRow("PST", it) }
                        preview.referralCreditCents?.takeIf { it > 0 }?.let { MoneyRow("Referral credit", -it, color = colors.success) }
                        ZDivider()
                        MoneyRow(if (stripe) "Total at closure" else "Held on your card", preview.totalCents ?: quote.amountCents, strong = true)
                        ZCaption(if (stripe) "Nothing is charged now — your card is saved and only charged when the job is done and you close it." else "A hold, not a charge — you pay only when the job is done and you close it.")
                    }
                }

                ZCard {
                    when (val state = billing) {
                        Loadable.Loading -> ZSpinner()
                        is Loadable.Failed -> {
                            ZBodyStrong("We couldn't load your payment details")
                            ZCaption(state.message)
                            ZButton("Retry", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { scope.launch { billing = Loadable.Loading; loadBilling() } }
                        }
                        is Loadable.Loaded -> {
                            val card = state.loaded.savedCard
                            when {
                                card != null -> CardLine("${(card.brand ?: "Card").replaceFirstChar { it.uppercase() }} •••• ${card.last4 ?: ""}")
                                state.loaded.driver != "stripe" -> CardLine("Test payments (simulated)")
                                setupIntentId != null -> {
                                    CardLine("Card added — it's saved when you confirm")
                                    // The way out when the server couldn't save that card:
                                    // a fresh SetupIntent (each secret is single-use).
                                    if (model.acceptError != null) ZButton("Use a different card", style = ZButtonStyle.GHOST, compact = true, enabled = !model.busy, fullWidth = false) {
                                        setupIntentId = null
                                        scope.launch { loadBilling() }
                                    }
                                }
                                else -> {
                                    ZBodyStrong("Add a payment card to accept this quote")
                                    ZCaption("Nothing is charged now — the card is only billed when the job is done and you close it.")
                                    if (state.loaded.publishableKey?.startsWith("pk_test_") == true) ZCaption("Test mode — use card 4242 4242 4242 4242 with any future expiry and CVC.", tone = ZTextTone.FAINT)
                                    cardError?.let { ZCaption(it, color = colors.danger) }
                                    ZButton("Add a card", compact = true, loading = collectingCard, fullWidth = false) { collectCard(state.loaded) }
                                }
                            }
                        }
                    }
                }

                // Beside the button that caused it — never a toast, which
                // renders under this sheet.
                model.acceptError?.let { ZBanner(it, tone = ZTone.DANGER) }
                ZButton("Confirm — ${Money.format(quote.amountCents)}", style = ZButtonStyle.SUCCESS, loading = model.busy, enabled = canConfirm) {
                    scope.launch { outcome = model.acceptQuote(quote, setupIntentId = setupIntentId) }
                }
            }
        }
    }
}

@Composable
private fun CardLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CreditCard, contentDescription = null, tint = ZTheme.colors.ink)
        ZBody(text)
    }
}

@Composable
fun CancelJobSheet(job: Job, model: JobDetailModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    ZSheet(onDismiss = onDismiss, title = "Cancel ${job.code ?: "this job"}?", dismissable = !model.busy) {
        val fee = job.cancellationFeeCents
        if (fee != null && fee > 0) {
            ZCard { ZMono("Cancellation fee — charged now"); ZMonoLarge(Money.format(fee), color = colors.danger) }
        }
        ZBody(JobPresentation.cancelConfirmCopy(job), tone = ZTextTone.SOFT)
        ZButton(JobPresentation.cancelButtonTitle(job), style = ZButtonStyle.DANGER, loading = model.busy) { scope.launch { model.cancel(); onDismiss() } }
        ZButton("Keep job", style = ZButtonStyle.OUTLINE, onClick = onDismiss)
    }
}

@Composable
fun ReportProblemSheet(model: JobDetailModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("quality") }
    var body by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf<Dispute?>(null) }
    ZSheet(onDismiss = onDismiss) {
        val done = opened
        if (done != null) {
            ZMono("Case opened")
            ZTitle("${done.code ?: "Case"} — our support team is on it")
            ZBody("We'll follow up by notification. Payment for this job stays paused until the case is resolved.", tone = ZTextTone.SOFT)
            ZButton("Done", onClick = onDismiss)
        } else {
            ZTitle("Report a problem")
            ZBanner("If anyone is in immediate danger, call 911 first. Safety reports go straight to our team with top priority.", tone = ZTone.DANGER)
            Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                ZLabel("What kind of problem?")
                for ((key, label) in JobPresentation.reportKinds) {
                    ZChoiceTile(label.take(2).uppercase(), label, "", selected = kind == key, tone = if (key == "safety") ZTone.DANGER else ZTone.WARNING) { kind = key }
                }
            }
            ZTextArea("What happened?", body, { body = it }, placeholder = "Describe what happened, when, and who was involved.")
            ZButton("Send report", style = ZButtonStyle.DANGER, loading = model.busy, enabled = body.isNotBlank()) { scope.launch { opened = model.report(kind, body) } }
        }
    }
}

@Composable
fun WarrantyClaimSheet(model: JobDetailModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var body by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf<Dispute?>(null) }
    ZSheet(onDismiss = onDismiss) {
        val done = opened
        if (done != null) {
            ZMono("Claim opened")
            ZTitle("${done.code ?: "Claim"} — our support team is on it")
            ZBody("Your pro and our support team can both see the claim. We'll follow up by notification.", tone = ZTextTone.SOFT)
            ZButton("Done", onClick = onDismiss)
        } else {
            ZTitle("Warranty claim")
            ZBody("Describe what's gone wrong since the work was completed. Your pro and our support team will both see this.", tone = ZTextTone.SOFT)
            ZTextArea("What happened?", body, { body = it }, placeholder = "e.g. The same joint started leaking again this morning.")
            ZButton("Open claim", loading = model.busy, enabled = body.isNotBlank()) { scope.launch { opened = model.claim(body) } }
        }
    }
}

@Composable
fun ReviewSheet(model: JobDetailModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rating by remember { mutableStateOf(model.job?.review?.rating ?: 0) }
    var comment by remember { mutableStateOf(model.job?.review?.comment ?: "") }
    ZSheet(onDismiss = onDismiss, title = if (model.job?.review == null) "Rate your pro" else "Edit review") {
        model.job?.company?.name?.let { ZBody(it, tone = ZTextTone.SOFT) }
        ZStars(rating) { rating = it }
        ZTextArea("How did the visit go?", comment, { comment = it }, placeholder = "Optional")
        ZButton(if (model.job?.review == null) "Submit review" else "Save review", loading = model.busy, enabled = rating >= 1) {
            scope.launch { if (model.submitReview(rating, comment)) onDismiss() }
        }
    }
}

/// Pay & close: one dollar tip input (no presets — "a tip isn't an
/// expectation", the web's TipPicker rule), then settle.
@Composable
fun PayAndCloseSheet(invoice: Invoice, model: JobDetailModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    var tip by remember { mutableStateOf("") }
    // The card that will be charged, changeable right here. The sheet
    // only leaves on success: it used to dismiss whatever happened, so a
    // declined card was a toast on the job page with no way out.
    val card = remember { PaymentCardModel(environment.client, context) }
    var declined by remember { mutableStateOf<String?>(null) }
    // The call ended without an answer and the job still reads unpaid.
    var unconfirmed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { card.load() }
    // The shared gate: only a loaded "no card" context blocks Pay — the
    // server is the real gate, and a failed fetch must not strand a good card.
    val canPay = !model.busy && !card.blocksPay
    val tipCents = Money.tipCents(tip)
    // Pinned open while the charge is in flight: swiping it away used to
    // cancel the request and toast a failure the server never reported.
    ZSheet(onDismiss = onDismiss, title = "Pay & close", dismissable = !model.busy) {
        ZBody("Paying closes the job and starts your workmanship warranty — or it settles automatically 48 hours after invoicing.", tone = ZTextTone.SOFT)
        InvoiceBreakdown(invoice)
        ZTextField("Tip your pro (optional)", tip, { tip = it }, placeholder = "0.00", keyboardType = KeyboardType.Decimal, corner = { ZCaption("100% goes to your pro") })
        PaymentCardSection(card, declined = declined) { declined = null }
        unconfirmed?.let { ZBanner(it, tone = ZTone.WARNING) }
        ZActionBand("Pay ${Money.format(invoice.amountDueCents + tipCents)}", loading = model.busy, enabled = canPay) {
            scope.launch {
                declined = null; unconfirmed = null
                when (val outcome = model.close(tipCents)) {
                    JobDetailModel.CloseOutcome.Closed -> onDismiss()
                    is JobDetailModel.CloseOutcome.Declined -> declined = outcome.message
                    is JobDetailModel.CloseOutcome.Unconfirmed -> unconfirmed = outcome.message
                    JobDetailModel.CloseOutcome.InFlight -> Unit
                    // Anything else is explained by the job page's toast,
                    // which this sheet would cover — so leave.
                    JobDetailModel.CloseOutcome.Failed -> onDismiss()
                }
            }
        }
    }
}

@Suppress("unused")
private val keepImports: List<Any?> = listOf(AvailabilityWindow("", ""), Modifier.fillMaxWidth())
