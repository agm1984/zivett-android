package com.zivett.app.features.customer.book

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.AddressBody
import com.zivett.app.core.models.BookingOptions
import com.zivett.app.core.models.BusinessEndpoints
import com.zivett.app.core.models.CustomerAddress
import com.zivett.app.core.models.CustomerEndpoints
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.ProSupplyResponse
import com.zivett.app.core.models.Property
import com.zivett.app.core.models.PropertyBody
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/// The live wizard: options, form, navigation, photos, drafts, submit.
class BookingWizardModel(
    private val client: ApiClient,
    area: BookingWizardEngine.Area = BookingWizardEngine.Area.CUSTOMER,
    presetMode: String? = null,
    private val presetCategorySlug: String? = null,
    private val presetPropertyId: Int? = null,
    private val resumeRequested: Boolean = false,
) {
    data class Submitted(val job: Job, val photosUploaded: Boolean)

    var options by mutableStateOf<Loadable<BookingOptions>>(Loadable.Loading)
    var form by mutableStateOf(BookingWizardEngine.Form(area = area, mode = presetMode, modePreset = presetMode != null))
    var stepIndex by mutableStateOf(0)
    var submitting by mutableStateOf(false)
    var fieldErrors by mutableStateOf<Map<String, String>>(emptyMap())
    var generalError by mutableStateOf<String?>(null)
    var submitted by mutableStateOf<Submitted?>(null)

    // Server-side draft: state autosaves debounced, photos upload the
    // moment they're picked, booking consumes the draft server-side.
    var serverDraft by mutableStateOf<ServerBookingDraft?>(null)
    /// A stored draft found on entry, offered as Resume / Start over.
    /// Any fresh progress (including deep-link presets) supersedes it.
    var pendingDraft by mutableStateOf<BookingDraftPayload?>(null)
    var draftUploading by mutableStateOf(false)
    var photoNotice by mutableStateOf<String?>(null)
    var supply by mutableStateOf<ProSupplyResponse?>(null)
    var creatingProperty by mutableStateOf(false)

    /// Guards the autosave against load-time mutations (presets, the
    /// default-place autofill) registering as booker progress.
    private var ready = false
    private var saveJob: CoroutineJob? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var autoFilledNotes = ""

    val area: BookingWizardEngine.Area get() = form.area
    val properties: List<Property> get() = options.value?.properties ?: emptyList()
    val questions: List<BookingOptions.Question> get() = category?.questions ?: emptyList()
    val category: BookingOptions.Category? get() = options.value?.categories?.firstOrNull { it.id == form.categoryId }
    val steps: List<BookingWizardEngine.Step> get() = BookingWizardEngine.steps(form, questions)
    val step: BookingWizardEngine.Step get() = steps[minOf(stepIndex, steps.size - 1)]
    val canContinue: Boolean get() = BookingWizardEngine.canContinue(step, form)
    val progress: Float get() = maxOf(0.05f, (stepIndex + 1).toFloat() / steps.size)
    val emergencyFeeCents: Int get() = category?.emergencyFeeCents ?: 0

    /// Dead days: the supply probe says no matched pro works them.
    val deadDays: Set<String> get() = (supply?.availableDates ?: emptyList()).filter { it.available == 0 }.map { it.date }.toSet()

    /// The anonymous supply probe: "N verified pros serve this area" on
    /// the Where step, and per-day availability that greys out dead days
    /// on the schedule grid. Zero supply shows nothing and never blocks
    /// booking; a failed probe just skips the extras.
    suspend fun probeSupply(days: Int? = null) {
        val coords = probeCoordinates
        supply = runCatching { client.send(CustomerEndpoints.proSupply(category?.slug, coords?.first, coords?.second, days)) }.getOrNull()
    }

    private val probeCoordinates: Pair<Double, Double>?
        get() {
            if (form.area == BookingWizardEngine.Area.BUSINESS) {
                val property = properties.firstOrNull { it.id == form.propertyId } ?: return null
                val lat = property.lat ?: return null; val lng = property.lng ?: return null
                return lat to lng
            }
            val place = (options.value?.addresses ?: emptyList()).firstOrNull { it.id == form.savedAddressId } ?: return null
            val lat = place.lat ?: return null; val lng = place.lng ?: return null
            return lat to lng
        }

    suspend fun load() {
        val request = if (form.area == BookingWizardEngine.Area.BUSINESS) BusinessEndpoints.bookingOptions() else CustomerEndpoints.bookingOptions()
        options = options.reloaded { client.send(request) }
        val loaded = options.value ?: return
        // Drafts are a convenience — a failed fetch just means no resume offer this visit.
        serverDraft = runCatching { client.send(BookingDraftEndpoints.show(form.area)) }.getOrNull()?.draft
        form = form.copy(photoCount = serverDraft?.photos?.size ?: 0)
        presetCategorySlug?.let { slug -> loaded.categories.firstOrNull { it.slug == slug }?.let { selectCategory(it); stepIndex = 1 } }
        presetPropertyId?.let { id -> if ((loaded.properties ?: emptyList()).any { it.id == id }) form = form.copy(propertyId = id) }
        if (form.area == BookingWizardEngine.Area.CUSTOMER && form.savedAddressId == null) {
            ((loaded.addresses ?: emptyList()).firstOrNull { it.isDefault } ?: loaded.addresses?.firstOrNull())?.let { useSavedPlace(it) }
        }

        val payload = serverDraft?.payload
        if (payload != null && payload.form.serviceCategoryId != null) {
            if (resumeRequested) {
                // The app-wide banner's Resume lands here — skip the second prompt.
                resume(payload)
            } else if (presetCategorySlug == null && presetPropertyId == null && !form.modePreset) {
                pendingDraft = payload
            }
            // Deep-link presets silently supersede the draft — starting
            // over IS a decision (the next autosave overwrites it).
        }
        ready = true
    }

    /// The resume prompt's category label ("your unfinished Plumbing booking").
    val pendingDraftCategory: String?
        get() = pendingDraft?.form?.serviceCategoryId?.let { id -> options.value?.categories?.firstOrNull { it.id == id }?.name }

    fun selectCategory(category: BookingOptions.Category) {
        form = form.copy(categoryId = category.id, intakeAnswers = if (form.categoryId != category.id) emptyMap() else form.intakeAnswers)
    }

    fun useSavedPlace(place: CustomerAddress) {
        var next = form.copy(savedAddressId = place.id, address = place.address)
        if (form.accessNotes.isEmpty() || form.accessNotes == autoFilledNotes) {
            next = next.copy(accessNotes = place.accessNotes ?: "")
            autoFilledNotes = next.accessNotes
        }
        form = next
    }

    fun useNewAddress() { form = form.copy(savedAddressId = null, address = "") }

    fun next() { if (canContinue && stepIndex < steps.size - 1) stepIndex += 1 }
    fun back() { stepIndex = maxOf(0, stepIndex - 1) }
    fun skip() { if (step.isOptional) stepIndex = minOf(steps.size - 1, stepIndex + 1) }
    fun jump(target: BookingWizardEngine.Step) { steps.indexOf(target).takeIf { it >= 0 }?.let { stepIndex = it } }

    fun toggleWindow(key: String) {
        form = form.copy(selectedWindows = if (key in form.selectedWindows) form.selectedWindows - key else form.selectedWindows + key)
    }

    fun setUrgency(key: String) {
        form = form.copy(urgency = key, selectedWindows = if (key in BookingWizardEngine.longHorizonUrgencies) emptySet() else form.selectedWindows)
    }

    // Photos — on the server draft the moment they're picked, so an
    // interruption never costs a re-upload; booking moves them onto the
    // job server-side.

    val draftPhotos: List<ServerBookingDraft.Photo> get() = serverDraft?.photos ?: emptyList()

    suspend fun importPhotos(data: List<ByteArray>) {
        val room = BookingWizardEngine.maxPhotos - draftPhotos.size
        if (data.isEmpty() || room <= 0) return
        draftUploading = true
        try {
            val response = client.send(BookingDraftEndpoints.uploadPhotos(form.area, data.take(room)))
            response.draft?.let { serverDraft = it }
            form = form.copy(photoCount = draftPhotos.size)
        } catch (_: Exception) {
            photoNotice = "Could not upload those photos. Please try again."
        } finally {
            draftUploading = false
        }
    }

    suspend fun removePhoto(photo: ServerBookingDraft.Photo) {
        try {
            client.send(BookingDraftEndpoints.deletePhoto(form.area, photo.id))
            serverDraft = serverDraft?.let { it.copy(photos = it.photos.filter { p -> p.id != photo.id }) }
            form = form.copy(photoCount = draftPhotos.size)
        } catch (_: Exception) {
            photoNotice = "Could not remove that photo. Please try again."
        }
    }

    // Draft persistence

    /// The web wizard's `draftSnapshot()`, from this platform's state.
    fun draftSnapshot(): BookingDraftPayload {
        val business = form.area == BookingWizardEngine.Area.BUSINESS
        val answers = mutableMapOf<String, BookingDraftPayload.Answer>()
        for ((id, values) in form.intakeAnswers) {
            val cleaned = values.filter { it.isNotBlank() }
            if (cleaned.isEmpty()) continue
            val multi = questions.firstOrNull { it.id == id }?.type == "multi"
            answers[id.toString()] = if (multi) BookingDraftPayload.Answer.many(cleaned) else BookingDraftPayload.Answer.one(cleaned[0])
        }
        return BookingDraftPayload(
            form = BookingDraftPayload.Form(
                mode = form.mode,
                serviceCategoryId = form.categoryId,
                issue = form.issue,
                urgency = form.urgency ?: "this_week",
                accessNotes = form.accessNotes,
                address = if (business) null else (if (form.savedAddressId != null) form.address else ""),
                customerAddressId = if (business) null else form.savedAddressId,
                propertyId = if (business) form.propertyId else null,
                unit = if (business) form.unit else null,
                tenant = if (business) form.tenant else null,
            ),
            intakeAnswers = answers,
            selectedWindows = BookingWizardEngine.windows(form.selectedWindows).map { "${it.date}|${it.window}" },
            newAddress = BookingDraftPayload.NewAddress(form.line1, form.unit, form.city, form.region, form.postal),
            addressChoice = if (business) null else (form.savedAddressId?.let { BookingDraftPayload.AddressChoice.place(it) } ?: BookingDraftPayload.AddressChoice.new),
            savedUnit = if (form.savedAddressId != null) form.unit else "",
            modePreset = form.modePreset,
            stepIndex = stepIndex,
        )
    }

    /// Debounced, best-effort — a failed save costs nothing until the
    /// app dies. The screen calls this on every form/step change.
    fun noteChanged() {
        if (!ready || submitted != null) return
        pendingDraft = null
        if (form.categoryId == null && stepIndex == 0) return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1500)
            if (isActive) saveDraft()
        }
    }

    private suspend fun saveDraft() {
        if (submitted != null) return
        val response = runCatching { client.send(BookingDraftEndpoints.save(form.area, draftSnapshot(), form.categoryId)) }.getOrNull() ?: return
        // Keep photos from the response — an upload may have landed between saves.
        serverDraft = response.draft
    }

    fun resumePending() {
        val payload = pendingDraft ?: return
        pendingDraft = null
        resume(payload)
    }

    fun discardDraft() {
        pendingDraft = null
        serverDraft = null
        form = form.copy(photoCount = 0)
        val area = form.area
        scope.launch { runCatching { client.send(BookingDraftEndpoints.discard(area)) } }
    }

    /// Restore the snapshot. The bank may have changed since — only a
    /// still-active category resumes; otherwise the flow starts fresh.
    private fun resume(payload: BookingDraftPayload) {
        val category = options.value?.categories?.firstOrNull { it.id == payload.form.serviceCategoryId }
        if (category == null) { discardDraft(); return }
        var next = form.copy(
            categoryId = category.id, mode = payload.form.mode, modePreset = payload.modePreset ?: false,
            urgency = payload.form.urgency, issue = payload.form.issue ?: "", accessNotes = payload.form.accessNotes ?: "",
        )
        if (form.area == BookingWizardEngine.Area.BUSINESS) {
            next = next.copy(propertyId = payload.form.propertyId, unit = payload.form.unit ?: "", tenant = payload.form.tenant ?: "")
        } else {
            val placeId = payload.addressChoice?.placeId
            val place = placeId?.let { id -> (options.value?.addresses ?: emptyList()).firstOrNull { it.id == id } }
            next = if (place != null) {
                next.copy(savedAddressId = place.id, address = payload.form.address?.takeIf { it.isNotEmpty() } ?: place.address, unit = payload.savedUnit ?: "")
            } else {
                val region = payload.newAddress?.region ?: ""
                next.copy(savedAddressId = null, address = "", line1 = payload.newAddress?.line1 ?: "", city = payload.newAddress?.city ?: "", region = region.ifEmpty { "BC" }, postal = payload.newAddress?.postal ?: "", unit = payload.newAddress?.unit ?: "")
            }
        }
        next = next.copy(intakeAnswers = (payload.intakeAnswers ?: emptyMap()).mapNotNull { (key, answer) -> key.toIntOrNull()?.let { it to answer.values } }.toMap())
        // Only future windows survive a resume — offered days may have passed.
        val today = BookingWizardEngine.today()
        next = next.copy(selectedWindows = (payload.selectedWindows ?: emptyList()).filter { (it.split("|").firstOrNull() ?: "") >= today }.toSet())
        form = next

        // Photos live on the server draft and survive the resume. Only a
        // customer draft still short of the 2-photo minimum is pulled
        // back to the photos screen instead of hitting a dead submit.
        val steps = this.steps
        val photosIndex = steps.indexOf(BookingWizardEngine.Step.Photos)
        val cap = if (form.area == BookingWizardEngine.Area.CUSTOMER && form.photoCount < BookingWizardEngine.minPhotos && photosIndex >= 0) photosIndex else steps.size - 1
        stepIndex = maxOf(0, minOf(payload.stepIndex ?: 0, cap))
    }

    // Inline add-property (business Where step)

    /// Creates the property without leaving the wizard and selects it.
    /// Returns field errors keyed by input on a 422, null on success.
    suspend fun addProperty(name: String, kind: String, address: String): Map<String, String>? {
        creatingProperty = true
        try {
            val trimmedKind = kind.trim()
            val created = client.send(BusinessEndpoints.createProperty(PropertyBody(name.trim(), trimmedKind.ifEmpty { null }, address.trim()))).property
            options.value?.let { value ->
                options = Loadable.Loaded(value.copy(properties = ((value.properties ?: emptyList()) + created).sortedBy { it.name.lowercase() }))
            }
            form = form.copy(propertyId = created.id)
            return null
        } catch (error: ApiError) {
            if (error is ApiError.Validation) return error.errors.firstMessages
            return mapOf("address" to error.userMessage)
        } catch (error: Exception) {
            return mapOf("address" to error.userMessage)
        } finally {
            creatingProperty = false
        }
    }

    fun modeConsequence(key: String): String = when (key) {
        "instant" -> if (emergencyFeeCents > 0) "Adds the ${category?.name ?: ""} emergency fee of ${Money.format(emergencyFeeCents)}." else "No emergency fee for this category."
        "scheduled" -> "Pick a day and arrival window — only pros available that day see your job."
        else -> "Verified pros send itemized quotes, typically within 24 hours. You compare and choose."
    }

    suspend fun submit() {
        if (submitting) return
        if (form.area == BookingWizardEngine.Area.CUSTOMER && draftPhotos.size < BookingWizardEngine.minPhotos) {
            generalError = "Please add at least ${BookingWizardEngine.minPhotos} photos of the issue."
            jump(BookingWizardEngine.Step.Photos)
            return
        }
        submitting = true; fieldErrors = emptyMap(); generalError = null
        try {
            // Auto-save a typed address so the next booking is one tap.
            if (form.area == BookingWizardEngine.Area.CUSTOMER && form.savedAddressId == null) {
                val address = BookingWizardEngine.composedAddress(form)
                val existing = options.value?.addresses?.firstOrNull { it.address.equals(address, ignoreCase = true) }
                if (existing != null) {
                    form = form.copy(savedAddressId = existing.id)
                } else {
                    val label = form.line1.take(60).ifEmpty { "Saved address" }
                    runCatching { client.send(CustomerEndpoints.createAddress(AddressBody(label, address, form.accessNotes.ifEmpty { null }))).address }.getOrNull()?.let { saved ->
                        form = form.copy(savedAddressId = saved.id, address = saved.address)
                    }
                }
            }

            // Photos were uploaded to the draft as they were picked;
            // booking moves them onto the job server-side and consumes the draft.
            val expectedPhotos = draftPhotos.size
            val payload = BookingWizardEngine.payload(form, BookingWizardEngine.today())
            val request = if (form.area == BookingWizardEngine.Area.BUSINESS) BusinessEndpoints.book(payload) else CustomerEndpoints.book(payload)
            val job = client.send(request).job
            // The server consumed the draft; kill any queued autosave so it can't recreate one.
            saveJob?.cancel()
            serverDraft = null
            pendingDraft = null
            submitted = Submitted(job, (job.photos?.size ?: 0) >= expectedPhotos)
        } catch (error: ApiError) {
            if (error is ApiError.Validation) {
                fieldErrors = error.errors.firstMessages
                error.errors.errors.keys.sorted().firstOrNull()?.let { first ->
                    if (first == "mode") form = form.copy(modePreset = false)
                    jump(BookingWizardEngine.step(first, questions))
                }
                generalError = if (fieldErrors.isEmpty()) error.errors.message else null
            } else generalError = error.userMessage
        } catch (error: Exception) {
            generalError = error.userMessage
        } finally {
            submitting = false
        }
    }
}
