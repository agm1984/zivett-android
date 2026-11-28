package com.zivett.app.features.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.DocumentContext
import com.zivett.app.core.models.ProDocument
import com.zivett.app.core.models.ProInvoiceEndpoints
import com.zivett.app.core.models.ProQuoteBody
import com.zivett.app.core.models.ProQuoteEndpoints
import com.zivett.app.core.models.ProQuotesResponse
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZChoiceTile
import com.zivett.app.design.ZDateField
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZErrorState
import com.zivett.app.design.ZFilterPills
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZHeroPanel
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMonoBody
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZTextArea
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.design.ZType
import com.zivett.app.features.company.QuoteLineDraft
import com.zivett.app.features.company.QuoteLineLogic
import com.zivett.app.features.shared.LocalNavBadges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── List ────────────────────────────────────────────────────────────

object OwnQuotesLogic {
    val filters = listOf("all" to "All", "draft" to "Draft", "sent" to "Sent", "accepted" to "Accepted", "declined" to "Declined", "expired" to "Expired")

    /// "3 of 10 quotes left this month" — only when the plan caps quotes.
    fun allowanceLine(left: Int?, perMonth: Int?): String? = if (left == null || perMonth == null) null else "${Money.count(left)} of ${Money.count(perMonth)} quotes left this month."

    /// The detail page's primary step and the rest, by status (web QuoteDetail `headerActions`).
    fun actions(quote: ProDocument): List<String> = when (quote.status) {
        "draft" -> listOf("edit", "mark_sent", "email")
        "sent", "expired" -> listOf("email", "decline", "accept")
        "accepted" -> listOf(if (quote.invoice != null) "open_invoice" else "invoice", if (quote.job != null) "open_job" else "schedule")
        else -> emptyList()
    }

    fun emailLabel(quote: ProDocument): String = if (quote.emails.isNotEmpty()) "Email it again" else "Email it to ${quote.customerFirstName}"

    /// The approval link card's sentence.
    fun linkLine(quote: ProDocument): String? {
        val link = quote.link ?: return null
        link.answered?.let { answer ->
            return "${if (answer.accepted) "Approved" else "Declined"} online by ${answer.name ?: "the customer"} ${ToolsFormat.day(answer.at)}." + (answer.reason?.let { " They said: \"$it\"" } ?: "")
        }
        if (link.count > 0) return link.lastViewedAt?.let { "Opened ${ToolsFormat.day(it)}. No answer yet." } ?: "Sent, not opened yet."
        if (link.canCopy) return "Email the quote and ${quote.customerFirstName} gets a link to approve or decline it. Or copy a link to text them yourself."
        return null
    }
}

class OwnQuotesModel(private val client: ApiClient) {
    var status by mutableStateOf("all")
    var query by mutableStateOf("")
    var state by mutableStateOf<Loadable<ProQuotesResponse>>(Loadable.Loading)
    private var ticket = 0
    private var page = 1
    var loadingMore by mutableStateOf(false)

    suspend fun load() {
        val mine = ++ticket
        page = 1
        try {
            val response = client.send(ProQuoteEndpoints.list(status, query))
            if (mine == ticket) state = Loadable.Loaded(response)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (mine == ticket && state !is Loadable.Loaded) state = Loadable.Failed(e.userMessage)
        }
    }

    suspend fun more() {
        val current = state.value ?: return
        if (!current.hasMore || loadingMore) return
        loadingMore = true
        try {
            val next = client.send(ProQuoteEndpoints.list(status, query, page + 1)); page += 1
            state = Loadable.Loaded(next.copy(quotes = current.quotes + next.quotes))
        } catch (e: CancellationException) { throw e } catch (_: Exception) {} finally { loadingMore = false }
    }
}

/// The company's own quotes (web `Quotes.vue`). Export is web-only.
@Composable
fun OwnQuotesScreen(filter: String?, nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember { OwnQuotesModel(environment.client).also { m -> filter?.let { m.status = it } } }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(model) { model.load() }
    val live = LocalNavBadges.current?.liveEvents ?: 0
    LaunchedEffect(live) { if (live > 0) model.load() }

    Column {
        ZTopBar("Quotes", onBack = { nav.back() })
        ZScreen(onRefresh = { model.load() }) {
            ZPageTitle("Quotes", "Quotes for your own customers. 0% ZiVETT commission.")
            ZButton("New quote") { nav.newQuote() }
            val response = model.state.value
            ZFilterPills(OwnQuotesLogic.filters.map { (key, label) -> Triple(key, label, if (key == "all") null else response?.counts?.get(key)) }, model.status) {
                model.status = it; scope.launch { model.load() }
            }
            ZTextField("Search", model.query, { model.query = it; searchJob?.cancel(); searchJob = scope.launch { delay(300); model.load() } }, placeholder = "Quote number or customer")
            when (val state = model.state) {
                Loadable.Loading -> ZSpinner()
                is Loadable.Failed -> ZErrorState("Could not load your quotes.") { scope.launch { model.load() } }
                is Loadable.Loaded -> {
                    OwnQuotesLogic.allowanceLine(state.loaded.quotesLeftThisMonth, state.loaded.quotesPerMonth)?.let { ZCaption(it) }
                    if (state.loaded.quotes.isEmpty()) ZEmptyState(Icons.Outlined.Description, "No quotes here", "Quotes you write for your own customers show here.")
                    for (row in state.loaded.quotes) {
                        ZCard(onClick = { nav.quote(row.id) }) { DocumentLine(row, invoice = false, subtitle = listOfNotNull(row.customer?.name, row.validUntil?.let { "valid until ${ToolsFormat.day(it)}" }).joinToString(" · ")) }
                    }
                    if (state.loaded.hasMore) ZButton("Show more", style = ZButtonStyle.OUTLINE, loading = model.loadingMore) { scope.launch { model.more() } }
                }
            }
            Spacer(Modifier.padding(ZSpacing.md))
        }
    }
}

// ── Detail ──────────────────────────────────────────────────────────

class OwnQuoteModel(val quoteId: Int, private val client: ApiClient) {
    var state by mutableStateOf<Loadable<ProDocument>>(Loadable.Loading)
    var acting by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)
    /// A fresh approval link, shown once.
    var link by mutableStateOf<String?>(null)
    var emailError by mutableStateOf<String?>(null)

    suspend fun load() { state = state.reloaded { client.send(ProQuoteEndpoints.show(quoteId)).quote } }

    private suspend fun act(key: String, success: String?, operation: suspend () -> ProDocument?): ProDocument? {
        if (acting != null) return null
        acting = key
        return try {
            val result = operation()
            result?.let { if (it.id == quoteId) state = Loadable.Loaded(it) }
            success?.let { toast = it }
            result
        } catch (e: CancellationException) { throw e } catch (e: Exception) { toast = e.userMessage; null } finally { acting = null }
    }

    suspend fun markSent() = act("send", "Marked as sent.") { client.send(ProQuoteEndpoints.send(quoteId)).quote }
    suspend fun answer(accepted: Boolean) = act(if (accepted) "accept" else "decline", if (accepted) "Marked accepted." else "Marked declined.") { client.send(ProQuoteEndpoints.answer(quoteId, accepted)).quote }
    suspend fun duplicate() = act("duplicate", "Copied into a new draft.") { client.send(ProQuoteEndpoints.duplicate(quoteId)).quote }
    suspend fun makeLink() = act("link", null) { client.send(ProQuoteEndpoints.link(quoteId)).also { link = it.url }.quote }

    suspend fun email(note: String): Boolean {
        emailError = null
        acting = "email"
        return try {
            state = Loadable.Loaded(client.send(ProQuoteEndpoints.email(quoteId, note)).quote); toast = "Emailed."; true
        } catch (e: CancellationException) { throw e } catch (e: Exception) { emailError = (e as? ApiError)?.first("note") ?: e.userMessage; false } finally { acting = null }
    }

    suspend fun askDeposit(cents: Int): ProDocument? = act("deposit", "Deposit invoice made.") { client.send(ProInvoiceEndpoints.deposit(quoteId, cents)).invoice }
}

/// One own quote (web `QuoteDetail.vue`): the status's next step, the
/// approval link, deposits, emails and the document itself.
@Composable
fun OwnQuoteScreen(quoteId: Int, nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val model = remember(quoteId) { OwnQuoteModel(quoteId, environment.client) }
    LaunchedEffect(model) { model.load() }
    // Back from the editor or the foreground: re-read.
    var resumed by remember { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleResumeEffect(model) { if (resumed) scope.launch { model.load() }; resumed = true; onPauseOrDispose { } }
    val live = LocalNavBadges.current?.liveEvents ?: 0
    LaunchedEffect(live) { if (live > 0 && model.acting == null) model.load() }
    var menu by remember { mutableStateOf(false) }
    var emailing by remember { mutableStateOf(false) }
    var deposit by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val quote = model.state.value

    fun pdf() = scope.launch { model.acting = "pdf"; ToolsPdf.share(context, environment.client, ProQuoteEndpoints.pdf(quoteId), "Quote-${quote?.number ?: quoteId}.pdf")?.let { model.toast = it }; model.acting = null }

    Column {
        ZTopBar(quote?.number?.let { "Quote $it" } ?: "Draft quote", onBack = { nav.back() }, actions = {
            if (quote != null) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (quote.status == "sent") DropdownMenuItem(text = { Text("Revise") }, onClick = { menu = false; nav.editQuote(quote.id) })
                    if (quote.status == "accepted" && quote.invoice == null && quote.depositLeftCents > 0) DropdownMenuItem(text = { Text(if (quote.deposits.isNotEmpty()) "Ask for another deposit" else "Ask for a deposit") }, onClick = { menu = false; deposit = true })
                    if (quote.status != "draft") DropdownMenuItem(text = { Text("Copy into a new draft") }, onClick = { menu = false; scope.launch { model.duplicate()?.let { nav.quote(it.id) } } })
                    DropdownMenuItem(text = { Text("Download PDF") }, onClick = { menu = false; pdf() })
                }
            }
        })
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { q ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        ZHeroPanel {
                            val (label, tone) = ToolsFormat.quoteStatus(q.status)
                            ZBadge(label, tone)
                            Text(q.number?.let { "Quote $it" } ?: "Draft quote", style = ZType.title, color = Color.White)
                            Text("${q.customer?.name ?: "Customer"} · ${Money.format(q.totalCents)}", style = ZType.caption, color = Color.White.copy(alpha = 0.75f))
                        }
                        QuoteActions(q, model, nav, onEmail = { emailing = true })

                        if (q.deposits.isNotEmpty()) {
                            ZCard {
                                ZHeadline("Deposits")
                                for (d in q.deposits) ClickRow({ nav.invoice(d.id) }) {
                                    Row { ZBody("Deposit ${d.number ?: "(draft)"}", Modifier.weight(1f)); ZBody(Money.format(d.subtotalCents)) }
                                }
                                ZCaption("${Money.format(q.depositLeftCents)} still to bill.")
                            }
                        }
                        OwnQuotesLogic.linkLine(q)?.let { line ->
                            ZCard {
                                ZHeadline("Customer's approval link")
                                ZBody(line)
                                if (q.link?.canCopy == true) ZButton("Copy a link to text", style = ZButtonStyle.OUTLINE, loading = model.acting == "link") { scope.launch { model.makeLink() } }
                            }
                        }
                        DocumentEmailsCard(q.emails)
                        if (q.letterhead?.email.isNullOrBlank()) {
                            ZCard {
                                ZBody("Your quotes show your phone but no email. Add a business email so customers can write back.")
                                com.zivett.app.design.ZTextAction("Add it to your business profile") { nav.push(com.zivett.app.app.OrgProfileRoute(com.zivett.app.app.Areas.COMPANY)) }
                            }
                        }
                        DocumentSheetCard(q)
                        if (q.status == "draft") {
                            ZCard {
                                ZHeadline("Delete this draft")
                                ZCaption("The draft and its line items are removed. Nothing was sent.", tone = ZTextTone.SOFT)
                                ZButton("Delete draft", style = ZButtonStyle.OUTLINE) { deleting = true }
                            }
                        }
                        Spacer(Modifier.padding(ZSpacing.md))
                    }
                }
            }
        }
    }

    if (emailing && quote != null) SendDocumentSheet(quote, model.acting == "email", model.emailError, onDismiss = { emailing = false }, onPdf = { pdf() }, onCustomer = { quote.customer?.id?.let { nav.customer(it) } }) { note ->
        scope.launch { if (model.email(note)) emailing = false }
    }
    if (deposit && quote != null) DepositSheet(quote, model.acting == "deposit", onDismiss = { deposit = false }) { cents ->
        scope.launch { model.askDeposit(cents)?.let { deposit = false; nav.invoice(it.id) } }
    }
    model.link?.let { url ->
        ZSheet({ model.link = null }, title = "Approval link") {
            ZMonoBody(url)
            ZCaption("This is the only time it's shown. Copy a new one if you lose it.")
            ZButton("Copy link") { copy(context, url); model.toast = "Copied." }
            ZButton("Share", style = ZButtonStyle.OUTLINE) { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), null)) }
        }
    }
    if (deleting) ToolsConfirm("Delete this draft?", "The draft and its line items are deleted. This can't be undone.", "Delete draft", danger = true, onDismiss = { deleting = false }) {
        scope.launch { runCatching { environment.client.send(ProQuoteEndpoints.delete(quoteId)) }.onSuccess { nav.back() }.onFailure { model.toast = it.userMessage } }
    }
}

fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("Link", text))
}

@Composable
private fun QuoteActions(q: ProDocument, model: OwnQuoteModel, nav: ToolsNav, onEmail: () -> Unit) {
    val scope = rememberCoroutineScope()
    val actions = OwnQuotesLogic.actions(q)
    if (actions.isEmpty()) return
    // The last action is the page's next step (filled); the rest outlined.
    for ((index, action) in actions.reversed().withIndex()) {
        val style = if (index == 0) ZButtonStyle.PRIMARY else ZButtonStyle.OUTLINE
        when (action) {
            "edit" -> ZButton("Edit", style = style) { nav.editQuote(q.id) }
            "mark_sent" -> ZButton("Mark as sent", style = style, loading = model.acting == "send") { scope.launch { model.markSent() } }
            "email" -> ZButton(OwnQuotesLogic.emailLabel(q), style = style, onClick = onEmail)
            "decline" -> ZButton("Mark declined", style = style, loading = model.acting == "decline") { scope.launch { model.answer(false) } }
            "accept" -> ZButton("Mark accepted", style = style, loading = model.acting == "accept") { scope.launch { model.answer(true) } }
            "open_invoice" -> ZButton("Open invoice${q.invoice?.number?.let { " $it" } ?: ""}", style = style) { q.invoice?.let { nav.invoice(it.id) } }
            "invoice" -> ZButton(if (q.deposits.isNotEmpty()) "Invoice the rest" else "Invoice it now", style = style) { nav.newInvoice(quoteId = q.id) }
            "open_job" -> ZButton("Open job ${q.job?.code.orEmpty()}", style = style) { q.job?.let { nav.job(it.id) } }
            "schedule" -> ZButton("Schedule the job", style = style) { nav.newJob(quoteId = q.id) }
        }
    }
}

/// Email a quote or invoice (web `SendDocumentModal`).
@Composable
fun SendDocumentSheet(document: ProDocument, sending: Boolean, error: String?, onDismiss: () -> Unit, onPdf: () -> Unit, onCustomer: () -> Unit, onSend: (String) -> Unit) {
    val noun = if (document.isInvoice) "invoice" else "quote"
    var note by remember { mutableStateOf("") }
    val preflight = document.email
    ZSheet(onDismiss, title = "Email this $noun", dismissable = !sending) {
        if (preflight?.recipient == null) {
            ZBody("${document.customer?.name ?: "This customer"} has no email address saved. Add one to their customer record to email them, or download the PDF and send it yourself.")
            ZButton("Download PDF", style = ZButtonStyle.OUTLINE) { onDismiss(); onPdf() }
            ZButton("Open ${document.customerFirstName}'s record") { onDismiss(); onCustomer() }
        } else {
            ZLabel("To"); ZBody(preflight.recipient)
            ZLabel("From"); ZBody(document.letterhead?.name ?: "Your business")
            preflight.replyTo?.let { ZLabel("Replies go to"); ZBody(it) } ?: ZCaption("You have no business email, so replies won't reach you and you won't get a copy.")
            if (!document.isInvoice) ZCaption("The email includes a link for ${document.customer?.name ?: "the customer"} to approve or decline the quote online. You're told as soon as they answer.")
            if (document.status == "draft") ZCaption(if (document.isInvoice) "Sending it issues the invoice and gives it its number." else "Sending it gives the quote its code and marks it sent.")
            ZTextArea("Note to ${document.customerFirstName} (optional)", note, { note = it.take(500) }, placeholder = "Thanks for having us out. Let me know if you have any questions.", minLines = 3)
            ZCaption("Plain text. Links and web addresses aren't allowed.")
            ZCaption("${Money.count(preflight.sentToday)} of ${Money.count(preflight.dailyLimit)} emails sent today")
            preflight.blocked?.let { ZBanner(it, tone = ZTone.WARNING) }
            error?.let { ZBanner(it, tone = ZTone.DANGER) }
            if (preflight.blocked != null) ZButton("Download PDF", style = ZButtonStyle.OUTLINE) { onDismiss(); onPdf() }
            else ZButton("Send email", loading = sending) { onSend(note) }
            ZButton("Cancel", style = ZButtonStyle.GHOST, onClick = onDismiss)
        }
    }
}

/// Part of an accepted quote billed now, before tax, taken off the final
/// invoice automatically.
@Composable
private fun DepositSheet(quote: ProDocument, saving: Boolean, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var percent by remember { mutableStateOf(30) }
    var dollars by remember { mutableStateOf("") }
    val cents = if (dollars.isNotBlank()) Money.parseCents(dollars) ?: 0 else Math.round(quote.subtotalCents * percent / 100.0).toInt()
    ZSheet(onDismiss, title = "Ask for a deposit", dismissable = !saving) {
        ZBody("A separate invoice for part of this quote, before tax. When you invoice the rest, the deposit comes off automatically.")
        com.zivett.app.design.ZSegmented(listOf(25 to "25%", 30 to "30%", 50 to "50%"), if (dollars.isBlank()) percent else -1) { percent = it; dollars = "" }
        ZTextField("Or an amount ($)", dollars, { dollars = it }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
        ZCaption("${Money.format(cents)} of ${Money.format(quote.depositLeftCents)} still to bill.")
        ZButton("Make the deposit invoice", loading = saving, enabled = cents > 0 && cents <= quote.depositLeftCents) { onSave(cents) }
        ZButton("Cancel", style = ZButtonStyle.GHOST, onClick = onDismiss)
    }
}

// ── Editor ──────────────────────────────────────────────────────────

class OwnQuoteEditorModel(val quoteId: Int?, private val client: ApiClient) {
    val customer = DocumentCustomerState(client)
    var context by mutableStateOf<DocumentContext?>(null)
    var existing by mutableStateOf<ProDocument?>(null)
    var loadError by mutableStateOf<String?>(null)
    var lines by mutableStateOf(listOf(DocumentLogic.blankLine()))
    var pricingType by mutableStateOf("fixed")
    var validUntil by mutableStateOf("")
    var exclusions by mutableStateOf("")
    var notes by mutableStateOf("")
    var chargeGst by mutableStateOf(false)
    var chargePst by mutableStateOf(false)
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
    var message by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
        private set

    val isDraft: Boolean get() = existing == null || existing?.status == "draft"

    suspend fun load(customerId: Int?) {
        try {
            val ctx = client.send(ProQuoteEndpoints.context())
            context = ctx
            validUntil = ctx.defaultValidUntil.orEmpty()
            chargeGst = ctx.tax.gstAvailable
            if (quoteId != null) {
                val quote = client.send(ProQuoteEndpoints.show(quoteId)).quote
                existing = quote
                quote.customer?.id?.let { customer.pick(it, keepAddress = quote.proCustomerAddressId) }
                customer.addressId = quote.proCustomerAddressId
                lines = DocumentLogic.drafts(quote.lineItems).ifEmpty { listOf(DocumentLogic.blankLine()) }
                pricingType = quote.pricingType ?: "fixed"
                validUntil = quote.validUntil.orEmpty()
                exclusions = quote.exclusions.orEmpty()
                notes = quote.notes.orEmpty()
                chargeGst = (quote.gstBps ?: 0) > 0
                chargePst = (quote.pstBps ?: 0) > 0
            } else if (customerId != null) customer.pick(customerId)
            loadError = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) { loadError = e.userMessage }
    }

    /// The saved quote, or null with errors set. `andSend` marks a draft sent.
    suspend fun save(andSend: Boolean): ProDocument? {
        errors = emptyMap(); message = null
        val person = customer.customer ?: run { errors = mapOf("pro_customer_id" to "Choose who this quote is for."); return null }
        saving = true
        return try {
            val body = ProQuoteBody(person.id, customer.addressId, QuoteLineLogic.body(lines), pricingType, validUntil.ifBlank { null }, exclusions.trim().ifBlank { null }, notes.trim().ifBlank { null }, chargeGst, chargePst)
            var saved = if (quoteId != null) client.send(ProQuoteEndpoints.update(quoteId, body)).quote else client.send(ProQuoteEndpoints.create(body)).quote
            if (andSend && saved.status == "draft") saved = client.send(ProQuoteEndpoints.send(saved.id)).quote
            saved
        } catch (e: CancellationException) { throw e } catch (e: ApiError.Validation) {
            errors = e.errors.firstMessages
            message = if (errors.isNotEmpty()) "Check the highlighted fields." else e.errors.message
            null
        } catch (e: Exception) { message = e.userMessage; null } finally { saving = false }
    }

    fun lineError(): String? = errors.entries.firstOrNull { it.key.startsWith("line_items") }?.value
}

/// New / edit own quote (web `QuoteEditor.vue`).
@Composable
fun OwnQuoteEditorScreen(quoteId: Int?, customerId: Int?, nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember(quoteId) { OwnQuoteEditorModel(quoteId, environment.client) }
    LaunchedEffect(model) { model.load(customerId) }
    val ctx = model.context

    Column {
        ZTopBar(if (quoteId != null) "Edit quote${model.existing?.number?.let { " $it" } ?: ""}" else "New quote", onBack = { nav.back() })
        ZScreen {
            ZCaption("For one of your own customers. 0% ZiVETT commission.")
            when {
                model.loadError != null -> ZErrorState(model.loadError.orEmpty()) { scope.launch { model.load(customerId) } }
                ctx == null -> ZSpinner()
                quoteId == null && ctx.atQuoteLimit -> ZCard {
                    ZHeadline("You've used this month's quotes")
                    ZBody(ctx.upgrade?.let { "Your plan includes ${ctx.quotesPerMonth ?: 0} quotes a month. The ${it.name} plan includes more." } ?: "Your plan includes ${ctx.quotesPerMonth ?: 0} quotes a month. More come with next month.")
                    ZCaption("Invoices and recording payments are never limited.")
                    if (ctx.upgrade != null) ZButton("See plans") { nav.plan() }
                }
                else -> {
                    DocumentCustomerCard(model.customer, model.errors["pro_customer_id"], model.errors["pro_customer_address_id"])
                    DocumentLinesCard(model.lines, { model.lines = it }, model.lineError())
                    ZCard {
                        ZHeadline("Terms")
                        ZLabel("Price")
                        ZChoiceTile("FX", "Fixed price", "This is the price for the work described.", model.pricingType == "fixed") { model.pricingType = "fixed" }
                        ZChoiceTile("ES", "Estimate", "Your best estimate; it may change with their approval.", model.pricingType == "estimate") { model.pricingType = "estimate" }
                        ZDateField("Valid until", model.validUntil, { model.validUntil = it }, error = model.errors["valid_until"])
                        ZTextArea("Not included (optional)", model.exclusions, { model.exclusions = it.take(2000) }, placeholder = "Drywall repair, permit fees", error = model.errors["exclusions"], minLines = 2)
                        ZTextArea("Note on this quote (optional)", model.notes, { model.notes = it.take(2000) }, error = model.errors["notes"], minLines = 2)
                        ZCaption("Printed on this quote for the customer to read. It isn't saved to their customer record.")
                    }
                    DocumentTotalsCard(model.lines, ctx.tax.gstBps, ctx.tax.pstBps, ctx.tax.gstAvailable, model.chargeGst, { model.chargeGst = it }, model.chargePst, { model.chargePst = it }, model.errors["charge_gst"])
                    model.message?.let { ZBanner(it, tone = ZTone.DANGER) }
                    val blocked = DocumentLogic.incomplete(model.lines)
                    if (model.isDraft) {
                        ZButton("Save and mark as sent", loading = model.saving, enabled = !blocked) { scope.launch { model.save(andSend = true)?.let { nav.back(); if (quoteId == null) nav.quote(it.id) } } }
                        ZButton("Save draft", style = ZButtonStyle.OUTLINE, enabled = !model.saving && !blocked) { scope.launch { model.save(andSend = false)?.let { nav.back(); if (quoteId == null) nav.quote(it.id) } } }
                    } else {
                        ZButton("Save changes", loading = model.saving, enabled = !blocked) { scope.launch { model.save(andSend = false)?.let { nav.back() } } }
                    }
                    if (blocked) ZCaption("Describe every line and give it a quantity.")
                    ZButton("Cancel", style = ZButtonStyle.GHOST) { nav.back() }
                }
            }
            Spacer(Modifier.padding(ZSpacing.md))
        }
    }
}

@Suppress("unused")
private fun keep(line: QuoteLineDraft) = line
