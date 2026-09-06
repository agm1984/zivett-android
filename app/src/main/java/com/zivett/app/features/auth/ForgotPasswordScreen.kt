package com.zivett.app.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.runtime.Composable
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
import androidx.navigation.NavHostController
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.LoginRoute
import com.zivett.app.core.auth.AuthEndpoints
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.design.ZBackLink
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZDisplay
import com.zivett.app.design.ZIconTile
import com.zivett.app.design.ZLinkButton
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTone
import kotlinx.coroutines.launch

class ForgotPasswordModel {
    var email by mutableStateOf("")
    var submitting by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)

    val canSubmit: Boolean get() = !submitting && email.isNotBlank()

    suspend fun submit(client: ApiClient) {
        if (!canSubmit) return
        submitting = true
        error = null
        try {
            status = client.send(AuthEndpoints.forgotPassword(email.trim())).status
        } catch (apiError: ApiError) {
            error = apiError.first("email") ?: apiError.userMessage
        } catch (e: Exception) {
            error = e.userMessage
        } finally {
            submitting = false
        }
    }
}

@Composable
fun ForgotPasswordScreen(nav: NavHostController) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember { ForgotPasswordModel() }
    val toLogin = { nav.navigate(LoginRoute) { popUpTo(LoginRoute) { inclusive = true } } }

    ZScreen {
        Spacer(Modifier.statusBarsPadding().padding(top = ZSpacing.lg))
        ZBackLink("Back to log in") { toLogin() }
        ZIconTile(Icons.Outlined.Email, size = 52.dp)
        ZDisplay("Forgot password?")
        ZBody("No problem. Enter your account email and we'll send you a reset link.", tone = ZTextTone.SOFT)

        val status = model.status
        if (status != null) {
            ZBanner(status, tone = ZTone.SUCCESS)
        } else {
            ZTextField("Email", model.email, { model.email = it }, placeholder = "you@example.com", error = model.error, keyboardType = KeyboardType.Email)
            ZButton("Send reset link", loading = model.submitting, enabled = model.canSubmit) { scope.launch { model.submit(environment.client) } }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            ZCaption("Remembered it?")
            ZLinkButton("Log in", weight = FontWeight.Medium) { toLogin() }
        }
    }
}
