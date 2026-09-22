package com.zivett.app.features.business

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Work
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.composable
import com.zivett.app.app.Areas
import com.zivett.app.app.BusinessAccountTab
import com.zivett.app.app.BusinessBookTab
import com.zivett.app.app.BusinessInvoicesRoute
import com.zivett.app.app.BusinessOverviewTab
import com.zivett.app.app.BusinessPropertiesTab
import com.zivett.app.app.BusinessRequestsTab
import com.zivett.app.app.BusinessSetupRoute
import com.zivett.app.app.InvoicesRoute
import com.zivett.app.app.JobDetailRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.NotificationPrefsRoute
import com.zivett.app.app.NotificationsRoute
import com.zivett.app.app.OrgProfileRoute
import com.zivett.app.app.ProfileRoute
import com.zivett.app.app.PropertiesRoute
import com.zivett.app.app.TeamRoute
import com.zivett.app.app.WarrantiesRoute
import com.zivett.app.core.models.NotificationArea
import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.design.ZAvatar
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTitle
import com.zivett.app.design.ZTopBar
import com.zivett.app.features.customer.account.AccountFooter
import com.zivett.app.features.customer.account.AccountLink
import com.zivett.app.features.customer.book.BookJobScreen
import com.zivett.app.features.customer.book.BookingWizardEngine
import com.zivett.app.features.customer.notifications.NotificationBell
import com.zivett.app.features.customer.notifications.rememberNotificationsModel
import com.zivett.app.features.customer.shell.bookerDestinations
import com.zivett.app.features.shared.LocalNav
import com.zivett.app.features.shared.RoleShell
import com.zivett.app.features.shared.shellTab
import kotlinx.coroutines.launch

/// Business (property manager) shell: Overview · Requests · Book ·
/// Properties · Account. The web hard-redirects org admins into the
/// setup wizard while `setup_completed_at` is null — here that's a
/// full-screen route on first appearance, leaving only through "Skip
/// for now" (which completes setup server-side). Members are exempt.
@Composable
fun BusinessShell() {
    val environment = LocalAppEnvironment.current
    val tabs = listOf(
        shellTab(BusinessOverviewTab, "Overview", Icons.Filled.Home),
        shellTab(BusinessRequestsTab, "Requests", Icons.Outlined.Work),
        shellTab(BusinessBookTab, "Book", Icons.Outlined.AddCircle),
        shellTab(BusinessPropertiesTab, "Properties", Icons.Outlined.Apartment),
        shellTab(BusinessAccountTab, "Account", Icons.Outlined.Person),
    )

    RoleShell(tabs, NotificationArea.BUSINESS, onPushJob = { nav, id -> nav.navigate(JobDetailRoute(id, Areas.BUSINESS)) }) { nav ->
        composable<BusinessOverviewTab> {
            val user = environment.session.user
            var gated by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!gated && user?.organizationRole == OrganizationRole.ADMIN && user.organization?.setupCompletedAt == null) { gated = true; nav.navigate(BusinessSetupRoute) }
            }
            BusinessBellScreen("Overview") { BusinessHomeScreen() }
        }
        composable<BusinessRequestsTab> { BusinessBellScreen("Requests") { BusinessRequestsScreen() } }
        composable<BusinessBookTab> { BookJobScreen(area = BookingWizardEngine.Area.BUSINESS) }
        composable<BusinessPropertiesTab> { BusinessBellScreen("Properties") { PropertiesScreen(onBack = null) } }
        composable<BusinessAccountTab> { BusinessAccountScreen() }
        composable<BusinessSetupRoute> { BusinessSetupScreen { nav.popBackStack() } }
        composable<BusinessInvoicesRoute> { com.zivett.app.features.customer.account.InvoicesScreen(com.zivett.app.core.models.JobArea.business) { nav.popBackStack() } }
        composable<PropertiesRoute> { PropertiesScreen { nav.popBackStack() } }
        bookerDestinations(nav)
    }
}

@Composable
fun BusinessBellScreen(title: String, content: @Composable () -> Unit) {
    val nav = LocalNav.current
    val bell = rememberNotificationsModel(NotificationArea.BUSINESS)
    Column {
        ZTopBar(title, actions = { NotificationBell(bell.unread) { nav.navigate(NotificationsRoute(Areas.BUSINESS)) } })
        content()
    }
}

/// Business "Account" tab: personal account, business profile, team,
/// invoices, warranties, notification prefs, log out.
@Composable
fun BusinessAccountScreen() {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var loggingOut by remember { mutableStateOf(false) }
    val user = environment.session.user ?: return
    val bell = rememberNotificationsModel(NotificationArea.BUSINESS)

    Column {
        ZTopBar("Account", actions = { NotificationBell(bell.unread) { nav.navigate(NotificationsRoute(Areas.BUSINESS)) } })
        ZScreen {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                ZAvatar(user.initials, size = 56.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ZTitle(user.name)
                    ZCaption(user.organization?.name ?: user.email)
                }
            }
            ZCard(padding = 0.dp) {
                AccountLink("My account", "Name, email, phone", Icons.Outlined.Person) { nav.navigate(ProfileRoute(Areas.BUSINESS)) }
                ZDivider(Modifier.padding(start = 60.dp))
                AccountLink("Business profile", "What pros see about ${user.organization?.name ?: "your business"}", Icons.Outlined.Business) { nav.navigate(OrgProfileRoute(Areas.BUSINESS)) }
                // Org-admin concerns — members don't get the rows (UX only; the server enforces).
                if (user.organizationRole == OrganizationRole.ADMIN) {
                    ZDivider(Modifier.padding(start = 60.dp))
                    AccountLink("Team", "Invite and manage teammates", Icons.Outlined.Group) { nav.navigate(TeamRoute(Areas.BUSINESS)) }
                }
                ZDivider(Modifier.padding(start = 60.dp))
                AccountLink("Notifications", "Email and SMS preferences", Icons.Outlined.NotificationsActive) { nav.navigate(NotificationPrefsRoute(Areas.BUSINESS)) }
            }
            ZCard(padding = 0.dp) {
                AccountLink("Invoices", "Consolidated monthly statements", Icons.Outlined.Description) { nav.navigate(InvoicesRoute(Areas.BUSINESS)) }
                ZDivider(Modifier.padding(start = 60.dp))
                AccountLink("Warranties", "Covered work and claims", Icons.Outlined.VerifiedUser) { nav.navigate(WarrantiesRoute(Areas.BUSINESS)) }
            }
            ZButton("Log out", style = ZButtonStyle.OUTLINE, loading = loggingOut) { loggingOut = true; scope.launch { environment.session.logout() } }
            AccountFooter()
            Spacer(Modifier.padding(ZSpacing.lg))
        }
    }
}
