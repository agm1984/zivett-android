package com.zivett.app

import com.zivett.app.core.models.BillingContext
import com.zivett.app.core.models.InvoiceResponse
import com.zivett.app.core.models.JobArea
import com.zivett.app.core.models.JobResponse
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.HttpApiClient
import com.zivett.app.core.network.PaymentAction
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.core.payments.ChallengeOutcome
import com.zivett.app.core.payments.PaymentChallenger
import com.zivett.app.features.customer.account.PayInvoiceModel
import com.zivett.app.features.customer.jobs.JobDetailModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// The bank's confirmation of a pay-time charge (409
/// `payment_action_required`). The SDK can't run in a unit test, so a
/// `PaymentChallenger` stands in for it — what matters here is what the
/// pay surfaces SAY for each way the challenge can end.
class PaymentChallengeTest {
    private val conflictBody = """{"message":"Your bank needs to confirm this payment.","code":"payment_action_required","client_secret":"pi_1_secret_2","payment_method_id":"pm_3"}"""
    private fun actionRequired() = HttpApiClient.errorFor(409, null, conflictBody.toByteArray())
    private val billing = BillingContext(driver = "stripe", publishableKey = "pk_test_x", savedCard = BillingContext.SavedCard("visa", "3184"))

    private fun jobClient() = PreviewApiClient(mapOf(
        "api/customer/jobs/5" to JobResponse(Fixtures.job(id = 5, status = JobStatus.INVOICED)),
        "api/billing/setup-intent" to billing,
    )).also { it.errors["api/customer/jobs/5/close"] = actionRequired() }

    @Test fun theConflictCarriesThePaymentMethodToConfirmWith() {
        assertEquals(PaymentAction("payment_action_required", "pi_1_secret_2", "pm_3"), actionRequired().paymentAction)

        // An older server sends no payment method; other conflicts aren't payment actions.
        val older = HttpApiClient.errorFor(409, null, """{"code":"payment_action_required","client_secret":"pi_1_secret_2"}""".toByteArray())
        assertNull(older.paymentAction!!.paymentMethodId)
        assertNull(HttpApiClient.errorFor(409, null, """{"message":"x","code":"schedule_conflict"}""".toByteArray()).paymentAction)
    }

    @Test fun theChallengeIsRunWithTheServersPaymentMethod() = runTest {
        var seen: PaymentAction? = null
        val model = JobDetailModel(5, jobClient(), JobArea.customer, challenger = PaymentChallenger { _, action -> seen = action; ChallengeOutcome.Canceled })
        model.load()

        assertEquals(JobDetailModel.CloseOutcome.Failed, model.close(0))
        assertEquals("pm_3", seen?.paymentMethodId)
        assertEquals(ChallengeOutcome.CANCELED_COPY, model.toast)
    }

    @Test fun aBankRefusalLandsBesideTheCardLikeADecline() = runTest {
        val model = JobDetailModel(5, jobClient(), JobArea.customer, challenger = PaymentChallenger { _, _ -> ChallengeOutcome.Failed("We are unable to authenticate your payment method.") })
        model.load()

        assertEquals(JobDetailModel.CloseOutcome.Declined("We are unable to authenticate your payment method."), model.close(0))
        assertNull(model.toast)
    }

    /// The result never came back (network died on the way home from the
    /// bank's page): the card MAY have been charged, so the copy must not
    /// say otherwise.
    @Test fun anUnknownResultNeverClaimsNothingWasCharged() = runTest {
        val model = JobDetailModel(5, jobClient(), JobArea.customer, challenger = PaymentChallenger { _, _ -> ChallengeOutcome.Unknown })
        model.load()

        assertEquals(JobDetailModel.CloseOutcome.Failed, model.close(0))
        assertEquals(ChallengeOutcome.UNKNOWN_COPY, model.toast)
        assertFalse(model.toast!!.contains("nothing was charged"))
    }

    @Test fun theInvoiceScreenTellsTheOutcomesApartToo() = runTest {
        suspend fun payWith(outcome: ChallengeOutcome): PayInvoiceModel {
            val client = PreviewApiClient(mapOf("api/billing/setup-intent" to billing))
            client.errors["api/customer/invoices/1/pay"] = actionRequired()
            val model = PayInvoiceModel(Fixtures.invoice(), client, challenger = PaymentChallenger { _, _ -> outcome })
            model.load()
            assertNull(model.pay())
            return model
        }

        assertEquals(ChallengeOutcome.CANCELED_COPY, payWith(ChallengeOutcome.Canceled).error)
        assertEquals("Do not honor.", payWith(ChallengeOutcome.Failed("Do not honor.")).declined)
        val unknown = payWith(ChallengeOutcome.Unknown)
        assertEquals(ChallengeOutcome.UNKNOWN_COPY, unknown.error)
        assertTrue(unknown.declined == null)
    }

    // A pay call that ends WITHOUT an answer (timeout, 5xx, the server's
    // own 503 "couldn't reach the payment provider").

    @Test fun aProviderErrorKeepsTheServersWordsAndACodeToReactTo() {
        val error = HttpApiClient.errorFor(503, null, """{"message":"We couldn't reach the payment provider. Check back shortly before retrying.","code":"payment_provider_error"}""".toByteArray())
        assertEquals("We couldn't reach the payment provider. Check back shortly before retrying.", error.unconfirmedPaymentMessage)
        assertEquals(ApiError.UNCONFIRMED_PAYMENT, ApiError.Transport("timeout").unconfirmedPaymentMessage)
        assertEquals(ApiError.UNCONFIRMED_PAYMENT, HttpApiClient.errorFor(500, null, "{}".toByteArray()).unconfirmedPaymentMessage)
        assertNull(HttpApiClient.errorFor(422, null, """{"message":"x"}""".toByteArray()).unconfirmedPaymentMessage)
    }

    @Test fun aTimeoutThatActuallySettledIsASuccess() = runTest {
        val client = jobClient()
        client.errors["api/customer/jobs/5/close"] = ApiError.Transport("timeout")
        val model = JobDetailModel(5, client, JobArea.customer)
        model.load()
        // By the time we look again, the charge has landed.
        client.responses["api/customer/jobs/5"] = JobResponse(Fixtures.job(id = 5, status = JobStatus.INVOICED).copy(closedAt = java.time.Instant.EPOCH))

        assertEquals(JobDetailModel.CloseOutcome.Closed, model.close(0))
        assertTrue(model.justClosed)
        assertNull(model.toast)
    }

    @Test fun aTimeoutThatDidNotSettleSaysSoInTheSheet() = runTest {
        val client = jobClient()
        client.errors["api/customer/jobs/5/close"] = ApiError.Server(503, "We couldn't reach the payment provider.", "payment_provider_error")
        val model = JobDetailModel(5, client, JobArea.customer)
        model.load()

        assertEquals(JobDetailModel.CloseOutcome.Unconfirmed("We couldn't reach the payment provider."), model.close(0))
        assertNull(model.toast)
        // It looked before it spoke.
        assertEquals("GET api/customer/jobs/5", client.sent.last())
    }

    @Test fun aNonPaymentConflictKeepsTheServersMessage() = runTest {
        val client = jobClient()
        client.errors["api/customer/jobs/5/close"] = HttpApiClient.errorFor(409, null, """{"message":"This job has an open dispute — support will be in touch."}""".toByteArray())
        val model = JobDetailModel(5, client, JobArea.customer)
        model.load()

        assertEquals(JobDetailModel.CloseOutcome.Failed, model.close(0))
        assertEquals("This job has an open dispute — support will be in touch.", model.toast)
    }

    @Test fun theInvoiceScreenLooksBeforeReportingATimeout() = runTest {
        val client = PreviewApiClient(mapOf("api/billing/setup-intent" to billing, "api/customer/invoices/1" to InvoiceResponse(Fixtures.invoice())))
        client.errors["api/customer/invoices/1/pay"] = ApiError.Transport("timeout")
        val unpaid = PayInvoiceModel(Fixtures.invoice(), client)
        assertNull(unpaid.pay())
        assertEquals(ApiError.UNCONFIRMED_PAYMENT, unpaid.error)
        assertNull(unpaid.paid)

        client.responses["api/customer/invoices/1"] = InvoiceResponse(Fixtures.invoice().copy(paidAt = java.time.Instant.EPOCH))
        val settled = PayInvoiceModel(Fixtures.invoice(), client)
        assertTrue(settled.pay()!!.isPaid)
        assertTrue(settled.paid!!.isPaid)
        assertNull(settled.error)
    }
}
