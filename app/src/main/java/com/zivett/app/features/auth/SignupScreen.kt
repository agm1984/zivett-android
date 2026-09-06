package com.zivett.app.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.LoginRoute
import com.zivett.app.app.SignupRoute
import com.zivett.app.core.Money
import com.zivett.app.core.PendingReferral
import com.zivett.app.core.PhoneFormatter
import com.zivett.app.core.auth.AuthSession
import com.zivett.app.core.auth.RegistrationForm
import com.zivett.app.core.models.MarketingEndpoints
import com.zivett.app.core.models.Marketing
import com.zivett.app.core.models.ReferralInvite
import com.zivett.app.core.models.UserRole
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCheckbox
import com.zivett.app.design.ZChoiceTile
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLinkButton
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZType
import kotlinx.coroutines.launch

/// The three signup tiles, in display order, with their backend role.
data class SignupRoleOption(val role: UserRole, val code: String, val title: String, val subtitle: String, val tone: ZTone) {
    companion object {
        val all = listOf(
            SignupRoleOption(UserRole.CUSTOMER, "HM", "Homeowner / renter", "Book repairs & maintenance for your home", ZTone.INFO),
            SignupRoleOption(UserRole.BUSINESS, "PM", "Business / property manager", "Manage repairs across multiple properties", ZTone.WARNING),
            SignupRoleOption(UserRole.COMPANY, "PR", "Skilled pro / company", "Get verified & receive qualified jobs", ZTone.SUCCESS),
        )
    }
}

class SignupFormModel(initialRole: UserRole? = null) {
    var form by mutableStateOf(RegistrationForm(role = initialRole?.takeIf { it in UserRole.registrable } ?: UserRole.CUSTOMER))
    var submitting by mutableStateOf(false)
    var fieldErrors by mutableStateOf<Map<String, String>>(emptyMap())
    var bannerError by mutableStateOf<String?>(null)
    var signupFee by mutableStateOf<Marketing.SignupFee?>(null)
    var invite by mutableStateOf<ReferralInvite?>(null)

    var phone: String
        get() = form.phone
        set(value) { form = form.copy(phone = PhoneFormatter.mask(value)) }

    val canSubmit: Boolean
        get() = !submitting && form.firstName.isNotBlank() && form.lastName.isNotBlank() && form.email.isNotBlank()
            && PhoneFormatter.isComplete(form.phone) && form.password.length >= 8 && form.terms

    /// The fee banner shows for customers only, and only while a fee is
    /// configured — matches `Signup.vue`.
    val showsFeeBanner: Boolean get() = form.role == UserRole.CUSTOMER && (signupFee?.cents ?: 0) > 0

    data class Banner(val strong: String, val rest: String, val tone: ZTone)

    val feeBanner: Banner?
        get() {
            val fee = signupFee ?: return null
            if (!showsFeeBanner) return null
            val amount = Money.format(fee.cents)
            return if (fee.waived) Banner("The $amount registration fee is waived", " — limited time, no code needed.", ZTone.SUCCESS)
            else Banner("Customer accounts carry a one-time $amount registration fee", ".", ZTone.NEUTRAL)
        }

    /// The invite banner: customer invites carry the discount offer;
    /// company invites just vouch (the reward is the referrer's). Each
    /// renders only on its matching role — matches Signup.vue.
    val inviteBanner: Banner?
        get() {
            val invite = invite ?: return null
            if (invite.kind == "customer" && form.role == UserRole.CUSTOMER && invite.firstName != null) {
                return Banner("${invite.firstName} invited you", " — sign up and get ${invite.discountLabel} your first booked job. The credit applies automatically.", ZTone.SUCCESS)
            }
            if (invite.kind == "company" && form.role == UserRole.COMPANY && invite.name != null) {
                return Banner(invite.name, " invited your business to ZiVETT.", ZTone.SUCCESS)
            }
            return null
        }

    suspend fun loadFee(client: ApiClient) {
        signupFee = runCatching { client.send(MarketingEndpoints.marketing()).signupFee }.getOrNull()
    }

    suspend fun loadReferral(client: ApiClient) {
        val code = PendingReferral.code ?: return
        form = form.copy(referralCode = code)
        invite = runCatching { client.send(MarketingEndpoints.referral(code)) }.getOrNull()
        // A company code presets the company role (the web's /r redirect
        // appends &role=company) — but never overrides an explicit pick.
        if (invite?.kind == "company" && form.role == UserRole.CUSTOMER) form = form.copy(role = UserRole.COMPANY)
    }

    suspend fun submit(session: AuthSession) {
        if (!canSubmit) return
        submitting = true
        fieldErrors = emptyMap()
        bannerError = null
        val payload = form.copy(email = form.email.trim().lowercase(), firstName = form.firstName.trim(), lastName = form.lastName.trim())
        try {
            session.register(payload)
            PendingReferral.code = null
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
fun SignupScreen(nav: NavHostController, initialRole: UserRole?) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val form = remember { SignupFormModel(initialRole) }
    val colors = ZTheme.colors

    LaunchedEffect(Unit) {
        form.loadFee(environment.client)
        form.loadReferral(environment.client)
    }

    ZScreen {
        AuthHeader(backTitle = "Back to home", onBack = { nav.backToHome() }, title = "Create your account") {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                ZBody("Already have one?", tone = ZTextTone.SOFT)
                ZLinkButton("Log in") { nav.navigate(LoginRoute) { popUpTo(SignupRoute()) { inclusive = true } } }
            }
        }

        form.inviteBanner?.let { banner -> ZBanner(strongBanner(banner.strong, banner.rest), tone = banner.tone) }
        form.feeBanner?.let { banner -> ZBanner(strongBanner(banner.strong, banner.rest), tone = banner.tone) }
        form.bannerError?.let { ZBanner(it, tone = ZTone.DANGER) }

        Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
            ZLabel("I'm signing up as…")
            for (option in SignupRoleOption.all) {
                ZChoiceTile(option.code, option.title, option.subtitle, selected = form.form.role == option.role, tone = option.tone) {
                    form.form = form.form.copy(role = option.role)
                }
            }
            form.fieldErrors["role"]?.let { ZCaption(it, color = colors.danger) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
            ZTextField("First name", form.form.firstName, { form.form = form.form.copy(firstName = it) }, Modifier.weight(1f), "Amara", form.fieldErrors["first_name"], capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
            ZTextField("Last name", form.form.lastName, { form.form = form.form.copy(lastName = it) }, Modifier.weight(1f), "Okafor", form.fieldErrors["last_name"], capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
        }

        ZTextField("Email", form.form.email, { form.form = form.form.copy(email = it) }, placeholder = "you@example.com", error = form.fieldErrors["email"], keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
        ZTextField("Phone", form.phone, { form.phone = it }, placeholder = "(555) 012-3456", error = form.fieldErrors["phone"], keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
        ZTextField("Password", form.form.password, { form.form = form.form.copy(password = it) }, placeholder = "At least 8 characters", error = form.fieldErrors["password"], secure = true, imeAction = ImeAction.Done)

        ZCheckbox(form.form.terms, { form.form = form.form.copy(terms = it) }) { TermsConsent() }
        form.fieldErrors["terms"]?.let { ZCaption(it, color = colors.danger) }

        ZButton("Create account", loading = form.submitting, enabled = form.canSubmit) {
            scope.launch { form.submit(environment.session) }
        }
        Spacer(Modifier.padding(ZSpacing.lg))
    }
}

/// "I agree to ZiVETT's Terms of Service and Privacy Policy" with the
/// two documents as links to the web app (same host as the API).
@Composable
fun TermsConsent() {
    val environment = LocalAppEnvironment.current
    val colors = ZTheme.colors
    val base = environment.config.apiBaseUrl.trimEnd('/')
    val text = buildAnnotatedString {
        append("I agree to ZiVETT's ")
        withLink(LinkAnnotation.Url("$base/terms")) { withStyle(SpanStyle(color = colors.link, fontWeight = FontWeight.SemiBold)) { append("Terms of Service") } }
        append(" and ")
        withLink(LinkAnnotation.Url("$base/privacy")) { withStyle(SpanStyle(color = colors.link, fontWeight = FontWeight.SemiBold)) { append("Privacy Policy") } }
    }
    Text(text, style = ZType.caption, color = colors.inkSoft)
}

@Composable
fun strongBanner(strong: String, rest: String) = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(strong) }
    append(rest)
}

@Suppress("unused")
private val keepUriHandler: @Composable () -> Unit = { LocalUriHandler.current }
