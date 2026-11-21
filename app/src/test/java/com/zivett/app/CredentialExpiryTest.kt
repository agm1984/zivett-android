package com.zivett.app

import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.CompanyOrganizationResponse
import com.zivett.app.core.models.CompanySetup
import com.zivett.app.core.models.CredentialExpiryResponse
import com.zivett.app.core.models.OrganizationProfile
import com.zivett.app.core.models.PassportResponse
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.core.network.ValidationErrors
import com.zivett.app.features.company.CompanySetupModel
import com.zivett.app.features.company.CredentialExpiry
import com.zivett.app.features.company.PassportModel
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Credential expiry dates (server: OnboardingState's `missing_expiry`,
/// `PATCH api/company/credentials/{id}/expiry`; web PR #181).
class CredentialExpiryTest {
    private val today = LocalDate.of(2026, 11, 21)

    private fun steps(
        uploaded: Int = 3,
        total: Int = 3,
        complete: Boolean = false,
        missingExpiry: List<String>? = listOf("Insurance"),
        plan: Boolean? = true,
    ) = CompanySetup.Steps(
        profile = CompanySetup.Steps.Step(complete = true, missing = emptyList()),
        details = CompanySetup.Steps.Step(complete = true, missing = emptyList()),
        credentials = CompanySetup.Steps.Step(complete = complete, uploaded = uploaded, total = total, missingExpiry = missingExpiry),
        plan = plan?.let { CompanySetup.Steps.Plan(complete = it) },
    )

    @Test fun theCredentialsStepDecodesMissingExpiryAndDocumentDates() {
        val decoded = JsonCoding.json.decodeFromString<CompanySetup>("""
        {"status": "draft", "submitted_at": null, "steps": {
          "profile": {"complete": true, "missing": []},
          "details": {"complete": true, "missing": []},
          "credentials": {"complete": false, "missing_expiry": ["Insurance"], "uploaded": 3, "total": 3,
            "documents": [{"kind": "insurance", "label": "Insurance", "status": "submitted", "rejection_reason": null, "expires_at": null, "document_id": 9, "original_name": "policy.pdf"}],
            "optional_documents": [{"kind": "worksafebc", "label": "WorkSafeBC", "status": "missing", "expires_at": null, "document_id": null}]},
          "plan": {"complete": true, "confirmed": true, "plan_name": "Basic"}}}
        """.trimIndent())
        assertEquals(listOf("Insurance"), decoded.steps.credentials.missingExpiry)
        assertEquals(9, decoded.steps.credentials.documents?.single()?.documentId)
        assertEquals("worksafebc", decoded.steps.credentials.optionalDocuments?.single()?.kind)
    }

    @Test fun anOlderServerHasNoMissingExpiry() {
        val decoded = JsonCoding.json.decodeFromString<CompanySetup.Steps.Step>("""{"complete": true, "uploaded": 3, "total": 3}""")
        assertNull(decoded.missingExpiry)
        assertNull(CredentialExpiry.stillNeededLine(decoded.missingExpiry.orEmpty()))
    }

    @Test fun theBlockerNamesTheMissingDatesLikeTheWeb() {
        assertEquals("Still needed before you can continue: Insurance expiry date.", CredentialExpiry.stillNeededLine(listOf("Insurance")))
        assertEquals(
            "Still needed before you can continue: Licence/Certification expiry date, Insurance expiry date.",
            CredentialExpiry.stillNeededLine(listOf("Licence/Certification", "Insurance")),
        )
        assertNull(CredentialExpiry.stillNeededLine(emptyList()))
    }

    @Test fun submitStaysBlockedWhileADateIsMissing() {
        assertEquals(listOf("Insurance expiry date"), CompanySetupModel.missingSummary(steps()))
        // Files missing AND a date missing: both are named.
        assertEquals(listOf("1 of 3 required documents", "Insurance expiry date"), CompanySetupModel.missingSummary(steps(uploaded = 2)))
        // Done: nothing blocks.
        assertEquals(emptyList<String>(), CompanySetupModel.missingSummary(steps(complete = true, missingExpiry = emptyList())))
        // An incomplete step the app can't explain still blocks, in plain words.
        assertEquals(listOf("credentials"), CompanySetupModel.missingSummary(steps(missingExpiry = null)))
    }

    @Test fun thePlanComesLastInTheSummary() {
        val summary = CompanySetupModel.missingSummary(steps(uploaded = 1, plan = false))
        assertEquals("choose a plan", summary.last())
        assertEquals("2 of 3 required documents", summary.first())
    }

    @Test fun theChecklistDetailSaysWhichDatesAreMissingOnceEveryFileIsIn() {
        assertEquals("Add the expiry date for: Insurance", CredentialExpiry.credentialsDetail(steps().credentials))
        assertEquals("2 of 3 documents uploaded", CredentialExpiry.credentialsDetail(steps(uploaded = 2).credentials))
        assertEquals("3 of 3 documents uploaded", CredentialExpiry.credentialsDetail(steps(complete = true, missingExpiry = emptyList()).credentials))
    }

    @Test fun theControlShowsTheStoredDateWhenAFileIsOnRecord() {
        assertEquals("2027-03-31", CredentialExpiry.controlLabel(hasDocument = true, stored = "2027-03-31", picked = LocalDate.of(2028, 1, 1)))
        assertEquals("Set expiry date", CredentialExpiry.controlLabel(hasDocument = true, stored = null, picked = null))
        // Before any upload, the picked date rides the upload.
        assertEquals("2027-12-31", CredentialExpiry.controlLabel(hasDocument = false, stored = null, picked = LocalDate.of(2027, 12, 31)))
        assertEquals("Set expiry date", CredentialExpiry.controlLabel(hasDocument = false, stored = null, picked = null))
    }

    @Test fun thePickerOpensOnTheStoredDate() {
        assertEquals(LocalDate.of(2027, 3, 31), CredentialExpiry.initialDate("2027-03-31", null, today))
        assertEquals(LocalDate.of(2027, 6, 1), CredentialExpiry.initialDate(null, LocalDate.of(2027, 6, 1), today))
        assertEquals(LocalDate.of(2027, 11, 21), CredentialExpiry.initialDate(null, null, today))
        assertEquals(LocalDate.of(2027, 11, 21), CredentialExpiry.initialDate("not a date", null, today))
    }

    @Test fun aVerifiedDatedDocumentIsLocked() {
        assertTrue(CredentialExpiry.dateLocked("approved", "2027-03-31"))
        assertTrue(CredentialExpiry.dateLocked("expired", "2026-01-31"))
        assertFalse(CredentialExpiry.dateLocked("approved", null))
        assertFalse(CredentialExpiry.dateLocked("submitted", "2027-03-31"))
    }

    @Test fun rowsNeedingADateMatchTheServersLabels() {
        assertTrue(CredentialExpiry.needsDate("Insurance", listOf("Insurance")))
        assertFalse(CredentialExpiry.needsDate("Photo ID", listOf("Insurance")))
    }

    private fun passport(expiresAt: String? = null) = PassportResponse(
        PassportResponse.Passport(
            name = "Harbour Plumbing",
            documents = PassportResponse.Passport.Documents(uploaded = 3, total = 3),
            credentials = listOf(PassportResponse.Passport.Credential(field = "Insurance", kind = "insurance", status = "submitted", documentId = 9, expiresAt = expiresAt, required = true)),
        ),
    )

    private fun organization(missingExpiry: List<String>) = CompanyOrganizationResponse(
        OrganizationProfile(id = 1, name = "Harbour Plumbing"),
        CompanySetup(status = "draft", steps = steps(missingExpiry = missingExpiry)),
    )

    @Test fun thePassportReadsMissingDatesFromTheSetupPayload() = runTest {
        val client = PreviewApiClient(mapOf(
            CompanyEndpoints.passport().path to passport(),
            CompanyEndpoints.organization().path to organization(listOf("Insurance")),
        ))
        val model = PassportModel(client)
        model.load()
        assertEquals(listOf("Insurance"), model.missingExpiry)
    }

    @Test fun thePassportStillLoadsWhenTheSetupPayloadFails() = runTest {
        val client = PreviewApiClient(mapOf(CompanyEndpoints.passport().path to passport()))
        val model = PassportModel(client)
        model.load()
        assertEquals("Harbour Plumbing", model.state.value?.passport?.name)
        assertEquals(emptyList<String>(), model.missingExpiry)
    }

    @Test fun savingADateOnAFileOnRecordPatchesItAndReloads() = runTest {
        val path = CompanyEndpoints.setCredentialExpiry(9, "2027-03-31").path
        assertEquals("api/company/credentials/9/expiry", path)
        val client = PreviewApiClient(mapOf(
            CompanyEndpoints.passport().path to passport(),
            CompanyEndpoints.organization().path to organization(listOf("Insurance")),
            path to CredentialExpiryResponse(CredentialExpiryResponse.Document(9, "2027-03-31")),
        ))
        val model = PassportModel(client)
        model.load()
        client.responses[CompanyEndpoints.passport().path] = passport(expiresAt = "2027-03-31")
        client.responses[CompanyEndpoints.organization().path] = organization(emptyList())

        assertTrue(model.setExpiry("insurance", 9, LocalDate.of(2027, 3, 31), today))
        assertTrue("PATCH api/company/credentials/9/expiry" in client.sent)
        assertEquals("Expiry date saved", model.toast)
        assertEquals(emptyList<String>(), model.missingExpiry)
        assertEquals("2027-03-31", model.state.value?.passport?.credentials?.single()?.expiresAt)
        assertNull(model.expiryErrors["insurance"])
        assertNull(model.savingExpiryKind)
    }

    @Test fun aRefusedDateShowsTheServersMessageUnderTheRow() = runTest {
        val path = CompanyEndpoints.setCredentialExpiry(9, "2027-03-31").path
        val client = PreviewApiClient(mapOf(CompanyEndpoints.passport().path to passport()))
        client.errors[path] = ApiError.Validation(ValidationErrors("This document is already verified. Upload the renewed document to change its expiry date."))
        val model = PassportModel(client)

        assertFalse(model.setExpiry("insurance", 9, LocalDate.of(2027, 3, 31), today))
        assertEquals("This document is already verified. Upload the renewed document to change its expiry date.", model.expiryErrors["insurance"])
        assertNull(model.toast)

        // A field error wins over the summary message.
        client.errors[path] = ApiError.Validation(ValidationErrors("Invalid.", mapOf("expires_at" to listOf("The expiry date has to be after today."))))
        assertFalse(model.setExpiry("insurance", 9, LocalDate.of(2027, 3, 31), today))
        assertEquals("The expiry date has to be after today.", model.expiryErrors["insurance"])
    }

    @Test fun aDateThatHasPassedIsCaughtBeforeSending() = runTest {
        val client = PreviewApiClient()
        val model = PassportModel(client)
        assertFalse(model.setExpiry("insurance", 9, today, today))
        assertEquals("The expiry date has to be after today.", model.expiryErrors["insurance"])
        assertTrue(client.sent.isEmpty())
        assertNull(CredentialExpiry.dateProblem(today.plusDays(1), today))
    }
}
