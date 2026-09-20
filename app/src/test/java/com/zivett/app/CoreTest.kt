package com.zivett.app

import com.zivett.app.core.AddressParts
import com.zivett.app.core.Money
import com.zivett.app.core.PhoneFormatter
import com.zivett.app.core.models.AppNotification
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

    @Test fun pushTapsResolveTheJobRef() {
        assertEquals("Z-93U4H3", PushRouting.jobRef(mapOf("route_name" to "customer.jobs.show", "route_id" to "Z-93U4H3")))
        assertEquals("Z-93U4H3", PushRouting.jobRef(mapOf("route_name" to "company.jobs.show", "route_params" to """{"id":"Z-93U4H3"}""")))
        assertEquals("42", PushRouting.jobRef(mapOf("route_name" to "business.requests.show", "route_params" to """{"id":42}""")))
        assertNull(PushRouting.jobRef(mapOf("route_name" to "company.dashboard", "route_id" to "x")))
    }
}

/// Three steps here: the web's plan step is web-only (store policy).
class BusinessSetupStepsTest {
    private fun setup(profileComplete: Boolean = true, profileMissing: List<String> = emptyList(), properties: Boolean = true) = BusinessSetup(
        status = "pending", steps = BusinessSetup.Steps(
            profile = BusinessSetup.Steps.Profile(profileComplete, profileMissing), properties = BusinessSetup.Steps.Properties(properties, if (properties) 1 else 0),
        ),
    )

    @Test fun completenessRules() {
        assertTrue(BusinessSetupSteps.completeness(BusinessSetupSteps.Step.TEAM, setup(false, properties = false)).complete)
        assertEquals(listOf("add at least one property"), BusinessSetupSteps.completeness(BusinessSetupSteps.Step.PROPERTIES, setup(properties = false)).missing)
    }

    @Test fun thereIsNoPlanStep() {
        // The app never offers a plan — the wizard ends at Team.
        assertEquals(listOf("Business profile", "Properties", "Team"), BusinessSetupSteps.Step.entries.map { it.title })
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
