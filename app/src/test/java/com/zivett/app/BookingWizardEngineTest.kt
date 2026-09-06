package com.zivett.app

import com.zivett.app.core.models.AvailabilityWindow
import com.zivett.app.core.models.BookingOptions
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.features.customer.book.BookingWizardEngine
import com.zivett.app.features.customer.book.BookingWizardEngine.Step
import com.zivett.app.features.customer.book.BookingWizardModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BookingWizardEngineTest {
    private val questions = listOf(BookingOptions.Question(id = 7, prompt = "What needs attention?", type = "single", options = listOf("Faucet", "Toilet")))

    @Test fun stepsFollowTheMode() {
        var form = BookingWizardEngine.Form()
        assertEquals(listOf(Step.Category, Step.Question(7), Step.Issue, Step.Photos, Step.Where, Step.Access, Step.Mode, Step.Review), BookingWizardEngine.steps(form, questions))
        form = form.copy(mode = "scheduled")
        assertEquals(listOf(Step.Category, Step.Issue, Step.Photos, Step.Where, Step.Access, Step.Mode, Step.Schedule, Step.Review), BookingWizardEngine.steps(form, emptyList()))
        form = form.copy(mode = "quote")
        assertTrue(Step.Urgency in BookingWizardEngine.steps(form, emptyList()))
        assertTrue(Step.Availability in BookingWizardEngine.steps(form, emptyList()))
        form = form.copy(urgency = "planning")
        assertFalse(Step.Availability in BookingWizardEngine.steps(form, emptyList()))
        form = form.copy(mode = "instant", modePreset = true)
        assertEquals(listOf(Step.Category, Step.Issue, Step.Photos, Step.Where, Step.Access, Step.Review), BookingWizardEngine.steps(form, emptyList()))
    }

    @Test fun photosAreTheHardGate() {
        var form = BookingWizardEngine.Form()
        assertFalse(BookingWizardEngine.canContinue(Step.Photos, form))
        form = form.copy(photoCount = 1)
        assertFalse(BookingWizardEngine.canContinue(Step.Photos, form))
        form = form.copy(photoCount = 2)
        assertTrue(BookingWizardEngine.canContinue(Step.Photos, form))
        assertTrue(BookingWizardEngine.canContinue(Step.Photos, BookingWizardEngine.Form(area = BookingWizardEngine.Area.BUSINESS)))
    }

    @Test fun whereNeedsASavedPlaceOrAFullAddress() {
        var form = BookingWizardEngine.Form()
        assertFalse(BookingWizardEngine.canContinue(Step.Where, form))
        form = form.copy(line1 = "14 Alder Court", city = "Nanaimo")
        assertTrue(BookingWizardEngine.canContinue(Step.Where, form))
        form = form.copy(savedAddressId = 3, address = "")
        assertFalse(BookingWizardEngine.canContinue(Step.Where, form))
        form = form.copy(address = "Saved")
        assertTrue(BookingWizardEngine.canContinue(Step.Where, form))
    }

    @Test fun composedAddressExcludesTheUnit() {
        val form = BookingWizardEngine.Form(line1 = "14 Alder Court", unit = "201", city = "Nanaimo", region = "BC", postal = "V9R 2K1")
        assertEquals("14 Alder Court, Nanaimo, BC V9R 2K1", BookingWizardEngine.composedAddress(form))
    }

    @Test fun derivedUrgency() {
        var form = BookingWizardEngine.Form(mode = "instant")
        assertEquals("asap", BookingWizardEngine.derivedUrgency(form, "2026-08-22"))
        form = form.copy(mode = "scheduled", selectedWindows = setOf("2026-08-23|morning"))
        assertEquals("this_week", BookingWizardEngine.derivedUrgency(form, "2026-08-22"))
        form = form.copy(selectedWindows = form.selectedWindows + "2026-08-22|evening")
        assertEquals("today", BookingWizardEngine.derivedUrgency(form, "2026-08-22"))
        form = form.copy(mode = "quote", urgency = "planning")
        assertEquals("planning", BookingWizardEngine.derivedUrgency(form, "2026-08-22"))
    }

    @Test fun windowsAreSortedByDateThenTimeOfDay() {
        val windows = BookingWizardEngine.windows(setOf("2026-08-23|morning", "2026-08-22|evening", "2026-08-22|morning"))
        assertEquals(listOf(AvailabilityWindow("2026-08-22", "morning"), AvailabilityWindow("2026-08-22", "evening"), AvailabilityWindow("2026-08-23", "morning")), windows)
    }

    @Test fun elapsedWindowsOnlyApplyToToday() {
        val noon = LocalDateTime.of(2026, 8, 22, 12, 5)
        assertTrue(BookingWizardEngine.isWindowElapsed("2026-08-22", "morning", noon))
        assertFalse(BookingWizardEngine.isWindowElapsed("2026-08-22", "afternoon", noon))
        assertFalse(BookingWizardEngine.isWindowElapsed("2026-08-23", "morning", noon))
    }

    @Test fun payloadMatchesStoreJobRequest() {
        val form = BookingWizardEngine.Form(
            categoryId = 1, mode = "scheduled", issue = "  Leak under sink \n", photoCount = 2,
            line1 = "14 Alder Court", city = "Nanaimo", postal = "V9R 2K1", unit = "201", accessNotes = "Gate 4471",
            selectedWindows = setOf("2026-08-23|morning"), intakeAnswers = mapOf(7 to listOf("Faucet"), 8 to listOf("Dripping", "Leaking"), 9 to listOf("  ")),
        )
        val body = BookingWizardEngine.payload(form, "2026-08-22")
        assertEquals("14 Alder Court, Nanaimo, BC V9R 2K1", body.address)
        assertEquals("Leak under sink", body.issue)
        assertEquals("this_week", body.urgency)
        assertEquals(listOf(AvailabilityWindow("2026-08-23", "morning")), body.availabilityWindows)
        assertEquals(listOf(7, 8), body.intakeAnswers?.map { it.questionId })

        val json = body.toJson()
        assertEquals("1", json["service_category_id"]?.jsonPrimitive?.content)
        assertEquals("201", json["unit"]?.jsonPrimitive?.content)
        assertNull(json["customer_address_id"])
        val intake = json["intake_answers"] as JsonObject
        assertEquals("Faucet", intake["7"]?.jsonPrimitive?.content)
        assertEquals(listOf("Dripping", "Leaking"), (intake["8"] as JsonArray).map { it.jsonPrimitive.content })
        assertEquals("morning", ((json["availability_windows"] as JsonArray).first() as JsonObject)["window"]?.jsonPrimitive?.content)
        // The wire encoding survives the app's coder untouched.
        assertTrue(JsonCoding.json.encodeToString(JsonObject.serializer(), json).contains("\"intake_answers\""))
    }

    @Test fun instantModeSendsNoWindows() {
        val form = BookingWizardEngine.Form(mode = "instant", selectedWindows = setOf("2026-08-22|morning"))
        assertNull(BookingWizardEngine.payload(form, "2026-08-22").availabilityWindows)
    }

    @Test fun fieldErrorsRouteToTheirStep() {
        assertEquals(Step.Where, BookingWizardEngine.step("address", emptyList()))
        assertEquals(Step.Question(7), BookingWizardEngine.step("intake_answers.7", questions))
        assertEquals(Step.Schedule, BookingWizardEngine.step("availability_windows", emptyList()))
    }

    @Test fun localDateKeyIsCalendarLocal() {
        assertEquals("2026-08-22", BookingWizardEngine.dateKey(LocalDate.of(2026, 8, 22)))
        assertEquals("2026-01-05", BookingWizardEngine.dateKey(LocalDate.of(2026, 1, 5)))
    }
}

class BookingWizardModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun main() { Dispatchers.setMain(dispatcher) }
    @After fun reset() { Dispatchers.resetMain() }

    @Test fun loadsOptionsAndPreselectsTheDefaultPlace() = runTest(dispatcher) {
        val client = PreviewApiClient(mapOf("api/customer/booking-options" to Fixtures.bookingOptions()))
        val model = BookingWizardModel(client)
        model.load()
        assertEquals(1, model.form.savedAddressId)
        assertEquals("14 Alder Court, Millbrook", model.form.address)
        assertEquals("Gate code 4471", model.form.accessNotes)
    }

    @Test fun categoryChangeResetsIntake() = runTest(dispatcher) {
        val client = PreviewApiClient(mapOf("api/customer/booking-options" to Fixtures.bookingOptions()))
        val model = BookingWizardModel(client)
        model.load()
        model.selectCategory(model.options.value!!.categories[0])
        model.form = model.form.copy(intakeAnswers = mapOf(1 to listOf("Faucet")))
        model.selectCategory(model.options.value!!.categories[1])
        assertTrue(model.form.intakeAnswers.isEmpty())
    }

    @Test fun submitRefusesWithoutPhotos() = runTest(dispatcher) {
        val client = PreviewApiClient(mapOf("api/customer/booking-options" to Fixtures.bookingOptions()))
        val model = BookingWizardModel(client)
        model.load()
        model.form = model.form.copy(categoryId = 1, issue = "Leak", mode = "quote", urgency = "planning")
        model.submit()
        assertTrue(model.generalError?.contains("at least 2 photos") == true)
        assertEquals(Step.Photos, model.step)
        assertFalse(client.sent.contains("POST api/customer/jobs"))
    }
}
