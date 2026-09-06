package com.zivett.app.features.shared

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.models.JobRefEndpoints
import com.zivett.app.core.models.NotificationArea
import com.zivett.app.core.push.PendingPushOpen
import com.zivett.app.core.push.PushRegistration
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZType
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/// One bottom-bar tab: its route object, label, icon, and an optional
/// badge count.
data class ShellTab(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector, val badge: () -> Int = { 0 })

inline fun <reified T : Any> shellTab(route: T, label: String, icon: ImageVector, noinline badge: () -> Int = { 0 }) =
    ShellTab(route, T::class, label, icon, badge)

val LocalNav = staticCompositionLocalOf<NavHostController> { error("No NavHostController") }

/// A role shell: Material navigation bar over one NavHost. The bar shows
/// on tab destinations and hides on pushed screens (the Android norm),
/// each tab keeps its own back stack, and a tapped push notification
/// opens the job it names over whatever tab is showing.
@Composable
fun RoleShell(
    tabs: List<ShellTab>,
    pushArea: NotificationArea?,
    onPushJob: (NavHostController, Int) -> Unit,
    graph: NavGraphBuilder.(NavHostController) -> Unit,
) {
    val nav = rememberNavController()
    val environment = LocalAppEnvironment.current
    val colors = ZTheme.colors
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val onTab = tabs.any { tab -> destination?.hasRoute(tab.routeClass) == true }

    // Push permission + token sync when a signed-in shell appears (the
    // OS prompt shows once; afterwards this is a cheap re-register).
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) PushRegistration.enable() }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !PushRegistration.hasPermission(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else PushRegistration.enable()
    }

    // A tapped push: resolve the job's public code to its id through
    // the area's endpoint, then open it. Cold launches park the ref
    // before any shell exists; warm taps flip the same state.
    val pending = PendingPushOpen.ref
    val pushScope = rememberCoroutineScope()
    LaunchedEffect(pending) {
        val ref = pending ?: return@LaunchedEffect
        if (pushArea == null) return@LaunchedEffect
        PendingPushOpen.take()
        // Consuming the ref re-keys this effect to null, which cancels it —
        // so the lookup runs on the shell's own scope, not the effect's.
        pushScope.launch {
            val id = ref.toIntOrNull() ?: runCatching { environment.client.send(JobRefEndpoints.job(pushArea, ref)).job.id }.getOrNull() ?: return@launch
            onPushJob(nav, id)
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalNav provides nav) {
        Scaffold(
            containerColor = colors.cream,
            bottomBar = {
                if (onTab) {
                    NavigationBar(containerColor = colors.surface, contentColor = colors.ink) {
                        for (tab in tabs) {
                            val selected = destination?.hasRoute(tab.routeClass) == true
                            val badge = tab.badge()
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    if (badge > 0) BadgedBox(badge = { Badge(containerColor = colors.danger) { Text(if (badge > 9) "9+" else badge.toString()) } }) { Icon(tab.icon, contentDescription = tab.label) }
                                    else Icon(tab.icon, contentDescription = tab.label)
                                },
                                label = { Text(tab.label, style = ZType.label.copy(fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp)), maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.navyDeep.takeIf { !colors.isDark } ?: colors.navy, selectedTextColor = colors.ink,
                                    unselectedIconColor = colors.inkMuted, unselectedTextColor = colors.inkMuted, indicatorColor = colors.warningSoft,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(navController = nav, startDestination = tabs.first().route, modifier = Modifier.padding(padding)) {
                graph(nav)
            }
        }
    }
}
