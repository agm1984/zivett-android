package com.zivett.app.features.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
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
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.ExceptionRequest
import com.zivett.app.core.models.ProCustomerEndpoints
import com.zivett.app.core.models.ProCustomersResponse
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.userMessage
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZCheckLine
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZErrorState
import com.zivett.app.design.ZFilterPills
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.features.shared.LocalNavBadges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/// The customer list's pure wording (web `Customers.vue`).
object CustomersLogic {
    fun exceptionStatus(status: String): Pair<String, ZTone> = when (status) {
        "approved" -> "Approved" to ZTone.SUCCESS
        "declined" -> "Declined" to ZTone.NEUTRAL
        else -> "With ZiVETT support" to ZTone.INFO
    }

    /// "12 of 50 customers on your plan" — only when the plan caps it.
    fun allowanceLine(allowance: ProCustomersResponse.Allowance): String? =
        allowance.limit?.let { "${Money.count(allowance.used)} of ${Money.count(it)} customers on your plan." }

    /// First run: no customers at all, so the list frame shows the first step.
    fun firstRun(response: ProCustomersResponse): Boolean = response.counts.active == 0 && response.counts.archived == 0
}

class CustomersModel(private val client: ApiClient) {
    var status by mutableStateOf("active")
    var query by mutableStateOf("")
    var state by mutableStateOf<Loadable<ProCustomersResponse>>(Loadable.Loading)
    var loadingMore by mutableStateOf(false)
    private var page = 1
    private var ticket = 0

    /// Keeps only the newest answer (the web's `latest` ticket).
    suspend fun load() {
        val mine = ++ticket
        page = 1
        try {
            val response = client.send(ProCustomerEndpoints.list(status, query))
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
            val next = client.send(ProCustomerEndpoints.list(status, query, page + 1))
            page += 1
            state = Loadable.Loaded(next.copy(customers = current.customers + next.customers))
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
        } finally { loadingMore = false }
    }
}

/// The company's own customer list (web `Customers.vue`): Active /
/// Archived, a search over name, phone, email or address, the plan's
/// limit, and "Customers you've asked us about". Import and CSV export are
/// web-only.
@Composable
fun CustomersScreen(nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember { CustomersModel(environment.client) }
    var adding by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(model) {
        ToolsPendingFilter.take({ ToolsPendingFilter.customers }, { ToolsPendingFilter.customers = null })?.let { model.status = it }
        model.load()
    }
    LaunchedEffect(ToolsPendingFilter.addCustomer) { if (ToolsPendingFilter.addCustomer) { ToolsPendingFilter.addCustomer = false; adding = true } }
    val live = LocalNavBadges.current?.liveEvents ?: 0
    LaunchedEffect(live) { if (live > 0) model.load() }

    ZScreen(onRefresh = { model.load() }) {
        ZPageTitle("Customers", "The people you work for directly. 0% ZiVETT commission on their work.")
        when (val state = model.state) {
            Loadable.Loading -> ZSpinner()
            is Loadable.Failed -> ZErrorState("Could not load your customers.") { scope.launch { model.load() } }
            is Loadable.Loaded -> {
                val response = state.loaded
                val atLimit = response.allowance.atLimit
                if (response.exceptionRequests.isNotEmpty()) ExceptionRequestsCard(response.exceptionRequests, nav)
                if (CustomersLogic.firstRun(response) && model.query.isBlank()) {
                    ZCard {
                        ZHeadline("Add your first customer")
                        ZCaption("Their contact details, service addresses and notes, then every quote, job and invoice you send them.", tone = ZTextTone.SOFT)
                        for (line in ToolsCopy.ownershipPromise.take(3)) ZCheckLine(line, tone = ZTextTone.INK)
                        ZButton("Add customer", enabled = !atLimit) { adding = true }
                        ZCaption("Importing from a spreadsheet is on the web, at zivett.com.")
                    }
                } else {
                    ZButton("Add customer", enabled = !atLimit) { adding = true }
                    ZFilterPills(listOf(Triple("active", "Active", response.counts.active), Triple("archived", "Archived", response.counts.archived)), model.status) {
                        model.status = it; scope.launch { model.load() }
                    }
                    ZTextField("Search", model.query, { text ->
                        model.query = text
                        searchJob?.cancel()
                        searchJob = scope.launch { delay(300); model.load() }
                    }, placeholder = "Name, phone, email or address")
                    if (atLimit) {
                        ZCard {
                            ZHeadline("You've reached your customer limit")
                            ZCaption(response.allowance.upgrade?.let { "The ${it.name} plan holds more customers." } ?: "Archive customers you no longer work for to make room.", tone = ZTextTone.SOFT)
                            if (response.allowance.upgrade != null) ZTextAction("See plans") { nav.plan() }
                        }
                    } else CustomersLogic.allowanceLine(response.allowance)?.let { ZCaption(it) }

                    if (response.customers.isEmpty()) {
                        ZEmptyState(Icons.Outlined.Group, if (model.query.isNotBlank()) "No matches" else "Nobody here", if (model.query.isNotBlank()) "Try a name, phone number, email or street." else "Archived customers show here.")
                    }
                    for (customer in response.customers) {
                        ZCard(onClick = { nav.customer(customer.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                                Column(Modifier.weight(1f)) {
                                    ZBodyStrong(customer.name, maxLines = 1)
                                    ZCaption(listOfNotNull(customer.phone, customer.email).joinToString(" · ").ifBlank { "No contact details" }, maxLines = 1)
                                    customer.address?.let { ZCaption(it + if (customer.addressesCount > 1) " (+${customer.addressesCount - 1})" else "", maxLines = 1) }
                                }
                                if (customer.balanceCents > 0) Column(horizontalAlignment = Alignment.End) {
                                    ZBodyStrong(Money.format(customer.balanceCents))
                                    ZCaption("Owed")
                                }
                            }
                        }
                    }
                    if (response.hasMore) ZButton("Show more", style = ZButtonStyle.OUTLINE, loading = model.loadingMore) { scope.launch { model.more() } }
                }
            }
        }
        Spacer(Modifier.padding(ZSpacing.md))
    }

    if (adding) {
        val response = model.state.value
        CustomerFormSheet(
            sources = response?.sources.orEmpty(),
            atLimit = response?.allowance?.atLimit == true,
            upgrade = response?.allowance?.upgrade,
            onPlan = { nav.plan() },
            onDismiss = { adding = false },
        ) { customer -> scope.launch { model.load() }; nav.customer(customer.id) }
    }
}

@Composable
private fun ExceptionRequestsCard(requests: List<ExceptionRequest>, nav: ToolsNav) {
    ZCard {
        ZHeadline("Customers you've asked us about")
        ZCaption("You told us these were your customers before they booked you through ZiVETT. Support decides each one.", tone = ZTextTone.SOFT)
        for (request in requests) {
            val (label, tone) = CustomersLogic.exceptionStatus(request.status)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                val id = request.customerId
                if (id != null) ZTextAction(request.customerName) { nav.customer(id) } else ZBodyStrong(request.customerName)
                ZBadge(label, tone)
            }
            ZCaption("Asked ${ToolsFormat.day(request.createdAt)}" + (request.decidedAt?.let { " · decided ${ToolsFormat.day(it)}" } ?: ""))
            request.decisionNote?.let { ZBody(it) }
        }
    }
}
