package com.zivett.app

import com.zivett.app.core.models.AppNotification
import com.zivett.app.core.models.CompanySummary
import com.zivett.app.core.models.CustomerRef
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.Message
import com.zivett.app.core.models.OpportunitiesResponse
import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.models.Quote
import com.zivett.app.core.models.QuoteStatus
import com.zivett.app.core.models.RouteParam
import com.zivett.app.core.models.Team
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.core.push.PushRouting
import com.zivett.app.core.push.PushTarget
import com.zivett.app.core.realtime.RealtimeEvents
import com.zivett.app.core.models.TeamArea
import com.zivett.app.features.business.TeamModel
import com.zivett.app.features.company.CompanyPresentation
import com.zivett.app.features.company.CompanyQuotesLogic
import com.zivett.app.features.shared.NavBadgesModel
import com.zivett.app.features.shared.NavBadgesResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// The web's 2026-10-29 review pass (Damon's review), as the app mirrors it.
class ReviewPassTest {
    @Test fun onlyClosedJobsTakeAReview() {
        assertEquals(setOf(JobStatus.WARRANTY_ACTIVE, JobStatus.WARRANTY_EXPIRED), JobStatus.reviewable)
        val company = CompanySummary(id = 9, name = "Harbour Plumbing")
        assertFalse(Fixtures.job(status = JobStatus.INVOICED, company = company).isReviewable)
        assertFalse(Fixtures.job(status = JobStatus.COMPLETED, company = company).isReviewable)
        assertTrue(Fixtures.job(status = JobStatus.WARRANTY_ACTIVE, company = company).isReviewable)
    }

    @Test fun nonJobNotificationsRouteToCompanyTabs() {
        assertEquals(PushTarget.Opportunities("Z-ABC123"), PushRouting.target(mapOf("route_name" to "company.opportunities", "route_params" to """{"job":"Z-ABC123"}""")))
        assertEquals(PushTarget.Quotes, PushRouting.target(mapOf("route_name" to "company.quotes")))
        assertEquals(PushTarget.Payouts, PushRouting.target(mapOf("route_name" to "company.payouts")))
        assertEquals(PushTarget.Job("Z-93U4H3"), PushRouting.target(mapOf("route_name" to "company.jobs.show", "route_id" to "Z-93U4H3")))
        assertNull(PushRouting.target(mapOf("route_name" to "company.dashboard")))
        // A job push's intent carries the ref; the feed's carries the job code.
        assertEquals("Z-ABC123", PushRouting.paramKey(PushTarget.Opportunities("Z-ABC123")))

        val bell = AppNotification(id = "n1", title = "New plumbing job near you", routeName = "company.opportunities", routeParams = mapOf("job" to RouteParam("Z-ABC123")))
        assertEquals(PushTarget.Opportunities("Z-ABC123"), bell.target)
        assertNull(bell.jobRef)
    }

    @Test fun senderLineStartsEachRunFromSomeoneElse() {
        val messages = listOf(
            Message(id = 1, senderId = 2, sender = "Mike R.", body = "On my way", senderLabel = "Harbour Plumbing · Tech"),
            Message(id = 2, senderId = 2, sender = "Mike R.", body = "10 minutes"),
            Message(id = 3, senderId = 1, sender = "Ana", body = "Thanks", mine = true),
            Message(id = 4, senderId = 3, sender = "ZiVETT", body = "Checking in"),
        )
        assertEquals("Mike R. · Harbour Plumbing · Tech", Message.senderLine(messages, 0))
        assertNull(Message.senderLine(messages, 1)) // same run
        assertNull(Message.senderLine(messages, 2)) // your own
        assertEquals("ZiVETT", Message.senderLine(messages, 3)) // an older server: no label
    }

    @Test fun threadRowsAndLivePayloadsDecodeTheLabel() {
        val row = JsonCoding.json.decodeFromString<Message>("""{"id":1,"sender_id":2,"sender":"Mike R.","body":"Hi","sender_role":"pro_tech","sender_label":"Harbour Plumbing · Tech","conversation_id":5}""")
        assertEquals("Harbour Plumbing · Tech", row.senderLabel)
        val live = RealtimeEvents.message("""{"id":1,"conversation_id":5,"sender_id":2,"sender":"Mike R.","body":"Hi","sender_label":"Homeowner"}""", currentUserId = 7)
        assertEquals("Homeowner", live?.senderLabel)
    }

    @Test fun bookerPhoneIsTapToCallWhileTheVisitIsLive() {
        val job = Fixtures.job(status = JobStatus.EN_ROUTE).copy(customer = CustomerRef("Ana Lee", phone = "(250) 555-0101"))
        assertEquals("tel:2505550101", CompanyPresentation.callUri(job.customer?.phone))
        assertTrue(CompanyPresentation.callsUpFront(job))
        assertFalse(CompanyPresentation.callsUpFront(job.copy(status = JobStatus.INVOICED)))
        assertFalse(CompanyPresentation.callsUpFront(job.copy(customer = CustomerRef("Ana Lee"))))
        assertNull(CompanyPresentation.callUri(""))
    }

    @Test fun quotedByYourTeamNamesWhoSentIt() {
        val feed = JsonCoding.json.decodeFromString<OpportunitiesResponse>(
            """{"opportunities":[],"quoted":[{"id":4,"amount_cents":15000,"status":"pending","submitted_by":{"id":12,"name":"Mike R."},"job":{"id":7,"code":"Z-ABC","title":"Leak","quotes_released":false}}]}""",
        )
        val quote = feed.quoted.single()
        assertEquals("Mike R.", CompanyQuotesLogic.quotedBy(quote, userId = 3))
        assertEquals("you", CompanyQuotesLogic.quotedBy(quote, userId = 12))
        assertEquals("your team", CompanyQuotesLogic.quotedBy(Quote(id = 1, amountCents = 1, status = QuoteStatus.PENDING), userId = 3))
        assertEquals("With ZiVETT for review", CompanyQuotesLogic.waitingState(quote))
        // An older server sends no `quoted`.
        assertTrue(JsonCoding.json.decodeFromString<OpportunitiesResponse>("""{"opportunities":[]}""").quoted.isEmpty())
    }

    private fun member(id: Int, status: String) = Team.Member(id = id, name = "Member $id", email = "m$id@example.com", role = OrganizationRole.MEMBER, status = status)

    @Test fun deactivatedTeammatesHideBehindTheToggle() {
        val members = listOf(member(1, "active"), member(2, "deactivated"))
        assertEquals(listOf(1), TeamModel.visibleMembers(members, showDeactivated = false).map { it.id })
        assertEquals(listOf(1, 2), TeamModel.visibleMembers(members, showDeactivated = true).map { it.id })
    }

    @Test fun removingATeammateDropsTheRowOrShowsWhy() = runTest {
        val team = Team(organization = Team.OrgRef(1, "Harbour Plumbing", com.zivett.app.core.models.OrganizationType.COMPANY), canManage = true, members = listOf(member(1, "active"), member(2, "active")))
        val client = PreviewApiClient(mapOf("api/company/team" to team))
        val model = TeamModel(client, TeamArea.company)
        model.load()
        assertNull(model.remove(member(2, "active")))
        assertTrue("DELETE api/company/team/members/2" in client.sent)
        assertEquals(listOf(1), model.state.value?.members?.map { it.id })

        client.errors["api/company/team/members/1"] = ApiError.Validation(JsonCoding.json.decodeFromString("""{"message":"Member 1 is still on the crew for Z-ABC. Swap them off in each job's crew picker, then remove them."}"""))
        assertEquals("Member 1 is still on the crew for Z-ABC. Swap them off in each job's crew picker, then remove them.", model.remove(member(1, "active")))
    }

    @Test fun navBadgesDecodeAndMarkSeen() = runTest {
        assertEquals(mapOf("messages" to 2), JsonCoding.json.decodeFromString<NavBadgesResponse>("""{"badges":{"messages":2}}""").badges)
        // PHP writes an empty map as [].
        assertTrue(JsonCoding.json.decodeFromString<NavBadgesResponse>("""{"badges":[]}""").badges.isEmpty())

        val client = PreviewApiClient(mapOf(
            "api/nav-badges" to NavBadgesResponse(mapOf("opportunities" to 3)),
            "api/nav-badges/seen" to NavBadgesResponse(mapOf("opportunities" to 0)),
        ))
        val badges = NavBadgesModel(client)
        badges.refresh()
        assertEquals(3, badges.count(NavBadgesModel.OPPORTUNITIES))
        badges.view(NavBadgesModel.OPPORTUNITIES)
        assertEquals(0, badges.count(NavBadgesModel.OPPORTUNITIES))
        assertEquals("POST api/nav-badges/seen", client.sent.last())
        badges.stopViewing(NavBadgesModel.OPPORTUNITIES)
        badges.refresh()
        assertEquals("GET api/nav-badges", client.sent.last())
    }
}
