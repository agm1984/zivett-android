package com.zivett.app.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation.NavHostController
import com.zivett.app.app.ForgotPasswordRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.LoginRoute
import com.zivett.app.app.SignupRoute
import com.zivett.app.core.auth.AuthSession
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCheckbox
import com.zivett.app.design.ZLinkButton
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTextAction
import kotlinx.coroutines.launch

/// Form state + submission for the login screen. Kept out of the view so
/// the error mapping (422 → field error, 429 → banner) is unit-tested.
class LoginFormModel {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var keepSignedIn by mutableStateOf(true)
    var submitting by mutableStateOf(false)
    var fieldErrors by mutableStateOf<Map<String, String>>(emptyMap())
    var bannerError by mutableStateOf<String?>(null)

    val canSubmit: Boolean get() = !submitting && email.isNotBlank() && password.isNotEmpty()

    suspend fun submit(session: AuthSession) {
        if (!canSubmit) return
        submitting = true
        fieldErrors = emptyMap()
        bannerError = null
        try {
            session.login(email, password)
        } catch (error: ApiError) {
            apply(error)
        } catch (error: Exception) {
            bannerError = error.userMessage
        } finally {
            submitting = false
        }
    }

    fun apply(error: ApiError) {
        when (error) {
            is ApiError.Validation -> {
                fieldErrors = error.errors.firstMessages
                if (fieldErrors.isEmpty()) bannerError = error.errors.message
            }
            else -> bannerError = error.userMessage
        }
    }
}

@Composable
fun LoginScreen(nav: NavHostController) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val form = remember { LoginFormModel() }

    ZScreen {
        AuthHeader(backTitle = "Back to home", onBack = { nav.backToHome() }, title = "Log in") {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                ZBody("New to ZiVETT?", tone = ZTextTone.SOFT)
                ZLinkButton("Create an account") { nav.navigate(SignupRoute()) { popUpTo(LoginRoute) { inclusive = true } } }
            }
        }

        form.bannerError?.let { ZBanner(it, tone = ZTone.DANGER) }

        ZTextField("Email", form.email, { form.email = it }, placeholder = "you@example.com", error = form.fieldErrors["email"], keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)

        ZTextField("Password", form.password, { form.password = it }, error = form.fieldErrors["password"], secure = true, imeAction = ImeAction.Done, corner = {
            ZTextAction("Forgot?", color = com.zivett.app.design.ZTheme.colors.inkMuted) { nav.navigate(ForgotPasswordRoute) }
        })

        ZCheckbox(form.keepSignedIn, { form.keepSignedIn = it }, text = "Keep me signed in")

        ZButton("Log in", loading = form.submitting, enabled = form.canSubmit) {
            scope.launch { form.submit(environment.session) }
        }
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.padding(ZSpacing.lg))
    }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())

@Suppress("unused")
private val keepCaption: @Composable () -> Unit = { ZCaption("") }
