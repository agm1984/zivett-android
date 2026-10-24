package com.zivett.app.features.business

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.zivett.app.app.Areas
import com.zivett.app.app.BookRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.media.PhotoImport
import com.zivett.app.core.models.BusinessEndpoints
import com.zivett.app.core.models.CustomerEndpoints
import com.zivett.app.core.models.Geocode
import com.zivett.app.core.models.Property
import com.zivett.app.core.models.PropertyBody
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZAddressFields
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLinkButton
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZRadius
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.features.shared.LocalNav
import com.zivett.app.features.shared.PinPickerMap
import kotlinx.coroutines.launch

class PropertiesModel(val client: ApiClient) {
    var state by mutableStateOf<Loadable<List<Property>>>(Loadable.Loading)
    var toast by mutableStateOf<String?>(null)

    suspend fun load() { state = state.reloaded { client.send(BusinessEndpoints.properties()).properties } }

    /// The authed, throttled geocode behind the pin editor.
    suspend fun geocode(address: String): Geocode = client.send(CustomerEndpoints.geocode(address))

    /// The photo change staged in the sheet, applied after the save.
    sealed interface PhotoChange {
        data object Keep : PhotoChange
        data class Replace(val jpeg: ByteArray) : PhotoChange
        data object Remove : PhotoChange
    }

    suspend fun save(body: PropertyBody, editing: Int?, photo: PhotoChange = PhotoChange.Keep) {
        val saved = if (editing != null) client.send(BusinessEndpoints.updateProperty(editing, body)).property else client.send(BusinessEndpoints.createProperty(body)).property
        toast = if (editing == null) "${body.name} added to your portfolio" else "${body.name} updated"
        // The property is saved either way; a refused photo only toasts.
        try {
            when (photo) {
                PhotoChange.Keep -> Unit
                is PhotoChange.Replace -> client.send(BusinessEndpoints.uploadPropertyPhoto(saved.id, photo.jpeg))
                PhotoChange.Remove -> client.send(BusinessEndpoints.deletePropertyPhoto(saved.id))
            }
        } catch (e: Exception) { toast = e.userMessage }
        load()
    }

    suspend fun delete(property: Property) {
        try { client.send(BusinessEndpoints.deleteProperty(property.id)); toast = "${property.name} removed"; load() } catch (e: Exception) { toast = e.userMessage }
    }
}

@Composable
fun PropertiesScreen(onBack: (() -> Unit)?) {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val model = remember { PropertiesModel(environment.client) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Property?>(null) }
    LaunchedEffect(model) { model.load() }

    Column {
        if (onBack != null) ZTopBar("Properties", onBack = onBack)
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { properties ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        ZPageTitle("Properties", "Requests are booked against a property so history stays organized.")
                        if (properties.isEmpty()) ZEmptyState(Icons.Outlined.Apartment, "No properties yet", "Add your first property to start booking requests against it.")
                        for (property in properties) {
                            ZCard {
                                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.Top) {
                                    PropertyThumb(property, size = 56.dp)
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        ZBodyStrong(property.name)
                                        ZCaption(listOfNotNull(property.kind, property.address).joinToString(" · "))
                                    }
                                    property.openJobs?.takeIf { it > 0 }?.let { ZBadge("$it open", ZTone.DANGER) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                                    ZButton("Request job", compact = true, fullWidth = false) { nav.navigate(BookRoute(Areas.BUSINESS, presetPropertyId = property.id)) }
                                    ZButton("Edit", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { editing = property }
                                    ZButton("Delete", style = ZButtonStyle.GHOST, compact = true, fullWidth = false) { scope.launch { model.delete(property) } }
                                }
                            }
                        }
                        ZButton("Add a property", style = ZButtonStyle.OUTLINE) { adding = true }
                        Spacer(Modifier.padding(ZSpacing.lg))
                    }
                }
            }
        }
    }

    if (adding) PropertyFormSheet(model, null) { adding = false }
    editing?.let { property -> PropertyFormSheet(model, property) { editing = null } }
}

@Composable
fun PropertyFormSheet(model: PropertiesModel, existing: Property?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var saving by remember { mutableStateOf(false) }
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    // The map pin: the typed address is for humans, the pin for
    // navigation — an edit must round-trip it, never drop it.
    var pin by remember { mutableStateOf(if (existing?.lat != null && existing.lng != null) existing.lat to existing.lng else null) }
    var pinAdjusted by remember { mutableStateOf(existing?.pinAdjustedAt != null) }
    var geocoding by remember { mutableStateOf(false) }
    // The photo is staged like every other field and applied on save.
    val context = LocalContext.current
    var photoJpeg by remember { mutableStateOf<ByteArray?>(null) }
    var removePhoto by remember { mutableStateOf(false) }
    var preparingPhoto by remember { mutableStateOf(false) }
    val shownPhotoUrl = if (removePhoto) null else existing?.photoUrl
    val hasPhoto = photoJpeg != null || shownPhotoUrl != null
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            preparingPhoto = true
            val jpeg = PhotoImport.jpegData(context, listOf(uri), 1024).firstOrNull()
            preparingPhoto = false
            if (jpeg != null) photoJpeg = jpeg else error = "Couldn't read that photo — try another."
        }
    }

    ZSheet(onDismiss = onDismiss, title = if (existing == null) "New property" else "Edit property") {
        error?.let { ZBanner(it, tone = ZTone.DANGER) }
        ZTextField("Property name", name, { name = it }, placeholder = "Alder Court", error = fieldErrors["name"], capitalization = KeyboardCapitalization.Words)
        ZTextField("Property type", kind, { kind = it }, placeholder = "12-unit residential", error = fieldErrors["kind"], capitalization = KeyboardCapitalization.Sentences)
        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            PropertyThumb(name, shownPhotoUrl, preview = photoJpeg, size = 64.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ZLabel("Photo (optional)")
                ZCaption("Helps your team spot the property at a glance.")
            }
            if (preparingPhoto) ZSpinner(size = 18.dp) else Column(horizontalAlignment = Alignment.End) {
                ZLinkButton(if (hasPhoto) "Change" else "Add") { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                if (hasPhoto) ZTextAction("Remove", color = ZTheme.colors.danger) { if (photoJpeg != null) photoJpeg = null else removePhoto = true }
            }
        }
        ZAddressFields(address, { address = it }, error = fieldErrors["address"], lookupClient = model.client)
        Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                ZLabel("Map pin")
                if (pinAdjusted) ZBadge("Adjusted", ZTone.INFO)
                Spacer(Modifier.weight(1f))
                if (geocoding) ZSpinner(size = 18.dp) else ZTextAction(if (pin == null) "Locate on map" else "Re-locate", enabled = address.isNotEmpty()) {
                    scope.launch {
                        geocoding = true
                        val result = runCatching { model.geocode(address) }.getOrNull()
                        geocoding = false
                        if (result?.lat != null && result.lng != null) { pin = result.lat to result.lng; pinAdjusted = false }
                        else error = "Couldn't locate that address — check the spelling, or save without a pin."
                    }
                }
            }
            pin?.let { current ->
                PinPickerMap(current.first, current.second, name, modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(ZRadius.tile))) { lat, lng -> pin = lat to lng; pinAdjusted = true }
                ZCaption("Tap the map to move the pin exactly where pros should arrive.", tone = ZTextTone.FAINT)
            }
        }
        ZButton(if (existing == null) "Add property" else "Save changes", loading = saving, enabled = name.isNotEmpty() && address.isNotEmpty() && !preparingPhoto) {
            scope.launch {
                saving = true; fieldErrors = emptyMap(); error = null
                try {
                    val photo = photoJpeg?.let { PropertiesModel.PhotoChange.Replace(it) }
                        ?: if (removePhoto && existing?.photoUrl != null) PropertiesModel.PhotoChange.Remove else PropertiesModel.PhotoChange.Keep
                    model.save(PropertyBody(name, kind.ifEmpty { null }, address, pin?.first, pin?.second, if (pin == null) null else pinAdjusted), existing?.id, photo)
                    onDismiss()
                } catch (apiError: ApiError) {
                    if (apiError is ApiError.Validation) fieldErrors = apiError.errors.firstMessages else error = apiError.userMessage
                } catch (e: Exception) { error = e.userMessage } finally { saving = false }
            }
        }
    }
}
