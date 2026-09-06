package com.zivett.app.core.auth

import com.zivett.app.core.models.User
import com.zivett.app.core.models.UserRole
import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.ApiRequest.Method
import com.zivett.app.core.network.MultipartForm
import kotlinx.serialization.Serializable

/// `{ token, user }` — what /api/auth/login and /api/auth/register return.
@Serializable
data class AuthPayload(val token: String, val user: User)

/// `{ status }` — forgot-password's always-identical acknowledgement.
@Serializable
data class StatusMessage(val status: String)

@Serializable
data class LoginCredentials(val email: String, val password: String, val deviceName: String)

/// `GET /api/auth/invitations/{token}` — what the acceptance screen
/// shows. `type` is the org type ('company' invites REQUIRE a photo) or
/// 'admin' for a platform invitation.
@Serializable
data class InvitationPayload(val invitation: Invitation) {
    @Serializable
    data class Invitation(val email: String, val organization: String, val type: String, val invitedBy: String = "")
}

/// The acceptance form; email and role come from the invitation itself.
data class InvitationForm(
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val password: String = "",
    val terms: Boolean = false,
    val deviceName: String = "",
)

/// The signup form as the backend's `RegisterRequest` wants it. `terms`
/// must be true (`accepted` rule); `phone` may carry the display mask —
/// the server strips it to ten digits.
@Serializable
data class RegistrationForm(
    val role: UserRole = UserRole.CUSTOMER,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val terms: Boolean = false,
    val referralCode: String? = null,
    val deviceName: String = "",
)

/// The endpoints the auth feature owns. Static factories keep call sites
/// short and put every path string in one file.
object AuthEndpoints {
    fun login(credentials: LoginCredentials) = ApiRequest.post<AuthPayload, LoginCredentials>("api/auth/login", credentials, authenticated = false)

    fun register(form: RegistrationForm) = ApiRequest.post<AuthPayload, RegistrationForm>("api/auth/register", form, authenticated = false)

    fun logout() = ApiRequest.empty(Method.POST, "api/auth/logout")

    /// Self-serve deletion (store policy). 422 on a wrong password; 409
    /// with a human `message` while live jobs or an admin-less
    /// organization block it.
    fun deleteAccount(password: String) = ApiRequest.empty(Method.DELETE, "api/auth/account", mapOf("password" to password))

    fun currentUser() = ApiRequest.get<User>("api/user")

    fun forgotPassword(email: String) = ApiRequest.post<StatusMessage, Map<String, String>>("api/auth/forgot-password", mapOf("email" to email), authenticated = false)

    /// Team invitations: read the invite behind the emailed token, then
    /// accept it — acceptance mints the member account (pre-verified;
    /// the token proved the address) and returns a bearer token.
    fun invitation(token: String) = ApiRequest.get<InvitationPayload>("api/auth/invitations/$token", authenticated = false)

    /// Multipart because company-org invites REQUIRE a member photo (the
    /// assigned-tech face bookers see); business/admin invites send none.
    fun acceptInvitation(token: String, form: InvitationForm, photo: ByteArray?): ApiRequest<AuthPayload> {
        val multipart = MultipartForm()
        multipart.addField("first_name", form.firstName)
        multipart.addField("last_name", form.lastName)
        multipart.addField("phone", form.phone)
        multipart.addField("password", form.password)
        multipart.addField("terms", "1")
        multipart.addField("device_name", form.deviceName)
        if (photo != null) multipart.addFile("photo", "photo.jpg", "image/jpeg", photo)
        return ApiRequest.multipart("api/auth/invitations/$token/accept", multipart, authenticated = false)
    }

    fun resendEmailVerification() = ApiRequest.empty(Method.POST, "api/auth/email/verification-notification")

    fun verifyEmail(code: String) = ApiRequest.empty(Method.POST, "api/auth/email/verify-code", mapOf("code" to code))

    /// Phone OTP — texted through the server's SMS seam; verifying is
    /// what unlocks SMS notifications (we never text unproven numbers).
    fun sendPhoneCode() = ApiRequest.empty(Method.POST, "api/auth/phone/verification-code")

    fun verifyPhone(code: String) = ApiRequest.empty(Method.POST, "api/auth/phone/verify", mapOf("code" to code))
}
