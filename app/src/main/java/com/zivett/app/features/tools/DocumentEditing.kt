package com.zivett.app.features.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Money
import com.zivett.app.core.models.LinePreset
import com.zivett.app.core.models.LinePresetBody
import com.zivett.app.core.models.LinePresetEndpoints
import com.zivett.app.core.models.ProCustomer
import com.zivett.app.core.models.ProCustomerEndpoints
import com.zivett.app.core.models.ProCustomerRow
import com.zivett.app.core.models.ProLine
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.userMessage
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZDropdown
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextField
import com.zivett.app.features.company.QuoteLineDraft
import com.zivett.app.features.company.QuoteLineEditor
import com.zivett.app.features.company.QuoteLineKind
import com.zivett.app.features.company.QuoteLineLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/// The pieces the own quote and invoice editors share: the customer they're
/// for, the address, the line rows (the marketplace composer's own editor)
/// and the tax maths (web `QuoteEditor` / `InvoiceEditor`).
object DocumentLogic {
    fun drafts(lines: List<ProLine>): List<QuoteLineDraft> = lines.map { line ->
        QuoteLineDraft(kind = QuoteLineKind.of(line.type) ?: QuoteLineKind.FEE, description = line.description, quantity = QuoteLineLogic.quantityText(line.quantity), unit = QuoteLineLogic.dollarsText(line.unitCents))
    }

    fun blankLine() = QuoteLineDraft(kind = QuoteLineKind.LABOUR, description = "", quantity = "1", unit = "")

    /// Half-up like the server and the web (`Math.round`).
    fun tax(subtotal: Int, bps: Int, on: Boolean): Int = if (on) Math.round(subtotal * bps / 10000.0).toInt() else 0

    /// Rows a person could save: described, with a quantity.
    fun incomplete(lines: List<QuoteLineDraft>): Boolean = lines.isEmpty() || lines.any { it.description.isBlank() || (it.quantity.trim().replace(',', '.').toDoubleOrNull() ?: 0.0) <= 0.0 }
}

/// Picks one of the company's own active customers, by search.
@Composable
fun CustomerPickerSheet(onDismiss: () -> Unit, onPick: (ProCustomerRow) -> Unit) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<ProCustomerRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    suspend fun load() {
        try { rows = environment.client.send(ProCustomerEndpoints.list("active", query)).customers; error = null } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.userMessage }
    }
    LaunchedEffect(Unit) { load() }
    ZSheet(onDismiss, title = "Choose a customer") {
        ZTextField("Search", query, { query = it; job?.cancel(); job = scope.launch { delay(300); load() } }, placeholder = "Name, phone, email or address")
        error?.let { ZCaption(it) }
        val list = rows
        when {
            list == null -> ZCaption("Loading…")
            list.isEmpty() -> ZCaption(if (query.isBlank()) "No customers yet. Add one from Customers first." else "No matches.")
            else -> for (row in list.take(40)) {
                ClickRow({ onDismiss(); onPick(row) }) {
                    Column {
                        ZBodyStrong(row.name)
                        ZCaption(listOfNotNull(row.phone, row.email, row.address).joinToString(" · "), maxLines = 1)
                    }
                }
                ZDivider()
            }
        }
    }
}

/// Holds who a document is for and their saved addresses.
class DocumentCustomerState(private val client: ApiClient) {
    var customer by mutableStateOf<ProCustomer?>(null)
    var addressId by mutableStateOf<Int?>(null)

    suspend fun pick(id: Int, keepAddress: Int? = null) {
        val person = runCatching { client.send(ProCustomerEndpoints.show(id)).customer }.getOrNull() ?: return
        customer = person
        addressId = keepAddress ?: person.activeAddresses.firstOrNull()?.id
    }
}

@Composable
fun DocumentCustomerCard(state: DocumentCustomerState, error: String?, addressError: String?, locked: Boolean = false) {
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    ZCard {
        ZHeadline("Customer")
        val person = state.customer
        if (person == null) {
            ZButton("Choose a customer", style = ZButtonStyle.OUTLINE) { picking = true }
            error?.let { ZCaption(it, tone = com.zivett.app.design.ZTextTone.INK) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    ZBodyStrong(person.name)
                    ZCaption(listOfNotNull(person.phone, person.email).joinToString(" · "))
                }
                if (!locked) ZTextAction("Change") { picking = true }
            }
            val addresses = person.activeAddresses
            if (addresses.isNotEmpty()) {
                ZDropdown(
                    "Service address", state.addressId?.toString() ?: "",
                    listOf("" to "No address") + addresses.map { it.id.toString() to ((it.label?.takeIf { l -> l.isNotBlank() }?.let { l -> "$l: " } ?: "") + it.oneLine) },
                    placeholder = "No address", error = addressError,
                ) { state.addressId = it.toIntOrNull() }
            }
        }
    }
    if (picking) CustomerPickerSheet({ picking = false }) { row -> scope.launch { state.pick(row.id) } }
}

/// The line rows: the shared row editor, Add a line, and the presets bar
/// (apply a saved set, or save these lines as one).
@Composable
fun DocumentLinesCard(lines: List<QuoteLineDraft>, onChange: (List<QuoteLineDraft>) -> Unit, error: String?) {
    val environment = LocalAppEnvironment.current
    var presets by remember { mutableStateOf(false) }
    ZCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZHeadline("Line items", Modifier.weight(1f))
            ZTextAction("Presets") { presets = true }
        }
        lines.forEachIndexed { index, line ->
            QuoteLineEditor(line, { next -> onChange(lines.toMutableList().also { it[index] = next }) }, { onChange(lines.filterIndexed { i, _ -> i != index }) })
        }
        ZButton("Add a line", style = ZButtonStyle.OUTLINE) { onChange(lines + DocumentLogic.blankLine()) }
        error?.let { ZCaption(it, tone = com.zivett.app.design.ZTextTone.INK) }
    }
    if (presets) PresetsSheet(environment.client, lines, onDismiss = { presets = false }) { preset -> onChange(lines.filter { it.description.isNotBlank() } + DocumentLogic.drafts(preset.lineItems)) }
}

/// Line presets (web `LinePresetsBar`): add a saved set's lines, or save
/// the current ones under a name.
@Composable
private fun PresetsSheet(client: ApiClient, lines: List<QuoteLineDraft>, onDismiss: () -> Unit, onApply: (LinePreset) -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<LinePreset>?>(null) }
    var name by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { list = runCatching { client.send(LinePresetEndpoints.list()).presets }.getOrElse { message = it.userMessage; emptyList() } }
    ZSheet(onDismiss, title = "Line presets") {
        val presets = list
        if (presets == null) ZCaption("Loading…")
        else if (presets.isEmpty()) ZCaption("No presets yet. Save lines you use often, like a hot water tank install, and add them in one tap.")
        presets?.forEach { preset ->
            ClickRow({ onDismiss(); onApply(preset) }) {
                Row { ZBodyStrong(preset.name, Modifier.weight(1f)); ZCaption(Money.format(preset.totalCents)) }
            }
            ZDivider()
        }
        if (!DocumentLogic.incomplete(lines)) {
            ZTextField("Save these lines as", name, { name = it.take(80) }, placeholder = "Hot water tank install")
            ZButton("Save preset", style = ZButtonStyle.OUTLINE, loading = saving, enabled = name.isNotBlank()) {
                saving = true
                scope.launch {
                    try {
                        val saved = client.send(LinePresetEndpoints.create(LinePresetBody(name.trim(), QuoteLineLogic.body(lines)))).preset
                        list = (list.orEmpty() + saved).sortedBy { it.name.lowercase() }; name = ""; message = "Saved."
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { message = e.userMessage } finally { saving = false }
                }
            }
        }
        message?.let { ZCaption(it) }
    }
}

/// The totals card with the tax switches.
@Composable
fun DocumentTotalsCard(
    lines: List<QuoteLineDraft>, gstBps: Int, pstBps: Int, gstAvailable: Boolean,
    chargeGst: Boolean, onGst: (Boolean) -> Unit, chargePst: Boolean, onPst: (Boolean) -> Unit, gstError: String?,
) {
    val subtotal = QuoteLineLogic.total(lines)
    val gst = DocumentLogic.tax(subtotal, gstBps, chargeGst)
    val pst = DocumentLogic.tax(subtotal, pstBps, chargePst)
    ZCard {
        ZHeadline("Total")
        Row { ZCaption("Subtotal", Modifier.weight(1f)); com.zivett.app.design.ZBody(Money.format(subtotal)) }
        if (chargeGst) Row { ZCaption("GST (${percent(gstBps)})", Modifier.weight(1f)); com.zivett.app.design.ZBody(Money.format(gst)) }
        if (chargePst) Row { ZCaption("PST (${percent(pstBps)})", Modifier.weight(1f)); com.zivett.app.design.ZBody(Money.format(pst)) }
        ZDivider()
        Row { ZBodyStrong("Total", Modifier.weight(1f)); ZBodyStrong(Money.format(subtotal + gst + pst)) }
        com.zivett.app.design.ZCheckbox(chargeGst, { if (gstAvailable) onGst(it) }, "Charge GST")
        if (!gstAvailable) ZCaption("Add your GST number (like 123456789RT0001) to your business profile to charge GST.")
        gstError?.let { ZCaption(it, tone = com.zivett.app.design.ZTextTone.INK) }
        com.zivett.app.design.ZCheckbox(chargePst, onPst, "Charge PST")
        ZCaption("You're the seller, so you collect and remit any tax you charge.")
    }
}

@Suppress("unused")
private val spacing = Arrangement.spacedBy(ZSpacing.sm)
