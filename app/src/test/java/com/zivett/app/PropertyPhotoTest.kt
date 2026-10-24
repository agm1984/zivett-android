package com.zivett.app

import com.zivett.app.core.models.Property
import com.zivett.app.core.models.initialsOf
import com.zivett.app.core.network.JsonCoding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/// A property's photo: the payload's `photo_url` decodes onto the model the
/// thumbnail renders from, and a property without one falls back to its
/// initials.
class PropertyPhotoTest {
    @Test fun aPropertyDecodesItsPhoto() {
        val property = JsonCoding.json.decodeFromString<Property>(
            """{"id":3,"name":"Alder Court","kind":"12-unit residential","address":"14 Alder Court, Nanaimo","photo_url":"https://cdn.zivett.com/property-photos/a.jpg"}""",
        )

        assertEquals("https://cdn.zivett.com/property-photos/a.jpg", property.photoUrl)
    }

    @Test fun aPropertyWithoutAPhotoFallsBackToItsInitials() {
        val property = JsonCoding.json.decodeFromString<Property>(
            """{"id":3,"name":"Alder Court","address":"14 Alder Court","photo_url":null}""",
        )

        assertNull(property.photoUrl)
        assertEquals("AC", initialsOf(property.name))
    }
}
