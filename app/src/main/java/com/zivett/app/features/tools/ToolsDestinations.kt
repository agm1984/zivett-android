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
}
