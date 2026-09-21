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
import com.zivett.app.core.network.JsonCoding
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
import java.time.YearMonth

/// Changing the card on a pay surface. PaymentSheet itself can't run in
/// a unit test, so these cover the decisions around it — and that a
/// declined card reaches the pay sheet as an OUTCOME it can act on, not
/// a toast under a sheet that already dismissed.
class PaymentCardTest {
    private fun context(driver: String = "stripe", saved: Boolean, secret: String? = "seti_secret") =
        BillingContext(driver = driver, publishableKey = "pk_test_x", clientSecret = secret, savedCard = if (saved) BillingContext.SavedCard("visa", "4242") else null)

    @Test fun aSavedCardOrTheSimulatedDriverCanCharge() = runTest {
        val saved = PaymentCardModel(PreviewApiClient(mapOf("api/billing/card" to context(saved = true))))
        saved.load()
        assertTrue(saved.canCharge)

        val none = PaymentCardModel(PreviewApiClient(mapOf("api/billing/card" to context(saved = false))))
        none.load()
        assertFalse(none.canCharge)

        val simulated = PaymentCardModel(PreviewApiClient(mapOf("api/billing/card" to BillingContext(driver = "simulated"))))
        simulated.load()
        assertTrue(simulated.canCharge)
    }

    /// One dropped billing request used to disable Pay for good on the
    /// invoice screen and the home hero. Only a LOADED "no card" blocks.
    @Test fun aFailedBillingFetchNeverBlocksPayAndCanBeRetried() = runTest {
        val client = PreviewApiClient()
        client.errors["api/billing/card"] = ApiError.Transport("offline")
        val invoice = PayInvoiceModel(Fixtures.invoice(), client)
        assertTrue(invoice.canPay) // still loading
        invoice.load()
        assertTrue(invoice.card.billing is Loadable.Failed)
        assertTrue(invoice.canPay)

        client.errors.clear()
        client.responses["api/billing/card"] = context(saved = false)
        invoice.card.retry()
        assertTrue(invoice.card.blocksPay)
        assertFalse(invoice.canPay)
    }

    @Test fun aSavedCardShowsItsExpiryAndKnowsWhenItLapsed() {
        val card = BillingContext.SavedCard("visa", "4242", expMonth = 4, expYear = 2027)
        assertEquals("Visa •••• 4242", card.label)
        assertEquals("exp 04/27", card.expiryLabel)
        // Good THROUGH its expiry month.
        assertFalse(card.isExpired(YearMonth.of(2027, 4)))
        assertTrue(card.isExpired(YearMonth.of(2027, 5)))

        // Cards saved before the server sent an expiry decode as unknown.
        val old = JsonCoding.json.decodeFromString<BillingContext>("""{"driver":"stripe","saved_card":{"brand":"visa","last4":"4242","exp_month":null}}""").savedCard!!
        assertNull(old.expiryLabel)
        assertFalse(old.isExpired())
        assertEquals(4, JsonCoding.json.decodeFromString<BillingContext>("""{"saved_card":{"brand":"visa","last4":"4242","exp_month":4,"exp_year":2027}}""").savedCard!!.expMonth)
    }

    /// Showing the card is a READ. The setup-intent POST used to double
    /// as it, so every home / pay / accept load minted a Stripe
    /// SetupIntent that nobody used.
    @Test fun loadingTheCardNeverMintsASetupIntent() = runTest {
        val client = PreviewApiClient(mapOf("api/billing/card" to context(saved = true, secret = null)))
        val model = PaymentCardModel(client)
        model.load()
        model.retry()
        PayInvoiceModel(Fixtures.invoice(), client).load()

        assertTrue(model.canCharge)
        assertEquals(List(3) { "GET api/billing/card" }, client.sent)
    }

    /// ...and the POST happens exactly when the card form is about to
    /// open — a fresh intent per attempt.
    @Test fun openingTheCardFormIsWhatMintsTheSetupIntent() = runTest {
        val client = PreviewApiClient(mapOf("api/billing/card" to context(saved = true, secret = null), "api/billing/setup-intent" to context(saved = true, secret = null)))
        val model = PaymentCardModel(client)
        model.load()
        model.changeCard()
        model.changeCard()

        assertEquals(listOf("GET api/billing/card", "POST api/billing/setup-intent", "POST api/billing/setup-intent"), client.sent)
    }

    /// A server from before `GET /api/billing/card` answers 404 (or 405 —
    /// the path exists for POST): fall back to the old read.
    @Test fun anOlderServerStillGetsItsCardRead() = runTest {
        for (missing in listOf(ApiError.NotFound, ApiError.Server(405, "Method Not Allowed"))) {
            val client = PreviewApiClient(mapOf("api/billing/setup-intent" to context(saved = true)))
            client.errors["api/billing/card"] = missing
            val model = PaymentCardModel(client)
            model.load()

            assertTrue(model.canCharge)
            assertEquals(listOf("GET api/billing/card", "POST api/billing/setup-intent"), client.sent)
        }

        // Any other failure is a failure — no quiet minting behind it.
        val client = PreviewApiClient(mapOf("api/billing/setup-intent" to context(saved = true)))
        client.errors["api/billing/card"] = ApiError.Transport("offline")
        val model = PaymentCardModel(client)
        model.load()
        assertTrue(model.billing is Loadable.Failed)
        assertEquals(listOf("GET api/billing/card"), client.sent)
    }

    /// The server's `expired` (marketplace clock) beats the phone's guess.
    @Test fun theServersExpiredVerdictWins() {
        val card = BillingContext.SavedCard("visa", "4242", expMonth = 4, expYear = 2027)
        assertTrue(card.copy(expired = true).isExpired(YearMonth.of(2027, 1)))
        assertFalse(card.copy(expired = false).isExpired(YearMonth.of(2030, 1)))
        assertTrue(JsonCoding.json.decodeFromString<BillingContext>("""{"driver":"stripe","publishable_key":"pk_test_x","saved_card":{"brand":"visa","last4":"4242","exp_month":4,"exp_year":2027,"expired":true}}""").savedCard!!.isExpired(YearMonth.of(2027, 1)))
        // A non-stripe driver sends `driver` alone.
        assertTrue(JsonCoding.json.decodeFromString<BillingContext>("""{"driver":"simulated"}""").canChargeWithoutCardForm)
    }

    @Test fun changingACardWithoutASetupIntentExplainsItself() = runTest {
        val client = PreviewApiClient(mapOf("api/billing/setup-intent" to context(saved = true, secret = null)))
        val model = PaymentCardModel(client)

        assertFalse(model.changeCard())

        assertTrue(model.error!!.contains("isn't available"))
        assertFalse(model.changing)
        // It minted a FRESH intent (secrets are single-use) and never
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
