package com.zivett.app

import com.zivett.app.core.models.ClockEndpoints
import com.zivett.app.core.models.ClockState
import com.zivett.app.core.models.MyDay
import com.zivett.app.core.models.MyHours
import com.zivett.app.core.models.ShiftBody
import com.zivett.app.core.models.clockState
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.network.PreviewApiClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// The team clock and My hours (server: TeamShiftsTest, PayrollTest):
/// payloads decode, older servers and businesses without tools included.
class TeamClockTest {
    private val shiftRow = """
    {"id": 5, "work_date": "2026-11-12", "started_at": "2026-11-12T15:02:00.000000Z", "ended_at": null,
     "open": true, "on_break": true, "paid_minutes": 190, "break_minutes": 12, "start_source": "work",
     "end_source": null, "edited": false, "original_started_at": null, "original_ended_at": null,
     "note": null, "approved": false, "needs_review": true}
    """.trimIndent()

    private val state = """{"enabled": true, "shift": $shiftRow, "on_break": true, "break_started_at": "2026-11-12T18:00:00.000000Z", "today_minutes": 190}"""

    @Test fun theClockStateDecodes() {
        val decoded = JsonCoding.json.decodeFromString<ClockState>(state)
        assertTrue(decoded.enabled && decoded.clockedIn && decoded.onBreak)
        assertEquals(190, decoded.todayMinutes)
        assertEquals("work", decoded.shift?.startSource)
        assertTrue(decoded.shift?.needsReview == true)
        assertTrue(decoded.breakStartedAt != null)
    }

    @Test fun aDisabledClockDecodes() {
        // TeamShifts::state without Business tools has no break_started_at.
        val decoded = JsonCoding.json.decodeFromString<ClockState>("""{"enabled": false, "shift": null, "on_break": false, "today_minutes": 0}""")
        assertFalse(decoded.enabled)
        assertFalse(decoded.clockedIn)
    }

    @Test fun myDayCarriesTheClockAndOlderServersDoNot() {
        val withShift = JsonCoding.json.decodeFromString<MyDay>("""{"date": "2026-11-12", "jobs": [], "shift": $state}""")
        assertEquals(5, withShift.shift?.shift?.id)
        val older = JsonCoding.json.decodeFromString<MyDay>("""{"date": "2026-11-12", "jobs": []}""")
        assertNull(older.shift)
    }

    @Test fun aNotFoundClockReadsAsDisabled() = runTest {
        val client = PreviewApiClient()
        client.errors[ClockEndpoints.state().path] = ApiError.NotFound
        assertFalse(client.clockState().enabled)
    }

    @Test fun myHoursDecodes() {
        val decoded = JsonCoding.json.decodeFromString<MyHours>(
            """{"period": {"start": "2026-11-09", "end": "2026-11-22"}, "previous": "2026-11-08", "next": "2026-11-23",
               "totals": {"paid_minutes": 600, "regular_minutes": 480, "overtime_minutes": 120, "double_minutes": 0, "break_minutes": 30, "days_worked": 1},
               "days": [{"date": "2026-11-12", "paid_minutes": 600, "regular_minutes": 480, "overtime_minutes": 120, "double_minutes": 0, "break_minutes": 30,
                         "shifts": [$shiftRow]}],
               "clock": $state,
               "rules": {"daily_ot": 8.0, "daily_double": 12.0, "weekly_ot": null}}""",
        )
        assertEquals("2026-11-09", decoded.period.start)
        assertEquals(120, decoded.totals.overtimeMinutes)
        assertEquals(5, decoded.days.single().shifts.single().id)
        assertEquals(8.0, decoded.rules.dailyOt!!, 0.0)
        assertNull(decoded.rules.weeklyOt)
    }

    @Test fun shiftBodiesEncodeSnakeCaseAndDropANullNote() {
        val body = JsonCoding.json.encodeToString(ShiftBody.serializer(), ShiftBody("2026-11-12T15:00:00Z", "2026-11-13T00:30:00Z", 30, null, "2026-11-12"))
        assertTrue(body.contains("\"started_at\""))
        assertTrue(body.contains("\"break_minutes\":30"))
        assertFalse(body.contains("note"))
    }
}
