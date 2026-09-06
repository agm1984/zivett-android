package com.zivett.app.features.company

import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.zivett.app.app.Areas
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.OrgProfileRoute
import com.zivett.app.core.Loadable
import com.zivett.app.core.media.PhotoImport
import com.zivett.app.core.models.CompanyDetails
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.CompanySummary
import com.zivett.app.core.models.DetailsBody
import com.zivett.app.core.models.PassportResponse
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZAvatar
import com.zivett.app.design.ZAvatarShape
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZCheckLine
import com.zivett.app.design.ZCheckbox
import com.zivett.app.design.ZColors
import com.zivett.app.design.ZHeroPanel
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMonoLarge
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextArea
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.design.ZType
import com.zivett.app.features.customer.book.BookingWizardEngine
import com.zivett.app.features.shared.LocalNav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class PassportModel(private val client: ApiClient) {
    var state by mutableStateOf<Loadable<PassportResponse>>(Loadable.Loading)
    var toast by mutableStateOf<String?>(null)
    var uploadingKind by mutableStateOf<String?>(null)

    suspend fun load() { state = state.reloaded { client.send(CompanyEndpoints.passport()) } }

    /// The shared upload core — the file picker sends bytes from a
    /// content Uri, the photo-library path sends re-encoded JPEG bytes.
    suspend fun upload(kind: String, fileName: String, mimeType: String, data: ByteArray, expiresAt: String?) {
        uploadingKind = kind
        try {
            client.send(CompanyEndpoints.uploadCredential(kind, fileName, mimeType, data, expiresAt))
            toast = "Document uploaded — our team reviews it as part of your passport"
            load()
        } catch (e: ApiError) { toast = e.first("file") ?: e.first("expires_at") ?: "Could not upload that document. PDF, JPEG, PNG or WebP up to 10 MB." }
        catch (e: Exception) { toast = e.userMessage } finally { uploadingKind = null }
    }

    suspend fun updateDetails(body: DetailsBody): Boolean {
        try { client.send(CompanyEndpoints.updateDetails(body)); toast = "Passport details updated"; load(); return true }
        catch (e: ApiError) { toast = e.first("category_rates") ?: e.userMessage } catch (e: Exception) { toast = e.userMessage }
        return false
    }
}

private val expiringKinds = setOf("license", "insurance", "worksafebc")

/// The Verified Pro Passport: credential rows with uploads, plus the
/// editable service details (trades & rates, radius, availability).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassportScreen(onBack: (() -> Unit)?) {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val model = remember { PassportModel(environment.client) }
    var expiryDates by remember { mutableStateOf<Map<String, LocalDate>>(emptyMap()) }
    var pickingExpiryFor by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<DetailsSection?>(null) }
    var viewing by remember { mutableStateOf<PassportResponse.Passport.Credential?>(null) }
    var pendingKind by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(model) { model.load() }

    fun expiry(kind: String): String? = expiryDates[kind]?.let { BookingWizardEngine.dateKey(it) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val kind = pendingKind ?: return@rememberLauncherForActivityResult
        pendingKind = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (name, mime, bytes) = withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                var name = "document"
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && index >= 0) name = cursor.getString(index) ?: name
                }
                Triple(name, mime, resolver.openInputStream(uri)?.use { it.readBytes() })
            }
            if (bytes == null) { model.toast = "Couldn't read that file."; return@launch }
            model.upload(kind, name, mime, bytes, expiry(kind))
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val kind = pendingKind ?: return@rememberLauncherForActivityResult
        pendingKind = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // 2048 keeps licence numbers and dates readable after the JPEG re-encode.
            val jpeg = PhotoImport.jpegData(context, listOf(uri), 2048).firstOrNull()
            if (jpeg == null) { model.toast = "Could not read that image. Try a different photo."; return@launch }
            model.upload(kind, "$kind.jpg", "image/jpeg", jpeg, expiry(kind))
        }
    }

    Column {
        ZTopBar("Passport", onBack = onBack)
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { response ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        ZHeroPanel {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                ZAvatar(CompanySummary(null, response.passport.name).initials, ZAvatarShape.COMPANY, 48.dp, onDark = true)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(response.passport.name, style = ZType.headline, color = Color.White)
                                        if (response.passport.approved) Icon(Icons.Filled.Verified, contentDescription = "Approved", tint = ZColors.fixedGoldBright)
                                    }
                                    Text("Verified Pro Passport · ${response.passport.number ?: ""}", style = ZType.mono, color = Color.White.copy(alpha = 0.7f))
                                }
                            }
                        }
                        if (!response.passport.approved && response.passport.documents.uploaded < response.passport.documents.total) {
                            ZBanner("${response.passport.documents.uploaded} of ${response.passport.documents.total} documents uploaded — completing your passport speeds up review.", tone = ZTone.WARNING)
                        }
                        val canManage = response.editable?.canManage ?: false
                        for (credential in response.passport.credentials) {
                            ZCard(padding = ZSpacing.sm) {
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        ZBodyStrong(credential.field)
                                        credential.value?.let { ZCaption(it, maxLines = 3) }
                                        credential.rejectionReason?.let { ZCaption(it, color = colors.danger) }
                                        credential.expiresAt?.let { ZCaption("Expires $it", tone = ZTextTone.FAINT) }
                                    }
                                    val badge = CompanyPresentation.credentialBadge(credential.status)
                                    ZBadge(badge.label, badge.tone)
                                }
                                val kind = credential.kind
                                if (kind != null) {
                                    // Teaching copy (the reviewers' rubric) — the purpose line only
                                    // while nothing is uploaded, the checklist always a tap away.
                                    CredentialFieldGuide.guide(kind)?.let { guide ->
                                        if (credential.documentId == null) ZCaption(guide.purpose)
                                        var open by remember(kind) { mutableStateOf(false) }
                                        Row(modifier = Modifier.clickable { open = !open }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            ZCaption("What a good upload shows")
                                            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = colors.inkSoft)
                                        }
                                        if (open) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                for (item in guide.checklist) ZCheckLine(item)
                                                ZCaption(guide.formats, tone = ZTextTone.FAINT)
                                                guide.fallback?.let { ZCaption(it, tone = ZTextTone.FAINT) }
                                            }
                                        }
                                    }
                                    if (kind in expiringKinds) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                                            ZCaption("Document expiry", modifier = Modifier.weight(1f))
                                            ZTextAction(expiryDates[kind]?.toString() ?: "Set expiry date") { pickingExpiryFor = kind }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                        if (model.uploadingKind == kind) {
                                            ZButton("Uploading…", style = ZButtonStyle.OUTLINE, compact = true, loading = true, fullWidth = false) {}
                                        } else {
                                            var menu by remember(kind) { mutableStateOf(false) }
                                            Box {
                                                ZButton(if (credential.documentId == null) "Upload" else "Replace", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { menu = true }
                                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                                    DropdownMenuItem(text = { Text("Photo library") }, onClick = { menu = false; pendingKind = kind; photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                                                    DropdownMenuItem(text = { Text("Choose a file (PDF or image)") }, onClick = { menu = false; pendingKind = kind; filePicker.launch(arrayOf("application/pdf", "image/*")) })
                                                }
                                            }
                                        }
                                        if (credential.documentId != null) ZTextAction("View") { viewing = credential }
                                    }
                                } else if (canManage && DetailsSection.from(credential.key) != null) {
                                    ZButton("Edit", style = ZButtonStyle.GHOST, compact = true, fullWidth = false) { editing = DetailsSection.from(credential.key) }
                                } else if (canManage && credential.key == "business_identity") {
                                    // The identity row edits through the org profile form.
                                    ZTextAction("Edit") { nav.navigate(OrgProfileRoute(Areas.COMPANY)) }
                                }
                            }
                        }
                        ZCaption("License and insurance documents become visible to customers once verified by ZiVETT. Tracked metrics update automatically as you complete jobs.")
                        Spacer(Modifier.padding(ZSpacing.lg))
                    }
                }
            }
        }
    }

    pickingExpiryFor?.let { kind ->
        val state = rememberDatePickerState(initialSelectedDateMillis = (expiryDates[kind] ?: LocalDate.now().plusYears(1)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingExpiryFor = null },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { millis -> expiryDates = expiryDates + (kind to Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) }
                pickingExpiryFor = null
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickingExpiryFor = null }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
    viewing?.let { credential -> credential.documentId?.let { id -> CredentialViewer(id, credential.documentMime, credential.field) { viewing = null } } }
    editing?.let { section -> model.state.value?.editable?.let { details -> DetailsEditorSheet(section, details, onDismiss = { editing = null }) { body -> model.updateDetails(body) } } }
}

/// Full-screen preview of an uploaded credential document — the pro
/// checking what they actually submitted. The bytes come through the
/// authed client, images render inline, PDFs page through PdfRenderer,
/// and "Open with…" hands the file to any other app.
@Composable
fun CredentialViewer(documentId: Int, mime: String?, title: String, onDismiss: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    var file by remember(documentId) { mutableStateOf<File?>(null) }
    var extension by remember(documentId) { mutableStateOf("pdf") }
    var pages by remember(documentId) { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    var failed by remember(documentId) { mutableStateOf(false) }

    suspend fun load() {
        failed = false
        try {
            val data = environment.client.send(CompanyEndpoints.downloadCredential(documentId))
            extension = CredentialFile.fileExtension(mime, data)
            val out = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                File(dir, "credential-$documentId.$extension").also { it.writeBytes(data) }
            }
            file = out
            if (extension == "pdf") pages = withContext(Dispatchers.IO) { renderPdf(out) }
        } catch (_: Exception) { failed = true }
    }
    LaunchedEffect(documentId) { load() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(colors.cream)) {
            ZTopBar(title, onBack = onDismiss, actions = {
                file?.let { f ->
                    ZTextAction("Open with…") {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, if (extension == "pdf") "application/pdf" else "image/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, null))
                    }
                }
            })
            val current = file
            when {
                current != null && CredentialFile.isImage(extension) -> AsyncImage(model = Uri.fromFile(current), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                current != null && pages.isNotEmpty() -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ZSpacing.sm), verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                    for (page in pages) Image(page.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().border(1.dp, colors.border), contentScale = ContentScale.FillWidth)
                }
                failed -> Column(modifier = Modifier.fillMaxSize().padding(ZSpacing.md), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                    ZBodyStrong("Couldn't load this document."); ZCaption("Check your connection and try again.")
                    ZButton("Retry", style = ZButtonStyle.OUTLINE, fullWidth = false) { scope.launch { load() } }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ZSpinner() }
            }
        }
    }
}

private fun renderPdf(file: File): List<android.graphics.Bitmap> = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { page ->
                    val scale = 1080f / page.width
                    val bitmap = android.graphics.Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), android.graphics.Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }
}.getOrDefault(emptyList())

enum class DetailsSection(val title: String) {
    TRADES("Trade categories"), RADIUS("Service radius"), AVAILABILITY("Availability"), ALERT_EMAILS("Job alert emails");

    companion object {
        fun from(key: String?): DetailsSection? = when (key) {
            "trade_categories" -> TRADES
            "service_radius" -> RADIUS
            "availability" -> AVAILABILITY
            "job_alert_emails" -> ALERT_EMAILS
            else -> null
        }
    }
}

/// Edits one section of the service details (trades & rates / radius /
/// availability / alert emails), mirroring `PassportDetailsEditor.vue`.
@Composable
fun DetailsEditorSheet(section: DetailsSection, details: CompanyDetails, onDismiss: () -> Unit, save: suspend (DetailsBody) -> Boolean) {
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    var selected by remember { mutableStateOf(details.selectedCategoryIds.toSet()) }
    var rates by remember { mutableStateOf((details.categoryRates ?: emptyMap()).mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to (v / 100).toString() } }.toMap()) }
    var radius by remember { mutableStateOf((details.serviceRadiusKm ?: 25).toFloat()) }
    var modes by remember { mutableStateOf((details.availabilityModes ?: emptyList()).toSet()) }
    // ISO weekdays 1–7; server null = every day, so an unset org seeds all seven.
    var workingDays by remember { mutableStateOf((details.workingDays ?: (1..7).toList()).toSet()) }
    var alertEmails by remember { mutableStateOf((details.jobAlertEmails ?: emptyList()).joinToString("\n")) }
    var saving by remember { mutableStateOf(false) }
    val rateMissing = section == DetailsSection.TRADES && selected.any { (rates[it]?.toIntOrNull() ?: 0) <= 0 }
    val weekdayLabels = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

    ZSheet(onDismiss = onDismiss, title = section.title) {
        when (section) {
            DetailsSection.TRADES -> {
                ZCaption("The trades you work in, each with your hourly rate — quotes are priced from it.")
                for (option in details.categoryOptions) {
                    ZCheckbox(option.id in selected, { on -> selected = if (on) selected + option.id else selected - option.id }, text = option.name)
                    if (option.id in selected) ZTextField("Rate ($/hr)", rates[option.id] ?: "", { rates = rates + (option.id to it) }, placeholder = "120", keyboardType = KeyboardType.Number)
                }
                if (rateMissing) ZCaption("Set an hourly rate for every selected trade.", color = colors.danger)
            }
            DetailsSection.RADIUS -> {
                ZMonoLarge("${radius.toInt()} km")
                Slider(value = radius, onValueChange = { radius = (it / 5).toInt() * 5f }, valueRange = 5f..200f, steps = 38)
                Row { ZCaption("5 km", modifier = Modifier.weight(1f)); ZCaption("200 km") }
            }
            DetailsSection.AVAILABILITY -> {
                ZCaption("The kinds of bookings you take.")
                for (option in details.modeOptions) ZCheckbox(option.value in modes, { on -> modes = if (on) modes + option.value else modes - option.value }, text = option.label)
                // Working days gate which scheduled jobs reach the feed (Availability::fits).
                ZLabel("Working days")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (day in 1..7) {
                        val on = day in workingDays
                        Text(weekdayLabels[day - 1], style = ZType.label, color = if (on) colors.onBrand else colors.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) colors.navy else colors.surface).border(1.dp, if (on) colors.navy else colors.borderStrong, RoundedCornerShape(10.dp)).clickable { workingDays = if (on) workingDays - day else workingDays + day }.padding(vertical = 10.dp))
                    }
                }
                if (workingDays.isEmpty()) ZCaption("Pick at least one working day — with none, no scheduled job can reach you.", color = colors.danger)
            }
            DetailsSection.ALERT_EMAILS -> {
                ZCaption("Where to email new-job alerts when a booking matches your trades and service area — one address per line. Leave empty to alert every active team member instead.")
                ZTextArea("", alertEmails, { alertEmails = it }, minLines = 4)
                @Suppress("unused") val mono = FontFamily.Monospace
            }
        }
        ZButton("Save", loading = saving, enabled = !rateMissing && !(section == DetailsSection.AVAILABILITY && workingDays.isEmpty())) {
            scope.launch {
                saving = true
                val body = when (section) {
                    DetailsSection.TRADES -> DetailsBody(categoryIds = selected.sorted(), categoryRates = selected.associate { it.toString() to (rates[it]?.toIntOrNull() ?: 0) * 100 })
                    DetailsSection.RADIUS -> DetailsBody(serviceRadiusKm = radius.toInt())
                    DetailsSection.AVAILABILITY -> DetailsBody(availabilityModes = modes.sorted(), workingDays = workingDays.sorted())
                    DetailsSection.ALERT_EMAILS -> DetailsBody(jobAlertEmails = alertEmails.split("\n").map { it.trim() }.filter { it.isNotEmpty() })
                }
                val ok = save(body)
                saving = false
                if (ok) onDismiss()
            }
        }
    }
}
