package com.zivett.app.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.zivett.app.core.auth.AuthSession
import com.zivett.app.core.models.User
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.HttpApiClient
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.core.push.PushRegistration
import com.zivett.app.core.realtime.RealtimeClient
import com.zivett.app.core.storage.InMemoryTokenStore
import com.zivett.app.core.storage.KeystoreTokenStore
import com.zivett.app.core.storage.TokenStore
import okhttp3.OkHttpClient

/// The composition root: builds the live object graph once and hands it
/// to the composable tree through `LocalAppEnvironment`. Previews and
/// tests build their own with stub clients (`AppEnvironment.preview`).
class AppEnvironment(
    val config: AppConfig,
    val client: ApiClient,
    tokenStore: TokenStore,
    observeLifecycle: Boolean = true,
) {
    val session: AuthSession = AuthSession(client, tokenStore, config.deviceName)

    /// Live updates over Reverb; lazily connects on first subscription
    /// and dies with the environment (a backend switch rebuilds it).
    val realtime: RealtimeClient = RealtimeClient(client, config.apiBaseUrl, observeLifecycle = observeLifecycle)

    companion object {
        fun live(context: Context): AppEnvironment {
            val config = AppConfig.current(context)
            val tokenStore = KeystoreTokenStore(context)
            val client = HttpApiClient(config.apiBaseUrl, tokenStore, HttpApiClient.defaultHttp())
            val environment = AppEnvironment(config, client, tokenStore)

            // One revoked token signs the whole app out — no screen has to
            // special-case 401.
            client.onUnauthenticated = { environment.session.signOutLocally() }
            PushRegistration.client = client

            return environment
        }

        /// Offline environment for previews: canned responses, no Keystore.
        fun preview(user: User? = null, responses: Map<String, Any> = emptyMap()): AppEnvironment {
            val config = AppConfig("https://preview.invalid", "Preview phone")
            val client = PreviewApiClient(responses)
            val environment = AppEnvironment(config, client, InMemoryTokenStore(if (user == null) null else "preview-token"), observeLifecycle = false)
            if (user != null) client.responses["api/user"] = user
            return environment
        }
    }
}

val LocalAppEnvironment = staticCompositionLocalOf<AppEnvironment> { error("No AppEnvironment provided") }

/// App-wide events the screens react to — the Android stand-in for the
/// iOS NotificationCenter names. Each is a piece of snapshot state a
/// screen observes; consuming it resets it.
object AppEvents {
    /// Bumped by the debug backend switcher — the whole object graph is
    /// rebuilt against the new base URL.
    var backendChanged: Int by mutableIntStateOf(0)

    /// Set when a referral link opens the app — the signed-out flow
    /// pushes the signup screen so the invite banner is actually seen
    /// (the web lands /r/{code} straight on /signup).
    var referralOpened: Boolean by mutableStateOf(false)

    /// Set (to the token) when a team-invitation link opens the app; the
    /// signed-out flow pushes the accept screen (web: /invitations/{token}).
    var invitationToken: String? by mutableStateOf(null)

    /// Set when Stripe's hosted Connect onboarding hands the browser back
    /// to the app (`zivett://stripe-return`, or the verified
    /// `https://…/app/stripe-return` page). The company shell lands on
    /// the dashboard, which consumes it and re-checks payout status.
    var stripeReturned: Boolean by mutableStateOf(false)
}

@Suppress("unused")
private val keepOkHttpImport: OkHttpClient? = null
