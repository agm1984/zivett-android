package com.zivett.app.features.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
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
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.OwnAddressBody
import com.zivett.app.core.models.LabelValue
import com.zivett.app.core.models.MarketingBody
import com.zivett.app.core.models.ProCustomer
import com.zivett.app.core.models.ProCustomerEndpoints
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZAddressFields
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZCheckbox
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZHeroPanel
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.design.ZType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/// The customer page's pure wording (web `CustomerDetail.vue`,
/// `MarketingConsentCard.vue`).
object CustomerDetailLogic {
    private val sourceWords = mapOf(
        "request_form" to "by ticking the box on your request form",
        "link" to "from the link in one of your emails",
        "company" to "and your team recorded it",
    )

    fun marketingExplanation(status: ProCustomer.Marketing): String = when (status.reason) {
        "opted_in" -> "Said yes to offers on ${ToolsFormat.day(status.since)} ${sourceWords[status.source].orEmpty()}.".replace(" .", ".")
        "recent" -> "A recent customer, so you can email them until ${ToolsFormat.day(status.impliedUntil)}. Ask them to say yes to keep emailing after that."
        "unsubscribed" -> if (status.source == "company") "Taken off on ${ToolsFormat.day(status.since)} at their request. Tick to record that they've said yes again."
            else "Unsubscribed on ${ToolsFormat.day(status.since)}. Tick only if they've told you they want offers again."
        "not_recent" -> "No recent work with them, so they need to say yes before you email them offers."
        else -> "Add an email for this customer to send them campaigns."
    }

    fun campaignProblem(problem: String): String = when (problem) {
        "failed" -> "Couldn't send"
        "bounced" -> "Bounced"
        "complained" -> "Marked as spam"
        "reported" -> "Reported"
        else -> problem
    }

    /// "3 campaigns · clicked 1 of 2 with a button · booked a job within 30 days of 1."
    fun campaignLine(items: List<ProCustomer.CampaignSend>): String {
        val withButton = items.filter { it.hadButton }
        val booked = items.count { it.booked != null }
        return listOfNotNull(
            "${items.size} ${if (items.size == 1) "campaign" else "campaigns"}${if (items.size >= 20) " (the last 20)" else ""}",
            if (withButton.isNotEmpty()) "clicked ${withButton.count { it.clickedAt != null }} of ${withButton.size} with a button" else null,
            if (booked > 0) "booked a job within 30 days of $booked" else "no booking within 30 days of one yet",
        ).joinToString(" · ") + "."
    }

    fun campaignOutcome(item: ProCustomer.CampaignSend): String {
        item.problem?.let { return campaignProblem(it) }
        val parts = listOfNotNull(
            item.booked?.let { "Booked ${ToolsFormat.day(it.bookedAt)}" + (it.code?.let { code -> ": $code" } ?: "") },
            if (item.hadButton) (item.clickedAt?.let { "Clicked ${ToolsFormat.day(it)}" } ?: "Didn't click") else null,
        )
        return parts.joinToString(" · ").ifBlank { "Delivered" }
    }

    fun unit(line2: String): String = if (line2.firstOrNull()?.isDigit() == true) "Unit $line2" else line2
}

class CustomerDetailModel(val customerId: Int, private val client: ApiClient) {
    var state by mutableStateOf<Loadable<ProCustomer>>(Loadable.Loading)
    var sources by mutableStateOf<List<LabelValue>>(emptyList())
    var busy by mutableStateOf(false)
        private set
    var toast by mutableStateOf<String?>(null)
    var marketingError by mutableStateOf<String?>(null)

    suspend fun load() {
        state = state.reloaded { client.send(ProCustomerEndpoints.show(customerId)).customer }
        if (sources.isEmpty()) sources = runCatching { client.send(ProCustomerEndpoints.list("active", null)).sources }.getOrDefault(emptyList())
    }

    private suspend fun act(success: String?, operation: suspend () -> ProCustomer?): Boolean {
        if (busy) return false
        busy = true
        return try {
            operation()?.let { state = Loadable.Loaded(it) }
            success?.let { toast = it }
            true
        } catch (e: CancellationException) { throw e } catch (e: Exception) { toast = e.userMessage; false } finally { busy = false }
    }

    suspend fun setArchived(archived: Boolean) = act(if (archived) "Archived. They're off your active list." else "Restored to your active list.") {
        client.send(ProCustomerEndpoints.archive(customerId, archived)).customer
    }

    suspend fun setMarketing(optedIn: Boolean, confirmed: Boolean): Boolean {
        marketingError = null
        if (busy) return false
        busy = true
        return try {
            state = Loadable.Loaded(client.send(ProCustomerEndpoints.marketing(customerId, MarketingBody(optedIn, if (optedIn) confirmed else null))).customer)
            toast = if (optedIn) "Recorded. They'll get your campaigns." else "They won't get your campaigns any more."
            true
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            marketingError = (e as? ApiError)?.first("confirmed") ?: e.userMessage; false
        } finally { busy = false }
    }

    suspend fun archiveAddress(addressId: Int) = act("Address archived.") {
        client.send(ProCustomerEndpoints.archiveAddress(customerId, addressId)); client.send(ProCustomerEndpoints.show(customerId)).customer
    }

    suspend fun delete(): Boolean = act(null) { client.send(ProCustomerEndpoints.delete(customerId)); null }
}

/// One own customer (web `CustomerDetail.vue`): what they owe, their
/// requests, quotes, jobs, invoices and payments, the campaigns they got,
/// contact, addresses, promotional-email consent and notes; Edit,
/// New quote / job / invoice, Archive, and Delete apart at the foot.
@Composable
fun CustomerDetailScreen(customerId: Int, nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember(customerId) { CustomerDetailModel(customerId, environment.client) }
    LaunchedEffect(model) { model.load() }
    var editing by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf<ProCustomer.Address?>(null) }
    var addingAddress by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val customer = model.state.value

    Column {
        ZTopBar(customer?.name ?: "Customer", onBack = { nav.back() }, actions = {
            if (customer != null) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; editing = true })
                    DropdownMenuItem(text = { Text(if (customer.archived) "Restore" else "Archive") }, onClick = { menu = false; scope.launch { model.setArchived(!customer.archived) } })
                }
            }
        })
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { person ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        ZHeroPanel {
                            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                                if (person.archived) ZBadge("Archived")
                                if (person.unsubscribed) ZBadge("Unsubscribed from campaigns")
                            }
                            Text(person.name, style = ZType.title, color = Color.White)
                            Text(listOfNotNull(person.sourceLabel, person.createdAt?.let { "added ${ToolsFormat.day(it)}" }).joinToString(" · "), style = ZType.caption, color = Color.White.copy(alpha = 0.75f))
                        }
                        if (!person.archived) {
                            ZButton("New quote") { nav.newQuote(person.id) }
                            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                                ZButton("New job", style = ZButtonStyle.OUTLINE, compact = true, modifier = Modifier.weight(1f)) { nav.newJob(customerId = person.id) }
                                ZButton("New invoice", style = ZButtonStyle.OUTLINE, compact = true, modifier = Modifier.weight(1f)) { nav.newInvoice(customerId = person.id) }
                            }
                        }

                        ZCard {
                            ZLabel("Owed to you")
                            Text(Money.format(person.balanceCents), style = ZType.stat, color = com.zivett.app.design.ZTheme.colors.ink)
                            ZCaption("Across their unpaid invoices. 0% ZiVETT commission on this work.", tone = ZTextTone.SOFT)
                        }

                        ContactCard(person)
                        AddressesCard(person, model, onAdd = { addingAddress = true }, onEdit = { address = it })
                        person.marketing?.let { status ->
                            ZCard {
                                ZHeadline("Promotional emails")
                                ZCheckbox(status.receives, { consent = true }, "Gets your campaigns", modifier = Modifier)
                                ZCaption(CustomerDetailLogic.marketingExplanation(status), tone = ZTextTone.SOFT)
                            }
                        }
                        ZCard {
                            ZHeadline("Notes")
                            ZBody(person.notes?.takeIf { it.isNotBlank() } ?: "Nothing yet. Only your team sees these.", tone = if (person.notes.isNullOrBlank()) ZTextTone.MUTED else ZTextTone.INK)
                            if (person.noReminders) ZCaption("No automatic reminders for this customer.")
                        }

                        HistoryCards(person, nav)

                        if (person.campaigns.isNotEmpty()) {
                            ZCard {
                                ZHeadline("Campaigns")
                                ZCaption(CustomerDetailLogic.campaignLine(person.campaigns))
                                for (item in person.campaigns) {
                                    ZDivider()
                                    ZTextAction(item.subject ?: "Campaign") { nav.campaign(item.campaignId) }
                                    ZCaption("Sent ${ToolsFormat.day(item.sentAt)} · ${CustomerDetailLogic.campaignOutcome(item)}", tone = if (item.problem != null) ZTextTone.INK else ZTextTone.MUTED)
                                }
                            }
                        }

                        ZCard {
                            ZHeadline("Delete customer")
                            ZCaption(
                                if (person.deleteAnonymises) "Removes ${person.name}'s name, contact details, addresses and notes. Their jobs and invoices stay on your books under \"Deleted customer\". To keep working for them, archive them instead."
                                else "Removes ${person.name} and their addresses for good. To keep their history, archive them instead.",
                                tone = ZTextTone.SOFT,
                            )
                            ZButton("Delete customer", style = ZButtonStyle.OUTLINE) { deleting = true }
                        }
                        Spacer(Modifier.padding(ZSpacing.md))
                    }
                }
            }
        }
    }

    if (editing && customer != null) {
        CustomerFormSheet(editing = customer, sources = model.sources, onPlan = { nav.plan() }, onDismiss = { editing = false }) {
            model.state = Loadable.Loaded(it); model.toast = "Customer saved."
        }
    }
    if ((addingAddress || address != null) && customer != null) {
        AddressSheet(customer.id, address, environment.client, onDismiss = { addingAddress = false; address = null }) {
            scope.launch { model.load() }; model.toast = if (address != null) "Address saved." else "Address added."
        }
    }
    if (consent && customer?.marketing != null) {
        MarketingSheet(customer, model) { consent = false }
    }
    if (deleting && customer != null) {
        ToolsConfirm(
            "Delete ${customer.name}?",
            "Their name, contact details, addresses and notes are deleted and can't be brought back. Archive them instead if you might work for them again." +
                if (customer.deleteAnonymises) " Their jobs and invoices stay on your books under \"Deleted customer\"." else "",
            "Delete customer", danger = true, onDismiss = { deleting = false },
        ) { scope.launch { if (model.delete()) nav.back() } }
    }
}

@Composable
private fun ContactCard(person: ProCustomer) {
    ZCard {
        ZHeadline("Contact")
        ZLabel("Phone")
        person.phone?.let { ToolsContact(it, phone = true) } ?: ZCaption("No phone")
        ZLabel("Email")
        person.email?.let { ToolsContact(it, phone = false) } ?: ZCaption("No email")
    }
}

@Composable
private fun AddressesCard(person: ProCustomer, model: CustomerDetailModel, onAdd: () -> Unit, onEdit: (ProCustomer.Address) -> Unit) {
    val scope = rememberCoroutineScope()
    ZCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZHeadline("Addresses", Modifier.weight(1f))
            ZTextAction("Add", onClick = onAdd)
        }
        val active = person.activeAddresses
        if (active.isEmpty()) ZCaption("No service address yet.")
        for (address in active) {
            address.label?.takeIf { it.isNotBlank() }?.let { ZLabel(it) }
            ZBody(address.oneLine)
            address.addressLine2?.takeIf { it.isNotBlank() }?.let { ZCaption(CustomerDetailLogic.unit(it), tone = ZTextTone.SOFT) }
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                ZTextAction("Edit") { onEdit(address) }
                ZTextAction("Archive", color = com.zivett.app.design.ZTheme.colors.inkSoft, enabled = !model.busy) { scope.launch { model.archiveAddress(address.id) } }
            }
        }
    }
}

@Composable
private fun HistoryCards(person: ProCustomer, nav: ToolsNav) {
    val history = person.history
    if (history.requests.isNotEmpty()) {
        ZCard {
            ZHeadline("Requests")
            ZCaption("What ${person.firstName} asked for through your request form.")
            for (item in history.requests) {
                ZDivider()
                ZCaption(listOfNotNull(ToolsFormat.day(item.createdAt), item.trade, item.timing).joinToString(" · "))
                item.message?.let { ZBody(it) }
            }
        }
    }
    @Composable
    fun header(title: String, action: String, onAction: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZHeadline(title, Modifier.weight(1f))
            if (!person.archived) ZTextAction(action, onClick = onAction)
        }
    }
    ZCard {
        header("Quotes", "New quote") { nav.newQuote(person.id) }
        if (history.quotes.isEmpty()) ZCaption("Quotes you send ${person.firstName} will show here.")
        for (row in history.quotes) { ZDivider(); ClickRow({ nav.quote(row.id) }) { DocumentLine(row, invoice = false) } }
    }
    ZCard {
        header("Jobs", "New job") { nav.newJob(customerId = person.id) }
        if (history.jobs.isEmpty()) ZCaption("Jobs you book for ${person.firstName} will show here, and on your calendar.")
        for (job in history.jobs) { ZDivider(); ClickRow({ nav.job(job.id) }) { JobLine(job) } }
    }
    ZCard {
        header("Invoices and payments", "New invoice") { nav.newInvoice(customerId = person.id) }
        if (history.invoices.isEmpty() && history.payments.isEmpty()) ZCaption("Invoices you send ${person.firstName}, and what they've paid you, will show here.")
        for (row in history.invoices) { ZDivider(); ClickRow({ nav.invoice(row.id) }) { DocumentLine(row, invoice = true) } }
        if (history.payments.isNotEmpty()) {
            ZLabel("Payments (${ToolsCopy.PRO_REPORTED.lowercase()})")
            for (payment in history.payments) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZCaption(listOfNotNull(ToolsFormat.day(payment.paidOn), payment.methodLabel, payment.invoiceNumber).joinToString(" · "), Modifier.weight(1f))
                    ZBodyStrong(Money.format(payment.amountCents))
                }
            }
        }
    }
}

/// Add or edit one service address (web `CustomerAddressModal`).
@Composable
private fun AddressSheet(customerId: Int, editing: ProCustomer.Address?, client: ApiClient, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf(editing?.label.orEmpty()) }
    var line by remember { mutableStateOf(editing?.oneLine.orEmpty()) }
    var unit by remember { mutableStateOf(editing?.addressLine2.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf<String?>(null) }

    fun save(confirm: Boolean) {
        saving = true; error = null
        scope.launch {
            try {
                val body = OwnAddressBody(line.trim(), unit.trim().ifBlank { null }, label.trim().ifBlank { null }, if (confirm) true else null)
                if (editing != null) client.send(ProCustomerEndpoints.updateAddress(customerId, editing.id, body)) else client.send(ProCustomerEndpoints.addAddress(customerId, body))
                onDismiss(); onSaved()
            } catch (e: CancellationException) { throw e } catch (e: ApiError.Conflict) {
                confirming = runCatching { JsonCoding.json.decodeFromString<com.zivett.app.core.models.CustomerRefusal>(e.body.decodeToString()).message }.getOrNull() ?: e.userMessage
            } catch (e: Exception) {
                error = (e as? ApiError)?.first("address_line1") ?: e.userMessage
            } finally { saving = false }
        }
    }

    ZSheet(onDismiss, title = if (editing != null) "Edit address" else "Add an address", dismissable = !saving) {
        val ask = confirming
        if (ask != null) {
            ZHeadline("Is this a different person?")
            ZBody(ask)
            ZButton("Yes, it's a different person", loading = saving) { save(true) }
            ZButton("Go back", style = ZButtonStyle.GHOST) { confirming = null }
        } else {
            ZTextField("Label (optional)", label, { label = it.take(60) }, placeholder = "Home, Cottage, Rental")
            ZAddressFields(line, { line = it }, error = error)
            ZTextField("Unit, suite, etc. (optional)", unit, { unit = it })
            ZButton(if (editing != null) "Save address" else "Add address", loading = saving, enabled = line.isNotBlank()) { save(false) }
        }
    }
}

/// The promotional-emails consent (web `MarketingConsentCard`): stopping is
/// one confirm; recording a yes needs the Pro to confirm the customer said so.
@Composable
private fun MarketingSheet(customer: ProCustomer, model: CustomerDetailModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val receives = customer.marketing?.receives == true
    var confirmed by remember { mutableStateOf(false) }
    ZSheet(onDismiss, title = if (receives) "Stop emailing ${customer.firstName} offers?" else "Record that ${customer.firstName} wants offers?", dismissable = !model.busy) {
        if (receives) {
            ZBody("${customer.name} won't get your campaigns any more. Quotes, invoices and reminders still go to them. To start again, they'll need to tell you they want offers.")
        } else {
            ZBody("Canada's anti-spam law lets you email offers to someone who said yes. Only tick this if ${customer.name} told you, in person, on the phone or in writing, that they want them.")
            ZCheckbox(confirmed, { confirmed = it }, "${customer.firstName} told me they want offers by email.")
        }
        model.marketingError?.let { ZBanner(it, tone = ZTone.DANGER) }
        ZButton(if (receives) "Stop the emails" else "Record their yes", loading = model.busy) {
            scope.launch { if (model.setMarketing(!receives, confirmed)) onDismiss() }
        }
        ZButton("Cancel", style = ZButtonStyle.OUTLINE, onClick = onDismiss)
    }
}
