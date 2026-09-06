package com.zivett.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zivett.app.app.AppEnvironment
import com.zivett.app.app.AppEvents
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.RootScreen
import com.zivett.app.core.PendingReferral
import com.zivett.app.core.payments.StripeBridge
import com.zivett.app.core.push.PendingPushOpen
import com.zivett.app.core.push.PushRegistration
import com.zivett.app.core.push.PushRouting
import com.zivett.app.design.ZivettTheme
import com.zivett.app.core.payments.StripeHost

class MainActivity : ComponentActivity() {
    private var environment by mutableStateOf<AppEnvironment?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        environment = AppEnvironment.live(this)
        handleIntent(intent)

        setContent {
            val current = environment ?: return@setContent
            ZivettTheme {
                CompositionLocalProvider(LocalAppEnvironment provides current) {
                    StripeHost(this)
                    RootScreen()
                }
            }
            // The debug backend switcher: switching signs out (a token from
            // one backend is meaningless on the other) and rebuilds the
            // app's object graph.
            val generation = AppEvents.backendChanged
            LaunchedEffect(generation) {
                if (generation > 0) {
                    current.session.signOutLocally()
                    val rebuilt = AppEnvironment.live(this@MainActivity)
                    environment = rebuilt
                    PushRegistration.client = rebuilt.client
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /// Referral links (/r/{code} → hold the code so signup can attribute
    /// it), team invitations (/invitations/{token}), and push taps (the
    /// job reference the notification carried).
    private fun handleIntent(intent: Intent?) {
        intent ?: return

        intent.getStringExtra(PushRouting.EXTRA_ROUTE_ID)?.let { ref ->
            PendingPushOpen.store(ref)
            intent.removeExtra(PushRouting.EXTRA_ROUTE_ID)
        }

        val uri: Uri = intent.data ?: return
        intent.data = null

        PendingReferral.code(uri)?.let { code ->
            PendingReferral.code = code
            AppEvents.referralOpened = true
            return
        }

        val parts = uri.pathSegments.filter { it.isNotEmpty() }
        if (parts.size >= 2 && parts[parts.size - 2] == "invitations") {
            AppEvents.invitationToken = parts.last()
        }
    }

    @Deprecated("Stripe's 3DS result rides the legacy activity-result path")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (StripeBridge.onActivityResult(requestCode, data)) return
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }
}
