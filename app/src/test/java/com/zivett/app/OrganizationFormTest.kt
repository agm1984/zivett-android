package com.zivett.app

import com.zivett.app.core.models.BusinessOrganizationResponse
import com.zivett.app.core.models.BusinessSetup
import com.zivett.app.core.models.OrganizationProfile
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.features.business.OrganizationFormModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganizationFormTest {
    private fun response(org: OrganizationProfile) = BusinessOrganizationResponse(
        organization = org,
        setup = BusinessSetup(status = "pending", steps = BusinessSetup.Steps(
            profile = BusinessSetup.Steps.Profile(false, emptyList()), properties = BusinessSetup.Steps.Properties(false, 0),
        )),
    )

    private suspend fun loaded(org: OrganizationProfile): OrganizationFormModel {
        val model = OrganizationFormModel(PreviewApiClient(mapOf("api/business/organization" to response(org))), company = false)
        model.load()
        return model
    }

    @Test fun theNoLogoMonogramIsTheFirstTwoInitials() {
        assertEquals("RP", OrganizationFormModel.initials("Ravensworth  plumbing & heating"))
        assertEquals("A", OrganizationFormModel.initials(" acme "))
        assertEquals("—", OrganizationFormModel.initials(""))
    }

    @Test fun theOptionalExtrasStayFoldedUntilOneHasAValue() = runTest {
        assertFalse(loaded(OrganizationProfile(id = 1, name = "Harbour Strata")).extrasOpen)
        assertTrue(loaded(OrganizationProfile(id = 1, name = "Harbour Strata", website = "https://harbour.example")).extrasOpen)
    }

    @Test fun aBlockedContinueMarksEachMissingFieldAndNamesTheFirst() = runTest {
        val model = loaded(OrganizationProfile(id = 1, name = "Harbour Strata"))
        // Form order, not label order; unknown labels are ignored.
        assertEquals("address_line1", model.flagMissing(listOf("phone", "postcode", "address", "something new")))
        assertEquals(setOf("address_line1", "postal_code", "phone"), model.fieldErrors.keys)
        assertEquals("Needed to continue.", model.fieldErrors["phone"])
        assertNull(model.flagMissing(listOf("something new")))
    }

    @Test fun editingAFlaggedFieldClearsItsError() = runTest {
        val model = loaded(OrganizationProfile(id = 1, name = "Harbour Strata"))
        model.flagMissing(listOf("city", "phone"))
        model.set("city", "Nanaimo")
        assertEquals(setOf("phone"), model.fieldErrors.keys)
        // Re-setting the same value is not an edit.
        model.set("phone", "")
        assertEquals(setOf("phone"), model.fieldErrors.keys)
    }
}
