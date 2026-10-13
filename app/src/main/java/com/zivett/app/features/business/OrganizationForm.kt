package com.zivett.app.features.business

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zivett.app.app.Areas
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.AddressLookup
import com.zivett.app.core.AddressParts
import com.zivett.app.core.Loadable
import com.zivett.app.core.media.PhotoImport
import com.zivett.app.core.models.BusinessEndpoints
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.IndustryOption
import com.zivett.app.core.models.OrganizationProfile
import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZAvatar
import com.zivett.app.design.ZAvatarShape
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZDropdown
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLinkButton
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZPhotoAvatar
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextArea
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTopBar
import kotlinx.coroutines.launch

/// The organization profile form (`BusinessProfileForm.vue`) shared by
/// business (no GST field) and company (GST on). Admin-only writes; the
/// server enforces, we also hide the save for members.
class OrganizationFormModel(private val client: ApiClient, val company: Boolean) {
    var state by mutableStateOf<Loadable<OrganizationProfile>>(Loadable.Loading)
    var fields by mutableStateOf<Map<String, String>>(emptyMap())
    var saving by mutableStateOf(false)
    var fieldErrors by mutableStateOf<Map<String, String>>(emptyMap())
    var toast by mutableStateOf<String?>(null)
    /// The industry picker's choices (business only, server-owned).
    var industryOptions by mutableStateOf<List<IndustryOption>>(emptyList())
    var logoBusy by mutableStateOf(false)

    /// Website, socials and the bio are optional extras — folded behind one
    /// toggle so the first screen is only what's needed (field audit #7:
    /// 14 fields up front lost pros signing up from a phone). Open whenever
    /// any of them has a value, or the server rejected one.
    var extrasOpen by mutableStateOf(false)

    companion object {
        val extraKeys = listOf("website", "social_x", "social_instagram", "social_facebook", "about")

        /// The no-logo placeholder: the business's initials (first letter of
        /// the first two words) — the monogram the invoice letterhead uses.
        fun initials(name: String): String =
            name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "—" }

        val fieldKeys = listOf("name", "address_line1", "address_line2", "city", "region", "postal_code", "phone", "website", "social_x", "social_instagram", "social_facebook", "gst_number", "about", "industry")
    }

    fun field(key: String): String = fields[key] ?: ""
    fun set(key: String, value: String) { fields = fields + (key to value) }

    /// Set once the street lookup filled something — the form says so.
    var addressAutoFilled by mutableStateOf(false)

    /// Finishing the street line fills the blank city / province /
    /// postcode — typed values always win (`AddressLookup`).
    suspend fun lookUpStreet() {
        fun typed() = AddressParts(field("address_line1"), field("city"), field("region"), field("postal_code"))
        AddressLookup.fill(client, typed(), ::typed)?.let {
            fields = fields + mapOf("city" to it.city, "region" to it.region, "postal_code" to it.postal)
            addressAutoFilled = true
        }
    }

    suspend fun load() {
        state = state.reloaded {
            if (company) client.send(CompanyEndpoints.organization()).organization
            else client.send(BusinessEndpoints.organization()).also { industryOptions = it.industryOptions ?: emptyList() }.organization
        }
        seed()
    }

    private fun seed() {
        val org = state.value ?: return
        fields = mapOf(
            "name" to org.name, "address_line1" to (org.addressLine1 ?: ""), "address_line2" to (org.addressLine2 ?: ""),
            "city" to (org.city ?: ""), "region" to (org.region ?: ""), "postal_code" to (org.postalCode ?: ""),
            "phone" to (org.phone ?: ""), "website" to (org.website ?: "").replace("https://", ""),
            // The @ prefix is chrome, not data — fields hold bare handles.
            "social_x" to (org.socialX ?: "").replace("@", ""), "social_instagram" to (org.socialInstagram ?: "").replace("@", ""),
            "social_facebook" to (org.socialFacebook ?: ""), "gst_number" to (org.gstNumber ?: ""), "about" to (org.about ?: ""), "industry" to (org.industry ?: ""),
        )
        if (extraKeys.any { field(it).isNotBlank() }) extrasOpen = true
    }

    suspend fun save() {
        if (saving) return
        saving = true; fieldErrors = emptyMap()
        val body = mutableMapOf<String, String?>()
        for (key in fieldKeys) {
            if (!company && key == "gst_number") continue
            // Industry is the business org's pick; a company's trades are its identity.
            if (company && key == "industry") continue
            var value = field(key).trim()
            if (key == "social_x" || key == "social_instagram") value = value.replace("@", "")
            body[key] = if (key == "website") (if (value.isEmpty()) null else "https://" + value.replace("https://", "").replace("http://", ""))
            else if (value.isEmpty()) (if (key == "name") value else null) else value
        }
        try {
            state = Loadable.Loaded(if (company) client.send(CompanyEndpoints.updateOrganization(body)).organization else client.send(BusinessEndpoints.updateOrganization(body)).organization)
            seed()
            toast = "Business profile saved"
        } catch (e: ApiError) {
            if (e is ApiError.Validation) {
                fieldErrors = e.errors.firstMessages
                // A rejected extra (a malformed website) must be visible to fix.
                if (extraKeys.any { it in fieldErrors }) extrasOpen = true
            } else toast = e.userMessage
        } catch (e: Exception) { toast = e.userMessage } finally { saving = false }
    }

    /* The org logo (rounded square everywhere, per the brand's avatar convention) — admin-only. */

    suspend fun uploadLogo(jpeg: ByteArray?) {
        logoBusy = true
        try {
            if (jpeg == null) { toast = "Could not read that image. Try a different file."; return }
            val response = if (company) client.send(CompanyEndpoints.uploadLogo(jpeg)) else client.send(BusinessEndpoints.uploadLogo(jpeg))
            state.value?.let { state = Loadable.Loaded(it.copy(logoUrl = response.logoUrl)) }
            toast = "Logo saved"
        } catch (e: ApiError) { toast = e.first("file") ?: e.userMessage } catch (e: Exception) { toast = e.userMessage } finally { logoBusy = false }
    }

    suspend fun removeLogo() {
        logoBusy = true
        try {
            if (company) client.send(CompanyEndpoints.deleteLogo()) else client.send(BusinessEndpoints.deleteLogo())
            state.value?.let { state = Loadable.Loaded(it.copy(logoUrl = null)) }
        } catch (e: Exception) { toast = e.userMessage } finally { logoBusy = false }
    }
}

@Composable
fun OrganizationFormScreen(area: String, onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val company = area == Areas.COMPANY
    val model = remember(company) { OrganizationFormModel(environment.client, company) }
    val canManage = environment.session.user?.organizationRole == OrganizationRole.ADMIN
    LaunchedEffect(model) { model.load() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { model.uploadLogo(PhotoImport.jpegData(context, listOf(uri), 1024).firstOrNull()) }
    }

    @Composable
    fun Field(key: String, label: String, placeholder: String, keyboard: KeyboardType = KeyboardType.Text, prefixHint: String? = null, modifier: Modifier = Modifier, onFocusLost: (() -> Unit)? = null) {
        ZTextField(label, model.field(key), { model.set(key, it) }, modifier = modifier, placeholder = placeholder, error = model.fieldErrors[key], keyboardType = keyboard, capitalization = KeyboardCapitalization.Words,
            corner = prefixHint?.let { { ZCaption(it, tone = ZTextTone.FAINT) } }, onFocusLost = onFocusLost)
    }

    Column {
        ZTopBar("Business profile", onBack = onBack)
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { org ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        ZPageTitle("Business profile", "This is the business ${if (company) "customers see across ZiVETT — on quotes, jobs, and your Verified Pro Passport." else "pros see on each request."}")
                        // Logo: rounded square (brand avatar convention).
                        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            // No logo yet → the initials monogram, so skipping it never looks broken.
                            val initials = OrganizationFormModel.initials(model.field("name").ifBlank { org.name })
                            if (org.logoUrl != null) ZPhotoAvatar(org.logoUrl, initials, 56.dp, ZAvatarShape.COMPANY) else ZAvatar(initials, ZAvatarShape.COMPANY, 56.dp, onDark = true)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                // Optional for companies too since 2026-10-12 (server no longer lists it missing).
                                ZLabel(if (company) "Logo (optional — add it any time)" else "Logo (optional)")
                                ZCaption(if (company) "Shown on your quotes and invoices." else "Shown to pros on your requests.")
                            }
                            if (canManage) {
                                if (model.logoBusy) ZSpinner(size = 18.dp) else Column(horizontalAlignment = Alignment.End) {
                                    ZLinkButton(if (org.logoUrl == null) "Add" else "Change") { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                    if (org.logoUrl != null) ZTextAction("Remove", color = colors.danger) { scope.launch { model.removeLogo() } }
                                }
                            }
                        }
                        Field("name", "Business name", "Ravensworth Plumbing")
                        Field("address_line1", "Address", "14 Alder Court", onFocusLost = { scope.launch { model.lookUpStreet() } })
                        if (model.addressAutoFilled) ZCaption(AddressLookup.filledNotice)
                        Field("address_line2", "Address line 2", "")
                        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                            Field("city", "City", "Nanaimo", modifier = Modifier.weight(1.4f))
                            Field("region", "Province", "BC", modifier = Modifier.weight(0.8f))
                            Field("postal_code", "Postal", "V9R 2K1", modifier = Modifier.weight(1f))
                        }
                        Field("phone", "Phone", "(555) 012-3456", keyboard = KeyboardType.Phone)
                        if (company) {
                            Field("gst_number", "GST number (if registered)", "123456789RT0001")
                            ZCaption("If your business is GST-registered, your invoices will add 5% GST and show this number. Leave blank if you're a small supplier.")
                        } else {
                            // Businesses declare their industry (required — the server refuses a save without one).
                            ZDropdown("Industry", model.field("industry"), model.industryOptions.map { it.value to it.label }, placeholder = "Pick your industry…", error = model.fieldErrors["industry"]) { model.set("industry", it) }
                            ZCaption("What kind of business you run — it helps us route the right pros and support to you.")
                        }
                        // Optional extras, folded away until asked for.
                        if (model.extrasOpen) {
                            Field("website", "Website (optional)", "www.example.com", prefixHint = "https://")
                            // Bare handles behind a fixed @ prefix, like the web form.
                            Field("social_x", "X / Twitter (optional)", "yourbusiness", prefixHint = "@")
                            Field("social_instagram", "Instagram (optional)", "yourbusiness", prefixHint = "@")
                            Field("social_facebook", "Facebook (optional)", "facebook.com/yourbusiness")
                            ZTextArea("About your business (optional)", model.field("about"), { model.set("about", it) }, placeholder = "What you do, how long you've been at it…", error = model.fieldErrors["about"])
                        } else {
                            ZTextAction("+ Add your website, social profiles and a short bio (optional)") { model.extrasOpen = true }
                        }
                        if (canManage) ZButton("Save changes", loading = model.saving) { scope.launch { model.save() } }
                        else ZCaption("Only organization admins can change the business profile.")
                        Spacer(Modifier.padding(ZSpacing.lg))
                    }
                }
            }
        }
    }
}
