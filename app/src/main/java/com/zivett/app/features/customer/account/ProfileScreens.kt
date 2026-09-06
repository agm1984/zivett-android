package com.zivett.app.features.customer.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.auth.AuthEndpoints
import com.zivett.app.core.auth.AuthSession
import com.zivett.app.core.media.PhotoImport
import com.zivett.app.core.models.CustomerProfile
import com.zivett.app.core.models.ProfileArea
import com.zivett.app.core.models.UpdateProfileBody
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZLinkButton
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZPhotoAvatar
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSectionHeader
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZToggleRow
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch

class ProfileModel(private val client: ApiClient, private val area: ProfileArea = ProfileArea.customer) {
    var state by mutableStateOf<Loadable<CustomerProfile>>(Loadable.Loading)
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var email by mutableStateOf("")
    var phone by mutableStateOf("")
    var saving by mutableStateOf(false)
    var fieldErrors by mutableStateOf<Map<String, String>>(emptyMap())
    var notice by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)

    suspend fun load() {
        state = state.reloaded { client.send(area.profile()) }
        state.value?.profile?.let { profile ->
            firstName = profile.firstName
            lastName = profile.lastName
            email = profile.email
            phone = profile.phone ?: ""
        }
    }

    val isDirty: Boolean
        get() {
            val profile = state.value?.profile ?: return false
            return firstName != profile.firstName || lastName != profile.lastName || email != profile.email || phone != (profile.phone ?: "")
        }

    suspend fun save(session: AuthSession) {
        if (!isDirty || saving) return
        saving = true; fieldErrors = emptyMap(); error = null; notice = null
        try {
            val updated = client.send(area.updateProfile(UpdateProfileBody(firstName.trim(), lastName.trim(), email, phone.ifEmpty { null })))
            state = Loadable.Loaded(updated)
            notice = if (updated.profile.emailVerified) "Profile saved." else "Profile saved — check your inbox to verify the new email."
            runCatching { session.refreshUser() }
        } catch (apiError: ApiError) {
            if (apiError is ApiError.Validation) fieldErrors = apiError.errors.firstMessages else error = apiError.userMessage
        } catch (e: Exception) {
            error = e.userMessage
        } finally {
            saving = false
        }
    }

    /* Phone verification: a 6-digit OTP through the server's SMS seam.
       Verifying is what unlocks SMS notifications; a phone change resets
       it server-side, so the row reappears after edits. */

    var phoneCode by mutableStateOf("")
    var phoneCodeSent by mutableStateOf(false)
    var phoneVerifying by mutableStateOf(false)

    suspend fun sendPhoneCode() {
        phoneVerifying = true; error = null; notice = null
        try {
            client.send(AuthEndpoints.sendPhoneCode())
            phoneCodeSent = true
        } catch (e: Exception) {
            error = e.userMessage
        } finally {
            phoneVerifying = false
        }
    }

    suspend fun verifyPhone(session: AuthSession) {
        if (phoneCode.length != 6 || phoneVerifying) return
        phoneVerifying = true; error = null
        try {
            client.send(AuthEndpoints.verifyPhone(phoneCode))
            state.value?.let { state = Loadable.Loaded(it.copy(profile = it.profile.copy(phoneVerified = true))) }
            phoneCodeSent = false; phoneCode = ""
            notice = "Phone verified — SMS updates are now available."
            runCatching { session.refreshUser() }
        } catch (apiError: ApiError) {
            error = apiError.first("code") ?: apiError.userMessage
        } catch (e: Exception) {
            error = e.userMessage
        } finally {
            phoneVerifying = false
        }
    }

    /* Company members: the profile photo bookers see on assigned jobs. */

    var photoBusy by mutableStateOf(false)
    val hasPhotoUI: Boolean get() = area.uploadPhoto != null

    suspend fun uploadPhoto(jpeg: ByteArray?) {
        val upload = area.uploadPhoto ?: return
        photoBusy = true; error = null
        try {
            if (jpeg == null) { error = "Could not read that image. Try a different photo."; return }
            val response = client.send(upload(jpeg))
            state.value?.let { state = Loadable.Loaded(it.copy(profile = it.profile.copy(photoUrl = response.photoUrl))) }
            notice = "Profile photo saved."
        } catch (apiError: ApiError) {
            error = apiError.userMessage
        } catch (_: Exception) {
            error = "Could not save that photo. Please try again."
        } finally {
            photoBusy = false
        }
    }

    suspend fun removePhoto() {
        val remove = area.deletePhoto ?: return
        photoBusy = true; error = null
        try {
            client.send(remove())
            state.value?.let { state = Loadable.Loaded(it.copy(profile = it.profile.copy(photoUrl = null))) }
        } catch (_: Exception) {
            error = "Could not remove the photo. Please try again."
        } finally {
            photoBusy = false
        }
    }
}

@Composable
fun ProfileScreen(area: ProfileArea, onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember(area) { ProfileModel(environment.client, area) }
    LaunchedEffect(model) { model.load() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { model.uploadPhoto(PhotoImport.jpegData(context, listOf(uri), 1024).firstOrNull()) }
    }

    Column {
        ZTopBar("Profile", onBack = onBack)
        ZScreen {
            ZLoadable(model.state, retry = { scope.launch { model.load() } }) { profile ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    ZPageTitle("Profile")
                    model.notice?.let { ZBanner(it, tone = ZTone.SUCCESS) }
                    model.error?.let { ZBanner(it, tone = ZTone.DANGER) }

                    // Company members: the face bookers see on jobs assigned
                    // to them — required to take jobs.
                    if (model.hasPhotoUI) {
                        ZCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                ZPhotoAvatar(profile.profile.photoUrl, profile.profile.name.take(1), 52.dp)
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ZBodyStrong("Profile photo")
                                    ZCaption(if (profile.profile.photoUrl == null) "Required to take jobs — bookers see who's coming." else "Bookers see this photo on jobs assigned to you.")
                                }
                                ZLinkButton(if (profile.profile.photoUrl == null) "Add" else "Change", enabled = !model.photoBusy) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                        ZTextField("First name", model.firstName, { model.firstName = it }, Modifier.weight(1f), "Amara", model.fieldErrors["first_name"], capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
                        ZTextField("Last name", model.lastName, { model.lastName = it }, Modifier.weight(1f), "Okafor", model.fieldErrors["last_name"], capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
                    }
                    ZTextField("Email", model.email, { model.email = it }, error = model.fieldErrors["email"], keyboardType = KeyboardType.Email, corner = {
                        ZBadge(if (profile.profile.emailVerified) "Verified" else "Unverified", if (profile.profile.emailVerified) ZTone.SUCCESS else ZTone.WARNING)
                    })
                    ZTextField("Phone", model.phone, { model.phone = it }, placeholder = "(555) 012-3456", error = model.fieldErrors["phone"], keyboardType = KeyboardType.Phone, corner = {
                        ZBadge(if (profile.profile.phoneVerified) "Verified" else "Unverified", if (profile.profile.phoneVerified) ZTone.SUCCESS else ZTone.WARNING)
                    })
                    // The inline verify row (web's ProfileCard): send the
                    // OTP, type it, done — this is what unlocks SMS updates.
                    if (!profile.profile.phoneVerified && model.phone.isNotEmpty() && !model.isDirty) {
                        if (model.phoneCodeSent) {
                            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.Bottom) {
                                ZTextField("Code we texted you", model.phoneCode, { model.phoneCode = it.filter { c -> c.isDigit() }.take(6) }, Modifier.weight(1f), "6 digits", keyboardType = KeyboardType.Number)
                                ZButton("Confirm", compact = true, loading = model.phoneVerifying, enabled = model.phoneCode.length == 6, fullWidth = false, modifier = Modifier.padding(bottom = 4.dp)) { scope.launch { model.verifyPhone(environment.session) } }
                            }
                            ZLinkButton("Resend code") { scope.launch { model.sendPhoneCode() } }
                        } else {
                            ZLinkButton("Verify this number to get SMS updates") { scope.launch { model.sendPhoneCode() } }
                        }
                    }

                    ZButton("Save changes", loading = model.saving, enabled = model.isDirty) { scope.launch { model.save(environment.session) } }
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }
}

class NotificationPreferencesModel(private val client: ApiClient, private val area: ProfileArea = ProfileArea.customer) {
    var state by mutableStateOf<Loadable<CustomerProfile>>(Loadable.Loading)
    var error by mutableStateOf<String?>(null)

    suspend fun load() { state = state.reloaded { client.send(area.profile()) } }

    suspend fun setEmail(category: String, on: Boolean) {
        val profile = state.value ?: return
        state = Loadable.Loaded(profile.copy(notificationPreferences = profile.notificationPreferences.map { if (it.category == category) it.copy(email = on) else it }))
        push(mapOf(category to on), null)
    }

    suspend fun setSms(on: Boolean) {
        val profile = state.value ?: return
        state = Loadable.Loaded(profile.copy(smsEnabled = on))
        push(null, on)
    }

    private suspend fun push(email: Map<String, Boolean>?, sms: Boolean?) {
        try {
            state = Loadable.Loaded(client.send(area.updatePreferences(email, sms)))
        } catch (apiError: ApiError) {
            error = apiError.userMessage
            load()
        } catch (e: Exception) {
            error = e.userMessage
        }
    }
}

@Composable
fun NotificationPreferencesScreen(area: ProfileArea, onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember(area) { NotificationPreferencesModel(environment.client, area) }
    LaunchedEffect(model) { model.load() }

    Column {
        ZTopBar("Notifications", onBack = onBack)
        ZToastBox(model.error, { model.error = null }) {
            ZScreen {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { profile ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        ZPageTitle("Notifications", "In-app alerts are always on. Choose what also reaches your inbox.")
                        ZSectionHeader("Email")
                        ZCard {
                            Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                                for (pref in profile.notificationPreferences) {
                                    ZToggleRow(pref.label, pref.description, pref.email, { on -> scope.launch { model.setEmail(pref.category, on) } })
                                }
                            }
                        }
                        ZSectionHeader("Text messages")
                        ZCard {
                            ZToggleRow(
                                "SMS alerts",
                                if (profile.profile.phoneVerified) "Time-sensitive updates to ${profile.profile.phone ?: "your phone"}." else "Verify your phone number first.",
                                profile.smsEnabled, { on -> scope.launch { model.setSms(on) } }, enabled = profile.profile.phoneVerified,
                            )
                        }
                        Spacer(Modifier.padding(ZSpacing.lg))
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private val keepFill: Modifier = Modifier.fillMaxWidth()
