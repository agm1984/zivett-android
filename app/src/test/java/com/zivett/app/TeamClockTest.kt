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
import com.zivett.app.features.company.MyDayModel
import com.zivett.app.features.company.MyHoursModel
import com.zivett.app.features.company.ShiftForm
import com.zivett.app.core.network.ValidationErrors
import com.zivett.app.features.company.TeamClockLogic
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
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

    // Formatting (web lib/payroll.js formatDuration)

    @Test fun durationsReadTheWayTheWebWritesThem() {
        assertEquals("—", TeamClockLogic.formatDuration(0))
        assertEquals("45 min", TeamClockLogic.formatDuration(45))
        assertEquals("7 h", TeamClockLogic.formatDuration(420))
        assertEquals("7 h 05 min", TeamClockLogic.formatDuration(425))
        assertEquals("7 h 30 min", TeamClockLogic.formatDuration(450))
        assertEquals("25 h 01 min", TeamClockLogic.formatDuration(1501))
        assertEquals("0 min", TeamClockLogic.durationOrZero(0))
    }

    // The clock card

    @Test fun theCardSaysWhereTheDayStands() {
        val now = Instant.parse("2026-11-12T18:12:00Z")
        val off = ClockState(enabled = true)
        assertEquals("You're not clocked in", TeamClockLogic.cardTitle(off, now, ZoneOffset.UTC))
        assertEquals("Clock in when your working day starts.", TeamClockLogic.cardLine(off, now, now))
        assertEquals("2 h paid today", TeamClockLogic.cardLine(off.copy(todayMinutes = 120), now, now))

        val on = JsonCoding.json.decodeFromString<ClockState>(state).copy(onBreak = false, breakStartedAt = null)
        assertEquals("On the clock since 3:02 pm", TeamClockLogic.cardTitle(on, now, ZoneOffset.UTC))
        // Ticks: 190 read 30 minutes ago → 220.
        assertEquals("3 h 40 min paid today", TeamClockLogic.cardLine(on, now.minusSeconds(30 * 60), now))
        assertTrue(TeamClockLogic.startedByWork(on))

        val onBreak = JsonCoding.json.decodeFromString<ClockState>(state)
        assertEquals("On a break · 12 min", TeamClockLogic.cardTitle(onBreak, now, ZoneOffset.UTC))
        // A break doesn't tick the paid minutes.
        assertEquals("3 h 10 min paid today", TeamClockLogic.cardLine(onBreak, now.minusSeconds(30 * 60), now))
        assertEquals("On a break · 0 min", TeamClockLogic.cardTitle(onBreak.copy(breakStartedAt = now), now, ZoneOffset.UTC))
    }

    @Test fun myDayClockActionsPostThenReread() = runTest {
        val clock = JsonCoding.json.decodeFromString<ClockState>(state)
        val day = MyDay(date = "2026-11-12", isToday = true, shift = ClockState(enabled = true))
        val client = PreviewApiClient(mapOf("api/company/my-day" to day, ClockEndpoints.clockIn().path to clock))
        val model = MyDayModel(client)
        model.load()
        assertFalse(model.clock!!.clockedIn)
        client.sent.clear()
        model.startDay()
        assertEquals(listOf("POST api/company/clock/in", "GET api/company/my-day"), client.sent)
        assertEquals("You're clocked in — have a good day", model.toast)
        client.sent.clear()
        client.responses[ClockEndpoints.endBreak().path] = clock
        model.endBreak()
        assertEquals("DELETE api/company/clock/break", client.sent.first())
        assertEquals("Back on the clock", model.toast)
    }

    // Adding a missed day (web ShiftModal.vue)

    @Test fun anEndBeforeTheStartRunsIntoTheNextDay() {
        val day = LocalDate.of(2026, 11, 12)
        val zone = ZoneId.of("America/Vancouver")
        val late = TeamClockLogic.endMoment(day, LocalTime.of(18, 0), LocalTime.of(2, 30), zone)
        assertEquals(LocalDate.of(2026, 11, 13), late.toLocalDate())
        assertTrue(TeamClockLogic.endsNextDay(LocalTime.of(18, 0), LocalTime.of(2, 30)))
        assertEquals(8 * 60, TeamClockLogic.paidMinutes(day, LocalTime.of(18, 0), LocalTime.of(2, 30), 30, zone))
        // Same time = a full 24 h, as on the web (end <= start).
        assertTrue(TeamClockLogic.endsNextDay(LocalTime.of(8, 0), LocalTime.of(8, 0)))
        // An ordinary day stays on its date.
        assertFalse(TeamClockLogic.endsNextDay(LocalTime.of(8, 0), LocalTime.of(16, 30)))
        assertEquals(day, TeamClockLogic.endMoment(day, LocalTime.of(8, 0), LocalTime.of(16, 30), zone).toLocalDate())
        assertEquals(8 * 60, TeamClockLogic.paidMinutes(day, LocalTime.of(8, 0), LocalTime.of(16, 30), 30, zone))
        // A break longer than the shift never goes negative.
        assertEquals(0, TeamClockLogic.paidMinutes(day, LocalTime.of(8, 0), LocalTime.of(9, 0), 90, zone))
        // ISO in UTC for the server: 6 pm Pacific (PST) = 02:00Z next day.
        assertEquals("2026-11-13T02:00:00Z", TeamClockLogic.iso(day, LocalTime.of(18, 0), zone))
    }

    @Test fun myHoursWordsShiftsTotalsAndRules() {
        val shift = JsonCoding.json.decodeFromString<ClockState>(state).shift!!
        assertEquals(listOf("Started at your first job step"), TeamClockLogic.flags(shift))
        assertEquals(
            listOf("Started at your first job step", "Closed automatically — you forgot to clock out", "Times changed"),
            TeamClockLogic.flags(shift.copy(endSource = "auto", edited = true)),
        )
        assertEquals(listOf("Added after the fact"), TeamClockLogic.flags(shift.copy(startSource = "added")))
        assertEquals("3:02 pm – now", TeamClockLogic.shiftTimes(shift, ZoneOffset.UTC))
        assertEquals("12 min break · 3 h 10 min paid", TeamClockLogic.shiftLine(shift))
        assertEquals(
            "Your company's rules: over 8 h a day is time-and-a-half; over 12 h is double time; over 40 regular hours a week is time-and-a-half.",
            TeamClockLogic.rulesLine(MyHours.Rules(8.0, 12.0, 40.0)),
        )
        assertEquals("Your company's rules: over 7.5 h a day is time-and-a-half.", TeamClockLogic.rulesLine(MyHours.Rules(dailyOt = 7.5)))
        assertNull(TeamClockLogic.rulesLine(MyHours.Rules()))
        val labels = TeamClockLogic.totals(MyHours.Totals(paidMinutes = 600, regularMinutes = 480, overtimeMinutes = 120, daysWorked = 1)).map { it.first }
        assertEquals(listOf("Paid hours", "Regular", "Overtime (1.5×)", "Days worked"), labels)
        assertTrue(TeamClockLogic.totals(MyHours.Totals(doubleMinutes = 30)).any { it.first == "Double time" })
        assertEquals("Mon, Nov 9 – Sun, Nov 22", TeamClockLogic.periodHeading(MyHours.Period("2026-11-09", "2026-11-22")))
        assertEquals("incl. 2 h overtime", TeamClockLogic.dayOvertime(MyHours.Day("2026-11-12", overtimeMinutes = 120)))
    }

    // The add / fix sheet

    private val pacific = ZoneId.of("America/Vancouver")

    @Test fun aLateShiftBodyEndsOnTheNextDay() {
        val form = ShiftForm(day = LocalDate.of(2026, 11, 12), start = LocalTime.of(18, 0), end = LocalTime.of(2, 30), breakText = "30", note = " Forgot ")
        val body = form.body("2026-11-09", pacific)
        assertEquals("2026-11-13T02:00:00Z", body.startedAt)
        assertEquals("2026-11-13T10:30:00Z", body.endedAt)
        assertEquals(30, body.breakMinutes)
        assertEquals("Forgot", body.note)
        assertEquals("Paid time: 8 h — ends the next day", form.paidLine(pacific))
        assertEquals("Paid time: 8 h", form.copy(start = LocalTime.of(8, 0), end = LocalTime.of(16, 30)).paidLine(pacific))
    }

    @Test fun theNoteIsRequiredAndAnOpenShiftMayStayOpen() {
        val adding = ShiftForm.adding(LocalDate.of(2026, 11, 12))
        assertFalse(adding.canSave)
        assertTrue(adding.copy(note = "Forgot").canSave)
        val shift = JsonCoding.json.decodeFromString<ClockState>(state).shift!!
        val fixing = ShiftForm.fixing(shift, ZoneOffset.UTC)
        assertEquals(5, fixing.shiftId)
        assertEquals(LocalTime.of(15, 2), fixing.start)
        assertNull(fixing.end)
        assertTrue(fixing.stillOpen && fixing.copy(note = "Started earlier").canSave)
        assertNull(fixing.copy(note = "x").body(null, ZoneOffset.UTC).endedAt)
    }

    @Test fun savingShowsTheServersRefusalInline() = runTest {
        val client = PreviewApiClient()
        val model = MyHoursModel(client)
        model.openAdd()
        model.form = model.form!!.copy(note = "Forgot to clock in")
        client.errors[ClockEndpoints.addShift(model.form!!.body(null)).path] =
            ApiError.Validation(ValidationErrors("Those hours overlap another shift.", mapOf("started_at" to listOf("Those hours overlap another shift."))))
        assertFalse(model.save())
        assertEquals("Those hours overlap another shift.", model.fieldErrors["started_at"])
        assertNull(model.formError)
        assertTrue(model.form != null)

        // An approved period: a 422 with no field is the message itself.
        model.openFix(JsonCoding.json.decodeFromString<ClockState>(state).shift!!)
        model.form = model.form!!.copy(note = "x")
        client.errors["api/company/my-hours/shifts/5"] = ApiError.Validation(ValidationErrors("This pay period is approved. Ask a company admin to change it."))
        assertFalse(model.save())
        assertEquals("This pay period is approved. Ask a company admin to change it.", model.formError)
    }

    @Test fun savingTakesTheNewPeriodAndCloses() = runTest {
        val hours = MyHours(period = MyHours.Period("2026-11-09", "2026-11-22"))
        val client = PreviewApiClient(mapOf("api/company/my-hours/shifts" to hours))
        val model = MyHoursModel(client)
        model.openAdd()
        model.form = model.form!!.copy(note = "Forgot")
        assertTrue(model.save())
        assertNull(model.form)
        assertEquals("Day added — the office will check it", model.toast)
        assertEquals("2026-11-09", model.hours?.period?.start)
        assertEquals("POST api/company/my-hours/shifts", client.sent.single())
    }

    // The clock's pushes ("Still working?", "We clocked you out")

    @Test fun clockPushesOpenMyDay() {
        val target = com.zivett.app.core.push.PushRouting.target(mapOf("route_name" to "company.my-day", "route_params" to "[]"))
        assertEquals(com.zivett.app.core.push.PushTarget.MyDay, target)
        assertNull(com.zivett.app.core.push.PushRouting.paramKey(target!!))
        // A bell row with no params resolves the same way.
        assertEquals(com.zivett.app.core.push.PushTarget.MyDay, com.zivett.app.core.push.PushRouting.target("company.my-day") { null })
    }

    @Test fun aRefreshRequestCounts() {
        val before = com.zivett.app.features.company.MyDayRefresh.requests
        com.zivett.app.features.company.MyDayRefresh.request()
        assertEquals(before + 1, com.zivett.app.features.company.MyDayRefresh.requests)
    }
}
