package com.zivett.app.features.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Description
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.BillVisits
import com.zivett.app.core.models.BillVisitsBody
import com.zivett.app.core.models.BillVisitsEndpoints
import com.zivett.app.core.models.InvoiceContext
import com.zivett.app.core.models.InvoiceFormat
import com.zivett.app.core.models.PaymentBody
import com.zivett.app.core.models.ProDocument
import com.zivett.app.core.models.ProInvoiceBody
import com.zivett.app.core.models.ProInvoiceEndpoints
import com.zivett.app.core.models.ProInvoicesResponse
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZCheckbox
import com.zivett.app.design.ZDateField
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZDropdown
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZErrorState
import com.zivett.app.design.ZFilterPills
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZHeroPanel
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextArea
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.design.ZType
import com.zivett.app.features.shared.LocalNavBadges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

object OwnInvoicesLogic {
    /// "$1,200.00 owed to you across 3 unpaid invoices."
    fun owedLine(response: ProInvoicesResponse): String {
        val count = response.counts["open"] ?: 0
        if (count == 0) return "Nobody owes you anything right now."
        return "${Money.format(response.outstandingCents)} owed to you across ${ToolsCopy.plural(count, "unpaid invoice")}."
    }

    /// The page's buttons by status (web InvoiceDetail `headerActions`),
    /// primary last.
    fun actions(invoice: ProDocument): List<String> = when (invoice.status) {
        "draft" -> listOf("edit", "issue")
        "void" -> listOfNotNull(if (invoice.replacedBy != null) "open_replacement" else null)
        else -> {
            val owed = invoice.balanceCents > 0
            val emailed = invoice.emails.isNotEmpty()
            if (emailed) listOfNotNull("email_again", if (owed) "record" else null)
            else listOfNotNull(if (owed) "record" else null, "email")
        }
    }

    /// Voidable: issued and not void (a paid one can be voided too, server-side rules apply).
    fun canVoid(invoice: ProDocument) = invoice.status != "draft" && invoice.status != "void"
}

// ── List (the Invoices tab) ─────────────────────────────────────────

class OwnInvoicesModel(private val client: ApiClient) {
    var status by mutableStateOf("all")
    var query by mutableStateOf("")
    var state by mutableStateOf<Loadable<ProInvoicesResponse>>(Loadable.Loading)
    var loadingMore by mutableStateOf(false)
    private var ticket = 0
    private var page = 1

    suspend fun load() {
        val mine = ++ticket
        page = 1
        try {
            val response = client.send(ProInvoiceEndpoints.list(status, query))
            if (mine == ticket) state = Loadable.Loaded(response)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (mine == ticket && state !is Loadable.Loaded) state = Loadable.Failed(e.userMessage)
        }
    }

    suspend fun more() {
        val current = state.value ?: return
        if (!current.hasMore || loadingMore) return
        loadingMore = true
        try { val next = client.send(ProInvoiceEndpoints.list(status, query, page + 1)); page += 1; state = Loadable.Loaded(next.copy(invoices = current.invoices + next.invoices)) }
        catch (e: CancellationException) { throw e } catch (_: Exception) {} finally { loadingMore = false }
    }
}

/// The company's own invoices (web `Invoices.vue`). Exports are web-only.
@Composable
fun OwnInvoicesScreen(nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember { OwnInvoicesModel(environment.client) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(model) {
        ToolsPendingFilter.take({ ToolsPendingFilter.invoices }, { ToolsPendingFilter.invoices = null })?.let { model.status = it }
        model.load()
    }
    val live = LocalNavBadges.current?.liveEvents ?: 0
    LaunchedEffect(live) { if (live > 0) model.load() }
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(model) { if (resumed) scope.launch { model.load() }; resumed = true; onPauseOrDispose { } }

    ZScreen(onRefresh = { model.load() }) {
        ZPageTitle("Invoices", "Bills for your own customers. Payments you record are ${ToolsCopy.PRO_REPORTED.lowercase()}.")
        ZButton("New invoice") { nav.newInvoice() }
        ZButton("Bill completed visits", style = ZButtonStyle.OUTLINE) { nav.push(com.zivett.app.app.ToolsBillVisitsRoute) }
        val response = model.state.value
        ZFilterPills(InvoiceFormat.filters.map { (key, label) -> Triple(key, if (key == "open") "Owed" else if (key == "draft") "Drafts" else label, if (key == "all") null else response?.counts?.get(key)) }, model.status) {
            model.status = it; scope.launch { model.load() }
        }
        ZTextField("Search", model.query, { model.query = it; searchJob?.cancel(); searchJob = scope.launch { delay(300); model.load() } }, placeholder = "Invoice number or customer")
        when (val state = model.state) {
            Loadable.Loading -> ZSpinner()
            is Loadable.Failed -> ZErrorState("Could not load your invoices.") { scope.launch { model.load() } }
            is Loadable.Loaded -> {
                ZCaption(OwnInvoicesLogic.owedLine(state.loaded))
                if (state.loaded.invoices.isEmpty()) ZEmptyState(Icons.Outlined.Description, "No invoices here", "Invoices you send your own customers show here.")
                for (row in state.loaded.invoices) ZCard(onClick = { nav.invoice(row.id) }) {
                    DocumentLine(row, invoice = true, subtitle = listOfNotNull(row.customer?.name, row.dueOn?.let { "due ${ToolsFormat.day(it)}" }, if (row.balanceCents > 0 && row.status != "draft") "${Money.format(row.balanceCents)} owed" else null).joinToString(" · "))
                }
                if (state.loaded.hasMore) ZButton("Show more", style = ZButtonStyle.OUTLINE, loading = model.loadingMore) { scope.launch { model.more() } }
            }
        }
        Spacer(Modifier.padding(ZSpacing.md))
    }
}

// ── Detail ──────────────────────────────────────────────────────────

class OwnInvoiceModel(val invoiceId: Int, private val client: ApiClient) {
    var state by mutableStateOf<Loadable<ProDocument>>(Loadable.Loading)
    var acting by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)
    var formError by mutableStateOf<String?>(null)

    suspend fun load() { state = state.reloaded { client.send(ProInvoiceEndpoints.show(invoiceId)).invoice } }

    private suspend fun act(key: String, success: String?, operation: suspend () -> ProDocument): Boolean {
        if (acting != null) return false
        acting = key; formError = null
        return try { state = Loadable.Loaded(operation()); success?.let { toast = it }; true }
        catch (e: CancellationException) { throw e } catch (e: ApiError.Validation) { formError = e.errors.firstMessages.values.firstOrNull() ?: e.errors.message; false }
        catch (e: Exception) { formError = e.userMessage; toast = e.userMessage; false } finally { acting = null }
    }

    suspend fun issue() = act("issue", "Issued.") { client.send(ProInvoiceEndpoints.issue(invoiceId)).invoice }
    suspend fun email(note: String) = act("email", "Emailed.") { client.send(ProInvoiceEndpoints.email(invoiceId, note)).invoice }
    suspend fun receipt(paymentId: Int) = act("receipt", "Receipt emailed.") { client.send(ProInvoiceEndpoints.receipt(invoiceId, paymentId)).invoice }
    suspend fun removePayment(paymentId: Int, reason: String) = act("payment", "Payment removed. It stays on the record.") { client.send(ProInvoiceEndpoints.removePayment(invoiceId, paymentId, reason)).invoice }
    suspend fun updatePayment(paymentId: Int, body: PaymentBody) = act("payment", "Payment changed.") { client.send(ProInvoiceEndpoints.updatePayment(invoiceId, paymentId, body)).invoice }

    suspend fun recordPayment(body: PaymentBody) = act("payment", null) {
        val recorded = client.send(ProInvoiceEndpoints.recordPayment(invoiceId, body))
        toast = when {
            recorded.receiptSent -> "Payment recorded. Receipt emailed."
            recorded.receiptError != null -> "Payment recorded. ${recorded.receiptError}"
            else -> "Payment recorded."
        }
        recorded.invoice
    }

    /// The corrected copy's id when one was started.
    suspend fun void(reason: String, replace: Boolean): Int? {
        var replacement: Int? = null
        act("void", "Voided. It keeps its number and is no longer owed.") {
            client.send(ProInvoiceEndpoints.void(invoiceId, reason, replace)).also { replacement = it.replacementId }.invoice
        }
        return replacement
    }
}

/// One own invoice (web `InvoiceDetail.vue`): issue, email, record and
/// change Pro-reported payments, receipts, void (with a corrected copy),
/// PDF, delete a draft.
@Composable
fun OwnInvoiceScreen(invoiceId: Int, nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val model = remember(invoiceId) { OwnInvoiceModel(invoiceId, environment.client) }
    LaunchedEffect(model) { model.load() }
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(model) { if (resumed) scope.launch { model.load() }; resumed = true; onPauseOrDispose { } }
    var menu by remember { mutableStateOf(false) }
    var emailing by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf(false) }
    var editingPayment by remember { mutableStateOf<ProDocument.Payment?>(null) }
    var removing by remember { mutableStateOf<ProDocument.Payment?>(null) }
    var voiding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val invoice = model.state.value

    fun pdf() = scope.launch { ToolsPdf.share(context, environment.client, ProInvoiceEndpoints.pdf(invoiceId), "Invoice-${invoice?.number ?: invoiceId}.pdf")?.let { model.toast = it } }

    Column {
        ZTopBar(invoice?.number?.let { "Invoice $it" } ?: "Draft invoice", onBack = { nav.back() }, actions = {
            if (invoice != null) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Download PDF") }, onClick = { menu = false; pdf() })
                    if (OwnInvoicesLogic.canVoid(invoice)) DropdownMenuItem(text = { Text("Void invoice…", color = com.zivett.app.design.ZTheme.colors.danger) }, onClick = { menu = false; voiding = true })
                }
            }
        })
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { doc ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        ZHeroPanel {
                            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                                val (label, tone) = ToolsFormat.invoiceStatus(doc.status)
                                ZBadge(label, tone)
                                if (doc.isDeposit) ZBadge("Deposit")
                            }
                            Text(doc.number?.let { "Invoice $it" } ?: "Draft invoice", style = ZType.title, color = Color.White)
                            Text(listOfNotNull(doc.customer?.name, Money.format(doc.totalCents), doc.dueOn?.let { "due ${ToolsFormat.day(it)}" }).joinToString(" · "), style = ZType.caption, color = Color.White.copy(alpha = 0.75f))
                        }

                        for ((index, action) in OwnInvoicesLogic.actions(doc).reversed().withIndex()) {
                            val style = if (index == 0) ZButtonStyle.PRIMARY else ZButtonStyle.OUTLINE
                            when (action) {
                                "edit" -> ZButton("Edit", style = style) { nav.editInvoice(doc.id) }
                                "issue" -> ZButton("Issue invoice", style = style, loading = model.acting == "issue") { scope.launch { model.issue() } }
                                "open_replacement" -> ZButton("Open the corrected invoice${doc.replacedBy?.number?.let { " $it" } ?: ""}", style = style) { doc.replacedBy?.let { nav.invoice(it.id) } }
                                "email_again" -> ZButton("Email it again", style = style) { emailing = true }
                                "email" -> ZButton("Email it to ${doc.customerFirstName}", style = style) { emailing = true }
                                "record" -> ZButton("Record a payment", style = style) { paying = true }
                            }
                        }
                        if (doc.status == "draft" && doc.paymentInstructions.isNullOrBlank()) {
                            ZCard {
                                ZHeadline("Say how to pay you")
                                ZBody("Add how customers pay you, like your e-Transfer address, before you issue it. It prints on the invoice.")
                                ZTextAction("Add payment instructions") { nav.push(com.zivett.app.app.ToolsSettingsRoute) }
                            }
                        }
                        doc.voidReason?.takeIf { doc.status == "void" }?.let { ZBanner("Voided${doc.voidedAt?.let { at -> " ${ToolsFormat.day(at)}" } ?: ""}: $it", tone = ZTone.NEUTRAL) }
                        doc.replaces?.let { ref -> ZCard { ClickRow({ nav.invoice(ref.id) }) { ZBody("Corrects void invoice ${ref.number ?: ""}") } } }
                        doc.quote?.let { ref -> ZCard { ClickRow({ nav.quote(ref.id) }) { ZBody("From quote ${ref.number ?: ""}") } } }
                        doc.job?.let { ref -> ZCard { ClickRow({ nav.job(ref.id) }) { ZBody("For job ${ref.code ?: ""}") } } }

                        if (doc.status != "draft") {
                            ZCard {
                                ZHeadline("Payments")
                                ZCaption("Payments you record are ${ToolsCopy.PRO_REPORTED.lowercase()}: ZiVETT never holds or collects that money.", tone = ZTextTone.SOFT)
                                if (doc.payments.isEmpty()) ZCaption("None recorded yet.")
                                for (payment in doc.payments) {
                                    ZDivider()
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            ZBodyStrong(Money.format(payment.amountCents) + if (payment.removed) " (removed)" else "", tone = if (payment.removed) ZTextTone.MUTED else ZTextTone.INK)
                                            ZCaption(listOfNotNull(ToolsFormat.day(payment.paidOn), payment.methodLabel, payment.reference, payment.reportedBy?.let { "by $it" }).joinToString(" · "))
                                            payment.removeReason?.let { ZCaption("Removed: $it") }
                                        }
                                        ZBadge(ToolsCopy.PRO_REPORTED, ZTone.NEUTRAL)
                                    }
                                    if (!payment.removed && doc.status != "void") Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                                        if (doc.email?.recipient != null) ZTextAction("Email a receipt", enabled = model.acting == null) { scope.launch { model.receipt(payment.id) } }
                                        ZTextAction("Change", enabled = model.acting == null) { editingPayment = payment }
                                        ZTextAction("Remove", color = com.zivett.app.design.ZTheme.colors.inkSoft, enabled = model.acting == null) { removing = payment }
                                    }
                                }
                            }
                        }
                        DocumentEmailsCard(doc.emails)
                        if (doc.letterhead?.email.isNullOrBlank()) {
                            ZCard {
                                ZHeadline("Add your email")
                                ZBody("Your invoices show your phone but no email. Add a business email so customers can write back.")
                                ZTextAction("Add it to your business profile") { nav.push(com.zivett.app.app.OrgProfileRoute(com.zivett.app.app.Areas.COMPANY)) }
                            }
                        }
                        DocumentSheetCard(doc)
                        if (doc.status == "draft") ZCard {
                            ZHeadline("Delete this draft")
                            ZCaption("The draft and its line items are removed. Nothing was issued.", tone = ZTextTone.SOFT)
                            ZButton("Delete draft", style = ZButtonStyle.OUTLINE) { deleting = true }
                        }
                        Spacer(Modifier.padding(ZSpacing.md))
                    }
                }
            }
        }
    }

    if (emailing && invoice != null) SendDocumentSheet(invoice, model.acting == "email", model.formError, onDismiss = { emailing = false }, onPdf = { pdf() }, onCustomer = { invoice.customer?.id?.let { nav.customer(it) } }) { note ->
        scope.launch { if (model.email(note)) emailing = false }
    }
    if ((paying || editingPayment != null) && invoice != null) PaymentSheet(invoice, editingPayment, model, onDismiss = { paying = false; editingPayment = null })
    removing?.let { payment -> ReasonSheet("Remove this payment?", "It stays on the record, marked removed, and the invoice is owed again.", "The cheque bounced", "Remove payment", model.acting == "payment", model.formError, onDismiss = { removing = null }) { reason ->
        scope.launch { if (model.removePayment(payment.id, reason)) removing = null }
    } }
    if (voiding) VoidSheet(model, onDismiss = { voiding = false }) { replacement -> voiding = false; replacement?.let { nav.editInvoice(it) } }
    if (deleting) ToolsConfirm("Delete this draft?", "The draft and its line items are deleted. This can't be undone.", "Delete draft", danger = true, onDismiss = { deleting = false }) {
        scope.launch { runCatching { environment.client.send(ProInvoiceEndpoints.delete(invoiceId)) }.onSuccess { nav.back() }.onFailure { model.toast = it.userMessage } }
    }
}

@Composable
private fun PaymentSheet(invoice: ProDocument, editing: ProDocument.Payment?, model: OwnInvoiceModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf(editing?.let { com.zivett.app.features.company.QuoteLineLogic.dollarsText(it.amountCents) } ?: com.zivett.app.features.company.QuoteLineLogic.dollarsText(invoice.balanceCents)) }
    var date by remember { mutableStateOf(editing?.paidOn ?: LocalDate.now().toString()) }
    var method by remember { mutableStateOf(editing?.method ?: "etransfer") }
    var reference by remember { mutableStateOf(editing?.reference.orEmpty()) }
    var reason by remember { mutableStateOf("") }
    var receipt by remember { mutableStateOf(false) }
    val canReceipt = editing == null && invoice.email?.recipient != null && invoice.email.blocked == null
    ZSheet(onDismiss, title = if (editing != null) "Change this payment" else "Record a payment", dismissable = model.acting == null) {
        ZBody("Money your customer paid you directly. It's marked ${ToolsCopy.PRO_REPORTED.lowercase()}: ZiVETT never holds or collects it.")
        ZTextField("Amount ($)", amount, { amount = it }, keyboardType = KeyboardType.Decimal)
        ZDateField("Date paid", date, { date = it })
        ZDropdown("How they paid", method, InvoiceFormat.methods) { method = it }
        ZTextField("Reference (optional)", reference, { reference = it.take(120) }, placeholder = "Cheque or e-Transfer number")
        if (editing != null) ZTextField("Why it's changing", reason, { reason = it.take(500) }, placeholder = "Typo in the amount")
        if (canReceipt) ZCheckbox(receipt, { receipt = it }, "Email ${invoice.customerFirstName} a receipt")
        model.formError?.let { ZBanner(it, tone = ZTone.DANGER) }
        val cents = Money.parseCents(amount)
        ZButton(if (editing != null) "Save payment" else "Record payment", loading = model.acting == "payment", enabled = (cents ?: 0) != 0 && (editing == null || reason.isNotBlank())) {
            scope.launch {
                val body = PaymentBody(cents ?: 0, date, method, reference.trim().ifBlank { null }, reason.trim().ifBlank { null }, if (editing == null) receipt else null)
                if (if (editing != null) model.updatePayment(editing.id, body) else model.recordPayment(body)) onDismiss()
            }
        }
        ZButton("Cancel", style = ZButtonStyle.GHOST, onClick = onDismiss)
    }
}

@Composable
private fun ReasonSheet(title: String, text: String, placeholder: String, confirm: String, busy: Boolean, error: String?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    ZSheet(onDismiss, title = title, dismissable = !busy) {
        ZBody(text)
        ZTextField("Why", reason, { reason = it.take(500) }, placeholder = placeholder)
        error?.let { ZBanner(it, tone = ZTone.DANGER) }
        ZButton(confirm, style = ZButtonStyle.DANGER, loading = busy, enabled = reason.isNotBlank()) { onConfirm(reason.trim()) }
        ZButton("Cancel", style = ZButtonStyle.GHOST, onClick = onDismiss)
    }
}

@Composable
private fun VoidSheet(model: OwnInvoiceModel, onDismiss: () -> Unit, onDone: (Int?) -> Unit) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf(true) }
    ZSheet(onDismiss, title = "Void this invoice?", dismissable = model.acting == null) {
        ZBody("For a mistake: it keeps its number, shows VOID, and is no longer owed.")
        ZTextField("Why (kept on the record)", reason, { reason = it.take(500) }, placeholder = "Wrong price on the labour line")
        ZCheckbox(replace, { replace = it }, "Start a corrected copy as a new draft")
        model.formError?.let { ZBanner(it, tone = ZTone.DANGER) }
        ZButton("Void invoice", style = ZButtonStyle.DANGER, loading = model.acting == "void", enabled = reason.isNotBlank()) {
            scope.launch { val replacement = model.void(reason.trim(), replace); if (model.formError == null) onDone(replacement) }
        }
        ZButton("Keep it", style = ZButtonStyle.GHOST, onClick = onDismiss)
    }
}

// ── Editor ──────────────────────────────────────────────────────────

class OwnInvoiceEditorModel(var invoiceId: Int?, private val client: ApiClient) {
    val customer = DocumentCustomerState(client)
    var context by mutableStateOf<InvoiceContext?>(null)
    var existing by mutableStateOf<ProDocument?>(null)
    var loadError by mutableStateOf<String?>(null)
    var lines by mutableStateOf(listOf(DocumentLogic.blankLine()))
    var dueTerms by mutableStateOf("receipt")
    var dueOn by mutableStateOf("")
    var notes by mutableStateOf("")
    var chargeGst by mutableStateOf(false)
    var chargePst by mutableStateOf(false)
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
    var message by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
        private set

    private fun fill(doc: ProDocument) {
        existing = doc; invoiceId = doc.id
        lines = DocumentLogic.drafts(doc.lineItems).ifEmpty { listOf(DocumentLogic.blankLine()) }
        dueTerms = doc.dueTerms ?: "receipt"
        dueOn = if (doc.dueTerms == "date") doc.dueOn.orEmpty() else ""
        notes = doc.notes.orEmpty()
        chargeGst = (doc.gstBps ?: 0) > 0
        chargePst = (doc.pstBps ?: 0) > 0
    }

    /// A quote or job seeds a draft on the server (it carries the lines and
    /// links), then this page edits that draft, as the web does.
    suspend fun load(customerId: Int?, jobId: Int?, quoteId: Int?) {
        try {
            val ctx = client.send(ProInvoiceEndpoints.context())
            context = ctx
            chargeGst = ctx.tax.gstAvailable
            val id = invoiceId
            when {
                id != null -> client.send(ProInvoiceEndpoints.show(id)).invoice.let { fill(it); it.customer?.id?.let { c -> customer.pick(c, keepAddress = it.proCustomerAddressId) }; customer.addressId = it.proCustomerAddressId }
                quoteId != null || jobId != null -> client.send(ProInvoiceEndpoints.create(ProInvoiceBody(quoteId = quoteId, proJobId = jobId))).invoice.let { fill(it); it.customer?.id?.let { c -> customer.pick(c, keepAddress = it.proCustomerAddressId) }; customer.addressId = it.proCustomerAddressId }
                customerId != null -> customer.pick(customerId)
            }
            loadError = null
        } catch (e: CancellationException) { throw e } catch (e: ApiError.Validation) {
            loadError = e.errors.firstMessages.values.firstOrNull() ?: e.errors.message
        } catch (e: Exception) { loadError = e.userMessage }
    }

    suspend fun save(andIssue: Boolean): ProDocument? {
        errors = emptyMap(); message = null
        val person = customer.customer ?: run { errors = mapOf("pro_customer_id" to "Choose who this invoice is for."); return null }
        saving = true
        return try {
            val body = ProInvoiceBody(
                proCustomerId = person.id, proCustomerAddressId = customer.addressId, lineItems = com.zivett.app.features.company.QuoteLineLogic.body(lines),
                dueTerms = dueTerms, dueOn = if (dueTerms == "date") dueOn.ifBlank { null } else null, notes = notes.trim().ifBlank { null }, chargeGst = chargeGst, chargePst = chargePst,
            )
            val id = invoiceId
            var saved = if (id != null) client.send(ProInvoiceEndpoints.update(id, body)).invoice else client.send(ProInvoiceEndpoints.create(body)).invoice
            invoiceId = saved.id
            if (andIssue) saved = runCatching { client.send(ProInvoiceEndpoints.issue(saved.id)).invoice }.getOrElse { message = it.userMessage; saved }
            saved
        } catch (e: CancellationException) { throw e } catch (e: ApiError.Validation) {
            errors = e.errors.firstMessages; message = if (errors.isNotEmpty()) "Check the highlighted fields." else e.errors.message; null
        } catch (e: Exception) { message = e.userMessage; null } finally { saving = false }
    }
}

/// New / edit own invoice (web `InvoiceEditor.vue`).
@Composable
fun OwnInvoiceEditorScreen(invoiceId: Int?, customerId: Int?, jobId: Int?, quoteId: Int?, nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember(invoiceId) { OwnInvoiceEditorModel(invoiceId, environment.client) }
    LaunchedEffect(model) { model.load(customerId, jobId, quoteId) }
    val ctx = model.context
    val editingExisting = invoiceId != null

    Column {
        ZTopBar(if (model.existing != null) "Edit invoice${model.existing?.number?.let { " $it" } ?: ""}" else "New invoice", onBack = { nav.back() })
        ZScreen {
            model.existing?.let { doc ->
                (doc.job?.code?.let { "From job $it" } ?: doc.quote?.number?.let { "From quote $it" } ?: doc.replaces?.number?.let { "Corrects void invoice $it" })?.let { ZCaption(it) }
            } ?: ZCaption("For one of your own customers. 0% ZiVETT commission.")
            when {
                model.loadError != null -> ZErrorState(model.loadError.orEmpty()) { scope.launch { model.load(customerId, jobId, quoteId) } }
                ctx == null -> ZSpinner()
                else -> {
                    DocumentCustomerCard(model.customer, model.errors["pro_customer_id"], model.errors["pro_customer_address_id"], locked = model.existing?.job != null || model.existing?.quote != null)
                    DocumentLinesCard(model.lines, { model.lines = it }, model.errors.entries.firstOrNull { it.key.startsWith("line_items") }?.value)
                    ZCard {
                        ZHeadline("Payment")
                        ZDropdown("Payment due", model.dueTerms, InvoiceFormat.dueTerms) { model.dueTerms = it }
                        if (model.dueTerms == "date") ZDateField("Due on", model.dueOn, { model.dueOn = it }, error = model.errors["due_on"])
                        ZTextArea("Note on this invoice (optional)", model.notes, { model.notes = it.take(2000) }, error = model.errors["notes"], minLines = 2)
                        ZLabel("How to pay you")
                        ZBody(ctx.paymentInstructions?.takeIf { it.isNotBlank() } ?: "Nothing yet, so your invoices don't say how to pay you.", tone = if (ctx.paymentInstructions.isNullOrBlank()) ZTextTone.MUTED else ZTextTone.INK)
                        if (ctx.canManageSettings) ZTextAction("Change in Business tools settings") { nav.push(com.zivett.app.app.ToolsSettingsRoute) }
                    }
                    DocumentTotalsCard(model.lines, ctx.tax.gstBps, ctx.tax.pstBps, ctx.tax.gstAvailable, model.chargeGst, { model.chargeGst = it }, model.chargePst, { model.chargePst = it }, model.errors["charge_gst"])
                    model.message?.let { ZBanner(it, tone = ZTone.DANGER) }
                    val blocked = DocumentLogic.incomplete(model.lines)
                    fun done(saved: ProDocument) { nav.back(); if (!editingExisting) nav.invoice(saved.id) }
                    ZButton("Save and issue", loading = model.saving, enabled = !blocked) { scope.launch { model.save(andIssue = true)?.let { done(it) } } }
                    ZButton("Save draft", style = ZButtonStyle.OUTLINE, enabled = !model.saving && !blocked) { scope.launch { model.save(andIssue = false)?.let { done(it) } } }
                    if (blocked) ZCaption("Describe every line and give it a quantity.")
                    ZButton("Cancel", style = ZButtonStyle.GHOST) { nav.back() }
                }
            }
            Spacer(Modifier.padding(ZSpacing.md))
        }
    }
}

// ── Bill completed visits ───────────────────────────────────────────

class BillVisitsModel(private val client: ApiClient) {
    var state by mutableStateOf<Loadable<BillVisits>>(Loadable.Loading)
    var from by mutableStateOf("")
    var to by mutableStateOf("")
    var picked by mutableStateOf<Set<Int>>(emptySet())
    var prices by mutableStateOf<Map<Int, String>>(emptyMap())
    var chargeGst by mutableStateOf(false)
    var dueTerms by mutableStateOf("receipt")
    var running by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var result by mutableStateOf<com.zivett.app.core.models.BillVisitsResult.Result?>(null)

    suspend fun load() {
        state = Loadable.Loading
        state = try {
            val data = client.send(BillVisitsEndpoints.load(from.ifBlank { null }, to.ifBlank { null }))
            from = data.from; to = data.to
            picked = data.customers.map { it.customer.id }.toSet()
            chargeGst = data.canChargeGst
            Loadable.Loaded(data)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Loadable.Failed(e.userMessage) }
    }

    suspend fun run(action: String) {
        error = null; running = true
        try {
            val cents = prices.mapNotNull { (job, text) -> Money.parseCents(text)?.let { job.toString() to it } }.toMap()
            result = client.send(BillVisitsEndpoints.run(BillVisitsBody(from, to, picked.toList(), cents, chargeGst, dueTerms, action))).result
        } catch (e: CancellationException) { throw e } catch (e: ApiError.Validation) {
            error = e.errors.firstMessages.values.firstOrNull() ?: e.errors.message
        } catch (e: Exception) { error = e.userMessage } finally { running = false }
    }
}

/// One invoice per customer for the completed visits in a period (web `BillVisits.vue`).
@Composable
fun BillVisitsScreen(nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember { BillVisitsModel(environment.client) }
    LaunchedEffect(model) { model.load() }

    Column {
        ZTopBar("Bill completed visits", onBack = { nav.back() })
        ZScreen {
            val result = model.result
            if (result != null) {
                ZCard {
                    ZHeadline("Done")
                    ZBody(listOfNotNull(
                        "${ToolsCopy.plural(result.created, "invoice")} made",
                        if (result.issued > 0) "${Money.count(result.issued)} issued" else null,
                        if (result.emailed > 0) "${Money.count(result.emailed)} emailed" else null,
                    ).joinToString(", ") + ".")
                    if (result.notEmailed.isNotEmpty()) {
                        ZBodyStrong("Not emailed. Send these from the invoice page:")
                        for (row in result.notEmailed) ZCaption("${row.customer}: ${row.reason}")
                    }
                    ZButton("See the invoices") { nav.back(); nav.invoices("all") }
                    ZButton("Bill another period", style = ZButtonStyle.OUTLINE) { model.result = null; scope.launch { model.load() } }
                }
                return@ZScreen
            }
            ZCaption("One invoice per customer for the visits you completed in a period.")
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                ZDateField("From", model.from, { model.from = it; scope.launch { model.load() } }, Modifier.weight(1f))
                ZDateField("To", model.to, { model.to = it; scope.launch { model.load() } }, Modifier.weight(1f))
            }
            ZLoadable(model.state, retry = { scope.launch { model.load() } }) { data ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    if (data.customers.isEmpty()) ZCaption("No completed visits left to bill in this period.")
                    for (row in data.customers) {
                        val on = row.customer.id in model.picked
                        ZCard {
                            ZCheckbox(on, { model.picked = if (it) model.picked + row.customer.id else model.picked - row.customer.id }, "Bill ${row.customer.name}")
                            ZCaption(listOf(ToolsCopy.plural(row.visits.size, "visit"), Money.format(row.totalCents), row.customer.email ?: "No email on file").joinToString(" · "))
                            for (visit in row.visits) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ZCaption("${ToolsFormat.day(visit.date)} · ${visit.title}", Modifier.weight(1f))
                                    if (visit.priceCents != null) ZCaption(Money.format(visit.priceCents))
                                }
                                if (visit.priceCents == null) ZTextField("Price for ${visit.title} ($)", model.prices[visit.id].orEmpty(), { model.prices = model.prices + (visit.id to it) }, keyboardType = KeyboardType.Decimal)
                            }
                        }
                    }
                    if (data.customers.isNotEmpty()) {
                        ZCard {
                            ZHeadline("Bill ${ToolsCopy.plural(model.picked.size, "customer")}")
                            ZDropdown("Payment due", model.dueTerms, InvoiceFormat.dueTerms.filter { it.first != "date" }) { model.dueTerms = it }
                            ZCheckbox(model.chargeGst, { if (data.canChargeGst) model.chargeGst = it }, "Charge GST")
                            if (!data.canChargeGst) ZCaption("Add your GST number to your business profile to charge it.")
                            data.email?.let { email -> ZCaption("${Money.count(email.sentToday)} of ${Money.count(email.dailyLimit)} emails sent today."); email.blocked?.let { ZBanner(it, tone = ZTone.WARNING) } }
                            model.error?.let { ZBanner(it, tone = ZTone.DANGER) }
                            ZButton("Issue and email them", loading = model.running, enabled = model.picked.isNotEmpty() && data.email?.blocked == null) { scope.launch { model.run("email") } }
                            ZButton("Issue them", style = ZButtonStyle.OUTLINE, enabled = model.picked.isNotEmpty() && !model.running) { scope.launch { model.run("issue") } }
                            ZButton("Leave them as drafts to check first", style = ZButtonStyle.GHOST, enabled = model.picked.isNotEmpty() && !model.running) { scope.launch { model.run("draft") } }
                        }
                    }
                }
            }
            Spacer(Modifier.padding(ZSpacing.md))
        }
    }
}
