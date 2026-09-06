package com.zivett.app.features.customer.account

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zivett.app.app.AddressesRoute
import com.zivett.app.app.Areas
import com.zivett.app.app.InvoicesRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.NotificationPrefsRoute
import com.zivett.app.app.ProfileRoute
import com.zivett.app.app.ReferralRoute
import com.zivett.app.app.WarrantiesRoute
import com.zivett.app.core.auth.AuthSession
import com.zivett.app.core.models.User
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.design.ZAvatar
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZIconTile
import com.zivett.app.design.ZNavRow
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTitle
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.features.shared.LocalNav
import kotlinx.coroutines.launch

/// Account tab root: who you are, then the sub-pages the web's account
/// area exposes (profile, notifications, addresses, referral, invoices,
/// warranties), then log out.
@Composable
fun AccountHomeScreen() {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var loggingOut by remember { mutableStateOf(false) }
    val user = environment.session.user ?: return

    Column {
        ZTopBar("Account")
        ZScreen {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                ZAvatar(user.initials, size = 56.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ZTitle(user.name)
                    ZCaption(user.email)
                }
            }

            ZCard(padding = 0.dp) {
                AccountLink("Profile", verificationSummary(user), Icons.Outlined.Person) { nav.navigate(ProfileRoute(Areas.CUSTOMER)) }
                ZDivider(Modifier.padding(start = 60.dp))
                AccountLink("Notifications", "Email and SMS preferences", Icons.Outlined.NotificationsActive) { nav.navigate(NotificationPrefsRoute(Areas.CUSTOMER)) }
                ZDivider(Modifier.padding(start = 60.dp))
                AccountLink("Saved addresses", "One-tap booking", Icons.Outlined.Home) { nav.navigate(AddressesRoute) }
            }

            ZCard(padding = 0.dp) {
                AccountLink("Invoices", "Receipts and anything due", Icons.Outlined.Description) { nav.navigate(InvoicesRoute(Areas.CUSTOMER)) }
                ZDivider(Modifier.padding(start = 60.dp))
                AccountLink("Warranties", "Covered work and claims", Icons.Outlined.VerifiedUser) { nav.navigate(WarrantiesRoute(Areas.CUSTOMER)) }
                ZDivider(Modifier.padding(start = 60.dp))
                AccountLink("Refer a friend", "Earn credit when they book", Icons.Outlined.CardGiftcard) { nav.navigate(ReferralRoute) }
            }

            ZButton("Log out", style = ZButtonStyle.OUTLINE, loading = loggingOut) {
                loggingOut = true
                scope.launch { environment.session.logout() }
            }

            AccountFooter()
            Spacer(Modifier.padding(ZSpacing.lg))
        }
    }
}

fun verificationSummary(user: User): String = when {
    user.isEmailVerified && user.isPhoneVerified -> "Email and phone verified"
    user.isEmailVerified -> "Phone not verified"
    else -> "Email not verified"
}

@Composable
fun AccountLink(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    ZNavRow(title, subtitle, onClick) { ZIconTile(icon) }
}

/// Public web pages — the same ones the SPA's footer links to. Fixed to
/// production on purpose: legal text doesn't have a localhost edition.
object LegalLinks {
    const val terms = "https://zivett.com/terms"
    const val privacy = "https://zivett.com/privacy"
}

/// The bottom of every role's Account screen: the legal links store
/// review looks for and the self-serve "Delete account" path. Shared by
/// customer, business, and company shells.
@Composable
fun AccountFooter() {
    val context = LocalContext.current
    var deleting by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = ZSpacing.md), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
            ZCaption("Terms of Service", tone = ZTextTone.LINK, modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(LegalLinks.terms))) })
            ZCaption("Privacy Policy", tone = ZTextTone.LINK, modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(LegalLinks.privacy))) })
        }
        ZCaption("Delete account", modifier = Modifier.clickable { deleting = true }, textAlign = TextAlign.Center)
    }

    if (deleting) DeleteAccountSheet(onDismiss = { deleting = false })
}

class DeleteAccountModel(private val session: AuthSession) {
    var password by mutableStateOf("")
    var busy by mutableStateOf(false)
    /// The 422 field error — a wrong password.
    var passwordError by mutableStateOf<String?>(null)
    /// The 409 reason — live jobs, or an organization left without an admin.
    var blockedReason by mutableStateOf<String?>(null)

    suspend fun submit() {
        busy = true
        passwordError = null
        blockedReason = null
        try {
            session.deleteAccount(password)
        } catch (error: ApiError) {
            when (error) {
                is ApiError.Validation -> passwordError = error.errors.first("password") ?: error.userMessage
                is ApiError.Conflict -> blockedReason = error.detail ?: error.userMessage
                else -> blockedReason = error.userMessage
            }
        } catch (error: Exception) {
            blockedReason = error.userMessage
        } finally {
            busy = false
        }
    }
}

/// Confirms with the password, then hands the result to `AuthSession`
/// — success lands on the Welcome screen via the router.
@Composable
fun DeleteAccountSheet(onDismiss: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember { DeleteAccountModel(environment.session) }

    ZSheet(onDismiss = onDismiss, title = "This can't be undone") {
        ZBody("Your name, email, phone, photo, saved addresses, and sign-in will be removed and you'll be signed out everywhere. Records of completed jobs stay with the other party, without your details.", tone = ZTextTone.SOFT)
        model.blockedReason?.let { ZBanner(it, tone = ZTone.WARNING) }
        ZTextField("Confirm your password", model.password, { model.password = it }, placeholder = "Password", error = model.passwordError, secure = true)
        ZButton("Delete my account", style = ZButtonStyle.DANGER, loading = model.busy, enabled = model.password.isNotEmpty()) { scope.launch { model.submit() } }
        ZButton("Keep my account", style = ZButtonStyle.OUTLINE, onClick = onDismiss)
    }
}
