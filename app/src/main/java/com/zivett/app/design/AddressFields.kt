package com.zivett.app.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.zivett.app.core.AddressParts

/// Structured address entry (street / city / province picker / postcode)
/// for a value the API stores as ONE string — the Android twin of the web's
/// `AddressFields.vue`, shared by every form that takes an address. The
/// caller holds the composed string; seeding it (an edit form) splits it
/// back into the fields best-effort via `AddressParts`.
@Composable
fun ZAddressFields(address: String, onAddressChange: (String) -> Unit, error: String? = null) {
    var parts by remember { mutableStateOf(AddressParts.parse(address)) }

    // A parent seeding/resetting the value re-splits the fields; our own
    // compositions round-trip identically, so this never fights typing.
    LaunchedEffect(address) {
        if (address != parts.composed) parts = AddressParts.parse(address)
    }

    fun update(next: AddressParts) {
        parts = next
        onAddressChange(next.composed)
    }

    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
        ZTextField("Street address", parts.line1, { update(parts.copy(line1 = it)) }, placeholder = "14 Alder Court", error = error, capitalization = KeyboardCapitalization.Words)
        ZTextField("City", parts.city, { update(parts.copy(city = it)) }, placeholder = "Nanaimo", capitalization = KeyboardCapitalization.Words)
        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                ZLabel("Province / state")
                ZRegionPicker(parts.region) { update(parts.copy(region = it)) }
            }
            ZTextField("Postcode", parts.postal, { update(parts.copy(postal = it)) }, modifier = Modifier.weight(1f), placeholder = "V9R 2K1", capitalization = KeyboardCapitalization.Characters)
        }
    }
}

/// The grouped region dropdown (Canada first). A stored region that
/// predates the picker still renders instead of silently blanking.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZRegionPicker(value: String, onChange: (String) -> Unit) {
    val colors = ZTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val display = AddressParts.regionGroups.flatMap { it.options }.firstOrNull { it.code == value }?.name ?: value.ifEmpty { "—" }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            textStyle = ZType.body.copy(color = colors.ink),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(ZRadius.field),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.navy, unfocusedBorderColor = colors.borderStrong,
                focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                focusedTextColor = colors.ink, unfocusedTextColor = colors.ink,
            ),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = colors.surface) {
            DropdownMenuItem(text = { Text("—", style = ZType.body) }, onClick = { onChange(""); expanded = false })
            if (value.isNotEmpty() && !AddressParts.isRegionCode(value)) {
                DropdownMenuItem(text = { Text(value, style = ZType.body) }, onClick = { onChange(value); expanded = false })
            }
            for (group in AddressParts.regionGroups) {
                DropdownMenuItem(text = { ZMono(group.label) }, onClick = {}, enabled = false)
                for (option in group.options) {
                    DropdownMenuItem(text = { Text(option.name, style = ZType.body, color = colors.ink) }, onClick = { onChange(option.code); expanded = false })
                }
            }
        }
    }
}

/// A generic labelled dropdown (industry, roles).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZDropdown(label: String, value: String, options: List<Pair<String, String>>, placeholder: String = "Pick one…", error: String? = null, onChange: (String) -> Unit) {
    val colors = ZTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.first == value }?.second ?: placeholder
    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
        if (label.isNotEmpty()) ZLabel(label)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = display,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                isError = error != null,
                textStyle = ZType.body.copy(color = if (value.isEmpty()) colors.inkFaint else colors.ink),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                shape = RoundedCornerShape(ZRadius.field),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.navy, unfocusedBorderColor = colors.borderStrong, errorBorderColor = colors.danger,
                    focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
                    focusedTextColor = colors.ink, unfocusedTextColor = colors.ink,
                ),
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = colors.surface) {
                for ((key, name) in options) {
                    DropdownMenuItem(text = { Text(name, style = ZType.body, color = colors.ink) }, onClick = { onChange(key); expanded = false })
                }
            }
        }
        if (error != null) ZCaption(error, color = colors.danger)
    }
}
