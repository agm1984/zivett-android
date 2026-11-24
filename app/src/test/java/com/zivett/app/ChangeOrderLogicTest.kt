package com.zivett.app

import com.zivett.app.core.models.ChangeOrder
import com.zivett.app.core.models.ChangeOrderBody
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.Quote
import com.zivett.app.core.models.QuoteLine
import com.zivett.app.core.models.QuoteStatus
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.design.ZTone
import com.zivett.app.features.company.QuoteLineDraft
import com.zivett.app.features.company.QuoteLineKind
import com.zivett.app.features.company.QuoteLineLogic
import com.zivett.app.features.shared.ChangeLine
import com.zivett.app.features.shared.ChangeOrderAudience
import com.zivett.app.features.shared.ChangeOrderLogic
import com.zivett.app.features.shared.QuotePresentation
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeOrderLogicTest {
    private fun line(kind: QuoteLineKind, quantity: String, unit: String, description: String = "Item") =
        QuoteLineDraft(kind = kind, description = description, quantity = quantity, unit = unit)

    private fun order(
        amount: Int, status: String = "approved", auto: Boolean = false, materials: Boolean = false,
        lines: List<QuoteLine>? = null, id: Int = 1, label: String = "Replace valve",
    ) = ChangeOrder(id = id, label = label, lineItems = lines, amountCents = amount, materials = materials, autoApproved = auto, status = status)

    private fun acceptedJob(changes: List<ChangeOrder> = emptyList()) = Fixtures.job(
        status = JobStatus.IN_PROGRESS,
        quotes = listOf(Quote(id = 1, amountCents = 50000, status = QuoteStatus.ACCEPTED, hourlyRateCents = 12000)),
    ).copy(changeOrders = changes)

    @Test fun aNewChangeStartsWithExtraLabourAtTheQuotesRate() {
        val row = ChangeOrderLogic.starterLine(12000)
        assertEquals(QuoteLineKind.LABOUR, row.kind)
        assertEquals("Extra labour", row.description)
        assertEquals("", row.quantity)
        assertEquals("120", row.unit)
        assertFalse(row.followsTime)
        assertEquals("", ChangeOrderLogic.starterLine(null).unit)
    }

    @Test fun pickingLabourFillsAnEmptyPriceOnly() {
        val empty = line(QuoteLineKind.MATERIAL, "1", "")
        assertEquals("120", ChangeOrderLogic.withKind(empty, QuoteLineKind.LABOUR, 12000).unit)
        assertEquals("80", ChangeOrderLogic.withKind(empty.copy(unit = "80"), QuoteLineKind.LABOUR, 12000).unit)
        assertEquals("", ChangeOrderLogic.withKind(empty, QuoteLineKind.FEE, 12000).unit)
    }

    @Test fun aCreditTotalsNegativeAndMaterialsCountOnlyWhenPositive() {
        val lines = listOf(line(QuoteLineKind.LABOUR, "2", "120"), line(QuoteLineKind.DISCOUNT, "1", "300"))
        assertEquals(-6000, QuoteLineLogic.total(lines))
        assertFalse(ChangeOrderLogic.hasMaterials(lines))
        assertTrue(ChangeOrderLogic.hasMaterials(lines + line(QuoteLineKind.MATERIAL, "1", "40")))
        assertFalse(ChangeOrderLogic.hasMaterials(listOf(line(QuoteLineKind.MATERIAL, "1", "0"))))
    }

    @Test fun theAgreedPriceIsTheQuotePlusApprovedChanges() {
        val job = acceptedJob(listOf(order(2000), order(-500, id = 2), order(9000, status = "proposed", id = 3), order(700, status = "declined", id = 4)))
        assertEquals(1500, ChangeOrderLogic.approvedCents(job))
        assertEquals(51500, ChangeOrderLogic.priceNow(job))
        assertNull(ChangeOrderLogic.priceNow(Fixtures.job()))
    }

    @Test fun theAgreedPriceCardSaysWhatTheChangesAdd() {
        assertEquals("\$500.00 quote + $30.00 in approved changes.", ChangeOrderLogic.agreedDetail(50000, 3000))
        assertEquals("\$500.00 quote − $25.00 in approved changes.", ChangeOrderLogic.agreedDetail(50000, -2500))
        assertEquals("Your accepted quote. Scope or price changes go through a change order.", ChangeOrderLogic.agreedDetail(50000, 0))
    }

    @Test fun appliesNowFollowsTheServersRule() {
        assertTrue("a credit always applies", ChangeOrderLogic.appliesNow(-500, materialsAtCost = true, autoLeftCents = 0))
        assertFalse("materials at cost always wait", ChangeOrderLogic.appliesNow(500, materialsAtCost = true, autoLeftCents = 2500))
        assertTrue(ChangeOrderLogic.appliesNow(2500, materialsAtCost = false, autoLeftCents = 2500))
        assertFalse(ChangeOrderLogic.appliesNow(2501, materialsAtCost = false, autoLeftCents = 2500))
        assertFalse("nothing left", ChangeOrderLogic.appliesNow(100, materialsAtCost = false, autoLeftCents = 0))
        assertFalse("older server", ChangeOrderLogic.appliesNow(100, materialsAtCost = false, autoLeftCents = null))
        assertEquals("Apply change", ChangeOrderLogic.buttonLabel(true))
        assertEquals("Send for approval", ChangeOrderLogic.buttonLabel(false))
    }

    @Test fun theOutcomeLineSaysWhetherItWaits() {
        assertEquals("This applies straight away and the booker is told. It bills with your final invoice.", ChangeOrderLogic.outcomeLine(-100, true))
        assertEquals("This is small enough to apply straight away. The booker is told, not asked. It bills with your final invoice.", ChangeOrderLogic.outcomeLine(100, true))
        assertEquals("The booker approves this on their job page before it's added. It bills with your final invoice.", ChangeOrderLogic.outcomeLine(100, false))
    }

    @Test fun theButtonNamesWhatIsMissing() {
        val ready = listOf(line(QuoteLineKind.FLAT, "1", "100"))
        assertEquals("Say what's changing", ChangeOrderLogic.problem(" ", ready, 50000))
        assertEquals("Add a line item", ChangeOrderLogic.problem("Valve", emptyList(), 50000))
        assertEquals("Finish your line items", ChangeOrderLogic.problem("Valve", listOf(line(QuoteLineKind.FLAT, "1", "100", " ")), 50000))
        assertEquals("Finish your line items", ChangeOrderLogic.problem("Valve", listOf(line(QuoteLineKind.LABOUR, "", "120")), 50000))
        assertEquals("The lines add up to \$0", ChangeOrderLogic.problem("Valve", listOf(line(QuoteLineKind.FLAT, "1", "0")), 50000))
        assertEquals("That takes off more than the job costs", ChangeOrderLogic.problem("Valve", listOf(line(QuoteLineKind.DISCOUNT, "1", "600")), 50000))
        assertNull("no agreed price to check against", ChangeOrderLogic.problem("Valve", listOf(line(QuoteLineKind.DISCOUNT, "1", "600")), null))
        assertNull(ChangeOrderLogic.problem("Valve", ready, 50000))
    }

    @Test fun statusWordsDependOnWhoIsReading() {
        assertEquals("Needs your approval", ChangeOrderLogic.status(order(100, "proposed"), ChangeOrderAudience.BOOKER).label)
        assertEquals("Waiting for the booker", ChangeOrderLogic.status(order(100, "proposed"), ChangeOrderAudience.COMPANY).label)
        assertEquals(ZTone.DANGER, ChangeOrderLogic.status(order(100, "declined"), ChangeOrderAudience.BOOKER).tone)
        assertEquals("Approved", ChangeOrderLogic.status(order(100), ChangeOrderAudience.BOOKER).label)
        assertEquals("Applied straight away", ChangeOrderLogic.status(order(100, auto = true), ChangeOrderAudience.COMPANY).label)
        assertEquals("Applied: price reduced", ChangeOrderLogic.status(order(-100, auto = true), ChangeOrderAudience.BOOKER).label)
    }

    @Test fun theEffectLineCountsLabourHoursAndMaterials() {
        val lines = listOf(QuoteLine("labour", "Extra labour", 2.0, 12000, 24000), QuoteLine("material", "Valve", 1.0, 4500, 4500))
        assertEquals("Adds \$285.00 to the price, including 2 hours of labour. Materials are billed at cost.", ChangeOrderLogic.effect(order(28500, lines = lines, materials = true)))
        assertEquals("Adds \$120.00 to the price, including 1 hour of labour.", ChangeOrderLogic.effect(order(12000, lines = listOf(QuoteLine("labour", "Labour", 1.0, 12000, 12000)))))
        assertEquals("Takes \$50.00 off the price.", ChangeOrderLogic.effect(order(-5000, lines = listOf(QuoteLine("discount", "No patching", 1.0, 5000, -5000)))))
        assertEquals("+\$120.00", ChangeOrderLogic.signedAmount(12000))
        assertEquals("−\$50.00", ChangeOrderLogic.signedAmount(-5000))
    }

    @Test fun aChangeOrderCallsADiscountACredit() {
        assertEquals("Credit", QuoteLineKind.DISCOUNT.label(changeOrder = true))
        assertEquals("Discount", QuoteLineKind.DISCOUNT.label(changeOrder = false))
        assertEquals("Materials", QuoteLineKind.MATERIAL.label(changeOrder = true))
        val credit = QuoteLine("discount", "No patching", 1.0, 5000, -5000)
        assertEquals("Credit", QuotePresentation.detail(credit, changeOrder = true))
        assertEquals("Discount", QuotePresentation.detail(credit))
        assertEquals("2 × \$120.00", QuotePresentation.detail(QuoteLine("labour", "Labour", 2.0, 12000, 24000), changeOrder = true))
    }

    @Test fun sentLineIsTheDay() {
        val sent = order(100).copy(createdAt = Instant.parse("2026-11-03T18:00:00Z"))
        assertEquals("Sent Nov 3", ChangeOrderLogic.sentLine(sent, ZoneOffset.UTC))
        assertNull(ChangeOrderLogic.sentLine(order(100)))
    }

    @Test fun approvedChangesJoinTheBookersBreakdown() {
        val itemized = order(
            28500, id = 2, label = "Replace valve",
            lines = listOf(QuoteLine("labour", "Labour", 2.0, 12000, 24000), QuoteLine("discount", "Skip patching", 1.0, 5000, -5000), QuoteLine("material", "Valve", 1.0, 9500, 9500)),
        )
        val older = order(3000, id = 1, label = "Haul away")
        val job = acceptedJob(listOf(itemized, older, order(800, status = "proposed", id = 3)))
        assertEquals(
            listOf(
                ChangeLine("Haul away", "Change", 3000),
                ChangeLine("Labour", "Change: Replace valve · 2 × \$120.00", 24000),
                ChangeLine("Skip patching", "Change: Replace valve · Credit", -5000),
                ChangeLine("Valve", "Change: Replace valve · Materials", 9500),
            ),
            ChangeOrderLogic.approvedLines(job),
        )
    }

    @Test fun a422ReadsTheLabelThenTheFirstLine() {
        assertEquals("Too long", ChangeOrderLogic.errorMessage(mapOf("line_items.0.quantity" to listOf("Bad"), "label" to listOf("Too long"))))
        assertEquals("Line 2: Must be more than 0", ChangeOrderLogic.errorMessage(mapOf("line_items.3.type" to listOf("x"), "line_items.1.quantity" to listOf("Must be more than 0"))))
        assertEquals("Add a line", ChangeOrderLogic.errorMessage(mapOf("line_items" to listOf("Add a line"))))
        assertNull(ChangeOrderLogic.errorMessage(emptyMap()))
    }

    @Test fun decodesAnItemizedChangeAndAnOlderOneLineChange() {
        val itemized = JsonCoding.json.decodeFromString<ChangeOrder>(
            """{"id":5,"label":"Replace valve","note":"It was seized.","line_items":[{"type":"labour","description":"Extra labour","quantity":1.5,"unit_cents":12000,"amount_cents":18000},{"type":"discount","description":"No patching","quantity":1,"unit_cents":2000,"amount_cents":-2000}],"amount_cents":16000,"materials":false,"auto_approved":true,"status":"approved","decided_at":"2026-11-03T18:00:00.000000Z","created_at":"2026-11-03T18:00:00.000000Z"}""",
        )
        assertEquals("It was seized.", itemized.note)
        assertEquals(2, itemized.lineItems!!.size)
        assertEquals(-2000, itemized.lineItems!![1].amountCents)
        assertTrue(itemized.autoApproved)

        val older = JsonCoding.json.decodeFromString<ChangeOrder>("""{"id":1,"label":"Valve","amount_cents":500,"status":"proposed"}""")
        assertNull(older.lineItems)
        assertNull(older.note)
        assertFalse(older.materials)
        assertFalse(older.autoApproved)

        val job = JsonCoding.json.decodeFromString<Job>("""{"id":9,"title":"Leak","status":"in_progress","category":{"id":1,"name":"Plumbing"},"change_order_auto_left_cents":1500}""")
        assertEquals(1500, job.changeOrderAutoLeftCents)
    }

    @Test fun theBodySendsLinesNotAnAmount() {
        val body = ChangeOrderBody("Valve", null, QuoteLineLogic.body(listOf(line(QuoteLineKind.MATERIAL, "2", "19.99", "Valve"))), materials = false)
        val encoded = Json.parseToJsonElement(JsonCoding.json.encodeToString(ChangeOrderBody.serializer(), body)).jsonObject
        assertEquals(setOf("label", "line_items", "materials"), encoded.keys)
        assertEquals("""{"type":"material","description":"Valve","quantity":2.0,"unit_cents":1999}""", encoded["line_items"].toString().removePrefix("[").removeSuffix("]"))
    }
}
