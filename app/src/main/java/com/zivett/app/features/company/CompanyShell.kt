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
import androidx.compose.runtime.key
import com.zivett.app.app.ToolsOverviewTab
import com.zivett.app.app.ToolsOverviewRoute
import com.zivett.app.app.ToolsRequestsRoute
import com.zivett.app.design.ZSegmented
import androidx.compose.material.icons.outlined.Inbox
import com.zivett.app.core.CompanySpace
import com.zivett.app.features.tools.ToolsNav
import com.zivett.app.features.tools.ToolsOverviewScreen
import com.zivett.app.features.tools.toolsDestinations
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
    val user = environment.session.user
    val tools = user?.let { Access.hasBusinessTools(it) } == true
    val toolsOnly = user?.let { Access.toolsOnly(it) } == true
    // Approved with Business tools: one side at a time, swapped from More.
    // Keyed so the switch rebuilds the bar and its back stacks from the top.
    val side = when {
        approved && tools -> CompanySpace.current
        toolsOnly -> CompanySpace.Side.BUSINESS
        else -> CompanySpace.Side.MARKETPLACE
    }
    key(side, tools) { CompanyShellBody(approved, tools, toolsOnly, side) }
}

/// The business side's tabs (Overview · Customers · Jobs · Invoices),
/// gated on the `business_tools` section.
private fun businessTabs(): List<com.zivett.app.features.shared.ShellTab> = listOf(
    shellTab(ToolsOverviewTab, "Overview", Icons.Filled.Home),
    shellTab(com.zivett.app.app.ToolsCustomersTab, "Customers", Icons.Outlined.Group),
)

@Composable
private fun CompanyShellBody(approved: Boolean, tools: Boolean, toolsOnly: Boolean, side: CompanySpace.Side) {
    val environment = LocalAppEnvironment.current
    val organization = environment.session.user?.organization
    val isAdmin = environment.session.user?.organizationRole == OrganizationRole.ADMIN

    val navBadges = rememberNavBadges()
    // Field techs open on My day (web `roleHome`); their Dashboard moves
    // into More so the bar stays at five.
    val fieldTech = environment.session.user?.let { Access.opensOnMyDay(it) } == true
    val more = shellTab(CompanyMoreTab, "More", Icons.Outlined.MoreHoriz)
    val tabs = when {
        approved && side == CompanySpace.Side.BUSINESS -> businessTabs() + more
        approved -> listOf(
            if (fieldTech) shellTab(CompanyMyDayTab, "My day", Icons.Outlined.Today)
            else shellTab(CompanyDashboardTab, "Dashboard", Icons.Filled.Home),
            // New feed jobs since this member last looked (`GET /api/nav-badges`).
            shellTab(CompanyOpportunitiesTab, "Opportunities", Icons.Outlined.AutoAwesome) { navBadges.count(NavBadgesModel.OPPORTUNITIES) },
            shellTab(CompanyQuotesTab, "Quotes", Icons.Outlined.Description),
            shellTab(CompanyJobsTab, "Jobs", Icons.Outlined.Work),
            more,
        )
        // Business tools only: the Overview is home; the marketplace setup
        // is one row away in More ("Find work on ZiVETT").
        toolsOnly -> businessTabs() + more
        // Application in review with the tools on: they run their own work
        // meanwhile (web: Application, then "Your business").
        organization?.submittedAt != null && tools -> listOf(shellTab(CompanyDashboardTab, "Application", Icons.Outlined.HourglassEmpty)) + businessTabs().take(3) + more
        // Application in review: dashboard shows the pipeline. The bell matters
        // most here — approval and document-review notifications are what a
        // submitted company waits on.
        organization?.submittedAt != null -> listOf(
            shellTab(CompanyDashboardTab, "Application", Icons.Outlined.HourglassEmpty),
            shellTab(CompanyPassportTab, "Passport", Icons.Outlined.Verified),
            more,
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
        onPushJob = { nav, id, _ -> nav.navigate(CompanyJobRoute(id)) },
        onStripeReturn = onStripeReturn,
        onPushTarget = { nav, target -> if (approved || toolsOnly) openCompanyTarget(nav, target, myDayIsTab = fieldTech && side == CompanySpace.Side.MARKETPLACE, marketplaceTabs = side == CompanySpace.Side.MARKETPLACE && approved) },
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
        composable<CompanyMoreTab> { CompanyMoreScreen(side, approved && tools, toolsOnly) }
        composable<ToolsOverviewTab> { BellScreen("Overview") { ToolsOverviewScreen(ToolsNav(nav)) } }
        composable<com.zivett.app.app.ToolsCustomersTab> { BellScreen("Customers") { com.zivett.app.features.tools.CustomersScreen(ToolsNav(nav)) } }
        toolsDestinations(nav)
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
/// an own job → that job's crew page, the team clock's nudges → My day
/// (the tab for field techs, pushed for everyone else) re-read on today.
/// Tabs are switched the way the bar switches them.
fun openCompanyTarget(nav: androidx.navigation.NavHostController, target: PushTarget, myDayIsTab: Boolean = false, marketplaceTabs: Boolean = true) {
    fun tab(route: Any) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    when (target) {
        // On the business side the marketplace lists open pushed, over the bar.
        is PushTarget.Opportunities -> { PendingOpportunity.code = target.jobCode; if (marketplaceTabs) tab(CompanyOpportunitiesTab) else nav.navigate(OpportunitiesRoute) }
        PushTarget.Quotes -> if (marketplaceTabs) tab(CompanyQuotesTab) else nav.navigate(CompanyQuotesRoute)
        PushTarget.Payouts -> nav.navigate(PayoutsRoute)
        is PushTarget.OwnJob -> nav.navigate(OwnJobRoute(target.id))
        PushTarget.MyDay -> {
            MyDayRefresh.request()
            if (myDayIsTab) tab(CompanyMyDayTab) else nav.navigate(MyDayRoute) { launchSingleTop = true }
        }
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

/// Everything behind "More". With Business tools on an approved company,
/// a switch at the top swaps the whole bar between the marketplace and
/// "Your business" (web `lib/companySpace.js`); each side lists its own rows.
@Composable
fun CompanyMoreScreen(side: CompanySpace.Side = CompanySpace.Side.MARKETPLACE, switchable: Boolean = false, toolsOnly: Boolean = false) {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var loggingOut by remember { mutableStateOf(false) }
    val user = environment.session.user ?: return
    val isAdmin = user.organizationRole == OrganizationRole.ADMIN
    val fieldTech = Access.opensOnMyDay(user)
    val organization = user.organization
    val business = side == CompanySpace.Side.BUSINESS
    val approved = organization?.isApproved == true
    val tools = Access.hasBusinessTools(user)

    @Composable
    fun rows(vararg items: (@Composable () -> Unit)?) {
        val shown = items.filterNotNull()
        if (shown.isEmpty()) return
        ZCard(padding = 0.dp) {
            shown.forEachIndexed { index, row ->
                if (index > 0) ZDivider(Modifier.padding(start = 60.dp))
                row()
            }
        }
    }

    Column {
        ZTopBar("More")
        ZScreen {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                ZAvatar(organization?.let { initialsOf(it.name) } ?: user.initials, ZAvatarShape.COMPANY, 56.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ZTitle(organization?.name ?: user.name)
                    ZMono(if (approved) "Verified Pro" else if (toolsOnly) "Business tools" else "Application in review")
                }
            }
            if (switchable) {
                ZSegmented(
                    listOf(CompanySpace.Side.MARKETPLACE to "Marketplace", CompanySpace.Side.BUSINESS to "Your business"),
                    side,
                ) { CompanySpace.remember(it) }
            }

            if (business || toolsOnly || (!approved && tools)) {
                // The business side (web `businessToolsItems`).
                rows(
                    if (!approved && !toolsOnly) ({ AccountLink("Overview", "Your own customers, quotes, jobs and invoices", Icons.Filled.Home) { nav.navigate(ToolsOverviewRoute) } }) else null,
                    { AccountLink("Requests", "People asking for work through your request form", Icons.Outlined.Inbox) { nav.navigate(ToolsRequestsRoute) } },
                )
                rows(
                    { AccountLink("My day", "The jobs you're on, in the order you'll do them", Icons.Outlined.Today) { nav.navigate(MyDayRoute) } },
                    if (Access.hasPayroll(user)) ({ AccountLink("My hours", "Your pay period on the clock, and fixing a day", Icons.Outlined.Schedule) { nav.navigate(MyHoursRoute) } }) else null,
                    if (user.canOpen("calendar")) ({ AccountLink("Calendar", "Your own jobs and your ZiVETT jobs", Icons.Outlined.CalendarMonth) { nav.navigate(CompanyCalendarRoute) } }) else null,
                )
                rows(
                    if (!approved && !toolsOnly && user.canOpen("passport")) ({ AccountLink("Verified Pro Passport", "Credentials, rates, radius, availability", Icons.Outlined.Verified) { nav.navigate(PassportRoute) } }) else null,
                    if (isAdmin) ({ AccountLink("Team", "Invite and manage teammates", Icons.Outlined.Group) { nav.navigate(TeamRoute(Areas.COMPANY)) } }) else null,
                    if (user.canOpen("profile")) ({ AccountLink("Business profile", "Your name, logo and contact details", Icons.Outlined.Business) { nav.navigate(OrgProfileRoute(Areas.COMPANY)) } }) else null,
                    if (isAdmin) ({ AccountLink("Plan", "Your membership and what it includes", Icons.Outlined.StarOutline) { nav.navigate(PlanRoute(Areas.COMPANY)) } }) else null,
                    // Tools-only companies: the marketplace setup is one row away.
                    if (toolsOnly) ({ AccountLink("Find work on ZiVETT", "Apply to quote on jobs from ZiVETT customers", Icons.Outlined.AutoAwesome) { nav.navigate(CompanySetupRoute) } }) else null,
                )
            } else {
                rows(
                    // Whichever of My day / Dashboard isn't a tab for this person.
                    if (fieldTech) ({ AccountLink("Dashboard", "Your company at a glance", Icons.Filled.Home) { nav.navigate(CompanyDashboardRoute) } })
                    else ({ AccountLink("My day", "The jobs you're on, in the order you'll do them", Icons.Outlined.Today) { nav.navigate(MyDayRoute) } }),
                    // The team clock's pay period: Business tools companies only.
                    if (Access.hasPayroll(user)) ({ AccountLink("My hours", "Your pay period on the clock, and fixing a day", Icons.Outlined.Schedule) { nav.navigate(MyHoursRoute) } }) else null,
                    if (user.canOpen("calendar")) ({ AccountLink("Calendar", "Confirmed jobs, proposals, blocked days", Icons.Outlined.CalendarMonth) { nav.navigate(CompanyCalendarRoute) } }) else null,
                    if (user.canOpen("jobs") || user.canOpen("payouts")) ({ AccountLink("Invoices", "What your completed jobs billed", Icons.Outlined.Description) { nav.navigate(CompanyInvoicesRoute) } }) else null,
                    if (user.canOpen("payouts")) ({ AccountLink("Payouts", "What's pending and what's been paid", Icons.Outlined.AccountBalance) { nav.navigate(PayoutsRoute) } }) else null,
                )
                rows(
                    if (user.canOpen("passport")) ({ AccountLink("Verified Pro Passport", "Credentials, rates, radius, availability", Icons.Outlined.Verified) { nav.navigate(PassportRoute) } }) else null,
                    if (user.canOpen("profile")) ({ AccountLink("Business profile", "What customers see", Icons.Outlined.Business) { nav.navigate(OrgProfileRoute(Areas.COMPANY)) } }) else null,
                    { AccountLink("Plan", "Your membership and what it includes", Icons.Outlined.StarOutline) { nav.navigate(PlanRoute(Areas.COMPANY)) } },
                    // Org-admin concerns: members don't get the rows (UX only; the server enforces).
                    if (isAdmin) ({ AccountLink("Team", "Invite and manage teammates", Icons.Outlined.Group) { nav.navigate(TeamRoute(Areas.COMPANY)) } }) else null,
                )
            }
            rows(
                { AccountLink("My account", "Name, email, phone", Icons.Outlined.Person) { nav.navigate(ProfileRoute(Areas.COMPANY)) } },
                { AccountLink("Notifications", "Email and SMS preferences", Icons.Outlined.NotificationsActive) { nav.navigate(NotificationPrefsRoute(Areas.COMPANY)) } },
            )
            ZButton("Log out", style = ZButtonStyle.OUTLINE, loading = loggingOut) { loggingOut = true; scope.launch { environment.session.logout() } }
            AdvertisingPrivacySection()
            AccountFooter()
            Spacer(Modifier.padding(ZSpacing.lg))
        }
    }
}
