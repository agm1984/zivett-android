package com.zivett.app

import com.zivett.app.core.models.BusinessOrganizationResponse
import com.zivett.app.core.models.BusinessSetup
import com.zivett.app.core.models.OrganizationProfile
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.features.business.OrganizationFormModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
