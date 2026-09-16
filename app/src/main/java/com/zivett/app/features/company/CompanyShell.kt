package com.zivett.app.features.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Work
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.zivett.app.app.Areas
import com.zivett.app.app.CompanyAccountTab
import com.zivett.app.app.CompanyCalendarRoute
import com.zivett.app.app.CompanyDashboardTab
import com.zivett.app.app.CompanyInvoiceRoute
import com.zivett.app.app.CompanyInvoicesRoute
import com.zivett.app.app.CompanyJobRoute
import com.zivett.app.app.CompanyJobsTab
import com.zivett.app.app.CompanyMoreTab
import com.zivett.app.app.CompanyOpportunitiesTab
import com.zivett.app.app.CompanyPassportTab
import com.zivett.app.app.CompanyQuotesRoute
import com.zivett.app.app.CompanyQuotesTab
import com.zivett.app.app.CompanySetupRoute
import com.zivett.app.app.CompanySetupTab
import com.zivett.app.app.CompanyTeamTab
import com.zivett.app.app.CredentialGuideRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.NotificationPrefsRoute
import com.zivett.app.app.NotificationsRoute
import com.zivett.app.app.OpportunitiesRoute
import com.zivett.app.app.OrgProfileRoute
import com.zivett.app.app.PassportRoute
import com.zivett.app.app.PayoutsRoute
import com.zivett.app.app.ProfileRoute
import com.zivett.app.app.MembershipRoute
import com.zivett.app.app.TeamRoute
import com.zivett.app.core.models.NotificationArea
import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.models.ProfileArea
import com.zivett.app.core.models.TeamArea
import com.zivett.app.core.models.initialsOf
import com.zivett.app.design.ZAvatar
import com.zivett.app.design.ZAvatarShape
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTitle
import com.zivett.app.design.ZTopBar
import com.zivett.app.features.business.TeamScreen
import com.zivett.app.features.customer.account.AccountFooter
import com.zivett.app.features.customer.account.AccountLink
import com.zivett.app.features.customer.account.ProfileScreen
import com.zivett.app.features.customer.notifications.NotificationBell
import com.zivett.app.features.customer.notifications.rememberNotificationsModel
import com.zivett.app.features.customer.shell.bookerDestinations
import com.zivett.app.features.shared.LocalNav
import com.zivett.app.features.shared.RoleShell
import com.zivett.app.features.shared.shellTab
import kotlinx.coroutines.launch

/// Company shell in three lifecycles, mirroring the web's sidebar menus:
/// draft → setup; submitted → application status; approved → the full
/// working set (Dashboard · Opportunities · Quotes · Jobs · More).
@Composable
fun CompanyShell(approved: Boolean) {
    val environment = LocalAppEnvironment.current
    val organization = environment.session.user?.organization
    val isAdmin = environment.session.user?.organizationRole == OrganizationRole.ADMIN

    val tabs = when {
        approved -> listOf(
            shellTab(CompanyDashboardTab, "Dashboard", Icons.Filled.Home),
            shellTab(CompanyOpportunitiesTab, "Opportunities", Icons.Outlined.AutoAwesome),
            shellTab(CompanyQuotesTab, "Quotes", Icons.Outlined.Description),
            shellTab(CompanyJobsTab, "Jobs", Icons.Outlined.Work),
            shellTab(CompanyMoreTab, "More", Icons.Outlined.MoreHoriz),
        )
        // Application in review: dashboard shows the pipeline. The bell matters
        // most here — approval and document-review notifications are what a
        // submitted company waits on.
        organization?.submittedAt != null -> listOf(
            shellTab(CompanyDashboardTab, "Application", Icons.Outlined.HourglassEmpty),
            shellTab(CompanyPassportTab, "Passport", Icons.Outlined.Verified),
            shellTab(CompanyMoreTab, "More", Icons.Outlined.MoreHoriz),
        )
        // Draft: setup is the app. Team is an org-admin concern.
        else -> listOfNotNull(
            shellTab(CompanySetupTab, "Setup", Icons.Outlined.Checklist),
            if (isAdmin) shellTab(CompanyTeamTab, "Team", Icons.Outlined.Group) else null,
            shellTab(CompanyAccountTab, "Account", Icons.Outlined.Person),
        )
    }

    RoleShell(tabs, NotificationArea.COMPANY, onPushJob = { nav, id -> nav.navigate(CompanyJobRoute(id)) }) { nav ->
        composable<CompanyDashboardTab> { BellScreen("Dashboard") { CompanyDashboardScreen() } }
        composable<CompanyOpportunitiesTab> { BellScreen("Opportunities") { OpportunitiesScreen() } }
        composable<CompanyQuotesTab> { BellScreen("Quotes") { CompanyQuotesScreen() } }
        composable<CompanyJobsTab> { BellScreen("Jobs") { CompanyActiveJobsScreen() } }
        composable<CompanyMoreTab> { CompanyMoreScreen() }
        composable<CompanyPassportTab> { BellScreen("Passport") { PassportScreen(onBack = null) } }
        composable<CompanySetupTab> { CompanySetupScreen(onBack = null) }
        composable<CompanyTeamTab> { TeamScreen(TeamArea.company, onBack = null) }
        composable<CompanyAccountTab> { ProfileScreen(ProfileArea.company) { } }

        composable<CompanyJobRoute> { entry -> CompanyJobDetailScreen(entry.toRoute<CompanyJobRoute>().jobId) { nav.popBackStack() } }
        composable<OpportunitiesRoute> { BellScreen("Opportunities", onBack = { nav.popBackStack() }) { OpportunitiesScreen() } }
        composable<CompanyQuotesRoute> { BellScreen("My quotes", onBack = { nav.popBackStack() }) { CompanyQuotesScreen() } }
        composable<CompanyCalendarRoute> { CompanyCalendarScreen { nav.popBackStack() } }
        composable<CompanyInvoicesRoute> { CompanyInvoicesScreen { nav.popBackStack() } }
        composable<CompanyInvoiceRoute> { entry -> CompanyInvoiceDetailScreen(entry.toRoute<CompanyInvoiceRoute>().invoiceId) { nav.popBackStack() } }
        composable<PayoutsRoute> { PayoutsScreen { nav.popBackStack() } }
        composable<PassportRoute> { PassportScreen { nav.popBackStack() } }
        composable<CompanySetupRoute> { CompanySetupScreen { nav.popBackStack() } }
        composable<CredentialGuideRoute> { CredentialGuideScreen { nav.popBackStack() } }
        bookerDestinations(nav)
    }
}

/// A tab root with the notification bell in its app bar (the company
/// screens mount the bell in the bar; the customer home has its own header).
@Composable
private fun BellScreen(title: String, onBack: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val nav = LocalNav.current
    val bell = rememberNotificationsModel(NotificationArea.COMPANY)
    Column {
        ZTopBar(title, onBack = onBack, actions = { NotificationBell(bell.unread) { nav.navigate(NotificationsRoute(Areas.COMPANY)) } })
        content()
    }
}

/// Everything behind "More": calendar, invoices, payouts, passport,
/// profile, membership (read-only), team, account.
@Composable
fun CompanyMoreScreen() {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var loggingOut by remember { mutableStateOf(false) }
    val user = environment.session.user ?: return
    val isAdmin = user.organizationRole == OrganizationRole.ADMIN

    Column {
        ZTopBar("More")
        ZScreen {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                ZAvatar(user.organization?.let { initialsOf(it.name) } ?: user.initials, ZAvatarShape.COMPANY, 56.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { ZTitle(user.organization?.name ?: user.name); ZMono("Verified Pro") }
            }
            ZCard(padding = 0.dp) {
                AccountLink("Calendar", "Confirmed jobs, proposals, blocked days", Icons.Outlined.CalendarMonth) { nav.navigate(CompanyCalendarRoute) }
                ZDivider(Modifier.padding(start = 60.dp))
                AccountLink("Invoices", "What your completed jobs billed", Icons.Outlined.Description) { nav.navigate(CompanyInvoicesRoute) }
                ZDivider(Modifier.padding(start = 60.dp))
                AccountLink("Payouts", "What's pending and what's been paid", Icons.Outlined.AccountBalance) { nav.navigate(PayoutsRoute) }
            }
            ZCard(padding = 0.dp) {
                AccountLink("Verified Pro Passport", "Credentials, rates, radius, availability", Icons.Outlined.Verified) { nav.navigate(PassportRoute) }
                ZDivider(Modifier.padding(start = 60.dp))
                AccountLink("Business profile", "What customers see", Icons.Outlined.Business) { nav.navigate(OrgProfileRoute(Areas.COMPANY)) }
                // Org-admin concerns — members don't get the rows (UX only; the server enforces).
                if (isAdmin) {
                    ZDivider(Modifier.padding(start = 60.dp))
                    AccountLink("Membership", "Your current plan and term", Icons.Outlined.Star) { nav.navigate(MembershipRoute(Areas.COMPANY)) }
                    ZDivider(Modifier.padding(start = 60.dp))
                    AccountLink("Team", "Invite and manage teammates", Icons.Outlined.Group) { nav.navigate(TeamRoute(Areas.COMPANY)) }
                }
            }
            ZCard(padding = 0.dp) {
                AccountLink("My account", "Name, email, phone", Icons.Outlined.Person) { nav.navigate(ProfileRoute(Areas.COMPANY)) }
                ZDivider(Modifier.padding(start = 60.dp))
                AccountLink("Notifications", "Email and SMS preferences", Icons.Outlined.NotificationsActive) { nav.navigate(NotificationPrefsRoute(Areas.COMPANY)) }
            }
            ZButton("Log out", style = ZButtonStyle.OUTLINE, loading = loggingOut) { loggingOut = true; scope.launch { environment.session.logout() } }
            AccountFooter()
            Spacer(Modifier.padding(ZSpacing.lg))
        }
    }
}
