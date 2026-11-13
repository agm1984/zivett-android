package com.zivett.app

import com.zivett.app.core.models.CompanyCalendar
import com.zivett.app.core.models.DayJob
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.MyDay
import com.zivett.app.core.models.OwnJobResponse
import com.zivett.app.core.models.RosterMember
import com.zivett.app.core.models.User
import com.zivett.app.core.network.JsonCoding
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
}
