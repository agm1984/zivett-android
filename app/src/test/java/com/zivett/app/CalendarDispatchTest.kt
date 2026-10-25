package com.zivett.app

import com.zivett.app.core.models.AvailabilityResponse
import com.zivett.app.core.models.CompanyCalendar
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobArea
import com.zivett.app.core.models.JobResponse
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.RescheduleRequest
import com.zivett.app.core.models.RescheduleSlots
import com.zivett.app.core.models.RosterMember
import com.zivett.app.core.models.WeeklyBlock
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.design.ZTone
import com.zivett.app.features.company.CalendarLogic
import com.zivett.app.features.company.CalendarLogic.TechFilter
import com.zivett.app.features.company.CalendarLogic.WindowState
import com.zivett.app.features.company.CompanyCalendarModel
import com.zivett.app.features.company.CompanyMoves
import com.zivett.app.features.company.MoveVisitModel
import com.zivett.app.features.customer.jobs.JobDetailModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/// The calendar as a dispatch tool (server: CalendarDispatchTest): the
/// payloads decode — PHP's `[]`-for-`{}` included — and the pure rules
/// the month grid, week board, day sheet and move picker derive.
class CalendarDispatchTest {
    private val full = """
    {
      "from": "2026-10-01", "to": "2026-10-31", "month": "2026-10",
      "jobs": [
        {"id": 7, "code": "Z-AAA111", "title": "Leaky tap", "status": "accepted", "mode": "scheduled", "date": "2026-10-27", "window": "afternoon",
         "category_code": "PL", "address": "1 Front St", "lat": 49.17, "lng": -123.94,
         "crew": [{"id": 3, "name": "Mike R.", "photo_url": "https://x/m.jpg"}, {"id": 4, "name": "Ana P.", "photo_url": null}],
         "crew_size": 2, "dispatched_at": null, "needs_crew": false, "needs_start": true, "overdue": false, "movable": true},
        {"id": 8, "code": "Z-BBB222", "title": "Burst pipe", "status": "accepted", "mode": "instant", "date": "2026-10-27", "window": null,
         "category_code": "PL", "address": "9 Bay Rd", "lat": 49.20, "lng": -123.98, "crew": [], "crew_size": 1,
         "dispatched_at": null, "needs_crew": true, "needs_start": true, "overdue": true, "movable": false}
      ],
      "tentative": [{"quote_id": 11, "job_code": "Z-CCC333", "title": "Fence", "amount_cents": 45000, "date": "2026-10-28", "window": "morning"}],
      "moves": [{"id": 5, "job_code": "Z-AAA111", "title": "Leaky tap", "date": "2026-10-29", "window": "morning", "from_date": "2026-10-27", "from_window": "afternoon"}],
      "openings": {"2026-10-28": [{"id": 21, "code": "Z-DDD444", "title": "Paint a room", "mode": "scheduled", "category_code": "PA", "windows": ["evening", "morning"], "distance_km": 3.4}]},
      "blocked_dates": [{"id": 2, "date": "2026-10-30", "window": null}, {"id": 3, "date": "2026-10-28", "window": "evening"}],
      "working_days": [1, 2, 3, 4, 5],
      "blocked_windows": [{"weekday": 5, "window": "evening"}],
      "window_capacity": 2,
      "load": {"2026-10-27|afternoon": 2, "2026-10-28|morning": 1},
      "time_off": [{"id": 9, "user_id": 4, "name": "Ana Patel", "date": "2026-10-28", "window": "morning"}],
      "team": [{"id": 3, "name": "Mike Ross", "short_name": "Mike R.", "photo_url": "https://x/m.jpg", "takes_jobs": true},
               {"id": 4, "name": "Ana Patel", "short_name": "Ana P.", "photo_url": null, "takes_jobs": true}],
      "base": {"lat": 49.16, "lng": -123.93, "label": "Ravensworth Plumbing"},
      "can_manage": true, "can_quote": true
    }
    """.trimIndent()

    private fun calendar(json: String = full) = JsonCoding.json.decodeFromString<CompanyCalendar>(json)

    // Decoding

    @Test fun theFullCalendarDecodes() {
        val calendar = calendar()
        assertEquals(2, calendar.jobs.size)
        val tap = calendar.jobs[0]
        assertEquals("Mike R.", tap.lead?.name)
        assertTrue(tap.needsStart && tap.movable)
        assertTrue(calendar.jobs[1].overdue)
        assertNull(calendar.jobs[1].window)
        assertEquals("2026-10-27", calendar.moves.single().fromDate)
        assertEquals(listOf("evening", "morning"), calendar.openings["2026-10-28"]?.single()?.windows)
        assertEquals(3.4, calendar.openings["2026-10-28"]?.single()?.distanceKm)
        assertEquals("evening", calendar.blockedDates[1].window)
        assertEquals(listOf(WeeklyBlock(5, "evening")), calendar.blockedWindows)
        assertEquals(2, calendar.windowCapacity)
        assertEquals(2, calendar.load["2026-10-27|afternoon"])
        assertEquals("Ana P.", calendar.team[1].display)
        assertEquals("Ravensworth Plumbing", calendar.base?.label)
        assertTrue(calendar.canManage && calendar.canQuote)
    }

    @Test fun phpEmptyArraysStandInForEmptyObjects() {
        val calendar = calendar("""{"month": "2026-10", "jobs": [], "openings": [], "load": [], "blocked_windows": null, "window_capacity": null, "base": null, "team": [], "time_off": [], "moves": []}""")
        assertTrue(calendar.openings.isEmpty())
        assertTrue(calendar.load.isEmpty())
        assertNull(calendar.blockedWindows)
        assertNull(calendar.base)
        assertFalse(calendar.canManage)
    }

    @Test fun anOlderServersCalendarStillDecodes() {
        val calendar = calendar("""{"month": "2026-10", "jobs": [{"id": 1, "title": "Sink", "status": "accepted", "date": "2026-10-03"}], "blocked_dates": [{"id": 1, "date": "2026-10-04"}], "working_days": null}""")
        assertTrue(calendar.jobs.single().crew.isEmpty())
        assertNull(calendar.blockedDates.single().window)
        assertTrue(calendar.openings.isEmpty())
    }

    @Test fun aBrokenOpeningRowIsDroppedNotFatal() {
        val calendar = calendar("""{"month": "2026-10", "openings": {"2026-10-05": [{"code": "no id"}, {"id": 2, "title": "Ok"}], "2026-10-06": "nonsense"}}""")
        assertEquals(listOf(2), calendar.openings["2026-10-05"]?.map { it.id })
        assertNull(calendar.openings["2026-10-06"])
    }

    @Test fun theMoveSlotsDecode() {
        val slots = JsonCoding.json.decodeFromString<RescheduleSlots>(
            """{"slots": [{"date": "2026-10-27", "window": "morning", "open": false, "reason": "full", "booked": 2, "capacity": 2, "current": false},
                          {"date": "2026-10-27", "window": "afternoon", "open": true, "reason": null, "booked": 0, "capacity": null, "current": true}],
               "reschedule_request": {"id": 5, "date": "2026-10-29", "window": "morning", "from_date": "2026-10-27", "from_window": "afternoon", "reason": "Truck in the shop", "proposed_at": "2026-10-24T17:00:00.000000Z"}}""",
        )
        assertEquals("full", slots.slots[0].reason)
        assertTrue(slots.slots[1].current)
        assertEquals("Truck in the shop", slots.rescheduleRequest?.reason)
        assertTrue(slots.rescheduleRequest?.proposedAt != null)
    }

    @Test fun aJobCarriesItsRescheduleRequest() {
        val base = """"id": 4, "title": "Sink", "status": "accepted", "category": {"id": 1, "code": "PL", "slug": "plumbing", "name": "Plumbing"}"""
        val pending = JsonCoding.json.decodeFromString<Job>("""{$base, "reschedule_request": {"id": 5, "date": "2026-10-29", "window": "morning", "from_date": "2026-10-27", "from_window": "afternoon", "reason": null, "proposed_at": null}}""")
        assertEquals("2026-10-29", pending.rescheduleRequest?.date)
        assertNull(JsonCoding.json.decodeFromString<Job>("""{$base, "reschedule_request": null}""").rescheduleRequest)
        assertNull(JsonCoding.json.decodeFromString<Job>("{$base}").rescheduleRequest)
    }

    @Test fun aRosterMemberCarriesOff() {
        assertTrue(JsonCoding.json.decodeFromString<RosterMember>("""{"id": 3, "name": "Mike", "assignable": true, "off": true}""").off)
        assertFalse(JsonCoding.json.decodeFromString<RosterMember>("""{"id": 3, "name": "Mike"}""").off)
    }

    // Labels + flags

    @Test fun loadLabelsReadAgainstTheCap() {
        assertEquals("1 of 2", CalendarLogic.loadLabel(1, 2))
        assertEquals("3 booked", CalendarLogic.loadLabel(3, null))
        assertEquals("Nothing booked", CalendarLogic.loadLabel(0, null))
        assertTrue(CalendarLogic.isFull(2, 2))
        assertFalse(CalendarLogic.isFull(5, null))
        assertEquals("M 0/2 · A 2/2 · E 0/2", CalendarLogic.dayLoadLabel(calendar(), "2026-10-27"))
        assertNull(CalendarLogic.dayLoadLabel(calendar().copy(windowCapacity = null), "2026-10-27"))
        assertTrue(CalendarLogic.dayHasFullWindow(calendar(), "2026-10-27"))
    }

    @Test fun overdueOutranksTheStaffingGaps() {
        val (tap, burst) = calendar().jobs
        assertEquals(CalendarLogic.Flag("Should have set off", ZTone.DANGER), CalendarLogic.flag(burst))
        assertEquals(CalendarLogic.Flag("Not started", ZTone.WARNING), CalendarLogic.flag(tap))
        assertEquals("No tech", CalendarLogic.flag(burst.copy(overdue = false))?.label)
        assertEquals("Needs crew", CalendarLogic.flag(tap.copy(needsCrew = true))?.label)
        assertNull(CalendarLogic.flag(tap.copy(needsStart = false)))
    }

    @Test fun instantWorkSortsFirstAsNow() {
        val jobs = CalendarLogic.jobsOn(calendar(), "2026-10-27")
        assertEquals(listOf("Z-BBB222", "Z-AAA111"), jobs.map { it.code })
        assertEquals("Now", CalendarLogic.windowTag(null))
    }

    // Filter + board

    @Test fun theTechFilterPicksCrewOrUnassigned() {
        val jobs = calendar().jobs
        assertEquals(listOf(7), CalendarLogic.filtered(jobs, TechFilter.Member(4)).map { it.id })
        assertEquals(listOf(8), CalendarLogic.filtered(jobs, TechFilter.Unassigned).map { it.id })
        assertEquals(2, CalendarLogic.filtered(jobs, TechFilter.Everyone).size)
    }

    @Test fun aCrewJobSitsOnEveryCrewMembersRow() {
        val rows = CalendarLogic.boardRows(calendar())
        assertEquals(listOf("Mike R.", "Ana P.", "Unassigned"), rows.map { it.label })
        assertEquals(listOf(7), rows[0].jobsByDate["2026-10-27"]?.map { it.id })
        assertEquals(listOf(7), rows[1].jobsByDate["2026-10-27"]?.map { it.id })
        assertEquals(listOf(8), rows[2].jobsByDate["2026-10-27"]?.map { it.id })
        assertEquals(listOf("Ana P."), CalendarLogic.boardRows(calendar(), TechFilter.Member(4)).map { it.label })
        assertEquals(listOf("Unassigned"), CalendarLogic.boardRows(calendar(), TechFilter.Unassigned).map { it.label })
    }

    @Test fun aMemberBoardHasNoEmptyUnassignedRow() {
        val member = calendar().copy(canManage = false, jobs = calendar().jobs.take(1))
        assertEquals(listOf("Mike R.", "Ana P."), CalendarLogic.boardRows(member).map { it.label })
    }

    @Test fun timeOffReadsWholeDayOrWindows() {
        val calendar = calendar()
        assertEquals("Off morning", CalendarLogic.offLabel(calendar, 4, "2026-10-28"))
        assertNull(CalendarLogic.offLabel(calendar, 3, "2026-10-28"))
        val allDay = calendar.copy(timeOff = calendar.timeOff + CompanyCalendar.TimeOff(10, 3, "Mike", "2026-10-28", null))
        assertEquals("Off", CalendarLogic.offLabel(allDay, 3, "2026-10-28"))
        assertEquals("All day", CalendarLogic.timeOffLabel(allDay.timeOff.last()))
        assertEquals("Morning off", CalendarLogic.timeOffLabel(calendar.timeOff.single()))
    }

    // Availability

    @Test fun windowStatesFollowTheCalendarRules() {
        val calendar = calendar()
        assertEquals(WindowState.Full(2, 2), CalendarLogic.windowState(calendar, "2026-10-27", "afternoon"))
        assertEquals(WindowState.Open, CalendarLogic.windowState(calendar, "2026-10-27", "morning"))
        assertEquals(WindowState.BlockedDate(3), CalendarLogic.windowState(calendar, "2026-10-28", "evening"))
        assertEquals(WindowState.BlockedDay(2), CalendarLogic.windowState(calendar, "2026-10-30", "morning"))
        // 2026-10-30 is a Friday — the whole-day block wins over the weekly one;
        // a Friday without a date block shows the weekly rule.
        assertEquals(WindowState.Weekly, CalendarLogic.windowState(calendar, "2026-10-23", "evening"))
        // Saturday isn't a working day.
        assertEquals(WindowState.DayOff, CalendarLogic.windowState(calendar, "2026-10-31", "morning"))
        assertEquals("Not a working day", CalendarLogic.dayStatus(calendar, "2026-10-31"))
        assertEquals("Blocked all day", CalendarLogic.dayStatus(calendar, "2026-10-30"))
        assertEquals("Open morning, afternoon", CalendarLogic.dayStatus(calendar, "2026-10-28"))
        assertEquals("Open for work", CalendarLogic.dayStatus(calendar, "2026-10-26"))
    }

    @Test fun openingsOnlyShowFromToday() {
        val calendar = calendar()
        assertEquals(1, CalendarLogic.openingsOn(calendar, "2026-10-28", today = "2026-10-24").size)
        assertTrue(CalendarLogic.openingsOn(calendar, "2026-10-28", today = "2026-10-29").isEmpty())
        assertEquals("1 open job", CalendarLogic.openingsLabel(1))
        assertEquals("3 open jobs", CalendarLogic.openingsLabel(3))
    }

    // Map legs

    @Test fun legsRunFromTheBaseThroughTheStopsInWindowOrder() {
        val calendar = calendar()
        val stops = CalendarLogic.stops(CalendarLogic.jobsOn(calendar, "2026-10-27"))
        assertEquals(listOf(1 to "Z-BBB222", 2 to "Z-AAA111"), stops.map { it.number to it.job.code })
        val legs = CalendarLogic.legs(calendar.base, stops)
        assertEquals(listOf("Ravensworth Plumbing", "1. Z-BBB222"), legs.map { it.from })
        assertEquals(listOf("1. Z-BBB222", "2. Z-AAA111"), legs.map { it.to })
        assertEquals(CalendarLogic.distanceKm(49.16, -123.93, 49.20, -123.98), legs[0].km, 1e-9)
        assertEquals(legs.sumOf { it.km }, CalendarLogic.totalKm(legs), 1e-9)
    }

    @Test fun withoutABaseTheRouteStartsAtStopOne() {
        val stops = CalendarLogic.stops(CalendarLogic.jobsOn(calendar(), "2026-10-27"))
        assertEquals(1, CalendarLogic.legs(null, stops).size)
        assertTrue(CalendarLogic.legs(null, stops.take(1)).isEmpty())
    }

    @Test fun unlocatedJobsAreLeftOffTheRoute() {
        val jobs = calendar().jobs.map { if (it.id == 8) it.copy(lat = null) else it }
        assertEquals(listOf(7), CalendarLogic.stops(jobs).map { it.job.id })
    }

    @Test fun haversineDistancesAndLabels() {
        // One degree of latitude is ~111.19 km on a 6,371 km sphere.
        assertEquals(111.19, CalendarLogic.distanceKm(49.0, -123.0, 50.0, -123.0), 0.01)
        // Vancouver → Nanaimo, ~61 km as the crow flies.
        assertEquals(61.0, CalendarLogic.distanceKm(49.2827, -123.1207, 49.1659, -123.9401), 1.0)
        assertEquals(0.0, CalendarLogic.distanceKm(49.0, -123.0, 49.0, -123.0), 1e-9)
        assertEquals("650 m", CalendarLogic.distanceLabel(0.6512))
        assertEquals("4.2 km", CalendarLogic.distanceLabel(4.2049))
    }

    // Settings

    @Test fun allSevenWorkingDaysSaveAsNull() {
        assertNull(CalendarLogic.workingDaysBody((1..7).toSet()))
        assertEquals(listOf(1, 2, 5), CalendarLogic.workingDaysBody(setOf(5, 1, 2)))
    }

    @Test fun weeklyBlocksToggleAndStaySorted() {
        val blocks = CalendarLogic.toggleWeekly(listOf(WeeklyBlock(5, "evening")), 1, "afternoon")
        assertEquals(listOf(WeeklyBlock(1, "afternoon"), WeeklyBlock(5, "evening")), blocks)
        assertEquals(listOf(WeeklyBlock(1, "afternoon")), CalendarLogic.toggleWeekly(blocks, 5, "evening"))
    }

    @Test fun aBlankCapMeansNoLimit() {
        assertEquals(CalendarLogic.Capacity.Valid(null), CalendarLogic.parseCapacity("  "))
        assertEquals(CalendarLogic.Capacity.Valid(3), CalendarLogic.parseCapacity("3"))
        assertEquals(CalendarLogic.Capacity.Invalid, CalendarLogic.parseCapacity("0"))
        assertEquals(CalendarLogic.Capacity.Invalid, CalendarLogic.parseCapacity("51"))
    }

    // Weeks

    @Test fun weeksRunMondayToSunday() {
        val start = CalendarLogic.weekStart(LocalDate.parse("2026-10-24"))
        assertEquals(LocalDate.parse("2026-10-19"), start)
        assertEquals(7, CalendarLogic.weekDays(start).size)
        assertEquals("Oct 19 – 25", CalendarLogic.weekLabel(start))
        assertEquals("Oct 26 – Nov 1", CalendarLogic.weekLabel(LocalDate.parse("2026-10-26")))
    }

    @Test fun switchingToTheWeekViewAsksForThatWeek() = runTest {
        val client = PreviewApiClient(mapOf("api/company/calendar" to calendar()))
        val model = CompanyCalendarModel(client, today = LocalDate.parse("2026-10-24"))
        model.load()
        model.switchTo(CompanyCalendarModel.Mode.WEEK, today = LocalDate.parse("2026-10-24"))
        assertEquals(LocalDate.parse("2026-10-19"), model.weekStart)
        model.shift(1)
        assertEquals(LocalDate.parse("2026-10-26"), model.weekStart)
        model.switchTo(CompanyCalendarModel.Mode.MONTH)
        assertEquals("2026-10", CalendarLogic.monthKey(model.month))
        assertEquals(4, client.sent.count { it == "GET api/company/calendar" })
    }

    @Test fun reopeningADayRemovesEveryBlockOnIt() = runTest {
        val withTwo = calendar().copy(blockedDates = listOf(AvailabilityResponse.BlockedDate(3, "2026-10-28", "evening"), AvailabilityResponse.BlockedDate(4, "2026-10-28", "morning")))
        val client = PreviewApiClient(mapOf("api/company/calendar" to withTwo))
        val model = CompanyCalendarModel(client, today = LocalDate.parse("2026-10-24"))
        model.load()
        model.reopenDay("2026-10-28")
        assertTrue("DELETE api/company/availability/blocked-dates/3" in client.sent)
        assertTrue("DELETE api/company/availability/blocked-dates/4" in client.sent)
    }

    // Moves

    @Test fun theCurrentAndClosedSlotsCantBePicked() {
        val open = RescheduleSlots.Slot("2026-10-27", "morning", open = true)
        assertTrue(CalendarLogic.pickable(open))
        assertFalse(CalendarLogic.pickable(open.copy(current = true)))
        assertFalse(CalendarLogic.pickable(open.copy(open = false, reason = "day_off")))
        assertEquals("Day off", CalendarLogic.slotReason("day_off"))
        assertEquals("Full", CalendarLogic.slotReason("full"))
        assertEquals("1 of 2", CalendarLogic.slotLoad(open.copy(booked = 1, capacity = 2)))
        assertEquals("1 booked", CalendarLogic.slotLoad(open.copy(booked = 1)))
        assertNull(CalendarLogic.slotLoad(open))
    }

    @Test fun slotsGroupByDateInWindowOrder() {
        val slots = listOf(
            RescheduleSlots.Slot("2026-10-28", "evening"), RescheduleSlots.Slot("2026-10-27", "afternoon"),
            RescheduleSlots.Slot("2026-10-27", "morning"), RescheduleSlots.Slot("2026-10-28", "morning"),
        )
        val grouped = CalendarLogic.slotsByDate(slots)
        assertEquals(listOf("2026-10-27", "2026-10-28"), grouped.map { it.first })
        assertEquals(listOf("morning", "afternoon"), grouped[0].second.map { it.window })
    }

    @Test fun sendingAMoveRecordsThePendingRequest() = runTest {
        val pending = RescheduleRequest(5, "2026-10-29", "morning", "2026-10-27", "afternoon")
        val client = PreviewApiClient(mapOf(
            "api/company/jobs/7/reschedule/slots" to RescheduleSlots(listOf(RescheduleSlots.Slot("2026-10-29", "morning", open = true))),
            "api/company/jobs/7/reschedule" to JobResponse(Fixtures.job(id = 7, status = JobStatus.ACCEPTED).copy(rescheduleRequest = pending)),
        ))
        val model = MoveVisitModel(7, client)
        model.load()
        assertFalse(model.canSend)
        model.picked = model.state.value!!.slots.single()
        assertTrue(model.canSend)
        val job = model.send()
        assertEquals(pending, job?.rescheduleRequest)
        assertEquals(pending, model.pending)
        assertTrue("POST api/company/jobs/7/reschedule" in client.sent)
    }

    @Test fun onlyAcceptedNonInstantJobsMove() {
        assertTrue(CompanyMoves.movable(Fixtures.job(status = JobStatus.ACCEPTED).copy(mode = "scheduled")))
        assertFalse(CompanyMoves.movable(Fixtures.job(status = JobStatus.ACCEPTED).copy(mode = "instant")))
        assertFalse(CompanyMoves.movable(Fixtures.job(status = JobStatus.EN_ROUTE).copy(mode = "scheduled")))
    }

    // The booker's answer

    @Test fun acceptingAMoveSwapsInTheMovedJob() = runTest {
        val pending = RescheduleRequest(5, "2026-10-29", "morning")
        val client = PreviewApiClient(mapOf(
            "api/customer/jobs/4" to JobResponse(Fixtures.job(id = 4, status = JobStatus.ACCEPTED).copy(rescheduleRequest = pending)),
            "api/customer/jobs/4/reschedule/accept" to JobResponse(Fixtures.job(id = 4, status = JobStatus.ACCEPTED).copy(scheduledDate = "2026-10-29", scheduledWindow = "morning")),
        ))
        val model = JobDetailModel(4, client, JobArea.customer)
        model.load()
        model.decideReschedule(accept = true)
        assertNull(model.job?.rescheduleRequest)
        assertEquals("2026-10-29", model.job?.scheduledDate)
        assertTrue(model.toast!!.startsWith("Visit moved to"))
    }

    @Test fun aScheduleConflictSaysWhyAndReloads() = runTest {
        val pending = RescheduleRequest(5, "2026-10-29", "morning")
        val client = PreviewApiClient(mapOf("api/business/requests/4" to JobResponse(Fixtures.job(id = 4, status = JobStatus.ACCEPTED).copy(rescheduleRequest = pending))))
        val message = "That time is no longer open on Ravensworth's calendar. Your visit stays as agreed."
        client.errors["api/business/requests/4/reschedule/accept"] = ApiError.Conflict(message, """{"message":"$message","code":"schedule_conflict"}""".toByteArray())
        val model = JobDetailModel(4, client, JobArea.business)
        model.load()
        model.decideReschedule(accept = true)
        assertEquals(message, model.toast)
        assertEquals(2, client.sent.count { it == "GET api/business/requests/4" })
        assertFalse(model.busy)
    }

    @Test fun decliningKeepsTheOriginalTime() = runTest {
        val client = PreviewApiClient(mapOf(
            "api/customer/jobs/4" to JobResponse(Fixtures.job(id = 4, status = JobStatus.ACCEPTED).copy(rescheduleRequest = RescheduleRequest(5, "2026-10-29", "morning"))),
            "api/customer/jobs/4/reschedule/decline" to JobResponse(Fixtures.job(id = 4, status = JobStatus.ACCEPTED)),
        ))
        val model = JobDetailModel(4, client, JobArea.customer)
        model.load()
        model.decideReschedule(accept = false)
        assertNull(model.job?.rescheduleRequest)
        assertEquals("Kept your original time", model.toast)
    }
}
