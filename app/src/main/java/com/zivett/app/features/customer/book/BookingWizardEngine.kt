package com.zivett.app.features.customer.book

import com.zivett.app.core.models.AvailabilityWindow
import com.zivett.app.core.models.BookJobBody
import com.zivett.app.core.models.BookingOptions
import com.zivett.app.features.customer.jobs.JobPresentation
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/// The step engine from `BookingWizard.vue`: which screens exist for the
/// current answers, whether the current one can continue, and the exact
/// payload `POST /api/customer/jobs` gets. The engine is pure over the
/// form state so it's unit-tested without Compose.
object BookingWizardEngine {
    enum class Area { CUSTOMER, BUSINESS }

    sealed interface Step {
        object Category : Step
        data class Question(val id: Int) : Step
        object Issue : Step
        object Photos : Step
        object Where : Step
        object Access : Step
        object Details : Step
        object Mode : Step
        object Schedule : Step
        object Urgency : Step
        object Availability : Step
        object Review : Step

        val phase: String
            get() = when (this) {
                Category, is Question, Issue, Photos -> "About the job"
                Where, Access, Details -> "Where"
                Mode, Schedule, Urgency, Availability -> "When"
                Review -> "Review"
            }

        val isOptional: Boolean get() = this is Question || this == Access || this == Details || this == Availability

        /// Optional steps get a Skip button — except access/details,
        /// where "Next" already reads as skip.
        val showsSkip: Boolean get() = isOptional && this != Access && this != Details
    }

    data class Form(
        val area: Area = Area.CUSTOMER,
        val categoryId: Int? = null,
        /// Business: requests are booked against a property.
        val propertyId: Int? = null,
        val tenant: String = "",
        val intakeAnswers: Map<Int, List<String>> = emptyMap(),
        val issue: String = "",
        val photoCount: Int = 0,
        val savedAddressId: Int? = null,
        val address: String = "",
        val line1: String = "", val city: String = "", val region: String = "BC", val postal: String = "",
        val unit: String = "",
        val accessNotes: String = "",
        val mode: String? = null,
        val modePreset: Boolean = false,
        val selectedWindows: Set<String> = emptySet(),
        val urgency: String? = null,
    )

    const val minPhotos = 2
    const val maxPhotos = 8
    val longHorizonUrgencies = setOf("next_few_weeks", "planning")
    val urgencies: List<Pair<String, String>> = listOf(
        "asap" to "Urgent — as soon as possible", "today" to "Today", "this_week" to "This week",
        "next_few_weeks" to "In the next few weeks", "planning" to "Not sure — still planning or budgeting",
    )

    fun steps(form: Form, questions: List<BookingOptions.Question>): List<Step> {
        val steps = mutableListOf<Step>(Step.Category)
        steps += questions.map { Step.Question(it.id) }
        steps += listOf(Step.Issue, Step.Photos, Step.Where, if (form.area == Area.BUSINESS) Step.Details else Step.Access)
        if (!form.modePreset) steps += Step.Mode
        when (form.mode) {
            "scheduled" -> steps += Step.Schedule
            "quote" -> {
                steps += Step.Urgency
                if (form.urgency?.let { it in longHorizonUrgencies } != true) steps += Step.Availability
            }
        }
        steps += Step.Review
        return steps
    }

    /// Address the server geocodes — unit deliberately excluded.
    fun composedAddress(form: Form): String =
        listOf(form.line1, form.city, "${form.region} ${form.postal}".trim()).filter { it.isNotEmpty() }.joinToString(", ")

    fun canContinue(step: Step, form: Form): Boolean = when (step) {
        Step.Category -> form.categoryId != null
        Step.Issue -> form.issue.isNotBlank()
        // Photos are the customer's hard gate; business books remotely.
        Step.Photos -> form.area == Area.BUSINESS || form.photoCount >= minPhotos
        Step.Where -> if (form.area == Area.BUSINESS) form.propertyId != null else (if (form.savedAddressId != null) form.address.isNotEmpty() else (form.line1.isNotEmpty() && form.city.isNotEmpty() && form.region.isNotEmpty()))
        Step.Mode -> form.mode != null
        Step.Schedule -> form.selectedWindows.isNotEmpty()
        is Step.Question -> (form.intakeAnswers[step.id] ?: emptyList()).any { it.isNotEmpty() }
        Step.Urgency -> form.urgency != null
        Step.Access, Step.Details, Step.Availability, Step.Review -> true
    }

    /// `derivedUrgency`.
    fun derivedUrgency(form: Form, today: String): String = when (form.mode) {
        "instant" -> "asap"
        "scheduled" -> if (form.selectedWindows.any { it.startsWith("$today|") }) "today" else "this_week"
        else -> form.urgency ?: "this_week"
    }

    /// `availabilityPayload` — sorted by date then window order.
    fun windows(selected: Set<String>): List<AvailabilityWindow> = selected.mapNotNull { key ->
        val parts = key.split("|", limit = 2)
        if (parts.size != 2) null else AvailabilityWindow(parts[0], parts[1])
    }.sortedWith(compareBy({ it.date }, { JobPresentation.windows.indexOf(it.window).let { i -> if (i < 0) 0 else i } }))

    fun payload(form: Form, today: String): BookJobBody {
        val address = if (form.savedAddressId != null) form.address else composedAddress(form)
        val intake = form.intakeAnswers.mapNotNull { (id, answers) ->
            val cleaned = answers.map { it.trim() }.filter { it.isNotEmpty() }
            if (cleaned.isEmpty()) null else BookJobBody.IntakeAnswerBody(id, cleaned)
        }.sortedBy { it.questionId }
        val wantsWindows = form.mode == "scheduled" || (form.mode == "quote" && form.selectedWindows.isNotEmpty())
        val business = form.area == Area.BUSINESS
        return BookJobBody(
            serviceCategoryId = form.categoryId ?: 0,
            mode = form.mode ?: "quote",
            urgency = derivedUrgency(form, today),
            issue = form.issue.trim(),
            intakeAnswers = intake.ifEmpty { null },
            availabilityWindows = if (wantsWindows) windows(form.selectedWindows) else null,
            address = if (business) null else address,
            unit = form.unit.ifEmpty { null },
            accessNotes = form.accessNotes.ifEmpty { null },
            customerAddressId = if (business) null else form.savedAddressId,
            propertyId = if (business) form.propertyId else null,
            tenant = if (business && form.tenant.isNotEmpty()) form.tenant else null,
        )
    }

    /// Local calendar date key — never a UTC `toISOString()`.
    fun dateKey(date: LocalDate): String = String.format(java.util.Locale.US, "%04d-%02d-%02d", date.year, date.monthValue, date.dayOfMonth)

    fun today(zone: ZoneId = ZoneId.systemDefault()): String = dateKey(LocalDate.now(zone))

    val windowEndHour = mapOf("morning" to 12, "afternoon" to 17, "evening" to 20)

    fun isWindowElapsed(dateKey: String, window: String, now: LocalDateTime): Boolean {
        if (dateKey != dateKey(now.toLocalDate())) return false
        val end = windowEndHour[window] ?: return false
        return now.hour >= end
    }

    /// Which step a 422 field error belongs to.
    fun step(forField: String, questions: List<BookingOptions.Question>): Step = when {
        forField == "service_category_id" -> Step.Category
        forField == "issue" -> Step.Issue
        forField in setOf("address", "customer_address_id", "property_id") -> Step.Where
        forField in setOf("unit", "tenant") -> Step.Details
        forField == "access_notes" -> Step.Access
        forField == "mode" -> Step.Mode
        forField == "availability_windows" -> Step.Schedule
        forField == "urgency" -> Step.Urgency
        forField.startsWith("intake") -> questions.firstOrNull()?.let { Step.Question(it.id) } ?: Step.Issue
        else -> Step.Review
    }
}
