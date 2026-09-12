package com.zivett.app.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zivett.app.core.auth.Access
import com.zivett.app.core.auth.AuthState
import com.zivett.app.core.models.User
import com.zivett.app.design.LightStatusBarIcons
import com.zivett.app.design.BrandMark
import com.zivett.app.design.BrandWordmark
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZColors
import com.zivett.app.design.ZDisplay
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZType
import com.zivett.app.features.auth.AuthFlow
import com.zivett.app.features.auth.VerifyEmailScreen
import com.zivett.app.features.business.BusinessShell
import com.zivett.app.features.company.CompanyShell
import com.zivett.app.features.customer.shell.CustomerShell
import kotlinx.coroutines.launch

/// Top-level router: splash while the stored token is checked, the auth
/// flow when nobody is signed in, otherwise the shell for the user's
/// role — with the blocking states (unverified, suspended…) handled
/// before any role shell can render.
@Composable
fun RootScreen() {
    val environment = LocalAppEnvironment.current
    val session = environment.session

    LaunchedEffect(session) { session.restore() }

    when (val state = session.state) {
        AuthState.Unknown -> SplashScreen()
        AuthState.SignedOut -> AuthFlow()
        is AuthState.SignedIn -> SignedInRouter(state.account)
    }
}

@Composable
private fun SignedInRouter(user: User) {
    when (val destination = Access.destination(user)) {
        Access.Destination.Customer -> CustomerShell()
        Access.Destination.Business -> BusinessShell()
        is Access.Destination.Company -> CompanyShell(approved = destination.approved)
        Access.Destination.VerifyEmail -> VerifyEmailScreen()
        is Access.Destination.Suspended -> BlockedAccountScreen("Account suspended", destination.reason)
        Access.Destination.Deactivated -> BlockedAccountScreen("Account deactivated", "Your account no longer has access to this organization.")
        Access.Destination.MissingOrganization -> BlockedAccountScreen("No organization", "Your account isn't attached to an organization. Contact support.")
        is Access.Destination.Unsupported -> BlockedAccountScreen("Use the web app", "Admin tools live at zivett.com — the mobile app is for customers, businesses, and pros.")
    }
}

/// Deep-charcoal brand moment matching the landing page: the checkmark
/// tile over the light wordmark and gold-dash tagline, with one settled
/// entrance. Deliberately the same in both appearances — the brand
/// panel doesn't flip with the system theme.
@Composable
fun SplashScreen() {
    LightStatusBarIcons()
    var arrived by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { arrived = true }
    val scale by animateFloatAsState(if (arrived) 1f else 0.88f, spring(dampingRatio = 0.6f), label = "splash-scale")
    val alpha by animateFloatAsState(if (arrived) 1f else 0f, spring(), label = "splash-alpha")

    Box(modifier = Modifier.fillMaxSize().background(ZColors.fixedNavyDeep), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
            Box(Modifier.scale(scale).alpha(alpha)) { BrandMark(96.dp) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ZSpacing.sm), modifier = Modifier.alpha(alpha)) {
                BrandWordmark(color = Color.White)
                Tagline()
            }
        }
    }
}

@Composable
fun Tagline() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
        Box(Modifier.width(16.dp).height(2.dp).background(ZColors.fixedGold))
        Text("Trusted Pros. Every Time", style = ZType.caption.copy(fontSize = 13.sp), color = Color.White.copy(alpha = 0.65f))
    }
}

/// A dead end with a way out: explains why, offers logout.
@Composable
fun BlockedAccountScreen(title: String, message: String) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    ZScreen(scrolls = false) {
        Spacer(Modifier.weight(1f))
        ZDisplay(title)
        ZBody(message, tone = ZTextTone.SOFT)
        ZButton("Log out", style = ZButtonStyle.OUTLINE) { scope.launch { environment.session.logout() } }
        Spacer(Modifier.weight(1f))
    }
    Spacer(Modifier.padding(ZSpacing.md).size(0.dp))
}
