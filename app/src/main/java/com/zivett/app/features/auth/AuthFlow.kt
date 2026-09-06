package com.zivett.app.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.zivett.app.BuildConfig
import com.zivett.app.app.AppConfig
import com.zivett.app.app.AppEvents
import com.zivett.app.app.ForgotPasswordRoute
import com.zivett.app.app.InvitationRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.LoginRoute
import com.zivett.app.app.SignupRoute
import com.zivett.app.app.Tagline
import com.zivett.app.app.WelcomeRoute
import com.zivett.app.design.BrandMark
import com.zivett.app.design.BrandWordmark
import com.zivett.app.design.ZBackLink
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZColors
import com.zivett.app.design.ZDisplay
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZType
import com.zivett.app.core.models.UserRole

/// The signed-out navigation graph. Routes are typed so deep links (a
/// referral, a team invitation) can push screens.
@Composable
fun AuthFlow() {
    val nav = rememberNavController()

    // A tapped referral link lands on signup, like the web's /r
    // redirect — the invite banner and code resolve there.
    val referral = AppEvents.referralOpened
    LaunchedEffect(referral) {
        if (referral) {
            AppEvents.referralOpened = false
            nav.navigate(SignupRoute()) { launchSingleTop = true }
        }
    }
    // A team-invitation link lands on the accept screen.
    val invitation = AppEvents.invitationToken
    LaunchedEffect(invitation) {
        if (invitation != null) {
            AppEvents.invitationToken = null
            nav.navigate(InvitationRoute(invitation))
        }
    }

    NavHost(navController = nav, startDestination = WelcomeRoute) {
        composable<WelcomeRoute> { WelcomeScreen(nav) }
        composable<LoginRoute> { LoginScreen(nav) }
        composable<SignupRoute> { entry ->
            val route = entry.toRoute<SignupRoute>()
            SignupScreen(nav, initialRole = route.role?.let { r -> UserRole.entries.firstOrNull { it.wire == r } })
        }
        composable<ForgotPasswordRoute> { ForgotPasswordScreen(nav) }
        composable<InvitationRoute> { entry -> AcceptInvitationScreen(nav, entry.toRoute<InvitationRoute>().token) }
    }
}

/// Pops everything back to the welcome screen.
fun NavHostController.backToHome() {
    popBackStack(WelcomeRoute, inclusive = false)
}

/// Stand-in for the web's marketing landing: the place "Back to home"
/// goes, and the fork between logging in and creating an account. The
/// top half is the landing hero's charcoal block — mark, wordmark, and
/// thesis on brand ground — with the two actions below on the page.
@Composable
fun WelcomeScreen(nav: NavHostController) {
    val colors = ZTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
        // The brand panel is fixed-dark in both appearances, like the
        // splash and the web's charcoal hero.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().background(ZColors.fixedNavyDeep).statusBarsPadding().padding(horizontal = ZSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZSpacing.lg, Alignment.Bottom),
        ) {
            BrandMark(72.dp)
            Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                BrandWordmark(color = Color.White)
                Text("Repairs, booked.", style = ZType.display.copy(fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp), color = Color.White)
                Text("Verified local pros for your home or properties — quoted, scheduled, and tracked in one place.", style = ZType.body, color = Color.White.copy(alpha = 0.72f))
            }
            Box(Modifier.padding(bottom = ZSpacing.xl)) { Tagline() }
        }

        Column(
            modifier = Modifier.fillMaxWidth().background(colors.cream).padding(horizontal = ZSpacing.md).padding(top = ZSpacing.lg, bottom = ZSpacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(ZSpacing.sm),
        ) {
            ZButton("Create an account") { nav.navigate(SignupRoute()) }
            ZButton("Log in", style = ZButtonStyle.OUTLINE) { nav.navigate(LoginRoute) }
            if (BuildConfig.BACKEND_SWITCHER) DebugBackendPicker()
        }
    }
}

/// Shared chrome for every auth page: the back link, the brand mark,
/// and a title block — the mark is what makes each form read as the
/// app's own screen rather than a web form.
@Composable
fun AuthHeader(backTitle: String, onBack: () -> Unit, title: String, subtitle: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs), modifier = Modifier.statusBarsPadding().padding(top = ZSpacing.lg)) {
        ZBackLink(backTitle, onBack)
        Spacer(Modifier.padding(ZSpacing.xs))
        BrandMark(44.dp)
        ZDisplay(title)
        subtitle()
    }
}

/// Debug builds only: switch between the local Sail backend and
/// production without touching build settings. Switching signs out
/// (a token from one backend is meaningless on the other) and rebuilds
/// the app's object graph.
@Composable
fun DebugBackendPicker() {
    val environment = LocalAppEnvironment.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var open by remember { mutableStateOf(false) }
    val host = android.net.Uri.parse(environment.config.apiBaseUrl).host ?: "?"

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ZCaption("Server: $host", tone = ZTextTone.FAINT, modifier = Modifier.clickable { open = true }.padding(8.dp))
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (backend in AppConfig.debugBackends) {
                val selected = environment.config.apiBaseUrl == backend.url
                DropdownMenuItem(
                    text = { Text(backend.name, style = ZType.body) },
                    leadingIcon = { if (selected) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = {
                        open = false
                        AppConfig.setOverride(context, backend.url)
                        AppEvents.backendChanged += 1
                    },
                )
            }
        }
    }
}
