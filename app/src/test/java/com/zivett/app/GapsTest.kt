package com.zivett.app

import com.zivett.app.core.models.JobStatus
import com.zivett.app.features.customer.jobs.JobPresentation
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/// The November gaps (tracker G1-G9).
class GapsTest {
    @Test fun freeCancelWindowIsWorded() {
        val job = Fixtures.job(status = JobStatus.ACCEPTED, company = Fixtures.ravensworth).copy(cancellationFeeCents = 0, freeCancelUntil = Instant.parse("2026-11-26T20:00:00Z"))
        assertEquals(
            "Free to cancel until NOV26. After that, or once your pro starts the request, a cancellation fee applies.",
            JobPresentation.cancelManageCopy(job, business = true) { "NOV26" },
        )
        assertEquals("Free to cancel: your pro hasn't set off.", JobPresentation.cancelManageCopy(job.copy(stalled = true)))
    }

    @Test fun teamEndpointsFollowTheArea() {
        assertEquals("api/company/team/invitations/4/resend", com.zivett.app.core.models.TeamArea.company.resend(4).path)
        assertEquals("api/business/team/members/7/password-reset", com.zivett.app.core.models.TeamArea.business.passwordReset(7).path)
        val leaver = com.zivett.app.core.network.JsonCoding.json.decodeFromString<com.zivett.app.core.models.RosterMember>("""{"id": 3, "name": "Sam", "assignable": false, "inactive": true, "assigned": true}""")
        assertEquals(true, leaver.inactive)
    }
}
