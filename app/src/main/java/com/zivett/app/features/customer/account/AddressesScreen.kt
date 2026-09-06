package com.zivett.app.features.customer.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.AddressBody
import com.zivett.app.core.models.CustomerAddress
import com.zivett.app.core.models.CustomerEndpoints
import com.zivett.app.core.models.Geocode
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
import com.zivett.app.design.ZCheckbox
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZLabel
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
import com.zivett.app.design.ZType
import com.zivett.app.features.shared.PinPickerMap
import kotlinx.coroutines.launch

class AddressesModel(private val client: ApiClient) {
    var state by mutableStateOf<Loadable<List<CustomerAddress>>>(Loadable.Loading)
    var error by mutableStateOf<String?>(null)

    suspend fun load() { state = state.reloaded { client.send(CustomerEndpoints.addresses()).addresses } }

    /// The authed, throttled geocode (`POST /api/geocode`) behind the
    /// form's "Locate on map".
    suspend fun geocode(address: String): Geocode = client.send(CustomerEndpoints.geocode(address))

    suspend fun save(body: AddressBody, editing: Int?) {
        if (editing != null) client.send(CustomerEndpoints.updateAddress(editing, body)) else client.send(CustomerEndpoints.createAddress(body))
        load()
    }

    suspend fun delete(address: CustomerAddress) {
        try { client.send(CustomerEndpoints.deleteAddress(address.id)); load() } catch (e: Exception) { error = e.userMessage }
    }

    suspend fun makeDefault(address: CustomerAddress) {
        try { save(AddressBody(address.label, address.address, address.accessNotes, isDefault = true), address.id) } catch (e: Exception) { error = e.userMessage }
    }
}

@Composable
fun AddressesScreen(onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember { AddressesModel(environment.client) }
    var editing by remember { mutableStateOf<CustomerAddress?>(null) }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(model) { model.load() }

    Column {
        ZTopBar("Addresses", onBack = onBack)
        ZToastBox(model.error, { model.error = null }) {
            ZScreen {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { addresses ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        ZPageTitle("Saved addresses", "Book in one tap — your default is preselected.")
                        if (addresses.isEmpty()) ZEmptyState(Icons.Outlined.Home, "No saved places yet", "Add your home so every booking starts filled in.")
                        for (address in addresses) {
                            ZCard {
                                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.Top) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            ZBodyStrong(address.label)
                                            if (address.isDefault) ZBadge("Default", ZTone.INFO)
                                        }
                                        ZCaption(address.address, tone = ZTextTone.SOFT)
                                        address.accessNotes?.takeIf { it.isNotEmpty() }?.let { ZCaption(it) }
                                    }
                                    var menu by remember { mutableStateOf(false) }
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = ZTheme.colors.inkMuted) }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; editing = address })
                                        if (!address.isDefault) DropdownMenuItem(text = { Text("Make default") }, onClick = { menu = false; scope.launch { model.makeDefault(address) } })
                                        DropdownMenuItem(text = { Text("Delete", color = ZTheme.colors.danger) }, onClick = { menu = false; scope.launch { model.delete(address) } })
                                    }
                                }
                            }
                        }
                        ZButton("Add an address", style = ZButtonStyle.OUTLINE) { adding = true }
                        Spacer(Modifier.padding(ZSpacing.lg))
                    }
                }
            }
        }
    }

    if (adding) AddressFormSheet(model, null) { adding = false }
    editing?.let { address -> AddressFormSheet(model, address) { editing = null } }
}

@Composable
fun AddressFormSheet(model: AddressesModel, existing: CustomerAddress?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var notes by remember { mutableStateOf(existing?.accessNotes ?: "") }
    var isDefault by remember { mutableStateOf(existing?.isDefault ?: false) }
    var saving by remember { mutableStateOf(false) }
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    // The map pin (web's AddressPin): the typed address is for humans,
    // the pin for navigation. Tap the map to adjust; a stored pin
    // always beats a re-geocode server-side.
    var pin by remember { mutableStateOf(if (existing?.lat != null && existing.lng != null) existing.lat to existing.lng else null) }
    var pinAdjusted by remember { mutableStateOf(existing?.pinAdjustedAt != null) }
    var geocoding by remember { mutableStateOf(false) }

    ZSheet(onDismiss = onDismiss, title = if (existing == null) "New address" else "Edit address") {
        error?.let { ZBanner(it, tone = ZTone.DANGER) }
        ZTextField("Label", label, { label = it }, placeholder = "Home", error = fieldErrors["label"], capitalization = KeyboardCapitalization.Words)
        ZAddressFields(address, { address = it }, error = fieldErrors["address"])
        ZTextField("Access notes", notes, { notes = it }, placeholder = "Gate code, parking, pets…", error = fieldErrors["access_notes"], capitalization = KeyboardCapitalization.Sentences)

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
            val current = pin
            if (current != null) {
                PinPickerMap(current.first, current.second, label, modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(ZRadius.tile))) { lat, lng -> pin = lat to lng; pinAdjusted = true }
                ZCaption("Tap the map to move the pin exactly where the pro should arrive.", tone = ZTextTone.FAINT)
            } else {
                ZCaption("Locate the address so pros navigate to the right spot.", tone = ZTextTone.FAINT)
            }
        }

        ZCheckbox(isDefault, { isDefault = it }, text = "Use as my default address")
        ZButton(if (existing == null) "Save address" else "Save changes", loading = saving, enabled = label.isNotEmpty() && address.isNotEmpty()) {
            scope.launch {
                saving = true; fieldErrors = emptyMap(); error = null
                try {
                    model.save(AddressBody(label, address, notes.ifEmpty { null }, isDefault, pin?.first, pin?.second, if (pin == null) null else pinAdjusted), existing?.id)
                    onDismiss()
                } catch (apiError: ApiError) {
                    if (apiError is ApiError.Validation) fieldErrors = apiError.errors.firstMessages else error = apiError.userMessage
                } catch (e: Exception) {
                    error = e.userMessage
                } finally {
                    saving = false
                }
            }
        }
    }
    @Suppress("unused") val keep = ZType.body to colors
}
