package com.zivett.app

import com.zivett.app.core.models.ProJobResponse
import com.zivett.app.core.models.ProJobsResponse
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.features.tools.OwnJobsLogic
import com.zivett.app.features.tools.ToolsFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/// Own jobs (server: ProJobPayload::full, JobController): the office's
/// payload decodes and the page picks the web's next step.
class OwnJobsTest {
    private val json = """
    {"job": {"id": 3, "code": "J-1042", "title": "Gutters", "status": "completed", "customer": {"id": 9, "name": "Pat Lee", "phone": "(250) 555-0101", "email": null},
      "address": "1 Front St, Nanaimo BC V9R 1A1", "scheduled_date": "2026-11-26", "scheduled_window": null, "start_time": "13:30", "estimated_hours": 2,
      "price_cents": 30000, "category": {"id": 1, "name": "Handyman", "code": "HM"}, "completed_at": "2026-11-26T22:00:00.000000Z", "created_at": "2026-11-20T00:00:00.000000Z",
      "crew": [{"id": 1, "name": "Mike Ross", "photo_url": null, "inactive": false}, {"id": 2, "name": "Sam Lee", "photo_url": null, "inactive": true}], "repeats": true,
      "description": null, "pro_customer_address_id": 5, "address_unit": "4", "lat": 49.1, "lng": -123.9, "quote": null,
      "line_items": [{"type": "flat", "description": "Gutters", "quantity": 1, "unit_cents": 30000, "amount_cents": 30000}],
      "changes": [{"id": 1, "label": "Extra downspout", "note": null, "line_items": [], "amount_cents": 4500, "created_at": "2026-11-26T20:00:00.000000Z", "created_by": "Ola"}],
      "changes_locked": null, "total_price_cents": 34500, "started_at": "2026-11-26T20:00:00.000000Z", "cancelled_at": null, "cancel_reason": null, "reminder_sent_at": null,
      "next": [], "work_notes": "Cleared and flushed", "actual_hours": 2.5,
      "time": [{"id": 4, "user": {"id": 1, "name": "Mike Ross", "photo_url": null}, "worked_on": "2026-11-26", "minutes": 150, "running": false, "started_at": null, "ended_at": null, "note": null, "removable": true}],
      "photos": [{"id": 8, "kind": "after", "url": "https://x/p.jpg", "thumb_url": "https://x/t.jpg", "created_at": "2026-11-26T22:00:00.000000Z", "mine": true}],
      "money": {"costs": [{"id": 2, "description": "Downspout", "amount_cents": 2000}], "costs_cents": 2000, "price_basis": "priced", "price_cents": 34500, "left_cents": 32500, "per_hour_cents": 13000},
      "series": {"id": 1, "frequency": "monthly", "label": "Every month", "ends_on": null, "active": true, "upcoming_count": 6, "upcoming": [{"id": 5, "scheduled_date": "2026-12-26", "scheduled_window": "morning"}]},
      "invoices": [{"id": 11, "number": "INV-0003", "status": "unpaid", "total_cents": 36225}]}}
    """.trimIndent()

    @Test fun officeJobDecodesAndPointsAtTheInvoice() {
        val job = JsonCoding.json.decodeFromString<ProJobResponse>(json).job
        assertTrue(job.crew[1].inactive)
        assertEquals("email_invoice", OwnJobsLogic.action(job))
        assertEquals("start", OwnJobsLogic.action(job.copy(status = "scheduled")))
        assertEquals("invoice", OwnJobsLogic.action(job.copy(invoices = emptyList())))
        assertEquals("issue", OwnJobsLogic.action(job.copy(invoices = listOf(job.invoices.single().copy(status = "draft")))))
        assertEquals(listOf("Price" to "$345.00", "Costs" to "$20.00", "Left after costs" to "$325.00", "Per hour worked" to "$130.00"), OwnJobsLogic.moneyLines(job.money!!))
        assertEquals("Thu, Nov 26 · 1:30 pm", ToolsFormat.jobWhen(job.scheduledDate, job.startTime, job.scheduledWindow))
        assertEquals("Not booked yet", ToolsFormat.jobWhen(null, null, null))
        assertEquals(6, job.series?.upcomingCount)
    }

    @Test fun listDecodesAndQuoteTitlesSkipLabour() {
        val list = JsonCoding.json.decodeFromString<ProJobsResponse>("""{"jobs": [], "total": 0, "has_more": false, "counts": {"active": 2, "all": 9}}""")
        assertEquals(9, list.counts["all"])
        val lines = listOf(com.zivett.app.core.models.ProLine("labour", "Labour"), com.zivett.app.core.models.ProLine("material", "Hot water tank"))
        assertEquals("Hot water tank", OwnJobsLogic.titleFromQuote(lines))
    }
}
