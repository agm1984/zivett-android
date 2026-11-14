package com.zivett.app

import com.zivett.app.core.auth.AuthSession
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.core.storage.InMemoryTokenStore
import com.zivett.app.features.auth.SignupFormModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignupFormTest {
    private fun filled() = SignupFormModel().apply {
        form = form.copy(firstName = "Amara", lastName = "Okafor", email = "amara@example.com", password = "password1", passwordConfirmation = "password1", terms = true)
        phone = "5550123456"
    }

    @Test fun theConfirmationIsRequired() {
        val model = filled()
        assertTrue(model.canSubmit)

        model.form = model.form.copy(passwordConfirmation = "")
        assertFalse(model.canSubmit)
    }

    @Test fun aMismatchedConfirmationStopsBeforeSending() = runTest {
        val client = PreviewApiClient()
        val model = filled()
        model.form = model.form.copy(passwordConfirmation = "password2")

        model.submit(AuthSession(client, InMemoryTokenStore(), "Pixel"))

        assertEquals("The two passwords don't match.", model.fieldErrors["password_confirmation"])
        assertTrue(client.sent.isEmpty())
    }
}
