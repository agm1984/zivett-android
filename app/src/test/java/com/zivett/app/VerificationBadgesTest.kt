package com.zivett.app

import com.zivett.app.core.models.CompanySummary
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.features.shared.expiredLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/// The quote card's verification chips: a lapsed item reads as expired in
/// plain words, and the quote payload's badges decode into the model the
/// chips render from.
class VerificationBadgesTest {
    @Test fun aLapsedItemReadsAsExpired() {
        assertEquals("Insurance expired", expiredLabel("Insurance verified"))
        assertEquals("Background check expired", expiredLabel("Background checked"))
        assertEquals("WorkSafeBC clearance expired", expiredLabel("WorkSafeBC clearance"))
    }

    @Test fun quoteCompaniesDecodeTheirBadges() {
        val company = JsonCoding.json.decodeFromString<CompanySummary>(
            """{"id":7,"name":"Ravensworth Plumbing","plan":"pro","rating":4.8,"count":6,"verification":[""" +
                """{"key":"worksafebc","label":"WorkSafeBC clearance","status":"verified","verified_at":"2026-10-20","expires_at":"2027-01-20"},""" +
                """{"key":"insurance","label":"Insurance verified","status":"expired","verified_at":null,"expires_at":"2026-10-01"}]}""",
        )

        assertEquals(listOf("worksafebc", "insurance"), company.verification?.map { it.key })
        assertEquals("2026-10-20", company.verification?.first()?.verifiedAt)
        assertEquals("expired", company.verification?.last()?.status)
    }
}
