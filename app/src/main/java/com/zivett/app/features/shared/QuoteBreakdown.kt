package com.zivett.app.features.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zivett.app.core.models.Quote
import com.zivett.app.core.models.QuoteLine
import com.zivett.app.core.Money
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZType
import com.zivett.app.features.company.QuoteLineKind
import com.zivett.app.features.company.QuoteLineLogic
import com.zivett.app.features.company.QuotePricingKind
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.customer.jobs.hoursLabel

/// How a sent quote reads, on every screen.
object QuotePresentation {
    fun detail(line: QuoteLine): String? {
        val type = QuoteLineKind.of(line.type)?.label ?: line.type
        val parts = listOfNotNull(
            type.takeUnless { line.description.equals(it, ignoreCase = true) },
            "${QuoteLineLogic.quantityText(line.quantity)} × ${Money.format(line.unitCents)}".takeUnless { line.quantity == 1.0 },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun time(quote: Quote): String? {
        val hours = quote.estimatedHours ?: return null
        val days = quote.estimatedDays?.let { ", over $it ${if (it == 1) "day" else "days"}" } ?: ""
        return "${hours.hoursLabel()} hours with a crew of ${quote.crewSize ?: 1}$days"
    }

    /// The short line where there's no room for the full breakdown.
    fun summary(quote: Quote): String? {
        val lines = quote.lineItems
        if (!lines.isNullOrEmpty()) {
            val kind = QuotePricingKind.of(quote.pricingType)?.label ?: "Quote"
            return "$kind · ${lines.size} ${if (lines.size == 1) "item" else "items"}"
        }
        return legacy(quote)
    }

    fun legacy(quote: Quote): String? {
        val rate = quote.hourlyRateCents ?: return null
        val hours = quote.estimatedHours ?: return null
        val crew = quote.crewSize ?: return null
        return "$crew × ${hours.hoursLabel()} h × ${Money.format(rate)}/hr"
    }
}

/// One quote in full, the web's `QuoteBreakdown.vue`: every line, the total and
/// the terms. Commission never reaches a booker, so it isn't here.
@Composable
fun QuoteBreakdown(quote: Quote, modifier: Modifier = Modifier) {
    val colors = ZTheme.colors
    val lines = quote.lineItems.orEmpty()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (lines.isEmpty()) {
            QuotePresentation.legacy(quote)?.let { ZCaption(it) }
        } else {
            for (line in lines) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(line.description, style = ZType.body, color = colors.ink)
                        QuotePresentation.detail(line)?.let { ZCaption(it) }
                    }
                    Text(
                        (if (line.amountCents < 0) "−" else "") + Money.format(kotlin.math.abs(line.amountCents)),
                        style = ZType.body, color = if (line.amountCents < 0) colors.success else colors.ink,
                    )
                }
                ZDivider()
            }
            Row {
                ZBodyStrong("Quote total", modifier = Modifier.weight(1f))
                ZBodyStrong(Money.format(quote.amountCents))
            }
        }
        QuotePricingKind.of(quote.pricingType)?.let { Term("Price type", it.label) }
        if (lines.isNotEmpty()) QuotePresentation.time(quote)?.let { Term("Time", it) }
        quote.proposedDate?.let { Term("Can arrive", JobPresentation.windowSlot(it, quote.proposedWindow)) }
        quote.validUntil?.let { Term("Valid until", it + if (quote.expired == true) " (expired)" else "") }
        quote.exclusions?.takeIf { it.isNotEmpty() }?.let { Term("Not included", it) }
        quote.message?.takeIf { it.isNotEmpty() }?.let { Term("Note", it) }
    }
}

@Composable
private fun Term(label: String, value: String) {
    Column {
        ZCaption(label)
        Text(value, style = ZType.body, color = ZTheme.colors.ink)
    }
}
