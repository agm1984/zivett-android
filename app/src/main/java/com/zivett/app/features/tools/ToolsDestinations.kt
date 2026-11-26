package com.zivett.app.features.tools

import androidx.compose.foundation.layout.Column
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.zivett.app.app.ToolsCustomerRoute
import com.zivett.app.app.ToolsOverviewRoute
import com.zivett.app.app.ToolsRequestsRoute
import com.zivett.app.design.ZTopBar

/// The business tools screens pushed over either side's bar. Tabs are
/// registered by the company shell beside its bell.
fun NavGraphBuilder.toolsDestinations(nav: NavHostController) {
    val tools = ToolsNav(nav)
    composable<ToolsOverviewRoute> { Column { ZTopBar("Overview", onBack = { nav.popBackStack() }); ToolsOverviewScreen(tools) } }
    composable<ToolsRequestsRoute> { RequestsScreen(tools) }
    composable<ToolsCustomerRoute> { entry -> CustomerDetailScreen(entry.toRoute<ToolsCustomerRoute>().customerId, tools) }
    composable<com.zivett.app.app.ToolsJobRoute> { entry -> ProJobScreen(entry.toRoute<com.zivett.app.app.ToolsJobRoute>().jobId, tools) }
    composable<com.zivett.app.app.ToolsJobEditorRoute> { entry -> entry.toRoute<com.zivett.app.app.ToolsJobEditorRoute>().let { ProJobEditorScreen(it.jobId, it.customerId, it.quoteId, it.date, tools) } }
    composable<com.zivett.app.app.ToolsInvoiceRoute> { entry -> OwnInvoiceScreen(entry.toRoute<com.zivett.app.app.ToolsInvoiceRoute>().invoiceId, tools) }
    composable<com.zivett.app.app.ToolsInvoiceEditorRoute> { entry -> entry.toRoute<com.zivett.app.app.ToolsInvoiceEditorRoute>().let { OwnInvoiceEditorScreen(it.invoiceId, it.customerId, it.jobId, it.quoteId, tools) } }
    composable<com.zivett.app.app.ToolsBillVisitsRoute> { BillVisitsScreen(tools) }
    composable<com.zivett.app.app.ToolsExpensesRoute> { ExpensesScreen(tools) }
    composable<com.zivett.app.app.ToolsTimesheetsRoute> { TimesheetsScreen(tools) }
    composable<com.zivett.app.app.ToolsPayrollRoute> { PayrollScreen(tools) }
    composable<com.zivett.app.app.ToolsReportsRoute> { ReportsScreen(tools) }
    composable<com.zivett.app.app.ToolsQuotesRoute> { entry -> OwnQuotesScreen(entry.toRoute<com.zivett.app.app.ToolsQuotesRoute>().filter, tools) }
    composable<com.zivett.app.app.ToolsQuoteRoute> { entry -> OwnQuoteScreen(entry.toRoute<com.zivett.app.app.ToolsQuoteRoute>().quoteId, tools) }
    composable<com.zivett.app.app.ToolsQuoteEditorRoute> { entry -> entry.toRoute<com.zivett.app.app.ToolsQuoteEditorRoute>().let { OwnQuoteEditorScreen(it.quoteId, it.customerId, tools) } }
}
