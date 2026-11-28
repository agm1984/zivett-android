package com.zivett.app

import com.zivett.app.core.models.PayrollSheet
import com.zivett.app.core.models.ProExpensesResponse
import com.zivett.app.core.models.ProReport
import com.zivett.app.core.models.Timesheet
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.features.tools.BooksLogic
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/// The books (server: ExpenseController, ProTimesheets::sheet,
/// Payroll::sheet, ProReports::build) decode in their real shapes.
class BooksTest {
    @Test fun expensesDecode() {
        val data = JsonCoding.json.decodeFromString<ProExpensesResponse>("""
        {"from": "2026-11-01", "to": "2026-11-30",
         "expenses": [{"id": 1, "spent_on": "2026-11-03", "category": "fuel", "category_label": "Fuel", "description": "Van", "amount_cents": 8000, "gst_cents": 381,
           "receipt": {"id": 1, "url": "https://x/r.jpg", "thumb_url": "https://x/t.jpg"}}],
         "totals": {"amount_cents": 8000, "gst_cents": 381, "count": 1},
         "by_category": [{"category": "fuel", "label": "Fuel", "amount_cents": 8000}],
         "categories": [{"value": "fuel", "label": "Fuel"}],
         "job_costs": [{"id": 2, "description": "Valve", "amount_cents": 2000, "spent_on": "2026-11-04", "job_id": 3, "job_title": "Tap", "job_code": "J-1"}]}
        """)
        assertEquals("https://x/t.jpg", data.expenses.single().receipt?.thumbUrl)
        assertEquals("J-1", data.jobCosts.single().jobCode)
    }

    @Test fun timesheetAndPayrollDecode() {
        val sheet = JsonCoding.json.decodeFromString<Timesheet>("""
        {"from": "2026-11-23", "to": "2026-11-29", "people": [{"user": {"id": 1, "name": "Mike", "photo_url": null}, "by_day": {"2026-11-24": 90}, "total_minutes": 90,
          "entries": [{"id": 4, "worked_on": "2026-11-24", "minutes": 90, "note": null, "job": {"id": 3, "code": "J-1", "title": "Tap", "customer": "Pat"}}]}],
         "days": ["2026-11-23"], "total_minutes": 90}
        """)
        assertEquals(90, sheet.people.single().byDay["2026-11-24"])
        val payroll = JsonCoding.json.decodeFromString<PayrollSheet>("""
        {"period": {"start": "2026-11-16", "end": "2026-11-29"}, "previous": "2026-11-15", "next": "2026-11-30",
         "settings": {"preset": "bc"},
         "people": [{"user": {"id": 1, "name": "Mike", "photo_url": null, "takes_jobs": true},
           "totals": {"paid_minutes": 480, "regular_minutes": 480, "overtime_minutes": 0, "double_minutes": 0, "break_minutes": 30, "days_worked": 1},
           "pay": {"rate_cents": 3000, "regular_cents": 24000, "overtime_cents": 0, "double_cents": 0, "vacation_cents": 960, "total_cents": 24960},
           "review": 1, "open": false, "approved": false,
           "days": [{"date": "2026-11-24", "paid_minutes": 480, "regular_minutes": 480, "overtime_minutes": 0, "double_minutes": 0, "break_minutes": 30,
             "shifts": [{"id": 9, "work_date": "2026-11-24", "started_at": "2026-11-24T16:00:00.000000Z", "ended_at": "2026-11-25T00:30:00.000000Z", "open": false,
               "on_break": false, "paid_minutes": 480, "break_minutes": 30, "start_source": "work", "end_source": "manual", "edited": false, "note": null, "approved": false, "needs_review": true}],
             "job_minutes": 240}]}],
         "totals": {"paid_minutes": 480, "overtime_minutes": 0, "gross_cents": 24960, "review": 1, "missing_rates": 0}}
        """)
        assertEquals(24960, payroll.people.single().pay?.totalCents)
        assertEquals(240, payroll.people.single().days.single().jobMinutes)
        assertEquals(LocalDate.parse("2026-11-16"), BooksLogic.dayInPeriod("2026-11-16", "2026-11-29", LocalDate.parse("2026-12-03")))
        assertEquals(LocalDate.parse("2026-11-20"), BooksLogic.dayInPeriod("2026-11-16", "2026-11-29", LocalDate.parse("2026-11-20")))
    }

    @Test fun reportsDecodeAndScale() {
        val report = JsonCoding.json.decodeFromString<ProReport>("""
        {"allowed": true, "range": "last_12_months", "from": "2025-12-01", "to": "2026-11-30",
         "months": [{"month": "2026-11", "invoiced_cents": 50000, "collected_cents": 20000, "job_costs_cents": 1000, "expenses_cents": 2000, "left_cents": 47000}],
         "totals": {"invoiced_cents": 50000, "collected_cents": 20000, "costs_cents": 3000, "left_cents": 47000, "owed_cents": 30000},
         "quotes": {"sent": 4, "won": 2, "lost": 1, "open": 1, "win_rate": 67}, "days_to_pay": {"median": 6, "invoices": 2},
         "top_customers": [{"id": 9, "name": "Pat", "invoiced_cents": 50000, "invoices": 2}], "trades": [{"name": "Plumbing", "jobs": 3}]}
        """)
        assertEquals(67, report.quotes?.winRate)
        assertEquals("Nov 2026", BooksLogic.month("2026-11"))
        assertEquals(0.5f, BooksLogic.share(25, 50), 0.0001f)
        assertEquals(0f, BooksLogic.share(10, 0), 0.0001f)
        assertFalse(JsonCoding.json.decodeFromString<ProReport>("""{"allowed": false, "upgrade": {"key": "pro", "name": "Pro", "price_cents": 4900}}""").allowed)
    }
}
