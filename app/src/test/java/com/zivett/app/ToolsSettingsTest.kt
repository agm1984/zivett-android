package com.zivett.app

import com.zivett.app.core.models.ToolsSettingsResponse
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.features.tools.ToolsSettingsLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/// Business tools settings (server: SettingsController::settings).
class ToolsSettingsTest {
    @Test fun settingsDecodeAndColoursCheck() {
        val settings = JsonCoding.json.decodeFromString<ToolsSettingsResponse>("""
        {"settings": {"gst_number": null, "payment_instructions": "e-Transfer to pay@x.test", "payment_methods": [{"kind": "etransfer", "value": "pay@x.test"}],
          "invoice_numbering": {"mode": "series", "prefix": "INV-", "next_number": 12, "locked": false, "example": "INV-0012"},
          "brand_color": null, "branding": {"name": "Harbour"}}}
        """).settings
        assertEquals(12, settings.invoiceNumbering.nextNumber)
        assertEquals("INV-0012", ToolsSettingsLogic.example("INV-", 12))
        assertTrue(ToolsSettingsLogic.validColor("#1f6f5c"))
        assertFalse(ToolsSettingsLogic.validColor("1f6f5c"))
    }
}
