package com.zivett.app.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.auth.AuthEndpoints
import com.zivett.app.core.auth.AuthSession
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZDisplay
import com.zivett.app.design.ZIconTile
import com.zivett.app.design.ZLinkButton
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/// The signup gate: a signed-in but unverified account lands here until
/// the 6-digit emailed code is redeemed. Mirrors `VerifyEmailCode.vue`,
/// including the 60-second resend cooldown.
class VerifyEmailModel {
    /// Digits only, capped at six.
    var code: String
        get() = rawCode
        set(value) { rawCode = value.filter { it.isDigit() }.take(6) }
    private var rawCode by mutableStateOf("")
    var submitting by mutableStateOf(false)
    var resending by mutableStateOf(false)
    var rechecking by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    var cooldownRemaining by mutableStateOf(0)

    val canSubmit: Boolean get() = !submitting && code.length == 6
    val canResend: Boolean get() = !resending && cooldownRemaining == 0

    suspend fun submit(session: AuthSession, client: ApiClient) {
        if (!canSubmit) return
        submitting = true
        error = null
        try {
            client.send(AuthEndpoints.verifyEmail(code))
            session.refreshUser()
        } catch (apiError: ApiError) {
            error = apiError.first("code") ?: apiError.userMessage
        } catch (e: Exception) {
            error = e.userMessage
        } finally {
            submitting = false
        }
    }

    suspend fun resend(client: ApiClient) {
        if (!canResend) return
        resending = true
        error = null
        try {
            client.send(AuthEndpoints.resendEmailVerification())
            notice = "A fresh code is on its way."
            cooldownRemaining = 60
        } catch (e: Exception) {
            error = e.userMessage
        } finally {
            resending = false
        }
    }

    fun tick() { if (cooldownRemaining > 0) cooldownRemaining -= 1 }

    /// The escape hatch for someone who verified through the emailed
    /// signed link in a browser — the app just needs to re-read the user.
    suspend fun recheck(session: AuthSession) {
        if (rechecking) return
        rechecking = true
        runCatching { session.refreshUser() }
        if (session.user?.emailVerifiedAt == null) error = "Still unverified — enter the code from the email, or resend one."
        rechecking = false
    }
}

@Composable
fun VerifyEmailScreen() {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember { VerifyEmailModel() }

    LaunchedEffect(Unit) { while (true) { delay(1000); model.tick() } }

    ZScreen {
        Spacer(Modifier.statusBarsPadding().padding(top = ZSpacing.lg))
        ZIconTile(Icons.Outlined.MarkEmailUnread, size = 52.dp)
        ZDisplay("Check your email")
        ZBody("We sent a 6-digit code to ${environment.session.user?.email ?: "your inbox"}. Enter it to finish setting up.", tone = ZTextTone.SOFT)

        model.notice?.let { ZBanner(it, tone = ZTone.SUCCESS) }

        ZTextField("Verification code", model.code, { model.code = it }, placeholder = "123456", error = model.error, keyboardType = KeyboardType.NumberPassword)

        ZButton("Verify", loading = model.submitting, enabled = model.canSubmit) { scope.launch { model.submit(environment.session, environment.client) } }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            ZCaption("Didn't get it?")
            if (model.cooldownRemaining > 0) {
                ZCaption("Resend in ${model.cooldownRemaining}s", tone = ZTextTone.FAINT)
            } else {
                ZLinkButton(if (model.resending) "Sending…" else "Send a new code", weight = FontWeight.Medium, enabled = model.canResend) { scope.launch { model.resend(environment.client) } }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            ZLinkButton(if (model.rechecking) "Checking…" else "I verified on the web — check again", weight = FontWeight.Medium) { scope.launch { model.recheck(environment.session) } }
        }

        ZButton("Log out", style = ZButtonStyle.GHOST, modifier = Modifier.padding(top = ZSpacing.lg)) { scope.launch { environment.session.logout() } }
    }
}
