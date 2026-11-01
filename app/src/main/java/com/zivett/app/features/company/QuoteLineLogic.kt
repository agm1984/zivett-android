package com.zivett.app.features.company

import com.zivett.app.core.models.Quote
import com.zivett.app.core.models.QuoteLineBody
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

/// A quote line's kinds and price firmness, mirroring the web's `lib/quotes.js`.
enum class QuoteLineKind(val value: String, val label: String) {
    LABOUR("labour", "Labour"),
    FLAT("flat", "Flat price"),
    MATERIAL("material", "Materials"),
    FEE("fee", "Fee"),
    DISCOUNT("discount", "Discount");

    companion object { fun of(value: String) = entries.firstOrNull { it.value == value } }
}

enum class QuotePricingKind(val value: String, val label: String, val detail: String) {
    FIXED("fixed", "Fixed price", "This is the price. Anything extra needs the customer to approve a change."),
    ESTIMATE("estimate", "Estimate", "Your best estimate. The price may change once you see the job, through approved changes."),
    SITE_VISIT("site_visit", "Site visit first", "Charge only for the visit, then send the real price as a change for the customer to approve.");

    companion object { fun of(value: String?) = entries.firstOrNull { it.value == value } }
}

/// One row being edited. Quantity and price stay text while typing. A labour
/// row follows hours × crew until someone types its quantity.
data class QuoteLineDraft(
    val id: String = UUID.randomUUID().toString(),
    val kind: QuoteLineKind = QuoteLineKind.MATERIAL,
    val description: String = "",
    val quantity: String = "1",
    val unit: String = "",
    val followsTime: Boolean = false,
)

object QuoteLineLogic {
    const val MIN_TOTAL_CENTS = 5000
    const val MAX_TOTAL_CENTS = 10_000_000

    private fun number(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

    fun unitCents(line: QuoteLineDraft): Int = ((number(line.unit) ?: 0.0) * 100).roundToInt()

    /// Rounded like the server (`QuoteLines::normalize`); a discount subtracts.
    fun cents(line: QuoteLineDraft): Int {
        val amount = ((number(line.quantity) ?: 0.0) * unitCents(line)).roundToInt()
        return if (line.kind == QuoteLineKind.DISCOUNT) -amount else amount
    }

    fun total(lines: List<QuoteLineDraft>): Int = lines.sumOf { cents(it) }

    fun quantityText(value: Double): String {
        val rounded = (value * 100).roundToInt() / 100.0
        return if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString() else rounded.toString()
    }

    fun dollarsText(cents: Int): String =
        if (cents % 100 == 0) (cents / 100).toString() else "%.2f".format(java.util.Locale.US, cents / 100.0)

    fun starterLine(rateCents: Int?) = QuoteLineDraft(
        kind = QuoteLineKind.LABOUR, description = "Labour", quantity = "",
        unit = rateCents?.let { dollarsText(it) } ?: "", followsTime = true,
    )

    /// The rows for a new quote, or to revise a sent one. A quote with no
    /// lines reads as a single labour row at its own rate.
    fun drafts(quote: Quote?, rateCents: Int?): List<QuoteLineDraft> {
        if (quote == null) return listOf(starterLine(rateCents))
        val personHours = (quote.estimatedHours ?: 0.0) * (quote.crewSize ?: 1)
        val lines = quote.lineItems
        if (lines.isNullOrEmpty()) {
            return listOf(starterLine(quote.hourlyRateCents ?: rateCents).copy(quantity = if (personHours > 0) quantityText(personHours) else ""))
        }
        return lines.map { line ->
            val kind = QuoteLineKind.of(line.type) ?: QuoteLineKind.FEE
            QuoteLineDraft(
                kind = kind, description = line.description, quantity = quantityText(line.quantity),
                unit = dollarsText(line.unitCents), followsTime = kind == QuoteLineKind.LABOUR && line.quantity == personHours,
            )
        }
    }

    /// What's stopping the quote being sent, as the button's words. Null when ready.
    fun problem(lines: List<QuoteLineDraft>, hours: Double, needsWindow: Boolean): String? {
        if (lines.isEmpty()) return "Add a line item"
        if (hours < 0.5) return "Set estimated hours"
        if (lines.any { it.description.isBlank() || (number(it.quantity) ?: 0.0) <= 0.0 }) return "Finish your line items"
        val total = total(lines)
        if (total < MIN_TOTAL_CENTS || total > MAX_TOTAL_CENTS) return "Total must be \$50 to \$100,000"
        if (needsWindow) return "Pick an arrival window"
        return null
    }

    fun body(lines: List<QuoteLineDraft>): List<QuoteLineBody> = lines.map {
        QuoteLineBody(it.kind.value, it.description.trim(), number(it.quantity) ?: 0.0, unitCents(it))
    }

    fun dateText(daysFromNow: Long, today: LocalDate = LocalDate.now()): String = today.plusDays(daysFromNow).toString()
}
