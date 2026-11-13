package com.zivett.app

import com.zivett.app.core.models.CompanyCalendar
import com.zivett.app.core.models.DayJob
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.MyDay
import com.zivett.app.core.models.OwnJobResponse
import com.zivett.app.core.models.RosterMember
import com.zivett.app.core.models.User
import com.zivett.app.core.auth.Access
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.MyDayEndpoints
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.features.company.MyDayLogic
import com.zivett.app.features.company.MyDayModel
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// My day, own jobs and the dispatch additions (server: MyDayTest,
/// TechScheduleTest, ArrivalTimeTest): the payloads decode, older servers
/// included.
class MyDayTest {
    private val day = """
    {"date": "2026-11-12", "is_today": true,
     "jobs": [
       {"kind": "job", "id": 7, "code": "Z-AAA111", "title": "Leaky tap", "status": "accepted", "trade": "Plumbing",
        "date": "2026-11-12", "window": "morning", "time": "09:30", "span": [570, 690], "address": "1 Front St, Nanaimo BC V9R 1A1",
        "unit": "Suite 4", "access_notes": "Gate code 1234", "lat": 49.17, "lng": -123.94,
        "contact": {"name": "Amara Okafor", "phone": "(250) 555-0101"},
        "crew": [{"id": 3, "name": "Mike R.", "photo_url": null}], "lead": true,
        "next": {"action": "advance", "status": "en_route", "label": "On my way"}, "clock": null},
       {"kind": "own", "id": 12, "code": "J-1042", "title": "Gutter clean", "status": "scheduled", "trade": null,
        "date": "2026-11-12", "window": "afternoon", "time": null, "span": [720, 1020], "address": "9 Bay Rd",
        "unit": null, "access_notes": null, "lat": null, "lng": null,
        "contact": {"name": "Ben Lee", "phone": null}, "crew": [], "lead": false,
        "next": {"action": "own_status", "status": "in_progress", "label": "Start job"}, "clock": {"running": false}}
     ],
     "clock": {"job_id": 12, "code": "J-1042", "title": "Gutter clean", "started_at": "2026-11-12T17:12:00.000000Z"},
     "can_clock": true}
    """.trimIndent()

    @Test fun myDayDecodes() {
        val decoded = JsonCoding.json.decodeFromString<MyDay>(day)
        assertTrue(decoded.isToday && decoded.canClock)
        val (zivett, own) = decoded.jobs
        assertFalse(zivett.isOwn)
        assertEquals(listOf(570, 690), zivett.span)
        assertEquals("Gate code 1234", zivett.accessNotes)
        assertEquals("(250) 555-0101", zivett.contact?.phone)
        assertEquals("advance", zivett.next?.action)
        assertEquals("en_route", zivett.next?.status)
        assertTrue(own.isOwn)
        assertEquals(false, own.clock?.running)
        assertEquals(12, decoded.clock?.jobId)
        assertTrue(decoded.clock?.startedAt != null)
    }

    @Test fun aSparseDayStillDecodes() {
        val decoded = JsonCoding.json.decodeFromString<MyDay>("""{"date": "2026-11-13", "jobs": [{"id": 1, "next": null}], "clock": null}""")
        assertFalse(decoded.isToday)
        assertEquals(DayJob.KIND_JOB, decoded.jobs.single().kind)
        assertNull(decoded.jobs.single().next)
        assertFalse(decoded.canClock)
    }

    @Test fun theOwnJobCrewViewDecodes() {
        val job = JsonCoding.json.decodeFromString<OwnJobResponse>(
            """{"job": {"id": 12, "code": "J-1042", "title": "Gutter clean", "status": "in_progress",
               "customer": {"id": null, "name": "Ben Lee", "phone": "250-555-0102", "email": null},
               "address": "9 Bay Rd", "address_unit": "Back door", "lat": 49.2, "lng": -123.9,
               "scheduled_date": "2026-11-12", "scheduled_window": "afternoon", "start_time": "13:00", "estimated_hours": 2.5,
               "price_cents": null, "category": null, "crew": [{"id": 3, "name": "Mike Ross", "photo_url": null}],
               "crew_view": true, "description": "Front and back", "work_notes": null, "actual_hours": 0.5,
               "time": [{"id": 1, "user": {"id": 3, "name": "Mike Ross", "photo_url": null}, "worked_on": "2026-11-12", "minutes": 34,
                         "running": true, "started_at": "2026-11-12T20:00:00.000000Z", "ended_at": null, "note": null, "removable": true}],
               "photos": [], "money": null, "series": null, "quote": null, "invoices": [], "next": ["completed"]}}""",
        ).job
        assertEquals(2.5, job.estimatedHours)
        assertEquals("Back door", job.addressUnit)
        assertTrue(job.clockRunning(3))
        assertFalse(job.clockRunning(4))
        assertTrue(job.onCrew(3))
        assertEquals(listOf("completed"), job.next)
    }

    @Test fun theCalendarCarriesOwnJobsAndBusyRows() {
        val calendar = JsonCoding.json.decodeFromString<CompanyCalendar>(
            """{"month": "2026-11",
               "jobs": [{"id": 7, "title": "Leaky tap", "status": "accepted", "date": "2026-11-12", "window": "morning",
                         "arrival_time": "09:30", "hours": 2, "span": [570, 690]}],
               "own_jobs": [
                 {"id": 12, "busy": false, "code": "J-1042", "title": "Gutter clean", "status": "scheduled", "customer_name": "Ben Lee",
                  "address": "9 Bay Rd", "lat": 49.2, "lng": -123.9, "crew": [{"id": 3, "name": "Mike Ross", "photo_url": null}],
                  "mine": true, "date": "2026-11-12", "window": "afternoon", "start_time": "13:00", "estimated_hours": 2, "span": [780, 900]},
                 {"id": "busy-13", "busy": true, "date": "2026-11-12", "window": "evening", "start_time": null, "estimated_hours": null, "span": [1020, 1260]},
                 {"busy": false, "title": "no date or id"}
               ],
               "can_open_own_jobs": true}""",
        )
        assertEquals("09:30", calendar.jobs.single().arrivalTime)
        assertEquals(2, calendar.ownJobs.size)
        assertTrue(calendar.canOpenOwnJobs)
        val entries = calendar.entries
        assertEquals(3, entries.size)
        val own = entries[1]
        assertTrue(own.isOwn && !own.busy)
        assertEquals(12, own.id)
        assertEquals("13:00", own.clockTime)
        assertNull(own.status)
        val busy = entries[2]
        assertTrue(busy.busy)
        assertTrue(busy.id < 0)
        assertEquals("Busy", busy.title)
    }

    @Test fun anOlderCalendarHasNoOwnJobs() {
        val calendar = JsonCoding.json.decodeFromString<CompanyCalendar>("""{"month": "2026-11", "jobs": []}""")
        assertTrue(calendar.ownJobs.isEmpty())
        assertFalse(calendar.canOpenOwnJobs)
    }

    @Test fun rosterMembersCarryTheirClashes() {
        val member = JsonCoding.json.decodeFromString<RosterMember>(
            """{"id": 3, "name": "Mike", "assignable": true, "auto_pickable": false,
               "busy": [{"kind": "own", "code": "J-1042", "title": "Gutter clean", "start": "09:00", "end": "11:00"}]}""",
        )
        assertEquals("J-1042", member.busy.single().code)
        assertFalse(member.isAutoPickable)
        assertTrue(JsonCoding.json.decodeFromString<RosterMember>("""{"id": 3, "name": "Mike"}""").busy.isEmpty())
    }

    @Test fun usersAndJobsCarryTheNewFields() {
        val user = JsonCoding.json.decodeFromString<User>(
            """{"id": 1, "first_name": "A", "last_name": "B", "name": "A B", "email": "a@b.c", "role": "company", "takes_jobs": false}""",
        )
        assertFalse(user.takesJobs)
        assertTrue(JsonCoding.json.decodeFromString<User>("""{"id": 1, "first_name": "A", "last_name": "B", "name": "A B", "email": "a@b.c", "role": "company"}""").takesJobs)
        val base = """"id": 4, "title": "Sink", "status": "accepted", "category": {"id": 1, "code": "PL", "slug": "plumbing", "name": "Plumbing"}"""
        assertEquals("09:30", JsonCoding.json.decodeFromString<Job>("""{$base, "arrival_time": "09:30"}""").arrivalTime)
        assertNull(JsonCoding.json.decodeFromString<Job>("{$base}").arrivalTime)
    }

    // My day's rules

    private fun job(json: String) = JsonCoding.json.decodeFromString<DayJob>(json)

    @Test fun clocksReadLikeTheWeb() {
        assertEquals("9:30 am", MyDayLogic.formatClock("09:30"))
        assertEquals("12:00 pm", MyDayLogic.formatClock("12:00"))
        assertEquals("12:15 am", MyDayLogic.formatClock("00:15"))
        assertEquals("", MyDayLogic.formatClock(null))
        assertEquals("09:30", MyDayLogic.clock(570))
    }

    @Test fun theWhenLineFallsBackFromSpanToWindowToAnyTime() {
        assertEquals("9:30 am – 11:30 am", MyDayLogic.timeLine(job("""{"id": 1, "time": "09:30", "span": [570, 690], "window": "morning"}""")))
        assertEquals("Around 9:30 am", MyDayLogic.timeLine(job("""{"id": 1, "time": "09:30"}""")))
        assertEquals("Morning (8am–12pm)", MyDayLogic.timeLine(job("""{"id": 1, "span": [480, 720], "window": "morning"}""")))
        assertEquals("Any time today", MyDayLogic.timeLine(job("""{"id": 1}""")))
        assertEquals("Any time", MyDayLogic.timeLine(job("""{"id": 1}"""), isToday = false))
    }

    @Test fun cardLinesNameTheRestOfTheCrewAndTheWayThere() {
        val tap = JsonCoding.json.decodeFromString<MyDay>(day).jobs[0]
        assertEquals("Z-AAA111 · Plumbing", MyDayLogic.codeLine(tap))
        assertEquals("Suite 4, 1 Front St, Nanaimo BC V9R 1A1", MyDayLogic.addressLine(tap))
        assertNull(MyDayLogic.crewLine(tap, 3))
        assertEquals("With Mike R.", MyDayLogic.crewLine(tap, 9))
        assertEquals("With Mike R. and Ana P.", MyDayLogic.crewLine(job("""{"id": 1, "crew": [{"id": 3, "name": "Mike R."}, {"id": 4, "name": "Ana P."}, {"id": 5, "name": "Me"}]}"""), 5))
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=49.17,-123.94", MyDayLogic.directionsUrl(49.17, -123.94, "x"))
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=9+Bay+Rd", MyDayLogic.directionsUrl(null, null, "9 Bay Rd"))
        assertNull(MyDayLogic.directionsUrl(null, null, " "))
        assertEquals("Accepted", MyDayLogic.status(tap).label)
        assertEquals("Scheduled", MyDayLogic.status(JsonCoding.json.decodeFromString<MyDay>(day).jobs[1]).label)
    }

    @Test fun theClockStripCountsUp() {
        val clock = JsonCoding.json.decodeFromString<MyDay>(day).clock!!
        assertEquals("Clocked in on J-1042 · Gutter clean", MyDayLogic.clockTitle(clock))
        val now = Instant.parse("2026-11-12T18:17:00Z")
        assertEquals("since 5:12 pm · 1 h 05 min", MyDayLogic.clockSince(clock, now, ZoneOffset.UTC))
        assertEquals("12 min", MyDayLogic.elapsed(Instant.parse("2026-11-12T18:05:00Z"), now))
    }

    @Test fun clockButtonsRideOpenOwnJobsWithBusinessTools() {
        val decoded = JsonCoding.json.decodeFromString<MyDay>(day)
        assertFalse(MyDayLogic.showsClock(decoded.jobs[0], decoded))
        assertTrue(MyDayLogic.showsClock(decoded.jobs[1], decoded))
        assertFalse(MyDayLogic.showsClock(decoded.jobs[1], decoded.copy(canClock = false)))
    }

    @Test fun onMyWayOpensTheJobPageEverythingElsePostsHere() {
        assertTrue(MyDayLogic.opensJobToAdvance(DayJob.Next("advance", "en_route", "On my way")))
        assertFalse(MyDayLogic.opensJobToAdvance(DayJob.Next("advance", "arrived", "I've arrived")))
        assertFalse(MyDayLogic.opensJobToAdvance(DayJob.Next("own_status", "in_progress", "Start job")))
    }

    @Test fun dayTitlesAreRelativeNearToday() {
        val today = LocalDate.of(2026, 11, 12)
        assertEquals("Today", MyDayLogic.dayTitle(today, today))
        assertEquals("Tomorrow", MyDayLogic.dayTitle(today.plusDays(1), today))
        assertEquals("Yesterday", MyDayLogic.dayTitle(today.minusDays(1), today))
        assertEquals("Monday, November 16", MyDayLogic.dayTitle(today.plusDays(4), today))
    }

    @Test fun fieldTechsOpenOnMyDay() {
        fun user(role: String, takes: Boolean, approved: Boolean = true) = JsonCoding.json.decodeFromString<User>(
            """{"id": 1, "first_name": "A", "last_name": "B", "name": "A B", "email": "a@b.c", "role": "company", "organization_role": "$role",
               "takes_jobs": $takes, "organization": {"id": 1, "type": "company", "name": "X"${if (approved) ", \"approved_at\": \"2026-10-01T00:00:00Z\"" else ""}}}""",
        )
        assertTrue(Access.opensOnMyDay(user("member", true)))
        assertFalse(Access.opensOnMyDay(user("member", false)))
        assertFalse(Access.opensOnMyDay(user("admin", true)))
        assertFalse(Access.opensOnMyDay(user("member", true, approved = false)))
    }

    @Test fun theModelStepsDaysAndReloadsAfterEveryAction() = runTest {
        val today = JsonCoding.json.decodeFromString<MyDay>(day)
        val client = PreviewApiClient(mapOf(MyDayEndpoints.day().path to today))
        val model = MyDayModel(client)
        model.load()
        assertEquals(2, model.day?.jobs?.size)
        model.shift(1)
        assertEquals(LocalDate.of(2026, 11, 13), model.date)
        model.goToday()
        assertNull(model.date)
        client.sent.clear()
        model.advance(today.jobs[0])
        assertEquals(listOf("POST ${CompanyEndpoints.advance(7).path}", "GET ${MyDayEndpoints.day().path}"), client.sent)
        client.sent.clear()
        client.responses[MyDayEndpoints.clockIn(12).path] = OwnJobResponse(com.zivett.app.core.models.OwnJob(id = 12))
        model.clockIn(today.jobs[1])
        assertEquals("POST api/company/own-jobs/12/clock-in", client.sent.first())
        assertEquals("Clocked in on J-1042", model.toast)
    }

    // The own job page

    @Test fun ownJobNotificationsOpenTheOwnJobPage() {
        val target = com.zivett.app.core.push.PushRouting.target(mapOf("route_name" to "company.own-jobs.show", "route_params" to """{"id":12}"""))
        assertEquals(com.zivett.app.core.push.PushTarget.OwnJob(12), target)
        assertEquals("12", com.zivett.app.core.push.PushRouting.paramKey(target!!))
        // The intent round trip MainActivity does with the single param.
        assertEquals(target, com.zivett.app.core.push.PushRouting.target("company.own-jobs.show") { key -> "12".takeIf { key == "id" } })
        assertNull(com.zivett.app.core.push.PushRouting.target(mapOf("route_name" to "company.own-jobs.show")))
    }

    @Test fun theOwnJobPageOffersStartFinishAndTheClock() {
        val base = com.zivett.app.core.models.OwnJob(id = 12, crew = listOf(CompanyCalendar.CrewTech(3, "Mike Ross")), scheduledDate = "2026-11-12", scheduledWindow = "afternoon", startTime = "13:00", estimatedHours = 2.5)
        val scheduled = base.copy(next = listOf("in_progress"))
        assertEquals("in_progress" to "Start job", com.zivett.app.features.company.OwnJobLogic.nextStatus(scheduled))
        assertEquals("completed" to "Finish job", com.zivett.app.features.company.OwnJobLogic.nextStatus(base.copy(status = "in_progress", next = listOf("completed"))))
        assertNull(com.zivett.app.features.company.OwnJobLogic.nextStatus(base.copy(status = "completed")))
        assertTrue(com.zivett.app.features.company.OwnJobLogic.showsClock(scheduled, 3))
        assertFalse(com.zivett.app.features.company.OwnJobLogic.showsClock(scheduled, 4))
        assertFalse(com.zivett.app.features.company.OwnJobLogic.showsClock(base.copy(status = "completed"), 3))
        assertEquals("Thu, Nov 12 · Afternoon · starts 1:00 pm · about 2.5 h", com.zivett.app.features.company.OwnJobLogic.whenLine(base))
        assertEquals("1 h 05 min", com.zivett.app.features.company.OwnJobLogic.minutes(65))
        assertEquals("2 h", com.zivett.app.features.company.OwnJobLogic.hours(2.0))
    }

    @Test fun theOwnJobModelTakesTheServersCopyAfterEachAction() = runTest {
        val started = com.zivett.app.core.models.OwnJob(id = 12, status = "in_progress", next = listOf("completed"))
        val client = PreviewApiClient(mapOf(MyDayEndpoints.ownJobStatus(12, "in_progress").path to OwnJobResponse(started)))
        val model = com.zivett.app.features.company.OwnJobModel(12, client)
        model.setStatus("in_progress")
        assertEquals("in_progress", model.job?.status)
        assertEquals("Job started — you're clocked in", model.toast)
        assertEquals(listOf("POST api/company/own-jobs/12/status"), client.sent)
    }
}
