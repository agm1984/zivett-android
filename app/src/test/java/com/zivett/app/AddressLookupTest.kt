package com.zivett.app

import com.zivett.app.core.AddressLookup
import com.zivett.app.core.AddressParts
import com.zivett.app.core.models.GeocodeDetails
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.PreviewApiClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressLookupTest {
    private val found = GeocodeDetails(parts = GeocodeDetails.Parts(city = "Nanaimo", region = "BC", postalCode = "V9R 2K1"))

    @Test fun onlyAStreetWithANumberAndANameIsLookedUp() {
        assertTrue(AddressLookup.worthLookingUp(AddressParts("14 Alder Ct")))
        assertFalse(AddressLookup.worthLookingUp(AddressParts("14")))
        assertFalse(AddressLookup.worthLookingUp(AddressParts("Alder Court")))
        // Nothing left blank → nothing to fill.
        assertFalse(AddressLookup.worthLookingUp(AddressParts("14 Alder Ct", "Nanaimo", "BC", "V9R 2K1")))
    }

    @Test fun mergeFillsOnlyBlankFields() {
        val (merged, filled) = AddressLookup.merge(AddressParts("14 Alder Ct", city = "Lantzville"), AddressLookup.Found("Nanaimo", "bc", "V9R 2K1"))
        assertEquals(AddressParts("14 Alder Ct", "Lantzville", "BC", "V9R 2K1"), merged)
        assertEquals(listOf("region", "postal"), filled)
    }

    @Test fun anUnknownRegionIsDropped() {
        val (merged, filled) = AddressLookup.merge(AddressParts("14 Alder Ct"), AddressLookup.Found(null, "Electoral Area A", null))
        assertEquals("", merged.region); assertTrue(filled.isEmpty())
    }

    @Test fun fillSendsTheTypedPartsAndMergesTheResult() = runTest {
        val client = PreviewApiClient(mapOf("api/geocode" to found))
        val parts = AddressParts("14 Alder Ct", region = "BC")
        assertEquals(AddressParts("14 Alder Ct", "Nanaimo", "BC", "V9R 2K1"), AddressLookup.fill(client, parts) { parts })
        assertEquals("14 Alder Ct, BC", AddressLookup.query(parts))
    }

    @Test fun fillGivesUpWhenTheStreetChangedMeanwhile() = runTest {
        val client = PreviewApiClient(mapOf("api/geocode" to found))
        assertNull(AddressLookup.fill(client, AddressParts("14 Alder Ct")) { AddressParts("14 Alder Court West") })
    }

    @Test fun aFailedLookupFillsNothing() = runTest {
        val client = PreviewApiClient().apply { errors["api/geocode"] = ApiError.Server(503, "down") }
        assertNull(AddressLookup.fill(client, AddressParts("14 Alder Ct")) { AddressParts("14 Alder Ct") })
        // Not worth looking up → never calls the server.
        val idle = PreviewApiClient()
        assertNull(AddressLookup.fill(idle, AddressParts("Alder")) { AddressParts("Alder") })
        assertTrue(idle.sent.isEmpty())
    }
}
