package com.zivett.app.core.payments

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.stripe.android.PaymentConfiguration
import com.stripe.android.core.exception.LocalStripeException
import com.stripe.android.core.exception.StripeException
import com.stripe.android.model.ConfirmPaymentIntentParams
import com.stripe.android.payments.paymentlauncher.PaymentLauncher
import com.stripe.android.payments.paymentlauncher.PaymentResult
import com.stripe.android.payments.paymentlauncher.rememberPaymentLauncher
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.rememberPaymentSheet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/// The ONE file that imports the Stripe SDK — the app-side mirror of the
/// web's `lib/stripe.js` and the server's `StripeGateway` seam. Keyed at
/// runtime from the billing payload's publishable key, never build-time.
///
/// PaymentSheet and PaymentLauncher both need an activity-result
/// registration made during composition, so `StripeHost()` is mounted
/// once at the root of MainActivity and the models call the suspend
/// functions here. (The bank challenge used to ride
/// `Stripe.handleNextActionForPayment` + MainActivity's deprecated
/// `onActivityResult`; the launcher owns its own result route, and the
/// SDK supplies its own return URL — the app declares none.)
object StripeBridge {
    /// Compose state: the launcher is keyed by it, so `StripeHost`
    /// (re)builds one the moment a billing payload hands us the key.
    internal var publishableKey by mutableStateOf<String?>(null)
        private set
    private var sheet: PaymentSheet? = null
    private val launcher = MutableStateFlow<PaymentLauncher?>(null)
    private var pendingCard: CompletableDeferred<String?>? = null
    private var pendingCardSecret: String? = null
    private var pendingPayment: CompletableDeferred<ChallengeOutcome>? = null

    fun configure(context: Context, key: String) {
        if (publishableKey != key) {
            PaymentConfiguration.init(context.applicationContext, key)
            publishableKey = key
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

    /// Get the bank's confirmation for a pay-time charge (409
    /// `payment_action_required`). The server charges OFF-session, so the
    /// PaymentIntent it hands back is in `requires_payment_method`, not
    /// `requires_action` — a bare next-action call fails on it. Stripe's
    /// documented recovery is to CONFIRM it again on-session with the
    /// same payment method, which is what presents the 3DS challenge.
    /// Without a `paymentMethodId` (an older server) the next-action
    /// route is all we have. The `payment_intent.succeeded` webhook
    /// finishes settlement server-side — callers re-poll after success.
    suspend fun confirmPayment(paymentIntentClientSecret: String, paymentMethodId: String?): ChallengeOutcome {
        // `configure` has only just published the key; the launcher
        // arrives with the next composition.
        val launcher = withTimeoutOrNull(3_000) { launcher.filterNotNull().first() }
            ?: return ChallengeOutcome.Failed("Bank confirmation isn't available right now — try again in a moment.")
        pendingPayment?.cancel()
        val deferred = CompletableDeferred<ChallengeOutcome>()
        pendingPayment = deferred
        if (paymentMethodId != null) {
            launcher.confirm(ConfirmPaymentIntentParams.createWithPaymentMethodId(paymentMethodId, paymentIntentClientSecret))
        } else {
            launcher.handleNextActionForPaymentIntent(paymentIntentClientSecret)
        }
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

    internal fun onPaymentResult(result: PaymentResult) {
        val deferred = pendingPayment ?: return
        pendingPayment = null
        deferred.complete(
            when (result) {
                is PaymentResult.Completed -> ChallengeOutcome.Succeeded
                is PaymentResult.Canceled -> ChallengeOutcome.Canceled
                is PaymentResult.Failed -> outcomeFor(result.throwable)
            },
        )
    }

    /// Only a refusal Stripe actually answered (a 4xx, or the SDK's own
    /// "authentication failed") proves nothing was charged. A dropped
    /// connection or a 5xx while fetching the result proves nothing.
    private fun outcomeFor(error: Throwable): ChallengeOutcome = when {
        error is LocalStripeException -> ChallengeOutcome.Failed(error.displayMessage ?: error.localizedMessage)
        error is StripeException && error.isClientError -> ChallengeOutcome.Failed(error.stripeError?.message ?: error.localizedMessage)
        else -> ChallengeOutcome.Unknown
    }

    internal fun attach(sheet: PaymentSheet) { this.sheet = sheet }

    internal fun detach(sheet: PaymentSheet) {
        if (this.sheet === sheet) this.sheet = null
    }

    internal fun attach(launcher: PaymentLauncher) { this.launcher.value = launcher }

    internal fun detach(launcher: PaymentLauncher) {
        if (this.launcher.value === launcher) this.launcher.value = null
    }
}

/// Mount once at the root of MainActivity's content: registers the
/// PaymentSheet and (once a publishable key is known) PaymentLauncher
/// result routes for the bridge above.
@Composable
fun StripeHost() {
    val sheet = rememberPaymentSheet { result -> StripeBridge.onSheetResult(result) }
    val handle = remember(sheet) { sheet }
    DisposableEffect(handle) {
        StripeBridge.attach(handle)
        onDispose { StripeBridge.detach(handle) }
    }

    val publishableKey = StripeBridge.publishableKey ?: return
    key(publishableKey) {
        val launcher = rememberPaymentLauncher(publishableKey, null) { result -> StripeBridge.onPaymentResult(result) }
        DisposableEffect(launcher) {
            StripeBridge.attach(launcher)
            onDispose { StripeBridge.detach(launcher) }
        }
    }
}
