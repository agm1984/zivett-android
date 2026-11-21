package com.zivett.app

import com.zivett.app.core.models.CompanyDashboard
import com.zivett.app.features.company.TimelineRowState
import com.zivett.app.features.company.applicationTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/// Payout setup opens at submission: the under-review stepper carries a
/// Payouts row on Stripe, and none on the simulated driver.
class PayoutSetupTimelineTest {
    @Test fun theApplicationStepperOffersPayoutsWhileUnderReview() {
        val unready = applicationTimeline(4, 4, 0, CompanyDashboard.StripePayouts(required = true, ready = false))
        assertEquals(listOf("Application submitted", "Credentials uploaded", "Passport review", "Payouts", "Approved & live"), unready.map { it.title })
        assertEquals(TimelineRowState.TODO, unready[3].state)

        val ready = applicationTimeline(4, 4, 0, CompanyDashboard.StripePayouts(required = true, ready = true))
        assertEquals(TimelineRowState.DONE, ready[3].state)

        val simulated = applicationTimeline(4, 4, 0, CompanyDashboard.StripePayouts(required = false, ready = false))
        assertFalse(simulated.any { it.title == "Payouts" })
    }
}
