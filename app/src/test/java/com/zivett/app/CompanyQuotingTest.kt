package com.zivett.app

import com.zivett.app.core.models.OpportunitiesResponse
import com.zivett.app.core.models.QuoteBody
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.HttpApiClient
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.core.network.ValidationErrors
import com.zivett.app.features.company.OpportunitiesModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// A quote that fails to send must not cost the company the opportunity:
/// only "the job is gone" (409 / 404) drops the card and closes the
/// composer. Everything else is an inline error with the draft intact.
class CompanyQuotingTest {
    private val job = Fixtures.job(id = 7)
    private val body = QuoteBody(estimatedHours = 2.0, crewSize = 1)
    private fun client() = PreviewApiClient(mapOf("api/company/opportunities" to OpportunitiesResponse(opportunities = listOf(job))))

    @Test fun aFailedSendKeepsTheCardAndTheComposer() = runTest {
        val failures = listOf(
            ApiError.Transport("offline"),
            ApiError.Server(500, null),
            ApiError.RateLimited(30),
            ApiError.Validation(ValidationErrors("Set up payouts before quoting — an accepted job must always have somewhere for the money to go.")),
        )
        for (failure in failures) {
            val client = client()
            client.errors["api/company/opportunities/7/quote"] = failure
            val model = OpportunitiesModel(client)
            model.load()

            assertEquals(failure.userMessage, model.submitQuote(job, body))
            assertEquals(listOf(job), model.state.value!!.opportunities)
            assertNull(model.toast)
        }
    }

    @Test fun aJobThatIsGoneStillLeavesTheFeed() = runTest {
        val client = client()
        client.errors["api/company/opportunities/7/quote"] = HttpApiClient.errorFor(409, null, """{"message":"Quotes for this job have closed."}""".toByteArray())
        val model = OpportunitiesModel(client)
        model.load()

        assertNull(model.submitQuote(job, body))
        assertTrue(model.state.value!!.opportunities.isEmpty())
        assertEquals("Quotes for this job have closed.", model.toast)
    }
}
