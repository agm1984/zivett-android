package com.zivett.app

import com.zivett.app.core.models.OpportunitiesResponse
import com.zivett.app.core.models.QuoteBody
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.HttpApiClient
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.core.network.ValidationErrors
import com.zivett.app.core.models.RatesResponse
import com.zivett.app.features.company.CompanyRates
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

    @Test fun changingARateInTheComposerRepricesTheFeed() = runTest {
        val client = client()
        client.responses["api/company/rates/${job.category.id}"] = RatesResponse(mapOf(job.category.id.toString() to 14500))
        val model = OpportunitiesModel(client)
        model.load()

        assertNull(model.saveRate(job.category.id, 14500))
        assertEquals(14500, model.state.value!!.hourlyRates[job.category.id.toString()])
        assertTrue("PATCH api/company/rates/${job.category.id}" in client.sent)
    }

    @Test fun aRefusedRateKeepsTheOldOneAndSaysWhy() = runTest {
        val client = client()
        client.errors["api/company/rates/${job.category.id}"] = HttpApiClient.errorFor(422, null, """{"message":"Too high.","errors":{"hourly_rate_cents":["The rate must be at most $1,000."]}}""".toByteArray())
        val model = OpportunitiesModel(client)
        model.load()

        assertEquals("The rate must be at most $1,000.", model.saveRate(job.category.id, 500000))
        assertTrue(model.state.value!!.hourlyRates.isEmpty())
    }

    @Test fun rateDraftsParseAsDollars() {
        assertEquals(12000, CompanyRates.parseDollars("120"))
        assertEquals(12050, CompanyRates.parseDollars("120,50"))
        assertNull(CompanyRates.parseDollars("0"))
        assertNull(CompanyRates.parseDollars("abc"))
    }
}
