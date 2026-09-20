package com.zivett.app

import com.zivett.app.core.Loadable
import com.zivett.app.core.models.BillingContext
import com.zivett.app.core.models.JobArea
import com.zivett.app.core.models.JobResponse
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.Quote
import com.zivett.app.core.models.QuoteStatus
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.HttpApiClient
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.core.network.ValidationErrors
import com.zivett.app.core.payments.PaymentCardModel
import com.zivett.app.features.customer.account.PayInvoiceModel
import com.zivett.app.features.customer.jobs.JobDetailModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Changing the card on a pay surface. PaymentSheet itself can't run in
/// a unit test, so these cover the decisions around it — and that a
/// declined card reaches the pay sheet as an OUTCOME it can act on, not
/// a toast under a sheet that already dismissed.
class PaymentCardTest {
    private fun context(driver: String = "stripe", saved: Boolean, secret: String? = "seti_secret") =
        BillingContext(driver = driver, publishableKey = "pk_test_x", clientSecret = secret, savedCard = if (saved) BillingContext.SavedCard("visa", "4242") else null)

    @Test fun aSavedCardOrTheSimulatedDriverCanCharge() = runTest {
        val saved = PaymentCardModel(PreviewApiClient(mapOf("api/billing/setup-intent" to context(saved = true))))
        saved.load()
        assertTrue(saved.canCharge)

        val none = PaymentCardModel(PreviewApiClient(mapOf("api/billing/setup-intent" to context(saved = false))))
        none.load()
        assertFalse(none.canCharge)

        val simulated = PaymentCardModel(PreviewApiClient(mapOf("api/billing/setup-intent" to BillingContext(driver = "simulated"))))
        simulated.load()
        assertTrue(simulated.canCharge)
    }

    /// One dropped billing request used to disable Pay for good on the
    /// invoice screen and the home hero. Only a LOADED "no card" blocks.
    @Test fun aFailedBillingFetchNeverBlocksPayAndCanBeRetried() = runTest {
        val client = PreviewApiClient()
        client.errors["api/billing/setup-intent"] = ApiError.Transport("offline")
        val invoice = PayInvoiceModel(Fixtures.invoice(), client)
        assertTrue(invoice.canPay) // still loading
        invoice.load()
        assertTrue(invoice.card.billing is Loadable.Failed)
        assertTrue(invoice.canPay)

        client.errors.clear()
        client.responses["api/billing/setup-intent"] = context(saved = false)
        invoice.card.retry()
        assertTrue(invoice.card.blocksPay)
        assertFalse(invoice.canPay)
    }

    @Test fun changingACardWithoutASetupIntentExplainsItself() = runTest {
        val client = PreviewApiClient(mapOf("api/billing/setup-intent" to context(saved = true, secret = null)))
        val model = PaymentCardModel(client)

        assertFalse(model.changeCard())

        assertTrue(model.error!!.contains("isn't available"))
        assertFalse(model.changing)
        // It asked for a FRESH context (secrets are single-use) and never
        // tried to save a card it didn't collect.
        assertEquals(listOf("POST api/billing/setup-intent"), client.sent)
    }

    @Test fun aDeclinedCardIsAnOutcomeNotAToast() = runTest {
        val client = PreviewApiClient(mapOf("api/customer/jobs/5" to JobResponse(Fixtures.job(id = 5, status = JobStatus.INVOICED))))
        client.errors["api/customer/jobs/5/close"] = ApiError.Validation(ValidationErrors("Your card was declined.", code = "payment_declined"))
        val model = JobDetailModel(5, client, JobArea.customer)
        model.load()

        assertEquals(JobDetailModel.CloseOutcome.Declined("Your card was declined."), model.close(500))
        assertNull(model.toast)
        assertFalse(model.justClosed)
    }

    @Test fun anOrdinaryFailureStillToasts() = runTest {
        val client = PreviewApiClient(mapOf("api/customer/jobs/5" to JobResponse(Fixtures.job(id = 5, status = JobStatus.INVOICED))))
        client.errors["api/customer/jobs/5/close"] = ApiError.Validation(ValidationErrors("This job has an open dispute."))
        val model = JobDetailModel(5, client, JobArea.customer)
        model.load()

        assertEquals(JobDetailModel.CloseOutcome.Failed, model.close(0))
        assertEquals("This job has an open dispute.", model.toast)
    }

    /// The accept sheet covers the job page's toast, so a refusal has to
    /// land where the sheet can show it.
    @Test fun anAcceptRefusalStaysInTheSheet() = runTest {
        val quote = Quote(id = 9, amountCents = 18000, status = QuoteStatus.PENDING)
        val client = PreviewApiClient(mapOf("api/customer/jobs/5" to JobResponse(Fixtures.job(id = 5))))
        val model = JobDetailModel(5, client, JobArea.customer)
        model.load()

        client.errors["api/customer/jobs/5/quotes/9/accept"] = ApiError.Validation(ValidationErrors("Add a payment card to accept this quote."))
        assertNull(model.acceptQuote(quote))
        assertEquals("Add a payment card to accept this quote.", model.acceptError)
        assertNull(model.toast)

        client.errors["api/customer/jobs/5/quotes/9/accept"] = HttpApiClient.errorFor(409, null, """{"message":"This job was just taken."}""".toByteArray())
        assertNull(model.acceptQuote(quote))
        assertEquals("This job was just taken.", model.acceptError)

        client.errors["api/customer/jobs/5/quotes/9/accept"] = ApiError.Transport("timeout")
        assertNull(model.acceptQuote(quote))
        assertTrue(model.acceptError!!.contains("couldn't reach"))
        assertFalse(model.busy)
    }

    /// Swiping the pay sheet away kills its coroutine scope. That used to
    /// cancel the HTTP call and toast "Could not close this job" about a
    /// card the server may have charged; now the call runs to the end and
    /// the job page gets the real answer.
    @Test fun aChargeInFlightOutlivesItsSheet() = runTest {
        val closed = Fixtures.job(id = 5, status = JobStatus.INVOICED).copy(closedAt = java.time.Instant.EPOCH)
        val gate = CompletableDeferred<Unit>()
        val preview = PreviewApiClient(mapOf("api/customer/jobs/5" to JobResponse(Fixtures.job(id = 5, status = JobStatus.INVOICED)), "api/customer/jobs/5/close" to JobResponse(closed)))
        val client = object : ApiClient {
            override suspend fun <T> send(request: ApiRequest<T>): T {
                if (request.path.endsWith("/close")) gate.await()
                return preview.send(request)
            }
        }
        val model = JobDetailModel(5, client, JobArea.customer)
        model.load()

        val sheet = launch { model.close(0) }
        runCurrent()
        assertTrue(model.busy)
        // A second tap can't start a second charge.
        assertEquals(JobDetailModel.CloseOutcome.InFlight, model.close(0))

        sheet.cancel()
        gate.complete(Unit)
        sheet.join()

        assertEquals(listOf("GET api/customer/jobs/5", "POST api/customer/jobs/5/close"), preview.sent)
        assertTrue(model.justClosed)
        assertNull(model.toast)
        assertFalse(model.busy)
    }
}
