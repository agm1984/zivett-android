package com.zivett.app

import com.zivett.app.core.models.CampaignAudience
import com.zivett.app.core.models.CampaignBody
import com.zivett.app.core.models.CampaignResponse
import com.zivett.app.core.models.CampaignsResponse
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.features.tools.CampaignsLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/// Campaigns (server: CampaignController, ProCampaigns::row / results).
class CampaignsTest {
    @Test fun listAndCampaignDecode() {
        val list = JsonCoding.json.decodeFromString<CampaignsResponse>("""
        {"campaigns": [{"id": 3, "template": "tune_up", "subject": "Spring tune-up", "heading": "Hi", "body": "Hello", "button_label": null, "button_url": null,
          "status": "scheduled", "recipients_count": 0, "sent_count": 0, "failed_count": 0, "skipped_count": 0, "sent_at": null, "finished_at": null,
          "scheduled_for": "2026-11-26T17:00:00.000000Z", "audience": {"last_job": "within_12", "cities": ["Nanaimo"], "category_ids": [], "skip_unengaged": false},
          "schedule_failed_reason": null, "updated_at": "2026-11-25T00:00:00.000000Z"}],
         "summary": {"allowed": true, "upgrade": null, "weekly_limit": 2, "sent_this_week": 1, "daily_emails": 200, "audience": 41, "blocked": null, "relationship_years": 2, "inquiry_months": 6},
         "templates": [{"key": "tune_up", "name": "Seasonal check-up", "about": "x", "subject": "Time", "heading": "It's", "body": "Hi {first_name}", "button_label": null}],
         "audience_options": {"cities": [{"name": "Nanaimo", "count": 12}], "categories": [{"id": 1, "name": "Plumbing", "count": 4}], "last_job": ["within_6"], "unengaged_after": 3},
         "can_edit": true}
        """)
        val campaign = list.campaigns.single()
        assertEquals("Scheduled to go to your recent customers on Thu, Nov 26 at 9:00 am (Pacific time).", CampaignsLogic.statusLine(campaign))
        assertTrue(campaign.audience!!.narrows)
        assertEquals("1 of 2 campaigns sent this week.", CampaignsLogic.weekLine(list.summary))
        assertFalse(CampaignAudience().narrows)

        val shown = JsonCoding.json.decodeFromString<CampaignResponse>("""
        {"campaign": {"id": 3, "subject": "S", "heading": "H", "body": "B", "status": "sent", "sent_count": 40, "sent_at": "2026-11-20T17:00:00.000000Z"},
         "problems": [], "results": {"clicked": 5, "booked": 1, "booked_cents": 30000, "within_days": 30,
           "people": [{"customer": {"id": 9, "name": "Pat"}, "clicked_at": "2026-11-21T00:00:00.000000Z", "booked": {"job_id": 3, "code": "J-1", "title": "Tap", "booked_at": "2026-11-22T00:00:00.000000Z", "price_cents": 30000}}]}}
        """)
        assertEquals(30000, shown.results?.bookedCents)
        assertEquals("Sent to 40 customers on Nov 20, 2026.", CampaignsLogic.statusLine(shown.campaign))
    }

    @Test fun bodiesLeaveTheAudienceOutWhenItDoesntNarrow() {
        val text = JsonCoding.json.encodeToString(CampaignBody.serializer(), CampaignBody("custom", "S", "H", "B"))
        assertFalse(text.contains("audience"))
        assertEquals("2026-11-26 09:00", CampaignsLogic.sendAt("2026-11-26", "09:00"))
    }
}
