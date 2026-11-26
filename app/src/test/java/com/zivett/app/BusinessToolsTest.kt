package com.zivett.app

import com.zivett.app.core.auth.Access
import com.zivett.app.core.models.BusinessToolsSummary
import com.zivett.app.core.models.CompanyCalendar
import com.zivett.app.core.models.Organization
import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.models.OrganizationType
import com.zivett.app.core.models.ProCustomerResponse
import com.zivett.app.core.models.ProCustomersResponse
import com.zivett.app.core.models.ProRequestsResponse
import com.zivett.app.core.models.ToolsOverview
import com.zivett.app.core.models.UserRole
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.features.tools.CustomerDetailLogic
import com.zivett.app.features.tools.CustomersLogic
import com.zivett.app.features.tools.ToolsOverviewLogic
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Business tools: the shell's lifecycle rules, and the Overview,
/// Requests and Customers payloads decode in the server's real shapes
/// (DashboardController, RequestController, CustomerController).
class BusinessToolsTest {
    private fun org(approved: Boolean = true, submitted: Boolean = approved, setup: Boolean = false, tools: Boolean = true) = Organization(
        id = 1, type = OrganizationType.COMPANY, name = "Harbour Plumbing",
        approvedAt = if (approved) Instant.EPOCH else null, submittedAt = if (submitted) Instant.EPOCH else null,
        setupCompletedAt = if (setup) Instant.EPOCH else null, businessToolsEnabledAt = if (tools) Instant.EPOCH else null,
    )

    @Test fun businessToolsFollowThePermissionWhenTheServerListsThem() {
        val base = Fixtures.user(UserRole.COMPANY, organization = org())
        assertTrue(Access.hasBusinessTools(base))
        assertFalse(Access.hasBusinessTools(base.copy(permissions = listOf("dashboard", "jobs"))))
        assertTrue(Access.hasBusinessTools(base.copy(permissions = listOf("business_tools"))))
        // Older server, tools off: no business side.
        assertFalse(Access.hasBusinessTools(Fixtures.user(UserRole.COMPANY, organization = org(tools = false))))
        assertFalse(Access.hasBusinessTools(Fixtures.user(UserRole.CUSTOMER)))
    }

    @Test fun toolsOnlyIsNeverSubmittedWithSetupFinished() {
        assertTrue(Access.toolsOnly(Fixtures.user(UserRole.COMPANY, organization = org(approved = false, submitted = false, setup = true))))
        assertFalse(Access.toolsOnly(Fixtures.user(UserRole.COMPANY, organization = org(approved = false, submitted = true, setup = true))))
        assertFalse(Access.toolsOnly(Fixtures.user(UserRole.COMPANY, organization = org(approved = false, submitted = false, setup = false))))
        assertFalse(Access.toolsOnly(Fixtures.user(UserRole.COMPANY, organization = org(approved = false, submitted = false, setup = true, tools = false))))
    }

    @Test fun payrollRowsNeedTheSessionFlag() {
        val user = Fixtures.user(UserRole.COMPANY, organization = org(), organizationRole = OrganizationRole.MEMBER)
        assertTrue(Access.hasPayroll(user))
        assertFalse(Access.hasPayroll(user.copy(payroll = false)))
        assertTrue(user.copy(permissions = null).canOpen("calendar"))
        assertFalse(user.copy(permissions = listOf("jobs")).canOpen("calendar"))
    }

    @Test fun allowanceSentenceMatchesTheWeb() {
        val capped = BusinessToolsSummary(allowances = BusinessToolsSummary.Allowances(customers = 1000, quotesPerMonth = 10, members = false))
        assertEquals("Up to 1,000 customers and 10 quotes a month, for company admins only.", capped.allowanceSentence)
        val open = BusinessToolsSummary(allowances = BusinessToolsSummary.Allowances(customers = null, quotesPerMonth = null))
        assertEquals("Unlimited customers and unlimited quotes, for the whole team.", open.allowanceSentence)
        assertEquals("Unlimited customers and quotes for everyone on the team (pilot).", BusinessToolsSummary(allowances = BusinessToolsSummary.Allowances(unlimited = true)).allowanceSentence)
        assertTrue(BusinessToolsSummary(selfServe = true, enabled = true, toolsOnly = true).locked)
        assertFalse(BusinessToolsSummary(selfServe = false, enabled = false).visible)
    }

    @Test fun overviewDecodes() {
        val overview = JsonCoding.json.decodeFromString<ToolsOverview>("""
        {"business_tools": {"enabled": true, "enabled_at": "2026-11-01T10:00:00.000000Z", "enabled_by": "Ola", "unlimited": false, "tools_only": false,
          "self_serve": true, "allowances": {"customers": 50, "quotes_per_month": 10, "members": true, "reminders": true, "reports": false, "unlimited": false},
          "plan": "Basic", "upgrade": {"key": "pro", "name": "Pro", "price_cents": 4900, "yearly_price_cents": 49000, "customers": null, "quotes_per_month": null},
          "email": {"sent_today": 0}},
         "reminders": {"allowed": true, "upgrade": null, "settings": {"appointment": true, "quote_follow_up": false, "invoice_overdue": true}, "defaulted": false, "follow_up_days": 3, "can_edit": true},
         "stats": {"customers": 12, "active_jobs": 3, "pending_quotes": 2, "outstanding_invoices": 1, "outstanding_cents": 40450, "paid_this_month_cents": 120000}}
        """)
        assertEquals(12, overview.stats.customers)
        assertEquals(40450, overview.stats.outstandingCents)
        assertEquals(false, overview.reminders?.settings?.get("quote_follow_up"))
        assertEquals("Basic", overview.businessTools.plan)
    }

    @Test fun weekRunsOwnAndZivettJobsByDay() {
        val calendar = JsonCoding.json.decodeFromString<CompanyCalendar>("""
        {"month": "2026-11", "from": "2026-11-25", "to": "2026-12-01",
         "jobs": [{"id": 7, "code": "Z-AAA111", "title": "Leaky tap", "status": "accepted", "date": "2026-11-26", "window": "afternoon"}],
         "own_jobs": [
           {"id": 12, "code": "J-1", "title": "Gutters", "status": "scheduled", "customer_name": "Ben Lee", "date": "2026-11-26", "window": "morning", "start_time": "09:30"},
           {"id": "busy-3", "busy": true, "date": "2026-11-26", "window": "evening"},
           {"id": 13, "code": "J-2", "title": "Fence", "status": "completed", "customer_name": "Pat", "date": "2026-11-25", "window": "morning"}
         ]}
        """)
        val week = ToolsOverviewLogic.week(calendar, LocalDate.parse("2026-11-25"), LocalDate.parse("2026-11-25"))
        assertEquals(listOf("Today", "Thu, Nov 26"), week.map { it.first })
        assertTrue(week[0].second.single().done)
        assertEquals(listOf("Gutters", "Leaky tap"), week[1].second.map { it.title })
        assertEquals("9:30 am", week[1].second[0].whenText)
    }

    @Test fun requestsDecode() {
        val response = JsonCoding.json.decodeFromString<ProRequestsResponse>("""
        {"requests": [{"id": 4, "name": "Pat Lee", "email": "pat@example.test", "phone": "(250) 555-0101", "address": "1 Front St, Nanaimo BC V9R 1A1",
          "message": "Tap drips", "category": {"id": 1, "name": "Plumbing", "code": "PL", "bg_color": "#eee", "fg_color": "#111", "icon": "wrench"},
          "intake_answers": [{"prompt": "What needs attention?", "type": "single", "answers": ["Faucet"]}], "mode": "scheduled", "urgency": null,
          "availability_windows": [{"date": "2026-11-27", "window": "morning"}], "timing": null, "marketing_opt_in": true, "status": "new",
          "customer_id": null, "existing_customer": {"id": 9, "name": "Pat Lee", "archived": false}, "created_at": "2026-11-25T08:00:00.000000Z", "handled_at": null}],
         "counts": {"new": 1, "handled": 4}, "form": {"url": "https://zivett.com/request/abc", "can_edit": true}}
        """)
        assertEquals(9, response.requests.single().existingCustomer?.id)
        assertEquals("Faucet", response.requests.single().intakeAnswers?.single()?.answers?.single())
        assertEquals(4, response.counts.handled)
        assertTrue(response.form.canEdit)
    }

    @Test fun customersDecodeWithTheirLimit() {
        val response = JsonCoding.json.decodeFromString<ProCustomersResponse>("""
        {"customers": [{"id": 9, "name": "Pat Lee", "email": null, "phone": "(250) 555-0101", "source": "referral", "source_label": "Referral",
          "address": "1 Front St, Nanaimo BC", "addresses_count": 2, "archived": false, "balance_cents": 1200, "created_at": "2026-11-01T00:00:00.000000Z"}],
         "total": 1, "has_more": false, "counts": {"active": 1, "archived": 0},
         "allowance": {"limit": 1, "upgrade": {"key": "pro", "name": "Pro", "price_cents": 4900}, "used": 1},
         "sources": [{"value": "referral", "label": "Referral"}],
         "exception_requests": [{"id": 1, "customer_name": "Sam", "customer_id": null, "job_code": "Z-AAA111", "status": "pending", "decision_note": null, "created_at": "2026-11-20T00:00:00.000000Z", "decided_at": null}]}
        """)
        assertTrue(response.allowance.atLimit)
        assertEquals("1 of 1 customers on your plan.", CustomersLogic.allowanceLine(response.allowance))
        assertEquals("With ZiVETT support", CustomersLogic.exceptionStatus(response.exceptionRequests.single().status).first)
        assertFalse(CustomersLogic.firstRun(response))
    }

    @Test fun customerDetailDecodesAndWordsItsConsent() {
        val customer = JsonCoding.json.decodeFromString<ProCustomerResponse>("""
        {"customer": {"id": 9, "name": "Pat Lee", "email": "pat@example.test", "phone": null, "source": "referral", "source_label": "Referral",
          "address": "1 Front St", "addresses_count": 1, "archived": false, "balance_cents": 0, "created_at": "2026-11-01T00:00:00.000000Z",
          "notes": null, "no_reminders": true, "unsubscribed": false,
          "marketing": {"since": null, "source": null, "implied_until": "2028-11-01", "can_email": true, "receives": true, "basis": "implied", "reason": "recent"},
          "campaigns": [{"campaign_id": 3, "subject": "Spring tune-up", "sent_at": "2026-11-10T00:00:00.000000Z", "problem": null, "had_button": true, "clicked_at": null, "booked": null}],
          "source_locked": false, "delete_anonymises": true,
          "addresses": [{"id": 5, "label": null, "address_line1": "1 Front St", "address_line2": "4", "city": "Nanaimo", "region": "BC", "postal_code": "V9R 1A1",
            "lat": null, "lng": null, "pin_adjusted_at": null, "one_line": "1 Front St, Nanaimo BC V9R 1A1", "archived": false}],
          "history": {"requests": [], "quotes": [{"id": 2, "kind": "quote", "number": "Q-7KD2", "status": "sent", "customer": {"id": 9, "name": "Pat Lee"}, "total_cents": 52500,
            "issued_at": null, "valid_until": "2026-12-01", "paid_cents": 0, "balance_cents": 52500, "due_on": null, "created_at": "2026-11-02T00:00:00.000000Z"}],
            "jobs": [{"id": 3, "code": "J-1", "title": "Gutters", "status": "scheduled", "customer": {"id": 9, "name": "Pat Lee"}, "address": "1 Front St",
              "scheduled_date": null, "scheduled_window": null, "start_time": null, "estimated_hours": 2.5, "price_cents": 30000, "category": null,
              "completed_at": null, "created_at": "2026-11-02T00:00:00.000000Z", "crew": [{"id": 1, "name": "Mike R", "photo_url": null, "inactive": false}], "repeats": false}],
            "invoices": [], "payments": [{"id": 1, "invoice_id": 4, "invoice_number": "INV-0001", "amount_cents": 1000, "paid_on": "2026-11-03", "method_label": "Cash"}]},
          "protection_months": 24}}
        """).customer
        assertEquals("Unit 4", CustomerDetailLogic.unit(customer.activeAddresses.single().addressLine2!!))
        assertEquals("A recent customer, so you can email them until Nov 1, 2028. Ask them to say yes to keep emailing after that.", CustomerDetailLogic.marketingExplanation(customer.marketing!!))
        assertEquals("1 campaign · clicked 0 of 1 with a button · no booking within 30 days of one yet.", CustomerDetailLogic.campaignLine(customer.campaigns))
        assertEquals("Didn't click", CustomerDetailLogic.campaignOutcome(customer.campaigns.single()))
        assertEquals(2.5, customer.history.jobs.single().estimatedHours!!, 0.0)
        assertNull(customer.history.jobs.single().scheduledDate)
    }
}
