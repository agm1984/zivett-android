package com.zivett.app

import com.zivett.app.core.models.BillingContext
import com.zivett.app.core.models.JobArea
import com.zivett.app.core.models.JobResponse
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.core.network.ValidationErrors
import com.zivett.app.core.payments.PaymentCardModel
import com.zivett.app.features.customer.jobs.JobDetailModel
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
}
