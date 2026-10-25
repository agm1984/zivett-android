package com.zivett.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
import com.zivett.app.core.push.PendingPushOpen
import com.zivett.app.core.push.PushRegistration
import com.zivett.app.core.push.PushRouting
import com.zivett.app.design.ZivettTheme
import com.zivett.app.core.payments.StripeHost
import com.zivett.app.core.store.StoreHost

class MainActivity : ComponentActivity() {
    private var environment by mutableStateOf<AppEnvironment?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = orientationFor(resources.configuration.smallestScreenWidthDp)
        enableEdgeToEdge()
        environment = AppEnvironment.live(this)
        if (BuildConfig.BACKEND_SWITCHER && intent?.getBooleanExtra("store_screenshots", false) == true) AppEvents.storeScreenshots = true
        handleIntent(intent)

        setContent {
            val current = environment ?: return@setContent
            ZivettTheme {
                CompositionLocalProvider(LocalAppEnvironment provides current) {
                    StripeHost()
                    StoreHost()
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

    // A fold or unfold recreates the activity (it doesn't handle size
    // changes itself), so onCreate re-applies the rule; this covers any
    // configuration change that's delivered here instead.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        requestedOrientation = orientationFor(newConfig.smallestScreenWidthDp)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /// Referral links (/r/{code} → hold the code so signup can attribute
    /// it), team invitations (/invitations/{token}), the return from
    /// Stripe's Connect onboarding, and push taps (the job reference the
    /// notification carried).
    private fun handleIntent(intent: Intent?) {
        intent ?: return

        android.util.Log.d(PushRouting.LOG_TAG, "handleIntent extras=${intent.extras?.keySet()} route_id=${intent.getStringExtra(PushRouting.EXTRA_ROUTE_ID)}")
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

        // zivett://stripe-return (the return page's button) or the App
        // Link https://…/app/stripe-return itself.
        if ((uri.scheme == "zivett" && uri.host == "stripe-return") || parts.takeLast(2) == listOf("app", "stripe-return")) {
            AppEvents.stripeReturned = true
            return
        }

        if (parts.size >= 2 && parts[parts.size - 2] == "invitations") {
            AppEvents.invitationToken = parts.last()
        }
    }
}

/// Phones stay portrait — a book-it, track-it, pay-it app held in one
/// hand. Large screens (600dp+ on the short side: an unfolded foldable, a
/// tablet) follow the device, since the layouts already cap their width.
internal fun orientationFor(smallestWidthDp: Int): Int =
    if (smallestWidthDp >= LARGE_SCREEN_DP) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

internal const val LARGE_SCREEN_DP = 600
