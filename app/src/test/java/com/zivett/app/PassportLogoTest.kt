package com.zivett.app

import com.zivett.app.core.models.PassportResponse
import com.zivett.app.core.network.JsonCoding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/// The Passport header's logo: `passport.logo_url` decodes onto the model,
/// and a payload without it (or an older server) decodes with no logo, so
/// the header falls back to initials.
class PassportLogoTest {
    @Test fun thePassportDecodesItsLogo() {
        val response = JsonCoding.json.decodeFromString<PassportResponse>(
            """{"passport":{"name":"Harbour Plumbing","approved":true,"documents":{"uploaded":4,"total":4},"logo_url":"https://cdn.zivett.com/org-logos/h.jpg"}}""",
        )

        assertEquals("https://cdn.zivett.com/org-logos/h.jpg", response.passport.logoUrl)
    }

    @Test fun aPassportWithoutALogoDecodesWithNone() {
        val nullLogo = JsonCoding.json.decodeFromString<PassportResponse>(
            """{"passport":{"name":"Harbour Plumbing","documents":{"uploaded":1,"total":4},"logo_url":null}}""",
        )
        val olderServer = JsonCoding.json.decodeFromString<PassportResponse>(
            """{"passport":{"name":"Harbour Plumbing","documents":{"uploaded":1,"total":4}}}""",
        )

        assertNull(nullLogo.passport.logoUrl)
        assertNull(olderServer.passport.logoUrl)
    }
}
