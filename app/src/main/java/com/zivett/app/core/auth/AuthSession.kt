package com.zivett.app.core.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zivett.app.core.models.User
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.push.PushRegistration
import com.zivett.app.core.storage.TokenStore

/// Whether anyone is signed in. `Unknown` lasts only while the launch
/// restore is in flight, so the root screen can show a splash instead of
/// flashing the login screen at a returning user.
sealed interface AuthState {
    object Unknown : AuthState
    object SignedOut : AuthState
    data class SignedIn(val account: User) : AuthState

    val user: User? get() = (this as? SignedIn)?.account
}

/// The app's single source of truth for "who is signed in". Owns the
/// token (via `TokenStore`) and the `User`, and is the only thing that
/// writes either. Screens observe `state`; feature code calls the verbs.
/// All state writes happen on the main thread (Compose snapshot state).
class AuthSession(
    private val client: ApiClient,
    private val tokenStore: TokenStore,
    private val deviceName: String,
) {
    var state: AuthState by mutableStateOf(AuthState.Unknown)
        private set

    val user: User? get() = state.user
    val isSignedIn: Boolean get() = user != null

    /// On launch: if a token is stored, confirm it still works by loading
    /// the user. A dead token (401) is discarded; a network failure keeps
    /// the token but lands on signed-out so the user can retry — we never
    /// throw away a good token because the Wi-Fi was flaky.
    suspend fun restore() {
        if (tokenStore.read() == null) {
            state = AuthState.SignedOut
            return
        }

        try {
            state = AuthState.SignedIn(client.send(AuthEndpoints.currentUser()))
        } catch (_: ApiError.Unauthenticated) {
            signOutLocally()
        } catch (_: Throwable) {
            state = AuthState.SignedOut
        }
    }

    suspend fun login(email: String, password: String) {
        val credentials = LoginCredentials(email.trim(), password, deviceName)
        establish(client.send(AuthEndpoints.login(credentials)))
    }

    suspend fun register(form: RegistrationForm) {
        establish(client.send(AuthEndpoints.register(form.copy(deviceName = deviceName))))
    }

    /// Accept a team invitation: mints the member account (pre-verified)
    /// and signs this device straight in, like register.
    suspend fun acceptInvitation(token: String, form: InvitationForm, photo: ByteArray?) {
        establish(client.send(AuthEndpoints.acceptInvitation(token, form.copy(deviceName = deviceName), photo)))
    }

    /// Revokes the token server-side (best effort — a dead connection
    /// must not trap someone in a signed-in state) and always clears it
    /// locally. The push token goes first: its DELETE needs the auth
    /// token that logout is about to revoke.
    suspend fun logout() {
        PushRegistration.release()
        runCatching { client.send(AuthEndpoints.logout()) }
        signOutLocally()
    }

    /// Delete the account. The server revokes every token as part of the
    /// scrub, so on success there's nothing left to log out of — we just
    /// drop local state. Errors propagate so the screen can show the
    /// wrong-password field error or the 409 reason.
    suspend fun deleteAccount(password: String) {
        PushRegistration.release()
        client.send(AuthEndpoints.deleteAccount(password))
        signOutLocally()
    }

    /// Re-fetch the user — after verification, a profile edit, or when a
    /// screen needs fresh approval state.
    suspend fun refreshUser() {
        state = AuthState.SignedIn(client.send(AuthEndpoints.currentUser()))
    }

    /// Drops the token and user without touching the server. Called by
    /// the API client on any 401 so one revoked token can't leave a
    /// screen half-authenticated.
    fun signOutLocally() {
        runCatching { tokenStore.clear() }
        state = AuthState.SignedOut
    }

    private fun establish(payload: AuthPayload) {
        tokenStore.write(payload.token)
        state = AuthState.SignedIn(payload.user)
    }
}
