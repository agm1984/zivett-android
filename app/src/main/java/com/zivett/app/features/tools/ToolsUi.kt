package com.zivett.app.features.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zivett.app.core.Money
import com.zivett.app.core.models.OwnJobStatus
import com.zivett.app.core.models.ProDocumentRow
import com.zivett.app.core.models.ProJobRow
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZMonoBody
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTone
import com.zivett.app.features.company.MyDayLogic
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/// Status badges and date wording for the business tools screens (web
/// `lib/businessTools.js` `quoteStatusMeta` / `jobStatusMeta` /
/// `invoiceStatusMeta`, `jobWhen`).
object ToolsFormat {
    fun quoteStatus(status: String): Pair<String, ZTone> = when (status) {
        "draft" -> "Draft" to ZTone.NEUTRAL
        "sent" -> "Sent" to ZTone.INFO
        "accepted" -> "Accepted" to ZTone.SUCCESS
        "declined" -> "Declined" to ZTone.NEUTRAL
        "expired" -> "Expired" to ZTone.WARNING
        else -> status.replaceFirstChar { it.uppercase() } to ZTone.NEUTRAL
    }

    fun invoiceStatus(status: String): Pair<String, ZTone> = when (status) {
        "draft" -> "Draft" to ZTone.NEUTRAL
        "unpaid" -> "Unpaid" to ZTone.INFO
        "partially_paid" -> "Partly paid" to ZTone.WARNING
        "overdue" -> "Overdue" to ZTone.DANGER
        "paid" -> "Paid" to ZTone.SUCCESS
        "void" -> "Void" to ZTone.NEUTRAL
        else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() } to ZTone.NEUTRAL
    }

    fun jobStatus(status: String): Pair<String, ZTone> = OwnJobStatus.label(status) to when (status) {
        OwnJobStatus.SCHEDULED -> ZTone.INFO
        OwnJobStatus.IN_PROGRESS -> ZTone.WARNING
        OwnJobStatus.COMPLETED -> ZTone.SUCCESS
        else -> ZTone.NEUTRAL
    }

    private val windowNames = mapOf("morning" to "Morning", "afternoon" to "Afternoon", "evening" to "Evening")
    private val weekday = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.CANADA)
    private val longDay = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.CANADA)

    fun windowName(window: String?): String = windowNames[window].orEmpty()

    /// "1:30 pm" or "Morning".
    fun timeLabel(startTime: String?, window: String?): String = startTime?.let { MyDayLogic.formatClock(it) }?.takeIf { it.isNotBlank() } ?: windowName(window)

    /// "Tue, Nov 10 · 1:30 pm", or "Not booked yet".
    fun jobWhen(date: String?, startTime: String?, window: String?): String {
        val day = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return "Not booked yet"
        val time = timeLabel(startTime, window)
        return listOf(day.format(weekday), time).filter { it.isNotBlank() }.joinToString(" · ")
    }

    fun jobWhen(job: ProJobRow): String = jobWhen(job.scheduledDate, job.startTime, job.scheduledWindow)

    /// "Nov 10, 2026" from a Y-m-d.
    fun day(date: String?): String = date?.let { runCatching { LocalDate.parse(it.take(10)).format(longDay) }.getOrNull() } ?: "No date"
    fun day(instant: Instant?): String = instant?.atZone(ZoneId.systemDefault())?.format(longDay) ?: "No date"

    /// Web `lib/timing.js` URGENCY_OPTIONS.
    fun urgency(value: String?): String? = when (value) {
        "asap" -> "Urgent, as soon as possible"
        "today" -> "Today"
        "this_week" -> "This week"
        "next_few_weeks" -> "In the next few weeks"
        "planning" -> "Not sure, still planning or budgeting"
        else -> null
    }

    fun documentNumber(row: ProDocumentRow): String = row.number ?: "Draft"
    fun money(cents: Int): String = Money.format(cents)
}

/// One row of a document list: number + status on the left, total right.
@Composable
fun DocumentLine(row: ProDocumentRow, invoice: Boolean, subtitle: String? = null) {
    val (label, tone) = if (invoice) ToolsFormat.invoiceStatus(row.status) else ToolsFormat.quoteStatus(row.status)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                ZMonoBody(ToolsFormat.documentNumber(row))
                ZBadge(label, tone)
            }
            subtitle?.let { ZCaption(it, maxLines = 1) }
        }
        ZBodyStrong(Money.format(row.totalCents))
    }
}

/// One own job in a list: title, when, status.
@Composable
fun JobLine(job: ProJobRow, showCustomer: Boolean = false) {
    val (label, tone) = ToolsFormat.jobStatus(job.status)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
        Column(Modifier.weight(1f)) {
            ZBodyStrong(job.title.ifBlank { "Job" }, maxLines = 1)
            ZCaption(listOfNotNull(if (showCustomer) job.customer?.name else null, ToolsFormat.jobWhen(job)).joinToString(" · "), maxLines = 1)
        }
        ZBadge(label, tone)
    }
}

/// A confirm dialog with the action named on its button (destructive
/// actions get their own confirm, never sit beside Save).
@Composable
fun ToolsConfirm(title: String, text: String, confirm: String, danger: Boolean = false, dismiss: String = "Cancel", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = if (danger) ZTheme.colors.danger else ZTheme.colors.link) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}

/// A phone or email someone taps (web `RpContactLink`).
@Composable
fun ToolsContact(value: String, phone: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    com.zivett.app.design.ZTextAction(value) {
        val uri = if (phone) com.zivett.app.features.company.CompanyPresentation.callUri(value) else "mailto:$value"
        uri?.let { runCatching { context.startActivity(android.content.Intent(if (phone) android.content.Intent.ACTION_DIAL else android.content.Intent.ACTION_SENDTO, android.net.Uri.parse(it))) } }
    }
}

/// A tappable list row inside a card.
@Composable
fun ClickRow(onClick: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = ZSpacing.xs),
    ) { content() }
}

/// A document's PDF, fetched with the bearer token and handed to the share
/// sheet (Files, Drive, Mail, print). Returns an error to show, or null.
object ToolsPdf {
    suspend fun share(context: android.content.Context, client: com.zivett.app.core.network.ApiClient, request: com.zivett.app.core.network.ApiRequest<ByteArray>, fileName: String): String? {
        val bytes = try { client.send(request) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { return e.message }
        val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
            java.io.File(dir, fileName.replace(Regex("[^A-Za-z0-9._-]"), "-")).apply { writeBytes(bytes) }
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val view = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Open it in a PDF viewer when there is one, with sharing beside it.
        val chooser = android.content.Intent.createChooser(send, null).putExtra(android.content.Intent.EXTRA_INITIAL_INTENTS, arrayOf(view))
        return runCatching { context.startActivity(chooser) }.exceptionOrNull()?.let { "Couldn't open the PDF." }
    }
}

/// The document as both sides read it (web `ProDocumentSheet`): billed to,
/// the lines, the taxes and the total, then terms and notes.
@Composable
fun DocumentSheetCard(document: com.zivett.app.core.models.ProDocument) {
    com.zivett.app.design.ZCard {
        document.billedTo?.let { to ->
            com.zivett.app.design.ZLabel(if (document.isInvoice) "Billed to" else "For")
            to.name?.let { ZBodyStrong(it) }
            listOfNotNull(to.address, to.unit?.let { com.zivett.app.features.tools.CustomerDetailLogic.unit(it) }, to.phone, to.email).forEach { ZCaption(it) }
        }
        com.zivett.app.design.ZDivider()
        for (line in document.lineItems) {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                Column(Modifier.weight(1f)) {
                    com.zivett.app.design.ZBody(line.description)
                    ZCaption("${com.zivett.app.features.company.QuoteLineLogic.quantityText(line.quantity)} × ${Money.format(line.unitCents)}")
                }
                com.zivett.app.design.ZBody((if (line.amountCents < 0) "−" else "") + Money.format(kotlin.math.abs(line.amountCents)))
            }
        }
        com.zivett.app.design.ZDivider()
        TotalRow("Subtotal", document.subtotalCents)
        if ((document.gstBps ?: 0) > 0) TotalRow("GST (${percent(document.gstBps ?: 0)})", document.gstCents)
        if ((document.pstBps ?: 0) > 0) TotalRow("PST (${percent(document.pstBps ?: 0)})", document.pstCents)
        TotalRow("Total", document.totalCents, strong = true)
        if (document.isInvoice && document.paidCents > 0) {
            TotalRow("Paid (${ToolsCopy.PRO_REPORTED.lowercase()})", -document.paidCents)
            TotalRow("Balance", document.balanceCents, strong = true)
        }
        document.gstNumber?.takeIf { (document.gstBps ?: 0) > 0 }?.let { ZCaption("GST number $it") }
        document.pricingType?.let { ZCaption(if (it == "estimate") "Estimate: the price may change with your approval." else "Fixed price for the work described.") }
        document.exclusions?.takeIf { it.isNotBlank() }?.let { com.zivett.app.design.ZLabel("Not included"); com.zivett.app.design.ZBody(it) }
        document.notes?.takeIf { it.isNotBlank() }?.let { com.zivett.app.design.ZLabel("Note"); com.zivett.app.design.ZBody(it) }
        document.paymentInstructions?.takeIf { it.isNotBlank() && document.isInvoice }?.let { com.zivett.app.design.ZLabel("How to pay"); com.zivett.app.design.ZBody(it) }
    }
}

@Composable
private fun TotalRow(label: String, cents: Int, strong: Boolean = false) {
    Row {
        if (strong) ZBodyStrong(label, Modifier.weight(1f)) else ZCaption(label, Modifier.weight(1f))
        val text = (if (cents < 0) "−" else "") + Money.format(kotlin.math.abs(cents))
        if (strong) ZBodyStrong(text) else com.zivett.app.design.ZBody(text)
    }
}

/// 500 bps → "5%", 750 → "7.5%".
fun percent(bps: Int): String = if (bps % 100 == 0) "${bps / 100}%" else "${bps / 100.0}%".replace(".0%", "%")

/// Every time a document was emailed (web `DocumentEmailsCard`).
object DocumentEmailLogic {
    fun label(email: com.zivett.app.core.models.DocumentEmail): String = when (email.kind) {
        "quote" -> "Quote"
        "invoice" -> "Invoice"
        "answer" -> "Confirmation of their answer"
        "follow_up" -> "Automatic follow-up"
        "overdue" -> "Automatic overdue reminder"
        "appointment" -> "Appointment reminder"
        "receipt" -> email.paymentCents?.let { "Receipt for ${Money.format(it)}" } ?: "Receipt"
        else -> "Email"
    }
}

@Composable
fun DocumentEmailsCard(emails: List<com.zivett.app.core.models.DocumentEmail>) {
    if (emails.isEmpty()) return
    com.zivett.app.design.ZCard {
        com.zivett.app.design.ZHeadline("Emailed")
        for (email in emails) {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                ZBodyStrong(DocumentEmailLogic.label(email), Modifier.weight(1f))
                when { email.failed -> ZBadge("Not sent", ZTone.DANGER); email.reported -> ZBadge("Reported", ZTone.DANGER); email.bounced -> ZBadge("Bounced", ZTone.WARNING) }
            }
            ZCaption(listOfNotNull(email.recipient?.let { "To $it" }, ToolsFormat.day(email.sentAt), email.sentBy).joinToString(" · "))
        }
    }
}
