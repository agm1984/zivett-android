package com.zivett.app

import com.zivett.app.core.AddressParts
import com.zivett.app.core.Money
import com.zivett.app.core.PhoneFormatter
import com.zivett.app.core.models.AppNotification
import com.zivett.app.core.models.AssignmentRoster
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.models.autoPick
import com.zivett.app.core.models.canAutoPick
import com.zivett.app.core.models.togglingCrew
import com.zivett.app.core.models.tooFewAssignable
import com.zivett.app.core.models.MemberBody
import com.zivett.app.core.models.Team
import com.zivett.app.core.models.TeamArea
import com.zivett.app.core.models.UserRole
import com.zivett.app.core.models.nextUp
import com.zivett.app.core.models.noFieldTechs
import com.zivett.app.core.models.nobodyAssignable
import com.zivett.app.core.models.BusinessSetup
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.QuoteBody
import com.zivett.app.core.models.User
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.HttpApiClient
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.push.PushRouting
import com.zivett.app.core.realtime.PusherProtocol
import com.zivett.app.core.realtime.RealtimeBackoff
import com.zivett.app.core.realtime.RealtimeEvents
import com.zivett.app.features.business.BusinessSetupSteps
import com.zivett.app.features.company.CompanyPresentation
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.customer.book.BookingDraftPayload
import com.zivett.app.features.customer.book.BookingDraftResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AddressPartsTest {
    @Test fun parsesAFullyQualifiedAddress() {
        val parts = AddressParts.parse("14 Alder Court, Nanaimo, BC V9R 2K1")
        assertEquals("14 Alder Court", parts.line1); assertEquals("Nanaimo", parts.city); assertEquals("BC", parts.region); assertEquals("V9R 2K1", parts.postal)
        assertEquals("14 Alder Court, Nanaimo, BC V9R 2K1", parts.composed)
    }

    @Test fun composeAndParseRoundTrip() {
        val parts = AddressParts("210 Granary Row, Building B", "Winnipeg", "MB", "R3B 0T1")
        assertEquals(parts, AddressParts.parse(parts.composed))
    }

    @Test fun freeTextLandsInTheStreetField() {
        val parts = AddressParts.parse("the old mill by the river")
        assertEquals("the old mill by the river", parts.line1); assertTrue(parts.city.isEmpty()); assertTrue(parts.region.isEmpty())
    }

    @Test fun aMissingRegionStillSplitsStreetAndCity() {
        val parts = AddressParts.parse("14 Alder Court, Nanaimo")
        assertEquals("14 Alder Court", parts.line1); assertEquals("Nanaimo", parts.city); assertTrue(parts.region.isEmpty())
    }

    @Test fun phoneMaskAndMoney() {
        assertEquals("(555) 012-3456", PhoneFormatter.mask("5550123456789"))
        assertEquals("(555", PhoneFormatter.mask("555"))
        assertTrue(PhoneFormatter.isComplete("(555) 012-3456"))
        assertFalse(PhoneFormatter.isComplete("555"))
        assertEquals("$1,234.50", Money.format(123_450))
        assertEquals("$0.00", Money.format(0))
    }

    /// The booker-side fee was renamed; the invoice screen and PDF kept
    /// printing "Booking & support fee". A label from the server wins.
    @Test fun theBookerFeeIsNamedOnce() {
        assertEquals("Trust & support fee", Fixtures.invoice().feeLabel)
        assertEquals("Booking & support fee", Fixtures.invoice().copy(customerFeeLabel = "Booking & support fee").feeLabel)
        assertEquals("Trust & support fee", Fixtures.invoice().copy(customerFeeLabel = " ").feeLabel)
    }

    /// The decimal keyboard follows the phone's locale: "5,50" on a
    /// fr-CA device used to parse as nothing — a $0 tip.
    @Test fun typedAmountsAcceptEitherDecimalSeparator() {
        assertEquals(550, Money.parseCents("5,50"))
        assertEquals(550, Money.parseCents("5.50"))
        assertEquals(550, Money.parseCents("5,5"))
        assertEquals(50, Money.parseCents(",5"))
        assertEquals(2000, Money.parseCents("$20"))
        assertEquals(100_000, Money.parseCents("1,000"))
        assertEquals(123_450, Money.parseCents("1,234.50"))
        assertEquals(123_450, Money.parseCents("1 234,50"))
        assertEquals(123_450, Money.parseCents("1.234,50"))
        assertEquals(556, Money.parseCents("5.555"))
        assertEquals(null, Money.parseCents(""))
        assertEquals(null, Money.parseCents("abc"))
        assertEquals(null, Money.parseCents("1,2,3"))

        assertEquals(550, Money.tipCents("5,50"))
        assertEquals(0, Money.tipCents(""))
        assertEquals(0, Money.tipCents("-5"))
        assertEquals(100_000, Money.tipCents("5000"))
    }
}

class DecodingTest {
    @Test fun decodesTheUserWithLaravelDates() {
        val json = """{"id":2,"first_name":"Amara","last_name":"Okafor","name":"Amara Okafor","email":"a@b.c","role":"company","organization_role":"admin","organization":{"id":1,"type":"company","name":"Ravensworth","approved_at":"2026-08-22T14:03:11.000000Z","plan_badge":"pro"},"email_verified_at":"2026-08-22","phone":null,"extra_key":true}"""
        val user = JsonCoding.json.decodeFromString<User>(json)
        assertEquals("Amara", user.firstName)
        assertTrue(user.organization!!.isApproved)
        assertTrue(user.isEmailVerified)
        assertEquals("AO", user.initials)
        assertEquals(Instant.parse("2026-08-22T00:00:00Z"), user.emailVerifiedAt)
    }

    @Test fun decodesAJobWithUnknownStatusTolerantFields() {
        val json = """{"id":9,"code":"Z-93U4H3","title":"Leak","status":"en_route","category":{"id":1,"name":"Plumbing"},"mode":"quote","quotes":[{"id":1,"amount_cents":18000,"status":"pending","company":{"id":4,"name":"Pro A","rating":4.8,"count":12}}],"change_orders":[{"id":1,"amount_cents":500,"status":"proposed"}]}"""
        val job = JsonCoding.json.decodeFromString<Job>(json)
        assertEquals(JobStatus.EN_ROUTE, job.status)
        assertEquals(1, job.pendingQuotes.size)
        assertTrue(job.changeOrders!!.first().isProposed)
        assertEquals("Z-93U4H3 · null", listOf(job.code, job.address).joinToString(" · ")) // address absent → null, never "null" in UI
    }

    @Test fun notificationRouteParamsAcceptCodesAndLegacyIds() {
        val code = JsonCoding.json.decodeFromString<AppNotification>("""{"id":"u1","title":"t","route_name":"customer.jobs.show","route_params":{"id":"Z-93U4H3"},"read":false}""")
        assertEquals("Z-93U4H3", code.jobRef)
        val legacy = JsonCoding.json.decodeFromString<AppNotification>("""{"id":"u2","title":"t","route_name":"company.jobs.show","route_params":{"id":42},"read":true}""")
        assertEquals("42", legacy.jobRef)
        assertEquals(42, legacy.routeParams!!["id"]!!.intValue)
        val other = JsonCoding.json.decodeFromString<AppNotification>("""{"id":"u3","title":"t","route_name":"company.dashboard","route_params":{},"read":true}""")
        assertNull(other.jobRef)
    }

    @Test fun quoteBodyWritesExplicitNullsSoARevisionCanClearTheSlot() {
        val text = JsonCoding.explicitNulls.encodeToString(QuoteBody.serializer(), QuoteBody(2.0, 1, null, null, null))
        assertTrue(text.contains("\"proposed_date\":null"))
        assertTrue(text.contains("\"message\":null"))
        // The default coder omits them (Laravel's validated() would keep the old value).
        assertFalse(JsonCoding.json.encodeToString(QuoteBody.serializer(), QuoteBody(2.0, 1)).contains("proposed_date"))
    }

    @Test fun bookingDraftSnapshotUsesTheWebsSpelling() {
        val payload = BookingDraftPayload(
            form = BookingDraftPayload.Form(mode = "quote", serviceCategoryId = 1, issue = "Leak", accessNotes = "Gate"),
            intakeAnswers = mapOf("7" to BookingDraftPayload.Answer.one("Faucet"), "8" to BookingDraftPayload.Answer.many(listOf("A", "B"))),
            selectedWindows = listOf("2026-08-23|morning"), addressChoice = BookingDraftPayload.AddressChoice.place(3), stepIndex = 4,
        )
        val text = JsonCoding.plain.encodeToString(BookingDraftPayload.serializer(), payload)
        assertTrue(text.contains("\"intakeAnswers\":{\"7\":\"Faucet\",\"8\":[\"A\",\"B\"]}"))
        assertTrue(text.contains("\"service_category_id\":1"))
        assertTrue(text.contains("\"access_notes\":\"Gate\""))
        assertTrue(text.contains("\"addressChoice\":3"))
        assertTrue(text.contains("\"stepIndex\":4"))

        // Decoding tolerates the empty-array payload a photo-first draft carries.
        val response = JsonCoding.json.decodeFromString<BookingDraftResponse>("""{"draft":{"payload":[],"category":null,"updated_at":"2026-08-22T14:03:11.000000Z","photos":[{"id":1,"url":"https://x/1.jpg","thumb_url":null}]}}""")
        assertNull(response.draft!!.payload)
        assertEquals(1, response.draft!!.photos.size)
        val resumed = JsonCoding.json.decodeFromString<BookingDraftResponse>("""{"draft":{"payload":{"form":{"mode":"quote","service_category_id":1,"issue":"x"},"intakeAnswers":{"7":"Faucet"},"addressChoice":"new","stepIndex":2},"photos":[]}}""")
        assertEquals(1, resumed.draft!!.payload!!.form.serviceCategoryId)
        assertTrue(resumed.draft!!.payload!!.addressChoice!!.isNew)
        assertEquals(listOf("Faucet"), resumed.draft!!.payload!!.intakeAnswers!!["7"]!!.values)
    }

    /// A declined charge is a 422 WITHOUT an `errors` key, carrying a
    /// `code`. The code used to be dropped on decode, so no screen could
    /// tell a decline from a form problem.
    @Test fun aDeclinedChargeKeepsItsCode() {
        val declined = HttpApiClient.errorFor(422, null, """{"message":"Your card was declined.","code":"payment_declined"}""".toByteArray())
        assertEquals("Your card was declined.", declined.paymentDeclinedMessage)
        assertEquals("Your card was declined.", declined.userMessage)

        val form = HttpApiClient.errorFor(422, null, """{"message":"x","errors":{"email":["Taken."]}}""".toByteArray())
        assertEquals(null, form.paymentDeclinedMessage)
        assertEquals("Taken.", form.first("email"))
    }

    @Test fun httpErrorsMapToTheirMeaning() {
        assertEquals(ApiError.Unauthenticated, HttpApiClient.errorFor(401, null, "{}".toByteArray()))
        assertEquals(ApiError.Forbidden("Nope"), HttpApiClient.errorFor(403, null, """{"message":"Nope"}""".toByteArray()))
        val validation = HttpApiClient.errorFor(422, null, """{"message":"Invalid","errors":{"email":["Bad email","Worse"]}}""".toByteArray())
        assertEquals("Bad email", validation.first("email"))
        assertEquals(ApiError.RateLimited(30), HttpApiClient.errorFor(429, "30", ByteArray(0)))
        val conflict = HttpApiClient.errorFor(409, null, """{"code":"schedule_conflict"}""".toByteArray())
        assertTrue(conflict is ApiError.Conflict)
        assertTrue((HttpApiClient.errorFor(500, null, ByteArray(0)) as ApiError.Server).detail == null)
    }
}

class PusherProtocolTest {
    @Test fun decodesAServerFrameWithItsStringPayload() {
        val raw = """{"event":"location.updated","channel":"private-job.7.location","data":"{\"lat\":49.1,\"lng\":-123.9,\"at\":\"2026-08-29T20:00:00.000000Z\"}"}"""
        val frame = PusherProtocol.decodeFrame(raw)!!
        assertEquals("location.updated", frame.event)
        assertEquals("private-job.7.location", frame.channel)
        val point = RealtimeEvents.point(frame.payload)!!
        assertEquals(49.1, point.lat, 0.0); assertEquals(-123.9, point.lng, 0.0); assertNotNull(point.at)
    }

    @Test fun parsesConnectionEstablished() {
        val frame = PusherProtocol.decodeFrame("""{"event":"pusher:connection_established","data":"{\"socket_id\":\"123.456\",\"activity_timeout\":30}"}""")!!
        val established = PusherProtocol.decodeEstablished(frame)!!
        assertEquals("123.456", established.socketId); assertEquals(30, established.activityTimeout)
    }

    @Test fun subscribeFrameCarriesChannelAndAuth() {
        val frame = PusherProtocol.subscribeFrame("private-conversation.9", "key:signature")
        assertTrue(frame.contains("\"event\":\"pusher:subscribe\""))
        assertTrue(frame.contains("\"channel\":\"private-conversation.9\""))
        assertTrue(frame.contains("\"auth\":\"key:signature\""))
    }

    @Test fun socketUrlFollowsTheSchemeRule() {
        assertEquals("ws://localhost:8091/app/k?protocol=7&client=zivett-android&version=1.0", PusherProtocol.socketUrl("k", "localhost", 8091, "http"))
        assertEquals("wss://zivett.com:443/app/k?protocol=7&client=zivett-android&version=1.0", PusherProtocol.socketUrl("k", "zivett.com", 443, "https"))
    }

    @Test fun backoffDoublesAndCaps() {
        assertEquals(1000L, RealtimeBackoff.delayMillis(0)); assertEquals(2000L, RealtimeBackoff.delayMillis(1)); assertEquals(8000L, RealtimeBackoff.delayMillis(3))
        assertEquals(30_000L, RealtimeBackoff.delayMillis(5)); assertEquals(30_000L, RealtimeBackoff.delayMillis(40))
    }

    @Test fun pushedPayloadsBecomeModels() {
        val row = RealtimeEvents.notification("""{"id":"uuid-1","title":"Quotes are ready","body":"Pick your pro.","route_name":"customer.jobs.show","route_params":{"id":"Z-93U4H3"},"type":"App\\Notifications\\MarketplaceEvent"}""")!!
        assertEquals("uuid-1", row.id); assertFalse(row.read); assertEquals("Z-93U4H3", row.jobRef); assertNotNull(row.createdAt)
        val payload = """{"id":41,"conversation_id":9,"sender_id":3,"sender":"Mike R.","body":"On my way","created_at":"2026-08-29T20:00:00.000000Z"}"""
        assertFalse(RealtimeEvents.message(payload, 8)!!.mine)
        assertTrue(RealtimeEvents.message(payload, 3)!!.mine)
        assertEquals("Illuminate\\Notifications\\Events\\BroadcastNotificationCreated", RealtimeEvents.notificationCreated)
    }

    @Test fun jobPingsAreRecognisedButNeverRendered() {
        val raw = """{"event":"job.updated","channel":"private-job.7","data":"{\"id\":7,\"status\":\"en_route\"}"}"""
        val frame = PusherProtocol.decodeFrame(raw)!!
        assertEquals("private-job.7", frame.channel)
        assertTrue(RealtimeEvents.isJobPing(frame.event, frame.payload, 7))
        // Another job's ping, another event, or the location stream: no refetch.
        assertFalse(RealtimeEvents.isJobPing("job.updated", """{"id":8,"status":"arrived"}""", 7))
        assertFalse(RealtimeEvents.isJobPing(RealtimeEvents.locationUpdated, """{"id":7}""", 7))
        // A body we can't read (or a status this build doesn't know) is still a ping.
        assertTrue(RealtimeEvents.isJobPing("job.updated", "{}", 7))
        assertTrue(RealtimeEvents.isJobPing("job.updated", "not json", 7))
        assertTrue(RealtimeEvents.isJobPing("job.updated", """{"id":7,"status":"some_future_status"}""", 7))
        assertEquals("job.7", RealtimeEvents.jobChannel(7))
        assertEquals("job.7.location", RealtimeEvents.jobLocationChannel(7))
    }

    @Test fun pushTapsResolveTheJobRef() {
        assertEquals("Z-93U4H3", PushRouting.jobRef(mapOf("route_name" to "customer.jobs.show", "route_id" to "Z-93U4H3")))
        assertEquals("Z-93U4H3", PushRouting.jobRef(mapOf("route_name" to "company.jobs.show", "route_params" to """{"id":"Z-93U4H3"}""")))
        assertEquals("42", PushRouting.jobRef(mapOf("route_name" to "business.requests.show", "route_params" to """{"id":42}""")))
        assertNull(PushRouting.jobRef(mapOf("route_name" to "company.dashboard", "route_id" to "x")))
    }
}

/// The web's four steps, plan first (Premium sold through Google Play).
class BusinessSetupStepsTest {
    private fun setup(profileComplete: Boolean = true, profileMissing: List<String> = emptyList(), properties: Boolean = true, plan: Boolean? = true) = BusinessSetup(
        status = "pending", steps = BusinessSetup.Steps(
            profile = BusinessSetup.Steps.Profile(profileComplete, profileMissing), properties = BusinessSetup.Steps.Properties(properties, if (properties) 1 else 0),
            plan = plan?.let { BusinessSetup.Steps.Plan(it, if (it) emptyList() else listOf("pick a plan")) },
        ),
    )

    @Test fun completenessRules() {
        assertTrue(BusinessSetupSteps.completeness(BusinessSetupSteps.Step.TEAM, setup(false, properties = false)).complete)
        assertEquals(listOf("add at least one property"), BusinessSetupSteps.completeness(BusinessSetupSteps.Step.PROPERTIES, setup(properties = false)).missing)
    }

    @Test fun thePlanComesFirst() {
        assertEquals(listOf("Plan", "Business profile", "Properties", "Team"), BusinessSetupSteps.Step.entries.map { it.title })
        assertEquals(BusinessSetupSteps.Step.PLAN, BusinessSetupSteps.firstIncomplete(setup(plan = false, profileComplete = false)))
        assertFalse(BusinessSetupSteps.canJump(BusinessSetupSteps.Step.PROFILE, BusinessSetupSteps.Step.PLAN, setup(plan = false)))
        // An older server without the step never blocks on it.
        assertTrue(BusinessSetupSteps.completeness(BusinessSetupSteps.Step.PLAN, setup(plan = null)).complete)
    }

    @Test fun reentryLandsOnTheFirstIncompleteStep() {
        assertEquals(BusinessSetupSteps.Step.PROFILE, BusinessSetupSteps.firstIncomplete(setup(profileComplete = false)))
        assertEquals(BusinessSetupSteps.Step.PROPERTIES, BusinessSetupSteps.firstIncomplete(setup(properties = false)))
        assertEquals(BusinessSetupSteps.Step.TEAM, BusinessSetupSteps.firstIncomplete(setup()))
    }

    @Test fun jumpsAndStatuses() {
        val state = setup(profileComplete = false, properties = false)
        assertTrue(BusinessSetupSteps.canJump(BusinessSetupSteps.Step.PROFILE, BusinessSetupSteps.Step.TEAM, state))
        assertFalse(BusinessSetupSteps.canJump(BusinessSetupSteps.Step.PROPERTIES, BusinessSetupSteps.Step.PROFILE, setup(profileComplete = false, profileMissing = listOf("phone"))))
        val profileDone = setup(properties = false)
        assertTrue(BusinessSetupSteps.canJump(BusinessSetupSteps.Step.PROPERTIES, BusinessSetupSteps.Step.PROFILE, profileDone))
        assertFalse(BusinessSetupSteps.canJump(BusinessSetupSteps.Step.TEAM, BusinessSetupSteps.Step.PROFILE, profileDone))
        assertTrue(BusinessSetupSteps.canJump(BusinessSetupSteps.Step.TEAM, BusinessSetupSteps.Step.PROPERTIES, setup()))
        val warn = setup(profileComplete = false, profileMissing = listOf("phone", "address"))
        assertEquals(BusinessSetupSteps.Status.WARN, BusinessSetupSteps.status(BusinessSetupSteps.Step.PROFILE, BusinessSetupSteps.Step.TEAM, warn))
        assertEquals(BusinessSetupSteps.Status.CURRENT, BusinessSetupSteps.status(BusinessSetupSteps.Step.TEAM, BusinessSetupSteps.Step.TEAM, warn))
        assertEquals(BusinessSetupSteps.Status.TODO, BusinessSetupSteps.status(BusinessSetupSteps.Step.PROPERTIES, BusinessSetupSteps.Step.PROFILE, setup(properties = false)))
        assertEquals("Business profile: phone · Properties: add at least one property", BusinessSetupSteps.whatsLeft(BusinessSetupSteps.Step.TEAM, setup(profileComplete = false, profileMissing = listOf("phone"), properties = false)))
        assertNull(BusinessSetupSteps.whatsLeft(BusinessSetupSteps.Step.PROFILE, setup(profileComplete = false)))
    }
}

/// Auto-assignment at acceptance (web 93729d2): the `takes_jobs` flag,
/// the roster's `auto_pickable`, and the en-route guard.
class TechAssignmentTest {
    private fun roster(json: String) = JsonCoding.json.decodeFromString<AssignmentRoster>(json)
    private fun job(json: String) = JsonCoding.json.decodeFromString<Job>(json)

    @Test fun rosterDecodesTheNewFlagsAndFallsBackOnAnOlderServer() {
        val current = roster("""{"assigned_user_id":null,"members":[{"id":1,"name":"Office Olu","assignable":true,"takes_jobs":false,"auto_pickable":false},{"id":2,"name":"Tech Tam","assignable":true,"takes_jobs":true,"auto_pickable":true}]}""")
        assertFalse(current.members[0].isAutoPickable)
        assertEquals(2, current.nextUp()?.id) // "Auto-assign next" skips office staff
        // Older server: no takes_jobs / auto_pickable — everyone assignable is a field tech.
        val older = roster("""{"members":[{"id":3,"name":"Sam","assignable":true},{"id":4,"name":"Nia","assignable":false,"needs_photo":true}]}""")
        assertTrue(older.members[0].takesJobs)
        assertTrue(older.members[0].isAutoPickable)
        assertFalse(older.members[1].isAutoPickable)
        assertEquals(3, older.nextUp()?.id)
    }

    @Test fun pickerNoticesTellNobodyAssignableFromNoFieldTechs() {
        val noPhotos = roster("""{"members":[{"id":1,"name":"A","assignable":false,"needs_photo":true}]}""")
        assertTrue(noPhotos.nobodyAssignable)
        assertFalse(noPhotos.noFieldTechs)
        assertNull(noPhotos.nextUp())
        val officeOnly = roster("""{"members":[{"id":1,"name":"A","assignable":true,"takes_jobs":false,"auto_pickable":false}]}""")
        assertFalse(officeOnly.nobodyAssignable)
        assertTrue(officeOnly.noFieldTechs)
        assertNull(officeOnly.nextUp())
        // The assigned head is never offered again.
        val assigned = roster("""{"members":[{"id":1,"name":"A","assignable":true,"auto_pickable":true,"assigned":true},{"id":2,"name":"B","assignable":true,"auto_pickable":true}]}""")
        assertEquals(2, assigned.nextUp()?.id)
    }

    @Test fun teamMembersDefaultToTakingJobs() {
        val team = JsonCoding.json.decodeFromString<Team>("""{"organization":{"id":1,"name":"R","type":"company"},"members":[{"id":1,"name":"A","email":"a@b.c","role":"admin","status":"active"},{"id":2,"name":"B","email":"b@b.c","role":"member","status":"active","takes_jobs":false}]}""")
        assertTrue(team.members[0].takesJobs)
        assertFalse(team.members[1].takesJobs)
    }

    @Test fun takesJobsIsAJsonBooleanOnTheCompanyPatchOnly() {
        val body = MemberBody("Amara", "Okafor", "member", "active", takesJobs = false)
        val company = String(TeamArea.company.updateMember(7, body).body!!)
        assertTrue(company.contains("\"takes_jobs\":false"))
        assertTrue(company.contains("\"first_name\":\"Amara\""))
        assertFalse(String(TeamArea.business.updateMember(7, body).body!!).contains("takes_jobs"))
        // Null = leave it unchanged (omitted, not written as null).
        assertFalse(String(TeamArea.company.updateMember(7, body.copy(takesJobs = null)).body!!).contains("takes_jobs"))
    }

    // Crews (web a2df43e): a quote prices `crew_size` techs and the job is
    // staffed with exactly that many, lead first.
    private val crewJob = """{"id":9,"title":"Deck","status":"accepted","mode":"quote","category":{"id":1,"name":"Painting"},"crew_size":3,"assigned_tech":{"id":5,"name":"Mike R."},"crew":[{"id":5,"name":"Mike R.","photo_url":"https://example.test/m.jpg"},{"id":6,"name":"Ana P."}]}"""

    @Test fun jobsDecodeTheirCrewAndCountWhoIsMissing() {
        val crew = job(crewJob)
        assertEquals(3, crew.crewSize)
        assertEquals(listOf(5, 6), crew.crewMembers.map { it.id })
        assertEquals("https://example.test/m.jpg", crew.crewMembers[0].photoUrl)
        assertEquals(1, crew.crewMissing)
        assertTrue(crew.isAssigned(6)) // crew members work the job like the lead
        assertTrue(CompanyPresentation.onCrew(crew, Fixtures.user(role = UserRole.COMPANY).copy(id = 6)))
        assertFalse(CompanyPresentation.onCrew(crew, null))
        assertFalse(crew.isAssigned(7))
        // Older server: no crew / crew_size — the lead is the whole crew.
        val older = job("""{"id":9,"title":"Leak","status":"accepted","mode":"quote","category":{"id":1,"name":"Plumbing"},"assigned_tech":{"id":5,"name":"Mike R."}}""")
        assertNull(older.crewSize)
        assertEquals(listOf(5), older.crewMembers.map { it.id })
        assertEquals(0, older.crewMissing)
        val unassigned = job("""{"id":9,"title":"Leak","status":"accepted","mode":"quote","category":{"id":1,"name":"Plumbing"}}""")
        assertEquals(1, unassigned.crewMissing)
        assertEquals(0, job(crewJob.replace("\"crew_size\":3", "\"crew_size\":2")).crewMissing)
    }

    @Test fun theAssignmentRosterCarriesTheCrew() {
        val current = roster("""{"assigned_user_id":5,"crew_user_ids":[5,6],"crew_size":3,"members":[]}""")
        assertEquals(listOf(5, 6), current.crewUserIds)
        assertEquals(3, current.crewSize)
        val older = roster("""{"assigned_user_id":5,"members":[]}""")
        assertTrue(older.crewUserIds.isEmpty())
        assertNull(older.crewSize)
    }

    @Test fun theCrewPickerTogglesAutoPicksAndWarns() {
        val team = roster("""{"members":[{"id":1,"name":"A","assignable":true,"auto_pickable":true},{"id":2,"name":"Office","assignable":true,"takes_jobs":false,"auto_pickable":false},{"id":3,"name":"C","assignable":true,"auto_pickable":true},{"id":4,"name":"D","assignable":false,"needs_photo":true}]}""")
        // First picked leads; a full crew takes no more; tapping again drops one.
        assertEquals(listOf(3, 1), listOf(3).togglingCrew(1, 2))
        assertEquals(listOf(3, 1), listOf(3, 1).togglingCrew(2, 2))
        assertEquals(listOf(1), listOf(3, 1).togglingCrew(3, 2))
        // Auto-pick fills the open slots with field techs in roster order, skipping office staff.
        assertEquals(listOf(1, 3), team.autoPick(emptyList(), 2))
        assertEquals(listOf(2, 1, 3), team.autoPick(listOf(2), 3))
        assertTrue(team.canAutoPick(listOf(1), 2))
        assertFalse(team.canAutoPick(listOf(1, 3), 3)) // no field tech left
        assertFalse(team.canAutoPick(listOf(1, 3), 2)) // full
        assertFalse(team.tooFewAssignable(3))
        assertTrue(team.tooFewAssignable(4))
    }

    @Test fun assigningSendsTheWholeCrewLeadFirst() {
        val body = String(CompanyEndpoints.assign(9, listOf(6, 5)).body!!)
        assertEquals("{\"user_ids\":[6,5]}", body)
    }

    // Start job (web 5f40de0): an admin starts a won job, then only a tech
    // on the crew can mark it en route.
    private val admin = Fixtures.user(role = UserRole.COMPANY).copy(id = 1, organizationRole = OrganizationRole.ADMIN)
    private val member = admin.copy(id = 6, organizationRole = OrganizationRole.MEMBER)
    private val started = crewJob.replace("\"crew_size\":3", "\"crew_size\":2,\"dispatched_at\":\"2026-10-22T16:00:00.000000Z\"")

    @Test fun jobsDecodeWhenTheyWereStarted() {
        assertNull(job(crewJob).dispatchedAt)
        assertEquals(java.time.Instant.parse("2026-10-22T16:00:00Z"), job(started).dispatchedAt)
    }

    @Test fun theJobActionFollowsTheStartRule() {
        val unstarted = job(crewJob)
        assertEquals(CompanyPresentation.JobAction.Start, CompanyPresentation.jobAction(unstarted, admin))
        assertEquals(CompanyPresentation.JobAction.Waiting("Waiting to be started"), CompanyPresentation.jobAction(unstarted, member))
        // Started: the crew goes; everyone else — admins included — waits on the lead.
        assertEquals(CompanyPresentation.JobAction.Advance("Mark on the way"), CompanyPresentation.jobAction(job(started), member))
        assertEquals(CompanyPresentation.JobAction.Waiting("Waiting for Mike R. to set off"), CompanyPresentation.jobAction(job(started), admin))
        assertEquals(CompanyPresentation.JobAction.Advance("Mark on the way"), CompanyPresentation.jobAction(job(started), admin.copy(id = 5)))
        val nobody = job("""{"id":9,"title":"Leak","status":"accepted","mode":"quote","category":{"id":1,"name":"Plumbing"},"dispatched_at":"2026-10-22T16:00:00.000000Z"}""")
        assertEquals(CompanyPresentation.JobAction.Waiting("Waiting for the crew to set off"), CompanyPresentation.jobAction(nobody, admin))
        // Later stages advance for anyone who can see the job; nothing past completed.
        assertEquals(CompanyPresentation.JobAction.Advance("Mark arrived"), CompanyPresentation.jobAction(job(started).copy(status = JobStatus.EN_ROUTE), admin))
        assertNull(CompanyPresentation.jobAction(job(started).copy(status = JobStatus.INVOICED), admin))
    }

    @Test fun startingSendsTheCrewAndSaysWhoIsGoing() {
        val request = CompanyEndpoints.start(9, listOf(5, 6))
        assertEquals("{\"user_ids\":[5,6]}", String(request.body!!))
        assertTrue(request.path.endsWith("api/company/jobs/9/start"))
        assertEquals("Mike R. and Ana P. are doing this job. Confirm to start it — they can then mark it en route — or change who's going.", CompanyPresentation.startPickerIntro(job(crewJob), 3))
        val nobody = job("""{"id":9,"title":"Leak","status":"accepted","mode":"quote","category":{"id":1,"name":"Plumbing"}}""")
        assertEquals("Pick who's doing this job. Starting it lets them mark it en route.", CompanyPresentation.startPickerIntro(nobody, 1))
        assertEquals("Pick 3 technicians. Starting it lets them mark it en route.", CompanyPresentation.startPickerIntro(nobody, 3))
        assertEquals("Z-1 started — the crew has been told to head out", CompanyPresentation.startedToast("Z-1", 2))
        assertEquals("Z-1 started — they've been told to head out", CompanyPresentation.startedToast("Z-1", 1))
    }

    @Test fun theStartNoticeSpeaksToWhoeverIsLooking() {
        val unstarted = job(crewJob)
        assertEquals("You're doing this job. Start job to confirm who's going, then mark it en route when you set off.", CompanyPresentation.startNotice(unstarted, admin.copy(id = 5)))
        assertEquals("You're doing this job. Your team will start it when it's time — you'll get a notification, then you can mark it en route.", CompanyPresentation.startNotice(unstarted, member))
        assertEquals("Started — you're on the crew. Mark it en route when you set off.", CompanyPresentation.startNotice(job(started), member))
        assertEquals("Mike R., Ana P. are lined up for this job. Start job to confirm who's going.", CompanyPresentation.startNotice(unstarted, admin))
        val nobody = job("""{"id":9,"title":"Leak","status":"accepted","mode":"quote","category":{"id":1,"name":"Plumbing"}}""")
        assertEquals("Start job to pick who's going.", CompanyPresentation.startNotice(nobody, admin))
        assertNull(CompanyPresentation.startNotice(job(started), admin)) // started, not on it: the action says it
        assertNull(CompanyPresentation.startNotice(unstarted.copy(status = JobStatus.EN_ROUTE), member))
    }

    @Test fun bookersSeeWhoLeadsTheCrew() {
        assertEquals("is your assigned technician", JobPresentation.crewCaption(0, 1))
        assertEquals("leads your crew", JobPresentation.crewCaption(0, 3))
        assertEquals("is on your crew", JobPresentation.crewCaption(2, 3))
        assertEquals("1 technician", CompanyPresentation.technicians(1))
        assertEquals("3 technicians", CompanyPresentation.technicians(3))
    }

    @Test fun theSessionUserCarriesItsPhoto() {
        val user = JsonCoding.json.decodeFromString<User>("""{"id":2,"first_name":"A","last_name":"B","name":"A B","email":"a@b.c","role":"company","photo_url":"https://example.test/p.jpg"}""")
        assertEquals("https://example.test/p.jpg", user.photoUrl)
    }
}
