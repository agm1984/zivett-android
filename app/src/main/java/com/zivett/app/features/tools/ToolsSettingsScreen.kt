package com.zivett.app.features.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Money
import com.zivett.app.core.models.LinePreset
import com.zivett.app.core.models.LinePresetEndpoints
import com.zivett.app.core.models.ToolsSettings
import com.zivett.app.core.models.ToolsSettingsEndpoints
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZErrorState
import com.zivett.app.design.ZFlowRow
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSegmented
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ToolsSettingsLogic {
    /// The colours the web offers for the company's own emails.
    val presets = listOf("#16181d", "#1f4e8c", "#1f6f5c", "#2f7d32", "#b4551d", "#a32638", "#6b3fa0", "#c79a1e")
    const val DEFAULT_COLOR = "#16181d"

    fun validColor(text: String) = Regex("^#[0-9a-fA-F]{6}$").matches(text)
    fun color(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.DarkGray)

    /// "INV-0012" from a prefix and the next number (web numbering example).
    fun example(prefix: String, next: Int?): String = prefix + (next ?: 1).toString().padStart(4, '0')
}

class ToolsSettingsModel(private val client: ApiClient) {
    var settings by mutableStateOf<ToolsSettings?>(null)
    var loadError by mutableStateOf<String?>(null)
    var presets by mutableStateOf<List<LinePreset>>(emptyList())
    var saving by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)
    var errors by mutableStateOf<Map<String, String>>(emptyMap())

    suspend fun load() {
        try {
            settings = client.send(ToolsSettingsEndpoints.show()).settings
            presets = runCatching { client.send(LinePresetEndpoints.list()).presets }.getOrDefault(emptyList())
            loadError = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) { loadError = e.userMessage }
    }

    suspend fun save(key: String, body: kotlinx.serialization.json.JsonObject) {
        saving = key; errors = emptyMap()
        try { settings = client.send(ToolsSettingsEndpoints.update(body)).settings; toast = "Saved." }
        catch (e: CancellationException) { throw e } catch (e: ApiError.Validation) { errors = e.errors.firstMessages; toast = e.errors.message }
        catch (e: Exception) { toast = e.userMessage } finally { saving = null }
    }

    suspend fun deletePreset(id: Int) {
        try { client.send(LinePresetEndpoints.delete(id)); presets = presets.filter { it.id != id }; toast = "Preset deleted." }
        catch (e: CancellationException) { throw e } catch (e: Exception) { toast = e.userMessage }
    }
}

/// Business tools settings (web: the invoice settings modals, the email
/// branding card and line presets): how customers pay you, invoice
/// numbers, GST number, the colour your emails wear, and saved line sets.
/// Org admins change them; everyone else reads.
@Composable
fun ToolsSettingsScreen(nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember { ToolsSettingsModel(environment.client) }
    val isAdmin = environment.session.user?.organizationRole == com.zivett.app.core.models.OrganizationRole.ADMIN
    LaunchedEffect(model) { model.load() }

    Column {
        ZTopBar("Business tools settings", onBack = { nav.back() })
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                ZPageTitle("Settings", "How your own quotes, invoices and emails look and read.")
                val settings = model.settings
                when {
                    model.loadError != null -> ZErrorState(model.loadError.orEmpty()) { scope.launch { model.load() } }
                    settings == null -> ZSpinner()
                    else -> {
                        if (!isAdmin) ZBanner("Only a company admin can change these.", tone = ZTone.INFO)
                        PaymentCard(settings, model, isAdmin)
                        NumberingCard(settings, model, isAdmin)
                        GstCard(settings, model, isAdmin)
                        BrandCard(settings, model, isAdmin)
                        ZCard {
                            ZHeadline("Line presets")
                            ZCaption("Sets of lines you add to quotes and invoices in one tap. Save one from the Presets link while you write a quote.")
                            if (model.presets.isEmpty()) ZCaption("None saved yet.")
                            for (preset in model.presets) {
                                ZDivider()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { ZBodyStrong(preset.name); ZCaption("${ToolsCopy.plural(preset.lineItems.size, "line")} · ${Money.format(preset.totalCents)}") }
                                    ZTextAction("Delete", color = ZTheme.colors.inkSoft) { scope.launch { model.deletePreset(preset.id) } }
                                }
                            }
                        }
                        ZCaption("Automatic reminders are switched on and off from the Overview.")
                    }
                }
                Spacer(Modifier.padding(ZSpacing.md))
            }
        }
    }
}

@Composable
private fun PaymentCard(settings: ToolsSettings, model: ToolsSettingsModel, isAdmin: Boolean) {
    val scope = rememberCoroutineScope()
    var text by remember(settings.paymentInstructions) { mutableStateOf(settings.paymentInstructions.orEmpty()) }
    ZCard {
        ZHeadline("How customers pay you")
        ZCaption("Printed on your invoices.", tone = ZTextTone.SOFT)
        if (isAdmin) {
            ZTextArea("", text, { text = it.take(1000) }, placeholder = "e-Transfer to pay@yourbusiness.ca, or cheque to 1 Wharf St.", error = model.errors["payment_instructions"], minLines = 3)
            if (text != settings.paymentInstructions.orEmpty()) ZButton("Save", style = ZButtonStyle.OUTLINE, loading = model.saving == "payment") {
                scope.launch { model.save("payment", buildJsonObject { put("payment_instructions", text.trim().ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull) }) }
            }
        } else ZBody(settings.paymentInstructions ?: "Nothing yet, so your invoices don't say how to pay you.")
    }
}

@Composable
private fun NumberingCard(settings: ToolsSettings, model: ToolsSettingsModel, isAdmin: Boolean) {
    val scope = rememberCoroutineScope()
    val numbering = settings.invoiceNumbering
    var mode by remember(numbering) { mutableStateOf(numbering.mode) }
    var prefix by remember(numbering) { mutableStateOf(numbering.prefix ?: "INV-") }
    var next by remember(numbering) { mutableStateOf((numbering.nextNumber ?: 1).toString()) }
    ZCard {
        ZHeadline("Invoice numbers")
        if (numbering.locked || !isAdmin) {
            ZBody(if (numbering.mode == "series") "Your own series, next ${numbering.example ?: ""}." else "Each invoice gets a code, like INV-7WQK2M.")
            if (numbering.locked) ZCaption("Locked when you issued your first invoice.")
        } else {
            ZSegmented(listOf("code" to "A code", "series" to "My own series"), mode) { mode = it }
            if (mode == "series") {
                ZTextField("Prefix", prefix, { prefix = it.take(12) }, error = model.errors["invoice_numbering.prefix"])
                ZTextField("Next number", next, { next = it.filter(Char::isDigit) }, keyboardType = KeyboardType.Number, error = model.errors["invoice_numbering.next_number"])
                ZCaption("Your next invoice: ${ToolsSettingsLogic.example(prefix, next.toIntOrNull())}")
            } else ZCaption("Each invoice gets a code, like INV-7WQK2M.")
            ZCaption("You can change this until you issue your first invoice.")
            ZButton("Save", style = ZButtonStyle.OUTLINE, loading = model.saving == "numbering") {
                scope.launch {
                    model.save("numbering", buildJsonObject {
                        put("invoice_numbering", buildJsonObject {
                            put("mode", mode)
                            if (mode == "series") { put("prefix", prefix); put("next_number", next.toIntOrNull() ?: 1) }
                        })
                    })
                }
            }
        }
    }
}

@Composable
private fun GstCard(settings: ToolsSettings, model: ToolsSettingsModel, isAdmin: Boolean) {
    val scope = rememberCoroutineScope()
    var gst by remember(settings.gstNumber) { mutableStateOf(settings.gstNumber.orEmpty()) }
    ZCard {
        ZHeadline("GST number")
        ZCaption("Needed to charge GST on your own quotes and invoices.", tone = ZTextTone.SOFT)
        if (isAdmin) {
            ZTextField("GST number", gst, { gst = it.take(20) }, placeholder = "123456789RT0001", error = model.errors["gst_number"])
            if (gst != settings.gstNumber.orEmpty()) ZButton("Save", style = ZButtonStyle.OUTLINE, loading = model.saving == "gst") {
                scope.launch { model.save("gst", buildJsonObject { put("gst_number", gst.trim().ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull) }) }
            }
        } else ZBody(settings.gstNumber ?: "Not set")
    }
}

@Composable
private fun BrandCard(settings: ToolsSettings, model: ToolsSettingsModel, isAdmin: Boolean) {
    val scope = rememberCoroutineScope()
    val saved = settings.brandColor ?: ToolsSettingsLogic.DEFAULT_COLOR
    var color by remember(saved) { mutableStateOf(saved) }
    ZCard {
        ZHeadline("Your email colour")
        ZCaption("The colour your quotes, invoices, reminders and campaigns wear, with your logo and name.", tone = ZTextTone.SOFT)
        ZFlowRow(spacing = 10.dp) {
            for (hex in ToolsSettingsLogic.presets) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(ToolsSettingsLogic.color(hex))
                        .border(if (hex.equals(color, true)) 3.dp else 0.dp, ZTheme.colors.brandGold, CircleShape)
                        .let { if (isAdmin) it.clickable { color = hex } else it },
                )
            }
        }
        if (isAdmin) {
            ZTextField("Or type one", color, { color = it.take(7) }, placeholder = "#1f6f5c", error = model.errors["brand_color"])
            if (ToolsSettingsLogic.validColor(color) && !color.equals(saved, true)) ZButton("Save colour", style = ZButtonStyle.OUTLINE, loading = model.saving == "brand") {
                scope.launch { model.save("brand", buildJsonObject { put("brand_color", color.lowercase()) }) }
            }
        }
    }
}
