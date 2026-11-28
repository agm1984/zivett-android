package com.zivett.app.features.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.zivett.app.app.Areas
import com.zivett.app.app.CompanyCalendarRoute
import com.zivett.app.app.CompanyJobRoute
import com.zivett.app.app.OwnJobRoute
import com.zivett.app.app.PlanRoute
import com.zivett.app.app.ToolsCustomersTab
import com.zivett.app.app.ToolsInvoicesTab
import com.zivett.app.app.ToolsJobsTab
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZSpacing

/// A list filter handed to a tab before switching to it (the web's
/// `?status=` query on the Overview's figures). The tab's model takes it
/// once on its next load.
object ToolsPendingFilter {
    var customers by mutableStateOf<String?>(null)
    var jobs by mutableStateOf<String?>(null)
    var invoices by mutableStateOf<String?>(null)
    /// Open the add form when the Customers tab next shows.
    var addCustomer by mutableStateOf(false)

    fun take(read: () -> String?, clear: () -> Unit): String? = read().also { clear() }
}

/// Navigation for the business tools screens, so they don't each know the
/// shell's routes. Tabs switch the way the bar does.
class ToolsNav(val nav: NavHostController) {
    private fun tab(route: Any) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun back() = nav.popBackStack()
    fun push(route: Any) = nav.navigate(route)

    fun customers(filter: String? = null) { ToolsPendingFilter.customers = filter; tab(ToolsCustomersTab) }
    fun jobs(filter: String? = null) { ToolsPendingFilter.jobs = filter; tab(ToolsJobsTab) }
    fun invoices(filter: String? = null) { ToolsPendingFilter.invoices = filter; tab(ToolsInvoicesTab) }
    fun calendar() = nav.navigate(CompanyCalendarRoute)
    fun ownJob(id: Int) = nav.navigate(OwnJobRoute(id))
    fun zivettJob(id: Int) = nav.navigate(CompanyJobRoute(id))
    fun plan() = nav.navigate(PlanRoute(Areas.COMPANY))
    fun customer(id: Int) = nav.navigate(com.zivett.app.app.ToolsCustomerRoute(id))
    fun addCustomer() { ToolsPendingFilter.addCustomer = true; customers() }
    fun quotes(filter: String? = null) = nav.navigate(com.zivett.app.app.ToolsQuotesRoute(filter))
    fun quote(id: Int) = nav.navigate(com.zivett.app.app.ToolsQuoteRoute(id))
    fun newQuote(customerId: Int? = null) = nav.navigate(com.zivett.app.app.ToolsQuoteEditorRoute(customerId = customerId))
    fun editQuote(id: Int) = nav.navigate(com.zivett.app.app.ToolsQuoteEditorRoute(quoteId = id))
    fun job(id: Int) = nav.navigate(com.zivett.app.app.ToolsJobRoute(id))
    fun newJob(customerId: Int? = null, quoteId: Int? = null, date: String? = null) = nav.navigate(com.zivett.app.app.ToolsJobEditorRoute(customerId = customerId, quoteId = quoteId, date = date))
    fun editJob(id: Int) = nav.navigate(com.zivett.app.app.ToolsJobEditorRoute(jobId = id))
    fun invoice(id: Int) = nav.navigate(com.zivett.app.app.ToolsInvoiceRoute(id))
    fun newInvoice(customerId: Int? = null, jobId: Int? = null, quoteId: Int? = null) = nav.navigate(com.zivett.app.app.ToolsInvoiceEditorRoute(customerId = customerId, jobId = jobId, quoteId = quoteId))
    fun editInvoice(id: Int) = nav.navigate(com.zivett.app.app.ToolsInvoiceEditorRoute(invoiceId = id))
    fun campaign(id: Int?) = nav.navigate(com.zivett.app.app.ToolsCampaignRoute(id))
}


/// The four ways to start work (web Overview's header actions): New quote
/// leads, the rest outlined.
@Composable
fun ToolsQuickActions(nav: ToolsNav) {
    ZButton("New quote") { nav.newQuote() }
    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
        ZButton("New job", style = ZButtonStyle.OUTLINE, compact = true, modifier = Modifier.weight(1f)) { nav.newJob() }
        ZButton("New invoice", style = ZButtonStyle.OUTLINE, compact = true, modifier = Modifier.weight(1f)) { nav.newInvoice() }
        ZButton("Add customer", style = ZButtonStyle.OUTLINE, compact = true, modifier = Modifier.weight(1f)) { nav.addCustomer() }
    }
}
