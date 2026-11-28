package com.zivett.app.features.tools

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
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
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.ProCustomerEndpoints
import com.zivett.app.core.models.ProRequest
import com.zivett.app.core.models.ProRequestsResponse
import com.zivett.app.core.models.RequestForm
import com.zivett.app.core.models.ToolsEndpoints
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZChip
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZFilterPills
import com.zivett.app.design.ZFlowRow
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZQRCode
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.shared.LocalNavBadges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class RequestsModel(private val client: ApiClient) {
    var status by mutableStateOf("new")
        private set
    var state by mutableStateOf<Loadable<ProRequestsResponse>>(Loadable.Loading)
    var busy by mutableStateOf<Int?>(null)
    var savingForm by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    /// Customer sources for the add form (the customers list carries them).
    var sources by mutableStateOf<List<com.zivett.app.core.models.LabelValue>>(emptyList())

    suspend fun load(next: String = status) {
        if (next != status) { status = next; state = Loadable.Loading }
        state = state.reloaded { client.send(ToolsEndpoints.requests(status)) }
        if (sources.isEmpty()) sources = runCatching { client.send(ProCustomerEndpoints.list("active", null)).sources }.getOrDefault(emptyList())
    }

    private suspend fun act(id: Int, success: String, operation: suspend () -> Unit): Boolean {
        busy = id
        return try { operation(); toast = success; load(); true } catch (e: CancellationException) { throw e } catch (e: Exception) { toast = e.userMessage; false } finally { busy = null }
    }

    suspend fun markAdded(id: Int, customerId: Int) = act(id, "Saved on their customer page.") { client.send(ToolsEndpoints.requestAdded(id, customerId)) }
    suspend fun dismiss(id: Int) = act(id, "Dismissed.") { client.send(ToolsEndpoints.dismissRequest(id)) }

    /// enable | new_link | disable.
    suspend fun setForm(action: String) {
        val current = state.value ?: return
        savingForm = true
        try {
            val form = client.send(ToolsEndpoints.requestForm(action)).form
            state = Loadable.Loaded(current.copy(form = form))
            toast = when (action) { "disable" -> "Your request form is off."; "new_link" -> "New link made. The old one no longer works."; else -> "Your request form is on." }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { toast = e.userMessage } finally { savingForm = false }
    }
}

/// People asking for work through the company's own request form (web
/// `pages/company/business/Requests.vue`): new requests lead as cards
/// whose next step is "Add as customer" (the usual form, so the ZiVETT
/// customer check runs), then quote; handled ones sit in a second tab.
@Composable
fun RequestsScreen(nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember { RequestsModel(environment.client) }
    LaunchedEffect(model) { model.load() }
    val live = LocalNavBadges.current?.liveEvents ?: 0
    LaunchedEffect(live) { if (live > 0) model.load() }
    var adding by remember { mutableStateOf<ProRequest?>(null) }

    Column {
        ZTopBar("Requests", onBack = { nav.back() })
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                ZPageTitle("Requests", "People asking you for work through your own request form.")
                val counts = model.state.value?.counts
                ZFilterPills(listOf(Triple("new", "New", counts?.new), Triple("handled", "Handled", counts?.handled)), model.status) { scope.launch { model.load(it) } }
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { response ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        if (response.requests.isEmpty()) {
                            ZEmptyState(Icons.Outlined.Inbox, if (model.status == "new") "No new requests" else "Nothing handled yet", if (model.status == "new") "Share your request form to get some." else "Requests you add or dismiss show here.")
                        }
                        for (request in response.requests) RequestCard(request, model, nav, onAdd = { adding = request })
                        RequestFormCard(response.form, model)
                    }
                }
                Spacer(Modifier.padding(ZSpacing.md))
            }
        }
    }

    adding?.let { request ->
        CustomerFormSheet(
            prefill = CustomerPrefill(request.name, request.phone.orEmpty(), request.email.orEmpty(), request.address.orEmpty(), "website"),
            sources = model.sources,
            onPlan = { nav.plan() },
            onDismiss = { adding = null },
        ) { customer ->
            scope.launch { if (model.markAdded(request.id, customer.id)) nav.customer(customer.id) }
        }
    }
}

@Composable
private fun RequestCard(request: ProRequest, model: RequestsModel, nav: ToolsNav, onAdd: () -> Unit) {
    val scope = rememberCoroutineScope()
    ZCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
            ZHeadline(request.name, Modifier.weight(1f))
            if (request.isNew) {
                if (request.existingCustomer != null) ZBadge("Already a customer", ZTone.SUCCESS) else ZBadge("New to you", ZTone.INFO)
            }
        }
        request.createdAt?.let { ZCaption(ToolsFormat.day(it)) }
        request.existingCustomer?.takeIf { request.isNew }?.let { existing ->
            ZCaption("Their ${if (request.email != null) "email or phone" else "phone"} matches ${existing.name}${if (existing.archived) " (archived)" else ""} on your customer list.", tone = ZTextTone.SOFT)
        }
        ZFlowRow {
            request.category?.let { ZChip(it.name) }
            request.mode?.let { ZBadge(JobPresentation.modeMeta(it).label, ZTone.NEUTRAL) }
            if (request.marketingOptIn) ZBadge("Wants offers by email", ZTone.SUCCESS)
        }
        request.intakeAnswers?.takeIf { it.isNotEmpty() }?.let { answers ->
            ZFlowRow { for (answer in answers) if (answer.answers.isNotEmpty()) ZChip("${answer.prompt}: ${answer.answers.joinToString(", ")}") }
        }
        request.message?.takeIf { it.isNotBlank() }?.let { ZBody(it) }
        request.phone?.let { phone -> ZLabel("Phone"); ToolsContact(phone, phone = true) }
        request.email?.let { email -> ZLabel("Email"); ToolsContact(email, phone = false) }
        request.address?.let { ZLabel("Where"); ZBody(it) }
        val whenText = listOfNotNull(
            if (request.mode == "instant") "As soon as possible" else request.urgency?.let { ToolsFormat.urgency(it) },
            request.availabilityWindows?.takeIf { it.isNotEmpty() }?.joinToString(", ") { JobPresentation.windowSlot(it.date, it.window) },
            request.timing,
        )
        if (whenText.isNotEmpty()) { ZLabel("When"); ZBody(whenText.joinToString(". ")) }

        val busy = model.busy == request.id
        val existing = request.existingCustomer
        when {
            request.isNew && existing != null -> {
                ZButton("Quote it", loading = busy) { scope.launch { if (model.markAdded(request.id, existing.id)) nav.newQuote(existing.id) } }
                ZButton("Done, it's theirs", style = ZButtonStyle.OUTLINE, enabled = !busy) { scope.launch { model.markAdded(request.id, existing.id) } }
                ZButton("Dismiss", style = ZButtonStyle.GHOST, enabled = !busy) { scope.launch { model.dismiss(request.id) } }
            }
            request.isNew -> {
                ZButton("Add as customer", enabled = !busy, onClick = onAdd)
                ZButton("Dismiss", style = ZButtonStyle.GHOST, enabled = !busy) { scope.launch { model.dismiss(request.id) } }
            }
            request.status == "added" && request.customerId != null -> ZTextAction("Saved on their customer page") { nav.customer(request.customerId) }
            else -> ZCaption("Dismissed.")
        }
    }
}

/// The request form's link and QR code, and its switch (org admins).
@Composable
private fun RequestFormCard(form: RequestForm, model: RequestsModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ZCard {
        ZHeadline("Your request form")
        val url = form.url
        if (url == null) {
            ZCaption("A form for your website, Google listing or van: people describe the job, and it lands here for you to add as a customer and quote. It isn't listed anywhere. Only people you give the link to find it.", tone = ZTextTone.SOFT)
            if (form.canEdit) ZButton("Switch on my request form", loading = model.savingForm) { scope.launch { model.setForm("enable") } }
            else ZCaption("A company admin can switch it on.")
        } else {
            ZCaption("Put this link on your website, Google listing or van. People who use it land here, under your name.", tone = ZTextTone.SOFT)
            com.zivett.app.design.ZMonoBody(url)
            ZQRCode(url)
            ZButton("Share link", style = ZButtonStyle.OUTLINE) {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), "Share your request form"))
            }
            if (form.canEdit) {
                ZButton("Make a new link", style = ZButtonStyle.GHOST, enabled = !model.savingForm) { scope.launch { model.setForm("new_link") } }
                ZButton("Switch it off", style = ZButtonStyle.GHOST, enabled = !model.savingForm) { scope.launch { model.setForm("disable") } }
                ZCaption("A new link stops the old one working, if it's getting spam.")
            }
        }
    }
}
