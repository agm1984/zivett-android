package com.zivett.app.features.customer.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Work
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.zivett.app.app.AddressesRoute
import com.zivett.app.app.Areas
import com.zivett.app.app.BookRoute
import com.zivett.app.app.ConversationRoute
import com.zivett.app.app.CustomerAccountTab
import com.zivett.app.app.CustomerBookTab
import com.zivett.app.app.CustomerHomeTab
import com.zivett.app.app.CustomerJobsTab
import com.zivett.app.app.CustomerMessagesTab
import com.zivett.app.app.InvoiceDetailRoute
import com.zivett.app.app.InvoicesRoute
import com.zivett.app.app.JobDetailRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.NotificationPrefsRoute
import com.zivett.app.app.NotificationsRoute
import com.zivett.app.app.OrgProfileRoute
import com.zivett.app.app.PayInvoiceRoute
import com.zivett.app.app.ProfileRoute
import com.zivett.app.app.ReferralRoute
import com.zivett.app.app.TeamRoute
import com.zivett.app.app.WarrantiesRoute
import com.zivett.app.core.models.NotificationArea
import com.zivett.app.features.business.OrganizationFormScreen
import com.zivett.app.features.business.TeamScreen
import com.zivett.app.features.customer.account.AccountHomeScreen
import com.zivett.app.features.customer.account.AddressesScreen
import com.zivett.app.features.customer.account.InvoiceDetailScreen
import com.zivett.app.features.customer.account.InvoicesScreen
import com.zivett.app.features.customer.account.NotificationPreferencesScreen
import com.zivett.app.features.customer.account.PayInvoiceScreen
import com.zivett.app.features.customer.account.ProfileScreen
import com.zivett.app.features.customer.account.ReferralScreen
import com.zivett.app.features.customer.account.WarrantiesScreen
import com.zivett.app.features.customer.book.BookJobScreen
import com.zivett.app.features.customer.book.BookingWizardEngine
import com.zivett.app.features.customer.home.CustomerHomeModel
import com.zivett.app.features.customer.home.CustomerHomeScreen
import com.zivett.app.features.customer.jobs.JobDetailScreen
import com.zivett.app.features.customer.jobs.JobsScreen
import com.zivett.app.features.customer.messages.ConversationScreen
import com.zivett.app.features.customer.messages.MessagesScreen
import com.zivett.app.features.customer.notifications.NotificationsScreen
import com.zivett.app.features.customer.notifications.rememberNotificationsModel
import com.zivett.app.features.shared.RoleShell
import com.zivett.app.features.shared.shellTab
import kotlinx.coroutines.launch

/// The customer tab bar: Home · Jobs · Book · Messages · Account.
@Composable
fun CustomerShell() {
    val environment = LocalAppEnvironment.current
    // Owned here (not by the home screen) so the Messages tab badge can
    // read the same home payload's `unread_messages` — `lib/nav.js` does
    // the equivalent on the web.
    val homeModel = remember { CustomerHomeModel(environment.client) }

    val tabs = listOf(
        shellTab(CustomerHomeTab, "Home", Icons.Filled.Home),
        shellTab(CustomerJobsTab, "Jobs", Icons.Outlined.Work),
        shellTab(CustomerBookTab, "Book", Icons.Outlined.AddCircle),
        shellTab(CustomerMessagesTab, "Messages", Icons.AutoMirrored.Outlined.Chat) { homeModel.home?.unreadMessages ?: 0 },
        shellTab(CustomerAccountTab, "Account", Icons.Outlined.Person),
    )

    RoleShell(tabs, NotificationArea.CUSTOMER, onPushJob = { nav, id -> nav.navigate(JobDetailRoute(id, Areas.CUSTOMER)) }) { nav ->
        composable<CustomerHomeTab> { CustomerHomeScreen(homeModel) }
        composable<CustomerJobsTab> { JobsScreen() }
        composable<CustomerBookTab> { BookJobScreen() }
        composable<CustomerMessagesTab> {
            // Reading the inbox clears the count server-side; re-read when
            // the user leaves Messages so the badge follows.
            val reloadScope = androidx.compose.runtime.rememberCoroutineScope()
            androidx.compose.runtime.DisposableEffect(Unit) { onDispose { reloadScope.launch { homeModel.load() } } }
            MessagesScreen()
        }
        composable<CustomerAccountTab> { AccountHomeScreen() }
        bookerDestinations(nav)
    }
}


/// The screens every booker shell (customer + business) pushes: job
/// detail, threads, invoices, account sub-pages, the booking wizard with
/// presets, and the notifications inbox. Registered once per shell.
fun NavGraphBuilder.bookerDestinations(nav: NavHostController) {
    composable<JobDetailRoute> { entry ->
        val route = entry.toRoute<JobDetailRoute>()
        JobDetailScreen(route.jobId, Areas.jobArea(route.area)) { nav.popBackStack() }
    }
    composable<ConversationRoute> { entry ->
        val route = entry.toRoute<ConversationRoute>()
        ConversationScreen(route.jobId, route.title, route.subtitle, route.readOnly, Areas.jobArea(route.area), route.showJobLink) { nav.popBackStack() }
    }
    composable<InvoiceDetailRoute> { entry ->
        val route = entry.toRoute<InvoiceDetailRoute>()
        InvoiceDetailScreen(route.invoiceId, Areas.jobArea(route.area)) { nav.popBackStack() }
    }
    composable<PayInvoiceRoute> { entry ->
        val route = entry.toRoute<PayInvoiceRoute>()
        PayInvoiceScreen(route.invoiceId, Areas.jobArea(route.area)) { nav.popBackStack() }
    }
    composable<InvoicesRoute> { entry -> InvoicesScreen(Areas.jobArea(entry.toRoute<InvoicesRoute>().area)) { nav.popBackStack() } }
    composable<WarrantiesRoute> { entry -> WarrantiesScreen(Areas.jobArea(entry.toRoute<WarrantiesRoute>().area)) { nav.popBackStack() } }
    composable<ProfileRoute> { entry -> ProfileScreen(Areas.profileArea(entry.toRoute<ProfileRoute>().area)) { nav.popBackStack() } }
    composable<NotificationPrefsRoute> { entry -> NotificationPreferencesScreen(Areas.profileArea(entry.toRoute<NotificationPrefsRoute>().area)) { nav.popBackStack() } }
    composable<AddressesRoute> { AddressesScreen { nav.popBackStack() } }
    composable<ReferralRoute> { ReferralScreen { nav.popBackStack() } }
    composable<TeamRoute> { entry -> TeamScreen(Areas.teamArea(entry.toRoute<TeamRoute>().area)) { nav.popBackStack() } }
    composable<OrgProfileRoute> { entry -> OrganizationFormScreen(entry.toRoute<OrgProfileRoute>().area) { nav.popBackStack() } }
    composable<BookRoute> { entry ->
        val route = entry.toRoute<BookRoute>()
        BookJobScreen(
            area = if (route.area == Areas.BUSINESS) BookingWizardEngine.Area.BUSINESS else BookingWizardEngine.Area.CUSTOMER,
            presetMode = route.presetMode, presetCategorySlug = route.presetCategorySlug, presetPropertyId = route.presetPropertyId, resumeRequested = route.resume,
            onBack = { nav.popBackStack() },
        )
    }
    composable<NotificationsRoute> { entry ->
        val area = Areas.notificationArea(entry.toRoute<NotificationsRoute>().area)
        val model = rememberNotificationsModel(area)
        NotificationsScreen(model, onBack = { nav.popBackStack() }) { id ->
            when (area) {
                NotificationArea.COMPANY -> nav.navigate(com.zivett.app.app.CompanyJobRoute(id))
                NotificationArea.BUSINESS -> nav.navigate(JobDetailRoute(id, Areas.BUSINESS))
                NotificationArea.CUSTOMER -> nav.navigate(JobDetailRoute(id, Areas.CUSTOMER))
            }
        }
    }
}

@Suppress("unused")
private val keepLaunched: @Composable () -> Unit = { LaunchedEffect(Unit) {} }
