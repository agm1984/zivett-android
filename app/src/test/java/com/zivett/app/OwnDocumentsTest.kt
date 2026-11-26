package com.zivett.app

import com.zivett.app.core.models.ProInvoicesResponse
import com.zivett.app.core.models.ProQuoteResponse
import com.zivett.app.core.models.ProQuotesResponse
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.features.company.QuoteLineKind
import com.zivett.app.features.tools.DocumentEmailLogic
import com.zivett.app.features.tools.DocumentLogic
import com.zivett.app.features.tools.OwnQuotesLogic
import com.zivett.app.features.tools.percent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Own quotes and invoices (server: ProDocumentPayload::full, QuoteController,
/// InvoiceController): the payloads decode and the pages pick the web's actions.
class OwnDocumentsTest {
    private val quoteJson = """
    {"quote": {"id": 2, "kind": "quote", "number": "Q-7KD2", "status": "sent", "customer": {"id": 9, "name": "Pat Lee"},
      "total_cents": 55125, "issued_at": "2026-11-02T10:00:00.000000Z", "valid_until": "2026-12-01", "paid_cents": 0, "balance_cents": 55125, "due_on": null,
      "created_at": "2026-11-02T09:00:00.000000Z",
      "letterhead": {"name": "Harbour Plumbing", "logo_url": null, "address": ["1 Wharf St", "Nanaimo, BC V9R 1A1"], "phone": "(250) 555-0100", "email": null, "website": null, "gst_number": "123456789RT0001"},
      "billed_to": {"name": "Pat Lee", "email": "pat@example.test", "phone": null, "address": "1 Front St, Nanaimo BC V9R 1A1", "unit": "4"},
      "pro_customer_address_id": 5,
      "line_items": [{"type": "labour", "description": "Labour", "quantity": 2.5, "unit_cents": 12000, "amount_cents": 30000},
                     {"type": "material", "description": "Valve", "quantity": 1, "unit_cents": 22500, "amount_cents": 22500}],
      "subtotal_cents": 52500, "gst_number": "123456789RT0001", "gst_bps": 500, "gst_cents": 2625, "pst_bps": 0, "pst_cents": 0,
      "pricing_type": "fixed", "exclusions": null, "notes": null, "accepted_at": null, "declined_at": null, "editable": true, "from_document_id": null, "job": null,
      "emailed_at": "2026-11-02T10:00:00.000000Z",
      "emails": [{"id": 1, "kind": "quote", "recipient": "pat@example.test", "sent_at": "2026-11-02T10:00:00.000000Z", "sent_by": "Ola", "failed": false, "bounced": false, "reported": false, "payment_cents": null, "automatic": false}],
      "email": {"recipient": "pat@example.test", "blocked": null, "reply_to": null, "copy_to": null, "sent_today": 1, "daily_limit": 50},
      "link": {"count": 1, "can_copy": true, "first_viewed_at": null, "last_viewed_at": null, "answered": null},
      "invoice": null, "deposits": [], "deposit_left_cents": 52500}}
    """.trimIndent()

    @Test fun quoteDecodesAndPicksItsActions() {
        val quote = JsonCoding.json.decodeFromString<ProQuoteResponse>(quoteJson).quote
        assertEquals(2, quote.lineItems.size)
        assertEquals(2.5, quote.lineItems[0].quantity, 0.0)
        assertEquals(listOf("email", "decline", "accept"), OwnQuotesLogic.actions(quote))
        assertEquals("Email it again", OwnQuotesLogic.emailLabel(quote))
        assertEquals("Sent, not opened yet.", OwnQuotesLogic.linkLine(quote))
        assertEquals("Quote", DocumentEmailLogic.label(quote.emails.single()))
        assertEquals("Pat", quote.customerFirstName)
        assertNull(quote.letterhead?.email)

        val accepted = quote.copy(status = "accepted", job = com.zivett.app.core.models.ProDocument.JobRef(3, "J-1"))
        assertEquals(listOf("invoice", "open_job"), OwnQuotesLogic.actions(accepted))
        assertEquals(listOf("edit", "mark_sent", "email"), OwnQuotesLogic.actions(quote.copy(status = "draft", emails = emptyList())))
        assertEquals("Email it to Pat", OwnQuotesLogic.emailLabel(quote.copy(emails = emptyList())))
    }

    @Test fun listsDecodeWithTheirCounts() {
        val quotes = JsonCoding.json.decodeFromString<ProQuotesResponse>("""
        {"quotes": [], "total": 0, "has_more": false, "counts": {"draft": 1, "sent": 2, "accepted": 0, "declined": 0, "expired": 3},
         "quotes_left_this_month": 7, "quotes_per_month": 10, "upgrade": {"key": "pro", "name": "Pro"}}
        """)
        assertEquals(3, quotes.counts["expired"])
        assertEquals("7 of 10 quotes left this month.", OwnQuotesLogic.allowanceLine(quotes.quotesLeftThisMonth, quotes.quotesPerMonth))
        assertNull(OwnQuotesLogic.allowanceLine(null, null))

        val invoices = JsonCoding.json.decodeFromString<ProInvoicesResponse>("""
        {"invoices": [{"id": 4, "kind": "invoice", "number": null, "status": "draft", "customer": {"id": 9, "name": "Pat"}, "total_cents": 1000,
           "issued_at": null, "valid_until": null, "paid_cents": 0, "balance_cents": 1000, "due_on": null, "created_at": "2026-11-02T00:00:00.000000Z"}],
         "total": 1, "has_more": false, "counts": {"open": 0, "overdue": 0, "paid": 0, "draft": 1, "void": 0, "all": 1}, "outstanding_cents": 0,
         "numbering": {"mode": "series", "prefix": "INV-", "next_number": 12, "locked": true, "example": "INV-0012"}}
        """)
        assertNull(invoices.invoices.single().number)
        assertEquals("INV-0012", invoices.numbering?.example)
    }

    @Test fun taxAndLinesFollowTheWeb() {
        assertEquals(2625, DocumentLogic.tax(52500, 500, true))
        assertEquals(0, DocumentLogic.tax(52500, 500, false))
        assertEquals(3, DocumentLogic.tax(50, 500, true)) // 2.5 rounds half-up
        assertEquals("5%", percent(500))
        assertEquals("7.5%", percent(750))
        val drafts = DocumentLogic.drafts(listOf(com.zivett.app.core.models.ProLine("discount", "Loyalty", 1.0, 1000, -1000)))
        assertEquals(QuoteLineKind.DISCOUNT, drafts.single().kind)
        assertEquals("10", drafts.single().unit)
        assertTrue(DocumentLogic.incomplete(listOf(DocumentLogic.blankLine())))
    }
}
