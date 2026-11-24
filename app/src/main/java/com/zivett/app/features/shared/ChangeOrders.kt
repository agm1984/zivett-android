package com.zivett.app.features.shared

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zivett.app.core.Money
import com.zivett.app.core.models.ChangeOrder
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.QuoteLine
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZFlowRow
import com.zivett.app.design.ZRadius
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZType
import com.zivett.app.features.company.QuoteLineDraft
import com.zivett.app.features.company.QuoteLineKind
import com.zivett.app.features.company.QuoteLineLogic
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/// Who's reading a change: the booker decides a waiting one, the company waits.
enum class ChangeOrderAudience { BOOKER, COMPANY }

/// A row of the booker's accepted-quote breakdown that came from an approved change.
data class ChangeLine(val description: String, val detail: String?, val amountCents: Int)

/// Itemized change orders (web `ChangeOrderModal.vue` + `ChangeOrderSummary.vue`):
/// the pro's form rules and how one change reads on every side. Pure, so tested.
object ChangeOrderLogic {
    /// The first row of a new change: more labour at the accepted quote's rate.
    fun starterLine(rateCents: Int?) = QuoteLineDraft(
        kind = QuoteLineKind.LABOUR, description = "Extra labour", quantity = "",
        unit = rateCents?.let { QuoteLineLogic.dollarsText(it) } ?: "",
    )

    /// Picking Labour on a row with no price fills in the quote's rate.
    fun withKind(line: QuoteLineDraft, kind: QuoteLineKind, rateCents: Int?): QuoteLineDraft {
        val unit = if (kind == QuoteLineKind.LABOUR && line.unit.isBlank() && rateCents != null) QuoteLineLogic.dollarsText(rateCents) else line.unit
        return line.copy(kind = kind, unit = unit)
    }

    fun hasMaterials(lines: List<QuoteLineDraft>): Boolean =
        lines.any { it.kind == QuoteLineKind.MATERIAL && QuoteLineLogic.cents(it) > 0 }

    fun approvedCents(job: Job): Int = job.changeOrders.orEmpty().filter { it.isApproved }.sumOf { it.amountCents }

    /// The agreed price right now (accepted quote + approved changes), or null without one.
    fun priceNow(job: Job): Int? = job.acceptedQuote?.let { it.amountCents + approvedCents(job) }

    /// The line under the pro's Agreed price figure, worded as the web's
    /// company job page: "$500.00 quote + $30.00 in approved changes."
    fun agreedDetail(quoteCents: Int, changesCents: Int): String =
        if (changesCents == 0) "Your accepted quote. Scope or price changes go through a change order."
        else "${Money.format(quoteCents)} quote ${if (changesCents > 0) "+" else "−"} ${Money.format(kotlin.math.abs(changesCents))} in approved changes."

    /// Mirrors `ChangeOrders::propose`: credits apply at once, materials at cost
    /// always wait, and small additions apply while the job's allowance lasts.
    fun appliesNow(totalCents: Int, materialsAtCost: Boolean, autoLeftCents: Int?): Boolean {
        if (totalCents < 0) return true
        if (materialsAtCost) return false
        val left = autoLeftCents ?: 0
        return left > 0 && totalCents <= left
    }

    /// What's stopping it being sent, as the button's words. Null when ready.
    fun problem(label: String, lines: List<QuoteLineDraft>, priceNow: Int?): String? {
        if (label.isBlank()) return "Say what's changing"
        if (lines.isEmpty()) return "Add a line item"
        if (lines.any { it.description.isBlank() || (it.quantity.trim().replace(',', '.').toDoubleOrNull() ?: 0.0) <= 0.0 }) return "Finish your line items"
        val total = QuoteLineLogic.total(lines)
        if (total == 0) return "The lines add up to \$0"
        if (priceNow != null && priceNow + total < 0) return "That takes off more than the job costs"
        return null
    }

    /// The summary box's last line: whether it applies now or waits.
    fun outcomeLine(totalCents: Int, appliesNow: Boolean): String {
        val first = when {
            appliesNow && totalCents < 0 -> "This applies straight away and the booker is told."
            appliesNow -> "This is small enough to apply straight away. The booker is told, not asked."
            else -> "The booker approves this on their job page before it's added."
        }
        return "$first It bills with your final invoice."
    }

    /// A 422's words for the form: the label or note first, then the lines
    /// ("Line 2: …" for a `line_items.1.quantity` key). Null when there are none.
    fun errorMessage(errors: Map<String, List<String>>): String? {
        for (key in listOf("label", "note", "line_items", "materials")) errors[key]?.firstOrNull()?.let { return it }
        val lineKey = errors.keys.filter { it.startsWith("line_items.") }.minByOrNull { it.split('.').getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE }
            ?: return errors.values.firstNotNullOfOrNull { it.firstOrNull() }
        val message = errors[lineKey]?.firstOrNull() ?: return null
        val index = lineKey.split('.').getOrNull(1)?.toIntOrNull() ?: return message
        return "Line ${index + 1}: $message"
    }

    fun buttonLabel(appliesNow: Boolean) = if (appliesNow) "Apply change" else "Send for approval"

    /// The toast once the server has it, by what it decided.
    fun sentMessage(order: ChangeOrder): String = when {
        order.isApproved && order.isCredit -> "Change applied. The booker has been told."
        order.isApproved -> "Change applied straight away. The booker has been told."
        else -> "Change sent to the booker for approval"
    }

    data class Status(val label: String, val tone: ZTone)

    fun status(order: ChangeOrder, audience: ChangeOrderAudience): Status = when (order.status) {
        "proposed" -> Status(if (audience == ChangeOrderAudience.BOOKER) "Needs your approval" else "Waiting for the booker", ZTone.WARNING)
        "declined" -> Status("Declined", ZTone.DANGER)
        else -> when {
            !order.autoApproved -> Status("Approved", ZTone.SUCCESS)
            order.isCredit -> Status("Applied: price reduced", ZTone.SUCCESS)
            else -> Status("Applied straight away", ZTone.SUCCESS)
        }
    }

    /// "+$120.00" / "−$40.00".
    fun signedAmount(cents: Int): String = (if (cents < 0) "−" else "+") + Money.format(kotlin.math.abs(cents))

    /// "Adds $X to the price, including 2 hours of labour. Materials are billed at cost."
    fun effect(order: ChangeOrder): String {
        val amount = Money.format(kotlin.math.abs(order.amountCents))
        val hours = order.lineItems.orEmpty().filter { it.type == QuoteLineKind.LABOUR.value }.sumOf { it.quantity }
        val including = if (hours > 0) ", including ${QuoteLineLogic.quantityText(hours)} ${if (hours == 1.0) "hour" else "hours"} of labour" else ""
        val main = if (order.isCredit) "Takes $amount off the price$including." else "Adds $amount to the price$including."
        return if (order.materials) "$main Materials are billed at cost." else main
    }

    private val sentDay = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun sentLine(order: ChangeOrder, zone: ZoneId = ZoneId.systemDefault()): String? =
        order.createdAt?.let { "Sent ${sentDay.withZone(zone).format(it)}" }

    /// Approved changes, oldest first, as rows under the accepted quote's lines:
    /// each line of an itemized change, or the one line of an older change.
    fun approvedLines(job: Job): List<ChangeLine> = job.changeOrders.orEmpty()
        .filter { it.isApproved }
        .sortedBy { it.id }
        .flatMap { order ->
            val label = order.label ?: "Scope change"
            val lines = order.lineItems
            if (lines.isNullOrEmpty()) {
                listOf(ChangeLine(label, "Change", order.amountCents))
            } else {
                lines.map { line ->
                    ChangeLine(
                        line.description,
                        listOfNotNull("Change: $label", QuotePresentation.detail(line, changeOrder = true)).joinToString(" · "),
                        line.amountCents,
                    )
                }
            }
        }
}

/// One change order, the same for the pro and the booker: what changed, its
/// status and date, why, its lines like a quote's, and what it does to the
/// price. Actions (approve / decline) go in [actions].
@Composable
fun ChangeOrderSummary(order: ChangeOrder, audience: ChangeOrderAudience, actions: @Composable () -> Unit = {}) {
    val colors = ZTheme.colors
    val status = ChangeOrderLogic.status(order, audience)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZBodyStrong(order.label ?: "Scope change", modifier = Modifier.weight(1f))
            Text(ChangeOrderLogic.signedAmount(order.amountCents), style = ZType.bodyStrong, color = if (order.isCredit) colors.success else colors.ink)
        }
        ZFlowRow(spacing = 8.dp) {
            ZBadge(status.label, status.tone)
            ChangeOrderLogic.sentLine(order)?.let { ZCaption(it, tone = ZTextTone.FAINT) }
        }
        order.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = ZType.body, color = colors.inkSoft) }
        val lines = order.lineItems.orEmpty()
        if (lines.isNotEmpty()) {
            val shape = RoundedCornerShape(ZRadius.card)
            Column(modifier = Modifier.fillMaxWidth().border(1.dp, colors.border, shape).padding(horizontal = 12.dp, vertical = 4.dp)) {
                lines.forEachIndexed { index, line ->
                    if (index > 0) ZDivider()
                    ChangeLineRow(line)
                }
            }
            ZCaption(ChangeOrderLogic.effect(order), tone = ZTextTone.SOFT)
        } else if (order.materials) {
            ZCaption("Materials are billed at cost.", tone = ZTextTone.SOFT)
        }
        actions()
    }
}

@Composable
private fun ChangeLineRow(line: QuoteLine) {
    val colors = ZTheme.colors
    Row(modifier = Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(line.description, style = ZType.body, color = colors.ink)
            QuotePresentation.detail(line, changeOrder = true)?.let { ZCaption(it) }
        }
        Text(
            (if (line.amountCents < 0) "−" else "") + Money.format(kotlin.math.abs(line.amountCents)),
            style = ZType.body, color = if (line.amountCents < 0) colors.success else colors.ink,
        )
    }
}
