package com.zivett.app.features.customer.book

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.zivett.app.app.Areas
import com.zivett.app.app.JobDetailRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Money
import com.zivett.app.core.media.PhotoImport
import com.zivett.app.core.models.BookingOptions
import com.zivett.app.core.models.Job
import com.zivett.app.design.ZActionBand
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZColors
import com.zivett.app.design.ZDisplay
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZRadius
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSelectableRow
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextArea
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTitle
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.design.ZType
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.shared.LocalNav
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BookJobScreen(
    area: BookingWizardEngine.Area = BookingWizardEngine.Area.CUSTOMER,
    presetMode: String? = null,
    presetCategorySlug: String? = null,
    presetPropertyId: Int? = null,
    /// The resume banner's entry — restores the server draft without the
    /// wizard's second prompt.
    resumeRequested: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    var model by remember { mutableStateOf(BookingWizardModel(environment.client, area, presetMode, presetCategorySlug, presetPropertyId, resumeRequested)) }
    LaunchedEffect(model) { model.load() }
    val user = environment.session.user

    Column(modifier = Modifier.fillMaxSize().background(ZTheme.colors.cream)) {
        ZTopBar(if (area == BookingWizardEngine.Area.BUSINESS) "New request" else "Book a job", onBack = onBack)
        when {
            user != null && !user.isEmailVerified -> VerifyGate()
            else -> {
                val submitted = model.submitted
                if (submitted != null) {
                    BookingSuccess(submitted.job, submitted.photosUploaded, model.form.mode ?: "quote", model.category?.name ?: "", area) {
                        val fresh = BookingWizardModel(environment.client, area)
                        model = fresh
                        scope.launch { fresh.load() }
                    }
                } else {
                    Box(modifier = Modifier.padding(horizontal = ZSpacing.md)) {
                        ZLoadable(model.options, retry = { scope.launch { model.load() } }) { }
                    }
                    model.options.value?.let { options -> WizardBody(model, options) }
                }
            }
        }
    }
}

@Composable
private fun VerifyGate() {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    ZScreen {
        ZMono("One quick step first", modifier = Modifier.padding(top = ZSpacing.xl))
        ZTitle("Verify your email to book")
        ZBody("We sent a code to ${environment.session.user?.email ?: "your inbox"}. Enter it from the account screen, then come back here.", tone = ZTextTone.SOFT)
        notice?.let { ZBanner(it, tone = ZTone.WARNING) }
        ZButton("I've verified — continue") {
            scope.launch {
                runCatching { environment.session.refreshUser() }
                if (environment.session.user?.isEmailVerified == false) notice = "Still unverified — enter the code from the email first."
            }
        }
    }
}

@Composable
private fun WizardBody(model: BookingWizardModel, options: BookingOptions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    var addingProperty by remember { mutableStateOf(false) }

    // Autosave on every form/step change (debounced in the model).
    LaunchedEffect(model.form, model.stepIndex) { model.noteChanged() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(BookingWizardEngine.maxPhotos)) { uris ->
        if (uris.isNotEmpty()) scope.launch { model.importPhotos(PhotoImport.jpegData(context, uris)) }
    }

    /// Single-choice taps advance after a beat, like the web's 260ms timer.
    fun autoAdvance() { scope.launch { delay(260); model.next() } }

    Column(modifier = Modifier.fillMaxSize()) {
        LinearProgressIndicator(progress = { model.progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = ZSpacing.md), color = colors.brandGold, trackColor = colors.surfaceSunken)
        ZMono("${model.step.phase} · ${model.stepIndex + 1} of ${model.steps.size}", modifier = Modifier.padding(horizontal = ZSpacing.md, vertical = ZSpacing.xs))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ZSpacing.md, vertical = ZSpacing.md), verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
            if (model.pendingDraft != null) {
                // A stored draft found on entry — Resume restores it (photos and
                // all), Start over deletes it. Any fresh progress dismisses the offer.
                ZCard {
                    ZBody("You have an unfinished ${model.pendingDraftCategory ?: "booking"}${if (model.pendingDraftCategory != null) " booking" else ""} from earlier — pick up where you left off?")
                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                        ZButton("Resume", compact = true, fullWidth = false) { model.resumePending() }
                        ZButton("Start over", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { model.discardDraft() }
                    }
                }
            }
            if (model.form.modePreset && model.form.mode == "instant" && model.emergencyFeeCents > 0 && model.step != BookingWizardEngine.Step.Review) {
                ZBanner("Instant dispatch adds a ${Money.format(model.emergencyFeeCents)} emergency fee.", tone = ZTone.WARNING)
            }
            model.generalError?.let { ZBanner(it, tone = ZTone.DANGER) }

            when (val step = model.step) {
                BookingWizardEngine.Step.Category -> CategoryStep(model, options, ::autoAdvance)
                is BookingWizardEngine.Step.Question -> model.questions.firstOrNull { it.id == step.id }?.let { QuestionStep(model, it, ::autoAdvance) }
                BookingWizardEngine.Step.Issue -> {
                    StepHeading("Tell us a bit more")
                    ZTextArea("What's the problem?", model.form.issue, { model.form = model.form.copy(issue = it) }, placeholder = "e.g. Water pooling under the kitchen sink, cabinet is getting wet", error = model.fieldErrors["issue"])
                }
                BookingWizardEngine.Step.Photos -> PhotosStep(model) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                BookingWizardEngine.Step.Where -> if (model.area == BookingWizardEngine.Area.BUSINESS) PropertyStep(model, ::autoAdvance) { addingProperty = true } else WhereStep(model, options)
                BookingWizardEngine.Step.Access -> {
                    StepHeading("Anything for getting in?", "Optional.")
                    ZTextArea("Access notes", model.form.accessNotes, { model.form = model.form.copy(accessNotes = it) }, placeholder = "Gate code, parking, pets…", error = model.fieldErrors["access_notes"], minLines = 3)
                }
                BookingWizardEngine.Step.Details -> {
                    StepHeading("Unit, tenant & access", "Optional — helps the pro find the right door.")
                    ZTextField("Unit / floor", model.form.unit, { model.form = model.form.copy(unit = it) }, placeholder = "Unit 4", error = model.fieldErrors["unit"])
                    ZTextField("Tenant / contact", model.form.tenant, { model.form = model.form.copy(tenant = it) }, placeholder = "A. Okafor", error = model.fieldErrors["tenant"], capitalization = KeyboardCapitalization.Words)
                    ZTextArea("Access notes", model.form.accessNotes, { model.form = model.form.copy(accessNotes = it) }, placeholder = "Gate code, parking, pets…", error = model.fieldErrors["access_notes"], minLines = 3)
                }
                BookingWizardEngine.Step.Mode -> ModeStep(model, options, ::autoAdvance)
                BookingWizardEngine.Step.Schedule -> WindowsStep(model, "When works for you?")
                BookingWizardEngine.Step.Urgency -> UrgencyStep(model, options, ::autoAdvance)
                BookingWizardEngine.Step.Availability -> WindowsStep(model, "When could a pro come?")
                BookingWizardEngine.Step.Review -> ReviewStep(model, options)
            }
            Spacer(Modifier.height(ZSpacing.lg))
        }

        // The funnel's winning action gets the gold band, full width below the controls row.
        Column(modifier = Modifier.fillMaxWidth().background(colors.surface.copy(alpha = 0.95f)).padding(ZSpacing.md), verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                if (model.stepIndex > 0) ZButton("Back", style = ZButtonStyle.GHOST, fullWidth = false) { model.back() }
                Spacer(Modifier.weight(1f))
                if (model.step.showsSkip) ZButton("Skip", style = ZButtonStyle.OUTLINE, fullWidth = false) { model.skip() }
                if (model.step != BookingWizardEngine.Step.Review) ZButton("Next", fullWidth = false, enabled = model.canContinue) { model.next() }
            }
            if (model.step == BookingWizardEngine.Step.Review) {
                ZActionBand(if (model.area == BookingWizardEngine.Area.BUSINESS) "Submit request" else "Submit job", loading = model.submitting) { scope.launch { model.submit() } }
            }
        }
    }

    if (addingProperty) AddPropertySheet(model) { addingProperty = false }
}

@Composable
private fun StepHeading(text: String, hint: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { ZTitle(text); if (hint != null) ZCaption(hint) }
}

@Composable
private fun CategoryStep(model: BookingWizardModel, options: BookingOptions, autoAdvance: () -> Unit) {
    val colors = ZTheme.colors
    StepHeading("What kind of pro do you need?")
    model.fieldErrors["service_category_id"]?.let { ZCaption(it, color = colors.danger) }
    for (row in options.categories.chunked(2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
            for (cat in row) {
                val selected = model.form.categoryId == cat.id
                Column(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(ZRadius.card)).background(colors.surface).border(if (selected) 2.dp else 1.dp, if (selected) colors.navy else colors.border, RoundedCornerShape(ZRadius.card))
                        .clickable { model.selectCategory(cat); autoAdvance() }.padding(ZSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(ZColors.parse(cat.bgColor) ?: colors.infoSoft), contentAlignment = Alignment.Center) {
                        Text(cat.code, style = ZType.monoBody.copy(fontWeight = FontWeight.Bold), color = ZColors.parse(cat.fgColor) ?: colors.info)
                    }
                    ZBodyStrong(cat.name)
                    cat.blurb?.let { ZCaption(it, maxLines = 2) }
                    // The trade's market floor.
                    cat.minHourlyRateCents?.let { ZLabel("From $${it / 100}/hr", tone = ZTextTone.LINK) }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuestionStep(model: BookingWizardModel, q: BookingOptions.Question, autoAdvance: () -> Unit) {
    val answers = model.form.intakeAnswers[q.id] ?: emptyList()
    fun set(values: List<String>) { model.form = model.form.copy(intakeAnswers = model.form.intakeAnswers + (q.id to values)) }
    when (q.type) {
        "single" -> {
            StepHeading(q.prompt, "Pick the closest match — pros quote faster with specifics.")
            for (option in q.options ?: emptyList()) ZSelectableRow(selected = answers == listOf(option), onClick = { set(listOf(option)); autoAdvance() }) { ZBodyStrong(option, modifier = Modifier.weight(1f)) }
        }
        "multi" -> {
            StepHeading(q.prompt, "Select all that apply.")
            for (option in q.options ?: emptyList()) ZSelectableRow(selected = option in answers, onClick = { set(if (option in answers) answers - option else answers + option) }) { ZBodyStrong(option, modifier = Modifier.weight(1f)) }
        }
        "description" -> {
            StepHeading(q.prompt, "A sentence or two is plenty.")
            ZTextArea("", answers.firstOrNull() ?: "", { set(listOf(it)) }, placeholder = q.placeholder ?: "")
        }
        else -> {
            StepHeading(q.prompt, "A few words is plenty.")
            ZTextField("", answers.firstOrNull() ?: "", { set(listOf(it.take(120))) }, placeholder = q.placeholder ?: "", capitalization = KeyboardCapitalization.Sentences)
        }
    }
}

@Composable
private fun PhotosStep(model: BookingWizardModel, pick: () -> Unit) {
    val colors = ZTheme.colors
    if (model.area == BookingWizardEngine.Area.BUSINESS) StepHeading("Add photos if you have them", "Pros quote faster when they can see the problem. Booking remotely? Continue without — you can add photos from the request page once you have them.")
    else StepHeading("Add photos of the issue", "At least ${BookingWizardEngine.minPhotos} — pros quote faster and more accurately when they can see the problem. JPEG, PNG or WebP.")
    model.photoNotice?.let { ZBanner(it, tone = ZTone.DANGER, onClick = { model.photoNotice = null }) }
    // Photos live on the server draft — uploaded when picked, so an interruption never costs a re-upload.
    val scope = rememberCoroutineScope()
    com.zivett.app.design.ZFlowRow(spacing = 6.dp) {
        for (photo in model.draftPhotos) {
            Box(Modifier.size(90.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceAlt)) {
                AsyncImage(model = photo.thumbUrl ?: photo.url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                IconButton(onClick = { scope.launch { model.removePhoto(photo) } }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) { Icon(Icons.Filled.Cancel, contentDescription = "Remove", tint = Color.White) }
            }
        }
    }
    if (model.draftUploading) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ZSpinner() }
    if (model.draftPhotos.size < BookingWizardEngine.maxPhotos) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.card)).background(colors.surfaceAlt).border(1.5.dp, colors.borderStrong, RoundedCornerShape(ZRadius.card)).clickable(onClick = pick).padding(vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = colors.link)
            ZBodyStrong("Tap to add photos", color = colors.link)
        }
    }
    ZCaption(if (model.area == BookingWizardEngine.Area.BUSINESS) "Optional · ${model.draftPhotos.size}/${BookingWizardEngine.maxPhotos} added" else "${model.draftPhotos.size} of ${BookingWizardEngine.maxPhotos} added")
}

/// Business: requests are booked against a property. Nobody leaves the
/// wizard to fix missing data — a fresh property is added in place.
@Composable
private fun PropertyStep(model: BookingWizardModel, autoAdvance: () -> Unit, addProperty: () -> Unit) {
    val colors = ZTheme.colors
    StepHeading("Which property?", "Requests are booked against a property so history stays organized.")
    model.fieldErrors["property_id"]?.let { ZCaption(it, color = colors.danger) }
    if (model.properties.isEmpty()) ZBanner("You haven't added any properties yet — add one now to book a request against it.", tone = ZTone.WARNING)
    for (property in model.properties) {
        ZSelectableRow(selected = model.form.propertyId == property.id, onClick = { model.form = model.form.copy(propertyId = property.id); autoAdvance() }) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ZBodyStrong(property.name)
                ZCaption(listOfNotNull(property.kind, property.address).joinToString(" · "))
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.tile)).border(1.5.dp, colors.borderStrong, RoundedCornerShape(ZRadius.tile)).clickable(onClick = addProperty).padding(vertical = 22.dp),
        horizontalArrangement = Arrangement.Center,
    ) { ZBodyStrong("+ Add a property", color = colors.link) }
}

@Composable
private fun WhereStep(model: BookingWizardModel, options: BookingOptions) {
    val colors = ZTheme.colors
    LaunchedEffect(model.form.savedAddressId) { model.probeSupply() }
    StepHeading("Where's the job?")
    (model.fieldErrors["address"] ?: model.fieldErrors["customer_address_id"])?.let { ZCaption(it, color = colors.danger) }
    // The supply signal — renders only when matched pros exist; zero supply shows nothing and never blocks booking.
    model.supply?.takeIf { it.count > 0 }?.let { supply ->
        ZBanner("${supply.count} verified ${if (supply.count == 1) "pro serves" else "pros serve"} this area${supply.minHourlyRateCents?.let { " · prices starting from $${it / 100}/hr" } ?: ""}.", tone = ZTone.SUCCESS)
    }
    for (place in options.addresses ?: emptyList()) {
        ZSelectableRow(selected = model.form.savedAddressId == place.id, onClick = { model.useSavedPlace(place) }) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { ZBodyStrong(place.label); if (place.isDefault) ZBadge("Default", ZTone.INFO) }
                ZCaption(place.address)
            }
        }
    }
    if (model.form.savedAddressId != null) {
        ZTextField("Unit (optional)", model.form.unit, { model.form = model.form.copy(unit = it) }, placeholder = "Suite 201", error = model.fieldErrors["unit"])
        ZButton("Use a different address", style = ZButtonStyle.GHOST, fullWidth = false) { model.useNewAddress() }
    } else {
        if (!(options.addresses ?: emptyList()).isEmpty()) ZLabel("Or a different address")
        ZTextField("Street address", model.form.line1, { model.form = model.form.copy(line1 = it) }, placeholder = "14 Alder Court", capitalization = KeyboardCapitalization.Words)
        ZTextField("Unit (optional)", model.form.unit, { model.form = model.form.copy(unit = it) }, placeholder = "Suite 201", error = model.fieldErrors["unit"])
        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
            ZTextField("City", model.form.city, { model.form = model.form.copy(city = it) }, Modifier.weight(1.4f), "Nanaimo", capitalization = KeyboardCapitalization.Words)
            ZTextField("Province", model.form.region, { model.form = model.form.copy(region = it.uppercase()) }, Modifier.weight(0.8f), "BC", capitalization = KeyboardCapitalization.Characters)
            ZTextField("Postal", model.form.postal, { model.form = model.form.copy(postal = it.uppercase()) }, Modifier.weight(1f), "V9R 2K1", capitalization = KeyboardCapitalization.Characters)
        }
    }
}

@Composable
private fun ModeStep(model: BookingWizardModel, options: BookingOptions, autoAdvance: () -> Unit) {
    val colors = ZTheme.colors
    StepHeading("When do you need it?")
    model.fieldErrors["mode"]?.let { ZCaption(it, color = colors.danger) }
    for (mode in options.modes) {
        val meta = JobPresentation.modeMeta(mode.key)
        ZSelectableRow(selected = model.form.mode == mode.key, onClick = { model.form = model.form.copy(mode = mode.key); autoAdvance() }, radius = ZRadius.card) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { ZBodyStrong(mode.name); mode.tag?.let { ZBadge(it, meta.tone) } }
                mode.desc?.let { ZCaption(it, tone = ZTextTone.SOFT) }
                mode.eta?.let { Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Schedule, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(14.dp)); ZCaption(it) } }
                ZCaption(model.modeConsequence(mode.key))
            }
        }
    }
}

@Composable
private fun UrgencyStep(model: BookingWizardModel, options: BookingOptions, autoAdvance: () -> Unit) {
    StepHeading("How soon?")
    for ((key, label) in BookingWizardEngine.urgencies) {
        ZSelectableRow(selected = model.form.urgency == key, onClick = { model.setUrgency(key); if (key != "asap") autoAdvance() }) { ZBodyStrong(label, modifier = Modifier.weight(1f)) }
    }
    if (model.form.urgency == "asap" && options.modes.any { it.key == "instant" }) {
        ZCard {
            ZCaption("Need it today? An emergency visit gets a pro moving now instead of waiting on quotes.", tone = ZTextTone.INK)
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                ZButton("Switch to emergency visit", compact = true, fullWidth = false) { model.form = model.form.copy(mode = "instant"); model.next() }
                ZButton("Keep quotes", style = ZButtonStyle.GHOST, compact = true, fullWidth = false) { model.next() }
            }
        }
    }
}

private val dayFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

@Composable
private fun WindowsStep(model: BookingWizardModel, title: String) {
    val colors = ZTheme.colors
    val now = LocalDateTime.now()
    val days = (0 until 14).map { LocalDate.now().plusDays(it.toLong()) }
    LaunchedEffect(model.form.categoryId) { model.probeSupply(days = 14) }
    StepHeading(title, "Pick every window that works — more options means a faster match.")
    model.fieldErrors["availability_windows"]?.let { ZCaption(it, color = colors.danger) }
    for ((i, day) in days.withIndex()) {
        val key = BookingWizardEngine.dateKey(day)
        // Dead day: the supply probe says no matched pro works it.
        val dead = key in model.deadDays
        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            ZLabel(if (i == 0) "Today" else dayFormatter.format(day), modifier = Modifier.width(78.dp))
            for (w in JobPresentation.windows) {
                val k = "$key|$w"
                val on = k in model.form.selectedWindows
                val elapsed = BookingWizardEngine.isWindowElapsed(key, w, now)
                val disabled = elapsed || dead
                Text(
                    JobPresentation.windowShortLabel(w),
                    style = ZType.label.copy(fontSize = 12.sp),
                    color = if (on) colors.onBrand else colors.ink,
                    modifier = Modifier.weight(1f).alpha(if (disabled) 0.35f else 1f).clip(RoundedCornerShape(8.dp)).background(if (on) colors.navy else colors.surface).border(1.dp, if (on) colors.navy else colors.borderStrong, RoundedCornerShape(8.dp))
                        .clickable(enabled = !disabled) { model.toggleWindow(k) }.padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
    if (model.deadDays.isNotEmpty()) ZCaption("Greyed-out days: no matched pro works that day.", tone = ZTextTone.FAINT)
}

@Composable
private fun ReviewStep(model: BookingWizardModel, options: BookingOptions) {
    val form = model.form
    val windows = BookingWizardEngine.windows(form.selectedWindows)
    StepHeading("Review & confirm")
    ZCard {
        ReviewRow(model, "Category", model.category?.name ?: "—", BookingWizardEngine.Step.Category)
        ReviewRow(model, "Timing", JobPresentation.modeMeta(form.mode).label, if (form.modePreset) BookingWizardEngine.Step.Review else BookingWizardEngine.Step.Mode)
        if (windows.isNotEmpty()) ZCaption(windows.joinToString("\n") { JobPresentation.windowSlot(it.date, it.window) })
        if (form.mode == "quote") {
            ReviewRow(model, "Urgency", BookingWizardEngine.urgencies.firstOrNull { it.first == form.urgency }?.second ?: "—", BookingWizardEngine.Step.Urgency)
            if (windows.isEmpty()) ZCaption("Timing flexible — arranged with your pro")
        }
        if (model.area == BookingWizardEngine.Area.BUSINESS) {
            ReviewRow(model, "Property", listOfNotNull(model.properties.firstOrNull { it.id == form.propertyId }?.name, form.unit.ifEmpty { null }).joinToString(" · "), BookingWizardEngine.Step.Where)
            if (form.tenant.isNotEmpty()) ReviewRow(model, "Tenant", form.tenant, BookingWizardEngine.Step.Details)
        } else {
            ReviewRow(model, "Address", (if (form.savedAddressId != null) form.address else BookingWizardEngine.composedAddress(form)) + (if (form.unit.isEmpty()) "" else " · Unit ${form.unit}"), BookingWizardEngine.Step.Where)
        }
        if (form.accessNotes.isNotEmpty()) ReviewRow(model, "Access notes", form.accessNotes, if (model.area == BookingWizardEngine.Area.BUSINESS) BookingWizardEngine.Step.Details else BookingWizardEngine.Step.Access)
        for (q in model.questions.filter { (form.intakeAnswers[it.id] ?: emptyList()).isNotEmpty() }) {
            ReviewRow(model, q.prompt, (form.intakeAnswers[q.id] ?: emptyList()).joinToString(", "), BookingWizardEngine.Step.Question(q.id))
        }
        ReviewRow(model, "Issue", form.issue, BookingWizardEngine.Step.Issue)
        ReviewRow(model, "Photos", "${model.draftPhotos.size} attached", BookingWizardEngine.Step.Photos)
    }
    ZCard {
        ZBodyStrong("Quotes set the price")
        ZCaption("Verified pros send itemized quotes; nothing is held until you accept one. The quote you accept — plus the trust & support fee and applicable taxes — is authorized then, and only charged when the job's done and you close it.")
        if (form.mode == "instant" && model.emergencyFeeCents > 0) ZCaption("Instant dispatch adds a flat ${Money.format(model.emergencyFeeCents)} emergency fee on top of the quote you accept.", tone = ZTextTone.INK)
        val phrase = JobPresentation.warrantyPhrase(options.warrantyDays)
        ZCaption("• ${phrase.replaceFirstChar { it.uppercase() }} on every paid job.\n• Cancel free any time before a pro is committed.\n• Any scope change is approved by you in-app before it bills.")
    }
    ZCaption("By submitting, you agree to ZiVETT's Terms of Service and Privacy Policy.", tone = ZTextTone.FAINT)
}

@Composable
private fun ReviewRow(model: BookingWizardModel, label: String, value: String, step: BookingWizardEngine.Step) {
    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { ZMono(label); ZBody(value) }
        if (step != BookingWizardEngine.Step.Review) ZTextAction("Edit") { model.jump(step) }
    }
}

/// The wizard's in-place property form (mirrors the web's add-property
/// modal): saving selects the new property and wizard progress survives.
@Composable
private fun AddPropertySheet(model: BookingWizardModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    ZSheet(onDismiss = onDismiss, title = "Add a property") {
        ZTextField("Property name", name, { name = it }, placeholder = "Alder Court Apartments", error = errors["name"], capitalization = KeyboardCapitalization.Words)
        ZTextField("Type (optional)", kind, { kind = it }, placeholder = "Apartment building", error = errors["kind"], capitalization = KeyboardCapitalization.Words)
        ZTextField("Address", address, { address = it }, placeholder = "14 Alder Court, Nanaimo, BC V9R 2K1", error = errors["address"], capitalization = KeyboardCapitalization.Words)
        ZButton("Add property", loading = model.creatingProperty, enabled = name.isNotBlank() && address.isNotBlank()) {
            scope.launch {
                val failures = model.addProperty(name, kind, address)
                if (failures != null) errors = failures else onDismiss()
            }
        }
    }
}

@Composable
fun BookingSuccess(job: Job, photosUploaded: Boolean, mode: String, categoryName: String, area: BookingWizardEngine.Area, bookAnother: () -> Unit) {
    val nav = LocalNav.current
    val colors = ZTheme.colors
    val message = when (mode) {
        "instant" -> "Nearby verified pros are quoting right now. Accept one and they're on their way."
        "scheduled" -> "Pros available on your chosen days are reviewing the job. You'll pick from their quotes."
        else -> "Verified pros are reviewing your request. Quotes usually land within 24 hours — we'll notify you."
    }
    val steps = when (mode) {
        "instant" -> listOf("Quotes arrive in minutes", "Accept the one you like", "Your pro heads out — track them live")
        "scheduled" -> listOf("Available pros send quotes", "Accept one to lock in your window", "Your pro arrives in the window you picked")
        else -> listOf("ZiVETT checks every quote before you see it", "Compare and accept the best fit", "Arrange timing with your pro in messages")
    }
    ZScreen {
        Icon(Icons.Filled.Verified, contentDescription = null, tint = colors.brandGold, modifier = Modifier.padding(top = ZSpacing.xl).size(40.dp))
        ZMono("${job.code ?: "Request"} submitted")
        ZDisplay(if (mode == "instant") "Emergency pros are being alerted now" else "Your ${if (area == BookingWizardEngine.Area.BUSINESS) "request" else "job"} is out to ${categoryName.lowercase()} pros near you")
        ZBody(message, tone = ZTextTone.SOFT)
        steps.forEachIndexed { i, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(colors.infoSoft), contentAlignment = Alignment.Center) { Text((i + 1).toString(), style = ZType.monoBody.copy(fontWeight = FontWeight.Bold), color = colors.info) }
                ZBody(step)
            }
        }
        if (!photosUploaded) ZBanner("Your photos didn't upload. Add them from the ${if (area == BookingWizardEngine.Area.BUSINESS) "request" else "job"} page so pros can see the problem.", tone = ZTone.WARNING)
        ZButton(if (area == BookingWizardEngine.Area.BUSINESS) "View request" else "View job") { nav.navigate(JobDetailRoute(job.id, if (area == BookingWizardEngine.Area.BUSINESS) Areas.BUSINESS else Areas.CUSTOMER)) }
        ZButton("Book another", style = ZButtonStyle.OUTLINE, onClick = bookAnother)
        Spacer(Modifier.padding(ZSpacing.lg))
    }
}
