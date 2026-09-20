package com.zivett.app.features.customer.account

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.zivett.app.R
import com.zivett.app.app.Areas
import com.zivett.app.app.InvoiceDetailRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.PayInvoiceRoute
import com.zivett.app.core.Features
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.BillingContext
import com.zivett.app.core.models.CustomerEndpoints
import com.zivett.app.core.models.Invoice
import com.zivett.app.core.models.JobArea
import com.zivett.app.core.models.initialsOf
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.payments.ChallengeOutcome
import com.zivett.app.core.payments.PaymentCardModel
import com.zivett.app.core.payments.PaymentCardSection
import com.zivett.app.core.payments.PaymentChallenger
import com.zivett.app.core.payments.StripeChallenger
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZActionBand
import com.zivett.app.design.ZAvatar
import com.zivett.app.design.ZAvatarShape
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZMonoLarge
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSectionHeader
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTitle
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.features.company.Dates
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.shared.InvoiceBreakdown
import com.zivett.app.features.shared.LocalNav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal

@Composable
fun InvoicesScreen(area: JobArea, onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Loadable<List<Invoice>>>(Loadable.Loading) }
    val areaKey = if (area.kind == JobArea.Kind.BUSINESS) Areas.BUSINESS else Areas.CUSTOMER
    val business = area.kind == JobArea.Kind.BUSINESS

    suspend fun load() { state = state.reloaded { environment.client.send(if (business) com.zivett.app.core.models.BusinessEndpoints.invoices() else CustomerEndpoints.invoices()).invoices } }
    LaunchedEffect(Unit) { load() }

    Column {
        ZTopBar("Invoices", onBack = onBack)
        ZScreen(onRefresh = { load() }) {
            ZLoadable(state, retry = { scope.launch { load() } }) { invoices ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    if (business) {
                        // Business invoices: consolidated monthly statements.
                        ZPageTitle("Invoices", "Consolidated by month across all properties.")
                        if (invoices.isEmpty()) ZEmptyState(Icons.Outlined.Description, "No invoices yet", "Invoices appear here once pros complete jobs across your properties.")
                        val months = invoices.groupBy { it.issuedAt?.let { d -> Dates.monthYear(d) } ?: "Undated" }
                        for ((label, rows) in months) {
                            ZSectionHeader(label)
                            Row(modifier = Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                ZCaption("${rows.size} ${if (rows.size == 1) "job" else "jobs"}", modifier = Modifier.weight(1f))
                                com.zivett.app.design.ZMonoBody(Money.format(rows.sumOf { it.amountDueCents }), weight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                            for (invoice in rows) InvoiceRow(invoice) { nav.navigate(InvoiceDetailRoute(invoice.id, areaKey)) }
                        }
                    } else {
                        ZPageTitle("Invoices")
                        val due = invoices.filter { !it.isPaid }
                        val paid = invoices.filter { it.isPaid }
                        if (invoices.isEmpty()) ZEmptyState(Icons.Outlined.Description, "No invoices yet", "Invoices appear here once a pro finishes a job.")
                        if (due.isNotEmpty()) { ZSectionHeader("Due"); for (invoice in due) InvoiceRow(invoice) { nav.navigate(InvoiceDetailRoute(invoice.id, areaKey)) } }
                        if (paid.isNotEmpty()) { ZSectionHeader("Paid"); for (invoice in paid) InvoiceRow(invoice) { nav.navigate(InvoiceDetailRoute(invoice.id, areaKey)) } }
                    }
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }
}

@Composable
fun InvoiceRow(invoice: Invoice, onClick: () -> Unit) {
    ZCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ZBodyStrong(invoice.job?.title ?: invoice.number ?: "Invoice", maxLines = 1)
                ZCaption(listOfNotNull(invoice.number, invoice.job?.company).joinToString(" · "))
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ZMonoLarge(Money.format(invoice.amountDueCents))
                ZBadge(if (invoice.isPaid) "Paid" else "Due", if (invoice.isPaid) ZTone.SUCCESS else ZTone.DANGER)
            }
        }
    }
}

/// Full invoice: line items, fees/taxes, who billed whom, and pay.
@Composable
fun InvoiceDetailScreen(invoiceId: Int, area: JobArea, onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    var state by remember { mutableStateOf<Loadable<Invoice>>(Loadable.Loading) }
    val areaKey = if (area.kind == JobArea.Kind.BUSINESS) Areas.BUSINESS else Areas.CUSTOMER

    suspend fun load() { state = state.reloaded { environment.client.send(area.invoiceDetail(invoiceId)).invoice } }
    LaunchedEffect(invoiceId) { load() }

    Column {
        ZTopBar("Invoice", onBack = onBack, actions = {
            // The web's "Print / save PDF" — here a share of the rendered
            // document (Files, Drive, Mail all ride the share sheet).
            state.value?.let { invoice ->
                IconButton(onClick = { scope.launch { InvoicePdf.share(context, invoice) } }) { Icon(Icons.Outlined.Share, contentDescription = "Share PDF", tint = colors.ink) }
            }
        })
        ZScreen {
            ZLoadable(state, retry = { scope.launch { load() } }) { invoice ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ZBadge(if (invoice.isPaid) "Paid" else "Due", if (invoice.isPaid) ZTone.SUCCESS else ZTone.DANGER)
                        ZTitle(invoice.detail?.job?.title ?: invoice.job?.title ?: "Invoice")
                        ZCaption(listOfNotNull(invoice.number, invoice.detail?.company?.name ?: invoice.job?.company).joinToString(" · "))
                    }

                    InvoiceBreakdown(invoice)

                    invoice.detail?.let { detail ->
                        ZCard {
                            Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                                // ZiVETT issues the document; the pro appears by name + logo only below.
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Image(painterResource(R.drawable.zivett_wordmark), contentDescription = "ZiVETT", modifier = Modifier.height(20.dp), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
                                    ZCaption("Bookings, payments & support · zivett.com", tone = ZTextTone.SOFT)
                                }
                                detail.company?.let { company ->
                                    ZDivider()
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        ZMono("Service by")
                                        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                                            if (company.logoUrl != null) AsyncImage(model = company.logoUrl, contentDescription = null, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                            else ZAvatar(initialsOf(company.name), ZAvatarShape.COMPANY, 28.dp)
                                            ZBodyStrong(company.name)
                                        }
                                        invoice.gstNumber?.let { ZCaption("GST # $it") }
                                    }
                                }
                                ZDivider()
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ZMono("Billed to")
                                    ZBodyStrong(detail.billedTo.name ?: "")
                                    listOfNotNull(detail.billedTo.address, detail.billedTo.unit?.let { "Unit $it" }).filter { it.isNotEmpty() }.forEach { ZCaption(it, tone = ZTextTone.SOFT) }
                                }
                            }
                        }
                    }

                    if (!invoice.isPaid) {
                        ZButton("Pay ${Money.format(invoice.amountDueCents)}") { nav.navigate(PayInvoiceRoute(invoice.id, areaKey)) }
                    } else invoice.paidAt?.let {
                        ZCaption("Paid ${Dates.shortTime(it)}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }
}

/// Pay an invoice: optional coupon, optional tip, then settle with the
/// card on file — or add one right here (PaymentSheet on the billing
/// context's SetupIntent → `POST /api/billing/card`), so a booker who
/// never accepted a quote in the app isn't dead-ended.
class PayInvoiceModel(
    val invoice: Invoice,
    private val client: ApiClient,
    private val area: JobArea = JobArea.customer,
    private val context: Context? = null,
    /// Runs the bank's confirmation on a 409 `payment_action_required`.
    private val challenger: PaymentChallenger? = context?.let { StripeChallenger(it) },
) {
    var couponCode by mutableStateOf("")
    var customTip by mutableStateOf("")
    var paying by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    /// The bank's message from a declined charge — the card section
    /// leads with it and promotes "Use a different card".
    var declined by mutableStateOf<String?>(null)
    /// The card that will be charged, changeable on every pay surface.
    val card = PaymentCardModel(client, context)
    var fieldErrors by mutableStateOf<Map<String, String>>(emptyMap())

    // One dollar input, strictly opt-in — no suggested amounts on
    // purpose: preset pills read as an expectation, and a tip isn't one
    // (mirrors the web's TipPicker). Clamped to the API's $1,000 ceiling.
    val tipCents: Int
        get() {
            val dollars = customTip.toBigDecimalOrNull() ?: return 0
            return minOf(100_000, maxOf(0, dollars.multiply(BigDecimal(100)).toInt()))
        }

    val totalCents: Int get() = invoice.amountDueCents + tipCents

    val canPay: Boolean get() = !paying && card.canCharge && tipCents <= 100_000

    suspend fun load() { card.load() }

    /// NonCancellable: leaving the screen mid-charge used to cancel the
    /// request and surface a failure about a card that may have been
    /// charged. (`paying` doubles as the re-entrancy guard via `canPay`.)
    suspend fun pay(): Invoice? {
        if (!canPay) return null
        paying = true; error = null; declined = null; fieldErrors = emptyMap()
        return withContext(NonCancellable) { payNow() }
    }

    private suspend fun payNow(): Invoice? {
        try {
            return client.send(area.payInvoice(invoice.id, couponCode.ifEmpty { null }?.uppercase(), tipCents)).invoice
        } catch (apiError: ApiError) {
            // Bank confirmation: run it in-app, then poll — the
            // payment_intent.succeeded webhook settles asynchronously.
            val action = apiError.paymentAction
            if (action != null) {
                val key = card.publishableKey
                val challenger = challenger
                if (key == null || challenger == null) {
                    error = ChallengeOutcome.UNAVAILABLE_COPY
                } else when (val outcome = challenger.confirm(key, action)) {
                    ChallengeOutcome.Succeeded -> { pollUntilPaid()?.let { return it }; error = ChallengeOutcome.SETTLING_COPY }
                    ChallengeOutcome.Canceled -> error = ChallengeOutcome.CANCELED_COPY
                    // The bank said no — same way out as a decline.
                    is ChallengeOutcome.Failed -> declined = outcome.message ?: ChallengeOutcome.FAILED_COPY
                    // The result never reached us: look before claiming
                    // anything about whether the card was charged.
                    ChallengeOutcome.Unknown -> { pollUntilPaid()?.let { return it }; error = ChallengeOutcome.UNKNOWN_COPY }
                }
            } else if (apiError.paymentDeclinedMessage != null) {
                // A declined card is not a dead end: keep the screen up
                // and let them swap cards right here.
                declined = apiError.paymentDeclinedMessage
            } else if (apiError is ApiError.Validation && apiError.errors.errors.isNotEmpty()) {
                fieldErrors = apiError.errors.firstMessages
            } else {
                error = apiError.userMessage
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.userMessage
        } finally {
            paying = false
        }
        return null
    }

    private suspend fun pollUntilPaid(): Invoice? {
        repeat(5) {
            delay(2000)
            val fresh = runCatching { client.send(area.invoiceDetail(invoice.id)).invoice }.getOrNull()
            if (fresh != null && fresh.isPaid) return fresh
        }
        return null
    }
}

@Composable
fun PayInvoiceScreen(invoiceId: Int, area: JobArea, onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var invoiceState by remember { mutableStateOf<Loadable<Invoice>>(Loadable.Loading) }
    LaunchedEffect(invoiceId) { invoiceState = invoiceState.reloaded { environment.client.send(area.invoiceDetail(invoiceId)).invoice } }

    // Nobody walks away from a charge in flight: back (gesture and
    // arrow) waits for the answer.
    var paying by remember { mutableStateOf(false) }
    BackHandler(enabled = paying) {}

    Column {
        ZTopBar("Pay", onBack = { if (!paying) onBack() })
        ZScreen {
            ZLoadable(invoiceState, retry = { scope.launch { invoiceState = invoiceState.reloaded { environment.client.send(area.invoiceDetail(invoiceId)).invoice } } }) { invoice ->
                val model = remember(invoice.id) { PayInvoiceModel(invoice, environment.client, area, context) }
                LaunchedEffect(model) { model.load() }
                paying = model.paying
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    ZPageTitle("Pay invoice", invoice.job?.title)
                    model.error?.let { ZBanner(it, tone = ZTone.DANGER) }
                    InvoiceBreakdown(invoice)
                    if (Features.couponsEnabled) {
                        ZTextField("Coupon code", model.couponCode, { model.couponCode = it.uppercase() }, placeholder = "Optional", error = model.fieldErrors["coupon_code"], capitalization = KeyboardCapitalization.Characters)
                    }
                    ZTextField("Tip your pro (optional)", model.customTip, { model.customTip = it }, placeholder = "0.00", error = model.fieldErrors["tip_cents"], keyboardType = KeyboardType.Decimal, corner = { ZCaption("100% goes to your pro") })
                    PaymentCardSection(model.card, declined = model.declined) { model.declined = null }
                    ZActionBand("Pay ${Money.format(model.totalCents)}", loading = model.paying, enabled = model.canPay) {
                        scope.launch { if (model.pay() != null) onBack() }
                    }
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }
}

/// Renders an invoice to a shareable PDF — the app's answer to the web's
/// print-styled `InvoiceSheet` + "Print / save PDF". One US-letter page;
/// the share sheet covers Files, Drive, Mail and print services. The
/// document is deliberately paper-white with ink text in both themes.
object InvoicePdf {
    suspend fun share(context: Context, invoice: Invoice) {
        val file = withContext(Dispatchers.IO) { render(context, invoice) } ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    fun render(context: Context, invoice: Invoice): File? = runCatching {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(612, 792, 1).create())
        draw(context, page.canvas, invoice)
        document.finishPage(page)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val name = (invoice.number ?: "invoice").replace("/", "-")
        val file = File(dir, "$name.pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        file
    }.getOrNull()

    private fun draw(context: Context, canvas: Canvas, invoice: Invoice) {
        val ink = AndroidColor.parseColor("#16181D")
        val muted = AndroidColor.parseColor("#8A8578")
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 11f }
        val strong = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 9f }
        val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 11f; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.RIGHT }
        val stamp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor(if (invoice.isPaid) "#1C8C3E" else "#C0492B"); textSize = 22f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textAlign = Paint.Align.RIGHT
        }
        val left = 36f; val right = 576f
        var y = 60f
        // The gold wordmark, 22pt tall on the text baseline (iOS InvoicePDF draws it at the same height).
        val wordmark = BitmapFactory.decodeResource(context.resources, R.drawable.zivett_wordmark)
        val wordmarkHeight = 22f
        canvas.drawBitmap(wordmark, null, RectF(left, y - wordmarkHeight, left + wordmarkHeight * wordmark.width / wordmark.height, y), Paint(Paint.FILTER_BITMAP_FLAG))
        wordmark.recycle()
        canvas.drawText(if (invoice.isPaid) "PAID" else "DUE", right, y, stamp)
        y += 14f
        canvas.drawText("Bookings, payments & support · zivett.com", left, y, small)
        y += 36f
        canvas.drawText(invoice.detail?.job?.title ?: invoice.job?.title ?: "Invoice", left, y, Paint(strong).apply { textSize = 15f })
        y += 16f
        canvas.drawText(listOfNotNull(invoice.number, invoice.issuedAt?.let { Dates.short(it) }).joinToString(" · "), left, y, small)
        y += 28f

        fun row(label: String, cents: Int, bold: Boolean = false) {
            canvas.drawText(label, left, y, if (bold) strong else body)
            canvas.drawText((if (cents < 0) "−" else "") + Money.format(kotlin.math.abs(cents)), right, y, if (bold) Paint(mono).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) } else mono)
            y += 18f
        }
        for (item in invoice.lineItems ?: emptyList()) row(item.label, item.amount)
        canvas.drawLine(left, y - 10f, right, y - 10f, Paint().apply { color = AndroidColor.parseColor("#E3E1DA") })
        row("Service subtotal", invoice.totalCents, bold = true)
        val feeBps = invoice.customerFeeBps; val fee = invoice.customerFeeCents
        if (feeBps != null && fee != null) row("Booking & support fee (${JobPresentation.percent(feeBps)})", fee)
        invoice.gstCents?.takeIf { invoice.gstBps != null }?.let { row("GST", it + (invoice.customerFeeGstCents ?: 0)) }
        invoice.pstCents?.takeIf { invoice.pstBps != null && it > 0 }?.let { row("PST", it) }
        invoice.tipCents?.takeIf { it > 0 }?.let { row("Tip", it) }
        invoice.referralCreditCents?.takeIf { it > 0 }?.let { row("Referral credit", -it) }
        canvas.drawLine(left, y - 10f, right, y - 10f, Paint().apply { color = AndroidColor.parseColor("#E3E1DA") })
        row("Total", invoice.amountDueCents, bold = true)
        invoice.refundedCents?.takeIf { it > 0 }?.let { row("Refunded", -it) }

        invoice.detail?.let { detail ->
            y += 20f
            var x = left
            detail.company?.let { company ->
                canvas.drawText("SERVICE BY", x, y, Paint(small).apply { typeface = Typeface.DEFAULT_BOLD })
                canvas.drawText(company.name, x, y + 14f, strong)
                invoice.gstNumber?.let { canvas.drawText("GST # $it", x, y + 27f, small) }
                x += 240f
            }
            canvas.drawText("BILLED TO", x, y, Paint(small).apply { typeface = Typeface.DEFAULT_BOLD })
            canvas.drawText(detail.billedTo.name ?: "", x, y + 14f, strong)
            detail.billedTo.address?.let { canvas.drawText(it, x, y + 27f, small) }
            y += 48f
        }
        invoice.paidAt?.let { canvas.drawText("Paid ${Dates.shortTime(it)}", left, y + 10f, small) }
    }
}
