package com.zivett.app

import com.zivett.app.core.models.Quote
import com.zivett.app.core.models.QuoteLine
import com.zivett.app.core.models.QuoteStatus
import com.zivett.app.features.company.QuoteLineDraft
import com.zivett.app.features.company.QuoteLineKind
import com.zivett.app.features.company.QuoteLineLogic
import com.zivett.app.features.shared.QuotePresentation
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteLineLogicTest {
    private fun line(kind: QuoteLineKind, quantity: String, unit: String, description: String = "Item") =
        QuoteLineDraft(kind = kind, description = description, quantity = quantity, unit = unit)

    @Test fun totalsEveryKindLikeTheServerAndADiscountSubtracts() {
        val lines = listOf(
            line(QuoteLineKind.LABOUR, "6", "120"),
            line(QuoteLineKind.FLAT, "1", "500"),
            line(QuoteLineKind.MATERIAL, "2.5", "19.99"),
            line(QuoteLineKind.FEE, "1", "45"),
            line(QuoteLineKind.DISCOUNT, "1", "100"),
        )
        assertEquals(121498, QuoteLineLogic.total(lines))
    }

    @Test fun theButtonNamesWhatIsMissing() {
        val ready = listOf(line(QuoteLineKind.FLAT, "1", "100"))
        assertEquals("Add a line item", QuoteLineLogic.problem(emptyList(), 1.0, false))
        assertEquals("Set estimated hours", QuoteLineLogic.problem(ready, 0.0, false))
        assertEquals("Finish your line items", QuoteLineLogic.problem(listOf(line(QuoteLineKind.FLAT, "1", "100", " ")), 1.0, false))
        assertEquals("Total must be \$50 to \$100,000", QuoteLineLogic.problem(listOf(line(QuoteLineKind.FLAT, "1", "30")), 1.0, false))
        assertEquals("Pick an arrival window", QuoteLineLogic.problem(ready, 1.0, true))
        assertNull(QuoteLineLogic.problem(ready, 1.0, false))
    }

    @Test fun aNewQuoteStartsWithLabourAtTheSavedRate() {
        val row = QuoteLineLogic.drafts(null, 12000).single()
        assertEquals(QuoteLineKind.LABOUR, row.kind)
        assertEquals("120", row.unit)
        assertTrue(row.followsTime)
    }

    @Test fun revisingKeepsTheLinesAndAnOlderQuoteBecomesOneLabourRow() {
        val sent = Quote(
            id = 1, amountCents = 76000, status = QuoteStatus.PENDING, estimatedHours = 3.0, crewSize = 2,
            lineItems = listOf(
                QuoteLine("labour", "Labour", 6.0, 12000, 72000),
                QuoteLine("material", "Part", 1.0, 4000, 4000),
            ),
        )
        val rows = QuoteLineLogic.drafts(sent, 12000)
        assertEquals(2, rows.size)
        assertTrue(rows[0].followsTime)
        assertEquals(76000, QuoteLineLogic.total(rows))

        val older = Quote(id = 2, amountCents = 24000, status = QuoteStatus.PENDING, hourlyRateCents = 12000, estimatedHours = 2.0, crewSize = 1)
        assertEquals(24000, QuoteLineLogic.total(QuoteLineLogic.drafts(older, 15000)))
    }

    @Test fun aSentLineSkipsATypeItIsNamedFor() {
        assertEquals("6 × \$120.00", QuotePresentation.detail(QuoteLine("labour", "Labour", 6.0, 12000, 72000)))
        assertEquals("Materials", QuotePresentation.detail(QuoteLine("material", "Faucet", 1.0, 20000, 20000)))
    }

    @Test fun validUntilIsDaysFromToday() {
        assertEquals("2026-11-15", QuoteLineLogic.dateText(14, LocalDate.of(2026, 11, 1)))
    }
}
