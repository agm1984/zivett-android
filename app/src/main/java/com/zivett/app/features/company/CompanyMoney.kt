package com.zivett.app.features.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zivett.app.app.CompanyInvoiceRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.Invoice
import com.zivett.app.core.models.PayoutsResponse
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMonoBody
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZStatTile
import com.zivett.app.design.ZTitle
import com.zivett.app.design.ZTopBar
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.shared.InvoiceBreakdown
import com.zivett.app.features.shared.LocalNav
import com.zivett.app.features.shared.MoneyRow
import kotlinx.coroutines.launch

@Composable
fun PayoutsScreen(onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Loadable<PayoutsResponse>>(Loadable.Loading) }
    suspend fun load() { state = state.reloaded { environment.client.send(CompanyEndpoints.payouts()) } }
    LaunchedEffect(Unit) { load() }

    Column {
        ZTopBar("Payouts", onBack = onBack)
        ZScreen(onRefresh = { load() }) {
            ZLoadable(state, retry = { scope.launch { load() } }) { response ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    ZPageTitle("Payouts")
                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                        ZStatTile(Money.format(response.summary.pendingCents), "Pending", Modifier.weight(1f))
                        ZStatTile(Money.format(response.summary.paidThisMonthCents), "Paid this month", Modifier.weight(1f))
                        ZStatTile(Money.format(response.summary.paidCents), "Paid all-time", Modifier.weight(1f))
                    }
                    if (response.payouts.isEmpty()) ZEmptyState(Icons.Outlined.AccountBalance, "No payouts yet", "Payouts appear here once a job closes and its invoice settles.")
                    for (payout in response.payouts) {
                        ZCard(padding = ZSpacing.sm) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ZBodyStrong(payout.jobTitle ?: payout.invoiceNumber ?: "Payout", maxLines = 1)
                                    ZCaption(listOfNotNull(payout.invoiceNumber, payout.jobCode, (payout.paidAt ?: payout.scheduledAt)?.let { Dates.short(it) }).joinToString(" · "))
                                }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ZMonoBody(Money.format(payout.amountCents), weight = FontWeight.Bold)
                                    val meta = CompanyPresentation.payoutBadge(payout.status)
                                    ZBadge(meta.label, meta.tone)
                                }
                            }
                        }
                    }
                    ZCaption("Each payout is your invoice subtotal minus the platform commission, plus 100% of any tip. Pass-through materials carry no commission.")
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }
}

/// Company invoices list — includes what the booker owes and your net.
@Composable
fun CompanyInvoicesScreen(onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Loadable<List<Invoice>>>(Loadable.Loading) }
    suspend fun load() { state = state.reloaded { environment.client.send(CompanyEndpoints.invoices()).invoices } }
    LaunchedEffect(Unit) { load() }

    Column {
        ZTopBar("Invoices", onBack = onBack)
        ZScreen(onRefresh = { load() }) {
            ZLoadable(state, retry = { scope.launch { load() } }) { invoices ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    ZPageTitle("Invoices")
                    if (invoices.isEmpty()) ZEmptyState(Icons.Outlined.Description, "No invoices yet", "Invoices appear here as your completed jobs are billed.")
                    for (invoice in invoices) CompanyInvoiceRow(invoice) { nav.navigate(CompanyInvoiceRoute(invoice.id)) }
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }
}

/// Company invoice detail: the document plus the payout panel — never a
/// pay form (that's the booker's side).
@Composable
fun CompanyInvoiceDetailScreen(invoiceId: Int, onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val colors = com.zivett.app.design.ZTheme.colors
    var state by remember { mutableStateOf<Loadable<Invoice>>(Loadable.Loading) }
    suspend fun load() { state = state.reloaded { environment.client.send(CompanyEndpoints.invoice(invoiceId)).invoice } }
    LaunchedEffect(invoiceId) { load() }

    Column {
        ZTopBar("Invoice", onBack = onBack)
        ZScreen {
            ZLoadable(state, retry = { scope.launch { load() } }) { invoice ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val meta = JobPresentation.invoiceStatusMeta(invoice.status)
                        ZBadge(meta.label, meta.tone)
                        ZTitle(invoice.detail?.job?.title ?: invoice.job?.title ?: "Invoice")
                        ZCaption(listOfNotNull(invoice.number, invoice.detail?.billedTo?.name ?: invoice.job?.customer).joinToString(" · "))
                    }
                    InvoiceBreakdown(invoice)
                    invoice.commissionRateBps?.let { commissionBps ->
                        ZCard {
                            ZHeadline("Your payout")
                            MoneyRow("Service subtotal", invoice.totalCents)
                            MoneyRow("Platform commission (${CompanyPresentation.percent(commissionBps)})", -(invoice.commissionCents ?: 0))
                            val trustBps = invoice.trustFeeBps; val trust = invoice.trustFeeCents
                            if (trustBps != null && trust != null && trust > 0) MoneyRow("Trust & support (${CompanyPresentation.percent(trustBps)})", -trust)
                            invoice.tipCents?.takeIf { it > 0 }?.let { MoneyRow("Tip — 100% yours", it, color = colors.success) }
                            invoice.companyReferralCreditCents?.takeIf { it > 0 }?.let { MoneyRow("Referral credit", it, color = colors.success) }
                            ZDivider()
                            MoneyRow("You receive", invoice.netCents ?: 0, strong = true, color = colors.success)
                            ZCaption(if (invoice.isPaid) "Settled — track it on your Payouts page." else "Queued as a payout once the booker pays or the invoice auto-settles.")
                        }
                    }
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }
}
