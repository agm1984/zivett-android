package com.zivett.app.core.payments

import androidx.activity.ComponentActivity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.stripe.android.ApiResultCallback
import com.stripe.android.PaymentConfiguration
import com.stripe.android.PaymentIntentResult
import com.stripe.android.Stripe
import com.stripe.android.model.StripeIntent
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.rememberPaymentSheet
import kotlinx.coroutines.CompletableDeferred

/// The ONE file that imports the Stripe SDK — the app-side mirror of the
/// web's `lib/stripe.js` and the server's `StripeGateway` seam. Keyed at
/// runtime from the billing payload's publishable key, never build-time.
///
/// PaymentSheet needs an activity-result registration made during
/// composition, so `StripeHost()` is mounted once at the root of
/// MainActivity and the models call the suspend functions here.
object StripeBridge {
    /// The 3DS/redirect return URL — the `zivett` scheme in the manifest.
    const val returnUrl = "zivett://stripe-redirect"

    private var publishableKey: String? = null
    private var sheet: PaymentSheet? = null
    private var activity: ComponentActivity? = null
    private var stripe: Stripe? = null
    private var pendingCard: CompletableDeferred<String?>? = null
    private var pendingCardSecret: String? = null
    private var pendingAction: CompletableDeferred<Boolean>? = null

    fun configure(context: Context, key: String) {
        if (publishableKey != key) {
            PaymentConfiguration.init(context.applicationContext, key)
            publishableKey = key
            stripe = Stripe(context.applicationContext, key)
        }
    }

    /// Collect and confirm a card against a SetupIntent with the
    /// prebuilt PaymentSheet (the PaymentElement's sibling). Returns the
    /// confirmed SetupIntent id — what the accept call pins — or null on
    /// cancel.
    suspend fun collectCard(setupIntentClientSecret: String): String? {
        val sheet = sheet ?: return null
        pendingCard?.cancel()
        val deferred = CompletableDeferred<String?>()
        pendingCard = deferred
        pendingCardSecret = setupIntentClientSecret

        val configuration = PaymentSheet.Configuration.Builder("ZiVETT").build()
        sheet.presentWithSetupIntent(setupIntentClientSecret, configuration)
        return deferred.await()
    }

    /// Run the bank's 3DS challenge for a PaymentIntent the server left
    /// in `requires_action` (409 `payment_action_required` +
    /// `client_secret`). The `payment_intent.succeeded` webhook finishes
    /// settlement server-side — callers re-poll after a true return.
    suspend fun handleNextAction(paymentIntentClientSecret: String): Boolean {
        val activity = activity ?: return false
        val stripe = stripe ?: return false
        pendingAction?.cancel()
        val deferred = CompletableDeferred<Boolean>()
        pendingAction = deferred
        stripe.handleNextActionForPayment(activity, paymentIntentClientSecret)
        return deferred.await()
    }

    /// `seti_xxx_secret_yyy` → `seti_xxx` — the id half of a client
    /// secret, same derivation the web relies on.
    fun setupIntentId(fromClientSecret: String): String? {
        val index = fromClientSecret.indexOf("_secret_")
        return if (index < 0) null else fromClientSecret.substring(0, index)
    }

    // Wiring from the host activity / composable.

    internal fun onSheetResult(result: PaymentSheetResult) {
        val deferred = pendingCard ?: return
        pendingCard = null
        when (result) {
            is PaymentSheetResult.Completed -> deferred.complete(pendingCardSecret?.let { setupIntentId(it) })
            is PaymentSheetResult.Canceled -> deferred.complete(null)
            is PaymentSheetResult.Failed -> deferred.completeExceptionally(result.error)
        }
        pendingCardSecret = null
    }

    /// Forward MainActivity's onActivityResult; true when Stripe consumed it.
    fun onActivityResult(requestCode: Int, data: Intent?): Boolean {
        val stripe = stripe ?: return false
        if (!stripe.isPaymentResult(requestCode, data)) return false
        stripe.onPaymentResult(requestCode, data, object : ApiResultCallback<PaymentIntentResult> {
            override fun onSuccess(result: PaymentIntentResult) {
                pendingAction?.complete(result.intent.status == StripeIntent.Status.Succeeded || result.intent.status == StripeIntent.Status.RequiresCapture || result.intent.status == StripeIntent.Status.Processing)
                pendingAction = null
            }

            override fun onError(e: Exception) {
                pendingAction?.complete(false)
                pendingAction = null
            }
        })
        return true
    }

    internal fun attach(activity: ComponentActivity, sheet: PaymentSheet) {
        this.activity = activity
        this.sheet = sheet
    }

    internal fun detach(sheet: PaymentSheet) {
        if (this.sheet === sheet) { this.sheet = null; this.activity = null }
    }
}

/// Mount once at the root of MainActivity's content: registers the
/// PaymentSheet launcher for the bridge above.
@Composable
fun StripeHost(activity: ComponentActivity) {
    val sheet = rememberPaymentSheet { result -> StripeBridge.onSheetResult(result) }
    val handle = remember(sheet) { sheet }
    DisposableEffect(handle) {
        StripeBridge.attach(activity, handle)
        onDispose { StripeBridge.detach(handle) }
    }
}
