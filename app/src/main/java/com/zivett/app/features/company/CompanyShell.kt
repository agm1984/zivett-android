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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.zivett.app.app.Areas
import com.zivett.app.app.CompanyAccountTab
import com.zivett.app.app.CompanyCalendarRoute
import com.zivett.app.app.CompanyDashboardRoute
import com.zivett.app.app.CompanyDashboardTab
import com.zivett.app.app.CompanyMyDayTab
import com.zivett.app.app.MyDayRoute
import com.zivett.app.app.MyHoursRoute
import com.zivett.app.app.OwnJobRoute
import com.zivett.app.core.auth.Access
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
import com.zivett.app.app.PlanRoute
import com.zivett.app.app.PassportRoute
import com.zivett.app.app.PayoutsRoute
import com.zivett.app.app.ProfileRoute
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
import com.zivett.app.features.customer.account.AdvertisingPrivacySection
import com.zivett.app.features.customer.account.AccountLink
import com.zivett.app.features.customer.account.ProfileScreen
import com.zivett.app.features.customer.notifications.NotificationBell
import com.zivett.app.features.customer.notifications.rememberNotificationsModel
import com.zivett.app.features.customer.shell.bookerDestinations
import com.zivett.app.features.plans.PlanPickerScreen
import com.zivett.app.features.shared.LocalNav
import com.zivett.app.features.shared.RoleShell
import com.zivett.app.features.shared.shellTab
import com.zivett.app.core.push.PushTarget
import com.zivett.app.features.shared.NavBadgesModel
import com.zivett.app.features.shared.rememberNavBadges
import kotlinx.coroutines.launch

/// Company shell in three lifecycles, mirroring the web's sidebar menus:
/// draft → setup; submitted → application status; approved → the full
/// working set (Dashboard · Opportunities · Quotes · Jobs · More).
@Composable
fun CompanyShell(approved: Boolean) {
    val environment = LocalAppEnvironment.current
    val organization = environment.session.user?.organization
    val isAdmin = environment.session.user?.organizationRole == OrganizationRole.ADMIN

    val navBadges = rememberNavBadges()
    // Field techs open on My day (web `roleHome`); their Dashboard moves
    // into More so the bar stays at five.
    val fieldTech = environment.session.user?.let { Access.opensOnMyDay(it) } == true
    val tabs = when {
        approved -> listOf(
            if (fieldTech) shellTab(CompanyMyDayTab, "My day", Icons.Outlined.Today)
            else shellTab(CompanyDashboardTab, "Dashboard", Icons.Filled.Home),
            // New feed jobs since this member last looked (`GET /api/nav-badges`).
            shellTab(CompanyOpportunitiesTab, "Opportunities", Icons.Outlined.AutoAwesome) { navBadges.count(NavBadgesModel.OPPORTUNITIES) },
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

    // Back from Stripe's onboarding: the dashboard owns the payout-status
    // re-check (and its "Set up payouts" card), so land there.
    val onStripeReturn: ((androidx.navigation.NavHostController) -> Unit)? = if (!approved) null else { nav ->
        nav.navigate(if (fieldTech) CompanyDashboardRoute else CompanyDashboardTab) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = false }
            launchSingleTop = true
        }
    }

    RoleShell(
        tabs, NotificationArea.COMPANY,
        onPushJob = { nav, id -> nav.navigate(CompanyJobRoute(id)) },
        onStripeReturn = onStripeReturn,
        onPushTarget = { nav, target -> if (approved) openCompanyTarget(nav, target) },
        navBadges = navBadges,
    ) { nav ->
        composable<CompanyDashboardTab> { BellScreen("Dashboard") { CompanyDashboardScreen() } }
        composable<CompanyMyDayTab> { BellScreen("My day") { MyDayScreen() } }
        composable<MyDayRoute> { BellScreen("My day", onBack = { nav.popBackStack() }) { MyDayScreen() } }
        composable<MyHoursRoute> { MyHoursScreen { nav.popBackStack() } }
        composable<OwnJobRoute> { entry -> OwnJobScreen(entry.toRoute<OwnJobRoute>().jobId) { nav.popBackStack() } }
        composable<CompanyDashboardRoute> { BellScreen("Dashboard", onBack = { nav.popBackStack() }) { CompanyDashboardScreen() } }
        composable<CompanyOpportunitiesTab> { BellScreen("Opportunities") { OpportunitiesScreen() } }
        composable<CompanyQuotesTab> { BellScreen("Quotes") { CompanyQuotesScreen() } }
        composable<CompanyJobsTab> { BellScreen("Jobs") { CompanyActiveJobsScreen() } }
        composable<CompanyMoreTab> { CompanyMoreScreen() }
        composable<CompanyPassportTab> { BellScreen("Passport") { PassportScreen(onBack = null) } }
        composable<CompanySetupTab> { CompanySetupScreen(onBack = null) }
        composable<CompanyTeamTab> { TeamScreen(TeamArea.company, onBack = null) }
        // No More tab before approval, so log out lives here.
        composable<CompanyAccountTab> {
            val environment = LocalAppEnvironment.current
            val scope = rememberCoroutineScope()
            ProfileScreen(ProfileArea.company, onBack = null, actions = {
                TextButton(onClick = { scope.launch { environment.session.logout() } }) { Text("Log out") }
            })
        }

        composable<CompanyJobRoute> { entry -> entry.toRoute<CompanyJobRoute>().let { route -> CompanyJobDetailScreen(route.jobId, route.openStart, route.advanceOnOpen) { nav.popBackStack() } } }
        composable<OpportunitiesRoute> { BellScreen("Opportunities", onBack = { nav.popBackStack() }) { OpportunitiesScreen() } }
        composable<CompanyQuotesRoute> { BellScreen("My quotes", onBack = { nav.popBackStack() }) { CompanyQuotesScreen() } }
        composable<CompanyCalendarRoute> { CompanyCalendarScreen { nav.popBackStack() } }
        composable<CompanyInvoicesRoute> { CompanyInvoicesScreen { nav.popBackStack() } }
        composable<CompanyInvoiceRoute> { entry -> CompanyInvoiceDetailScreen(entry.toRoute<CompanyInvoiceRoute>().invoiceId) { nav.popBackStack() } }
        composable<PayoutsRoute> { PayoutsScreen { nav.popBackStack() } }
        composable<PassportRoute> { PassportScreen { nav.popBackStack() } }
        composable<CompanySetupRoute> { CompanySetupScreen { nav.popBackStack() } }
        composable<CredentialGuideRoute> { CredentialGuideScreen { nav.popBackStack() } }
        composable<PlanRoute> { entry -> PlanPickerScreen(entry.toRoute<PlanRoute>().area) { nav.popBackStack() } }
        bookerDestinations(nav)
    }
}

/// A non-job notification's destination (web `lib/notificationRoute.js`):
/// new job near you → the feed (composer open for that job), quote
/// released → My quotes, payout sent / payouts set up → Payouts, put on
/// an own job → that job's crew page. Tabs are
/// switched the way the bar switches them.
fun openCompanyTarget(nav: androidx.navigation.NavHostController, target: PushTarget) {
    fun tab(route: Any) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    when (target) {
        is PushTarget.Opportunities -> { PendingOpportunity.code = target.jobCode; tab(CompanyOpportunitiesTab) }
        PushTarget.Quotes -> tab(CompanyQuotesTab)
        PushTarget.Payouts -> nav.navigate(PayoutsRoute)
        is PushTarget.OwnJob -> nav.navigate(OwnJobRoute(target.id))
        is PushTarget.Job -> {}
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
/// profile, team, account.
@Composable
fun CompanyMoreScreen() {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var loggingOut by remember { mutableStateOf(false) }
    val user = environment.session.user ?: return
    val isAdmin = user.organizationRole == OrganizationRole.ADMIN
    val fieldTech = Access.opensOnMyDay(user)

    Column {
        ZTopBar("More")
        ZScreen {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                ZAvatar(user.organization?.let { initialsOf(it.name) } ?: user.initials, ZAvatarShape.COMPANY, 56.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { ZTitle(user.organization?.name ?: user.name); ZMono("Verified Pro") }
            }
            ZCard(padding = 0.dp) {
                // Whichever of My day / Dashboard isn't a tab for this person.
                if (fieldTech) AccountLink("Dashboard", "Your company at a glance", Icons.Filled.Home) { nav.navigate(CompanyDashboardRoute) }
                else AccountLink("My day", "The jobs you're on, in the order you'll do them", Icons.Outlined.Today) { nav.navigate(MyDayRoute) }
                // The team clock's pay period — Business tools companies only.
                if (user.organization?.hasBusinessTools == true) {
                    ZDivider(Modifier.padding(start = 60.dp))
                    AccountLink("My hours", "Your pay period on the clock, and fixing a day", Icons.Outlined.Schedule) { nav.navigate(MyHoursRoute) }
                }
                ZDivider(Modifier.padding(start = 60.dp))
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
                ZDivider(Modifier.padding(start = 60.dp))
                AccountLink("Plan", "Your membership and what it includes", Icons.Outlined.StarOutline) { nav.navigate(PlanRoute(Areas.COMPANY)) }
                // Org-admin concerns — members don't get the rows (UX only; the server enforces).
                if (isAdmin) {
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
            AdvertisingPrivacySection()
            AccountFooter()
            Spacer(Modifier.padding(ZSpacing.lg))
        }
    }
}
