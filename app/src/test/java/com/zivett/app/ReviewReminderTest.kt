package com.zivett.app

import com.zivett.app.core.models.AppNotification
import com.zivett.app.core.models.AssignedTech
import com.zivett.app.core.models.CompanySummary
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.Review
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.push.PushRouting
import com.zivett.app.core.push.PushTarget
import com.zivett.app.features.customer.jobs.QuickReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/// The review reminder (web 2026-11-21): `Notify::reviewRequested` routes
/// to the booker's job with `review: 1`, and the job page offers the quick
/// rating sheet when the job is closed and unrated.
class ReviewReminderTest {
    private val company = CompanySummary(id = 9, name = "Harbour Plumbing")

    @Test fun pushDataCarriesTheReviewFlagAsANumberOrAString() {
        // PHP's int 1 inside the JSON-string params.
        assertEquals(PushTarget.Job("Z-93U4H3", review = true), PushRouting.target(mapOf("route_name" to "customer.jobs.show", "route_params" to """{"id":"Z-93U4H3","review":1}""")))
        // Stringified by FCM, and flattened keys.
        assertEquals(PushTarget.Job("Z-93U4H3", review = true), PushRouting.target(mapOf("route_name" to "business.requests.show", "route_params" to """{"id":"Z-93U4H3","review":"1"}""")))
        assertEquals(PushTarget.Job("Z-93U4H3", review = true), PushRouting.target(mapOf("route_name" to "customer.jobs.show", "route_id" to "Z-93U4H3", "route_params.review" to "1")))
        assertEquals(PushTarget.Job("Z-93U4H3", review = true), PushRouting.target(mapOf("route_name" to "customer.jobs.show", "route_params" to """{"id":"Z-93U4H3","review":true}""")))
    }

    @Test fun absentOrFalsyFlagIsAPlainJobOpen() {
        assertEquals(PushTarget.Job("Z-93U4H3"), PushRouting.target(mapOf("route_name" to "customer.jobs.show", "route_params" to """{"id":"Z-93U4H3"}""")))
        assertEquals(PushTarget.Job("Z-93U4H3", review = false), PushRouting.target(mapOf("route_name" to "customer.jobs.show", "route_params" to """{"id":"Z-93U4H3","review":0}""")))
        assertFalse(PushRouting.flag(null))
        assertFalse(PushRouting.flag(""))
        assertTrue(PushRouting.flag(" 1 "))
    }

    @Test fun bellRowsCarryTheReviewFlag() {
        val asInt = JsonCoding.json.decodeFromString<AppNotification>("""{"id":"n1","title":"How did Harbour Plumbing do?","route_name":"customer.jobs.show","route_params":{"id":"Z-93U4H3","review":1}}""")
        assertEquals(PushTarget.Job("Z-93U4H3", review = true), asInt.target)
        assertEquals("Z-93U4H3", asInt.jobRef)
        val asString = JsonCoding.json.decodeFromString<AppNotification>("""{"id":"n2","title":"How did Harbour Plumbing do?","route_name":"business.requests.show","route_params":{"id":"Z-93U4H3","review":"1"}}""")
        assertEquals(PushTarget.Job("Z-93U4H3", review = true), asString.target)
        val plain = JsonCoding.json.decodeFromString<AppNotification>("""{"id":"n3","title":"Invoice ready","route_name":"customer.jobs.show","route_params":{"id":"Z-93U4H3"}}""")
        assertEquals(PushTarget.Job("Z-93U4H3", review = false), plain.target)
    }

    @Test fun theTappedIntentKeepsTheFlagThroughToTheShell() {
        assertEquals(PushTarget.Job("Z-93U4H3", review = true), PushRouting.intentTarget("customer.jobs.show", "Z-93U4H3", review = true))
        assertEquals(PushTarget.Job("Z-93U4H3"), PushRouting.intentTarget("customer.jobs.show", "Z-93U4H3", review = false))
        // An older build's intent (no route name) is still a job.
        assertEquals(PushTarget.Job("Z-93U4H3"), PushRouting.intentTarget(null, "Z-93U4H3", review = false))
        // Non-job routes ignore it.
        assertEquals(PushTarget.Quotes, PushRouting.intentTarget("company.quotes", null, review = true))
    }

    @Test fun onlyClosedUnratedJobsGetTheSheet() {
        assertTrue(QuickReview.qualifies(Fixtures.job(status = JobStatus.WARRANTY_ACTIVE, company = company)))
        assertTrue(QuickReview.qualifies(Fixtures.job(status = JobStatus.WARRANTY_EXPIRED, company = company)))
        // Invoiced but unpaid rates in the pay sheet instead; invoiced and closed counts.
        assertFalse(QuickReview.qualifies(Fixtures.job(status = JobStatus.INVOICED, company = company)))
        assertTrue(QuickReview.qualifies(Fixtures.job(status = JobStatus.INVOICED, company = company).copy(closedAt = Instant.now())))
        // Already rated → just the job.
        assertFalse(QuickReview.qualifies(Fixtures.job(status = JobStatus.WARRANTY_ACTIVE, company = company).copy(review = Review(id = 1, rating = 5))))
        assertFalse(QuickReview.qualifies(Fixtures.job(status = JobStatus.WARRANTY_ACTIVE)))
        assertFalse(QuickReview.qualifies(Fixtures.job(status = JobStatus.COMPLETED, company = company)))
    }

    @Test fun commentLabelFollowsTheRating() {
        assertNull(QuickReview.commentLabel(0))
        assertEquals("What went wrong?", QuickReview.commentLabel(1))
        assertEquals("What could have gone better?", QuickReview.commentLabel(2))
        assertEquals("What could have gone better?", QuickReview.commentLabel(3))
        assertEquals("What did they do well?", QuickReview.commentLabel(4))
        assertEquals("What did they do well?", QuickReview.commentLabel(5))
    }

    @Test fun facesAreTheCrewElseTheTechUpToThree() {
        val crew = (1..4).map { AssignedTech(id = it, name = "Tech $it") }
        val job = Fixtures.job(status = JobStatus.WARRANTY_ACTIVE, company = company)
        assertEquals(crew.take(3), QuickReview.faces(job.copy(crew = crew)))
        val lead = AssignedTech(id = 7, name = "Mike R.")
        assertEquals(listOf(lead), QuickReview.faces(job.copy(assignedTech = lead)))
        assertTrue(QuickReview.faces(job).isEmpty())
        assertEquals("Plumbing · Z-TEST01", QuickReview.context(job))
    }
}
