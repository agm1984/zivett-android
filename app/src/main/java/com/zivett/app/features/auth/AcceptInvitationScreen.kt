package com.zivett.app.features.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.PhoneFormatter
import com.zivett.app.core.auth.AuthEndpoints
import com.zivett.app.core.auth.AuthSession
import com.zivett.app.core.auth.InvitationForm
import com.zivett.app.core.auth.InvitationPayload
import com.zivett.app.core.media.PhotoImport
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCheckbox
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLinkButton
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTone
import kotlinx.coroutines.launch

/// The team-invitation landing (`AcceptInvitation.vue`): reads the
/// invite behind the emailed token, collects the member's details, and
/// signs the device straight in. Company-org invites REQUIRE a photo —
/// the assigned-tech face bookers see on their jobs.
class AcceptInvitationModel(val token: String, private val client: ApiClient) {
    var invitation by mutableStateOf<Loadable<InvitationPayload.Invitation>>(Loadable.Loading)
    var form by mutableStateOf(InvitationForm())
    var photoData by mutableStateOf<ByteArray?>(null)
    var photoUri by mutableStateOf<Uri?>(null)
    var submitting by mutableStateOf(false)
    var fieldErrors by mutableStateOf<Map<String, String>>(emptyMap())
    var bannerError by mutableStateOf<String?>(null)

    var phone: String
        get() = form.phone
        set(value) { form = form.copy(phone = PhoneFormatter.mask(value)) }

    /// Company teammates join with a face; business/admin invites don't.
    val requiresPhoto: Boolean get() = invitation.value?.type == "company"

    val canSubmit: Boolean
        get() = !submitting && form.firstName.isNotBlank() && form.lastName.isNotBlank() && PhoneFormatter.isComplete(form.phone)
            && form.password.length >= 8 && form.terms && (!requiresPhoto || photoData != null)

    suspend fun load() {
        invitation = invitation.reloaded { client.send(AuthEndpoints.invitation(token)).invitation }
    }

    suspend fun submit(session: AuthSession) {
        if (!canSubmit) return
        submitting = true
        fieldErrors = emptyMap()
        bannerError = null
        val trimmed = form.copy(firstName = form.firstName.trim(), lastName = form.lastName.trim())
        try {
            session.acceptInvitation(token, trimmed, photoData)
        } catch (error: ApiError) {
            if (error is ApiError.Validation) {
                fieldErrors = error.errors.firstMessages
                if (fieldErrors.isEmpty()) bannerError = error.errors.message
            } else bannerError = error.userMessage
        } catch (error: Exception) {
            bannerError = error.userMessage
        } finally {
            submitting = false
        }
    }
}

@Composable
fun AcceptInvitationScreen(nav: NavHostController, token: String) {
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember(token) { AcceptInvitationModel(token, environment.client) }
    val colors = ZTheme.colors

    LaunchedEffect(model) { model.load() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            model.photoUri = uri
            model.photoData = PhotoImport.jpegData(context, listOf(uri), maxDimension = 1024).firstOrNull()
        }
    }

    ZScreen {
        ZLoadable(model.invitation, retry = { scope.launch { model.load() } }) { invitation ->
            Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                AuthHeader(backTitle = "Back to home", onBack = { nav.backToHome() }, title = "Join ${invitation.organization}") {
                    ZBody("${invitation.invitedBy} invited ${invitation.email} to the team. Set up your account to accept.", tone = ZTextTone.SOFT)
                }

                model.bannerError?.let { ZBanner(it, tone = ZTone.DANGER) }

                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                    ZTextField("First name", model.form.firstName, { model.form = model.form.copy(firstName = it) }, Modifier.weight(1f), "Amara", model.fieldErrors["first_name"], capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
                    ZTextField("Last name", model.form.lastName, { model.form = model.form.copy(lastName = it) }, Modifier.weight(1f), "Okafor", model.fieldErrors["last_name"], capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
                }

                ZTextField("Phone", model.phone, { model.phone = it }, placeholder = "(555) 012-3456", error = model.fieldErrors["phone"], keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
                ZTextField("Password", model.form.password, { model.form = model.form.copy(password = it) }, placeholder = "At least 8 characters", error = model.fieldErrors["password"], secure = true, imeAction = ImeAction.Done)

                if (model.requiresPhoto) {
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                        ZLabel("Your photo")
                        ZCaption("Customers see the tech showing up at their door — a clear face photo is required to take jobs.")
                        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            model.photoUri?.let { uri ->
                                AsyncImage(model = uri, contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            }
                            ZLinkButton(if (model.photoData == null) "Add a photo" else "Change photo") {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        }
                        model.fieldErrors["photo"]?.let { ZCaption(it, color = colors.danger) }
                    }
                }

                ZCheckbox(model.form.terms, { model.form = model.form.copy(terms = it) }) { TermsConsent() }
                model.fieldErrors["terms"]?.let { ZCaption(it, color = colors.danger) }

                ZButton("Accept invitation", loading = model.submitting, enabled = model.canSubmit) { scope.launch { model.submit(environment.session) } }
                Spacer(Modifier.padding(ZSpacing.lg))
            }
        }
    }
}
