package com.zivett.app.core.payments

import android.content.Context
import com.zivett.app.core.network.PaymentAction

/// How the bank's confirmation of a pay-time charge ended. Deliberately
/// free of SDK types so the pay models (and their tests) never see Stripe.
///
/// The three non-success cases are NOT the same thing to the booker:
/// backing out and a bank refusal both mean nothing was charged, but a
/// result we couldn't fetch (the network dropped on the way back from the
/// bank's page) means we don't know — and must not say otherwise.
sealed interface ChallengeOutcome {
    data object Succeeded : ChallengeOutcome

    /// The booker backed out of the bank's screen.
    data object Canceled : ChallengeOutcome

    /// The bank (or Stripe) refused — `message` is theirs when they gave one.
    data class Failed(val message: String?) : ChallengeOutcome

    /// The challenge ran but its result never reached us.
    data object Unknown : ChallengeOutcome

    companion object {
        const val CANCELED_COPY = "The bank confirmation wasn't completed — nothing was charged. Try again when you're ready."
        const val FAILED_COPY = "Your bank couldn't confirm this payment. Try again or use a different card."
        const val UNKNOWN_COPY = "We couldn't confirm that payment with your bank. If it went through it will show here shortly — otherwise it's safe to try again."
        const val SETTLING_COPY = "Payment confirmed — it can take a few seconds to settle. Check back in a moment."
        const val UNAVAILABLE_COPY = "Your bank needs an extra confirmation for this payment. Please complete it from zivett.com for now."
    }
}

/// Runs the bank's confirmation for a 409 `payment_action_required`. A
/// seam so the pay models can be driven in unit tests; the app's one
/// implementation goes through `StripeBridge`.
fun interface PaymentChallenger {
    suspend fun confirm(publishableKey: String, action: PaymentAction): ChallengeOutcome
}

class StripeChallenger(private val context: Context) : PaymentChallenger {
    override suspend fun confirm(publishableKey: String, action: PaymentAction): ChallengeOutcome {
        val secret = action.clientSecret ?: return ChallengeOutcome.Failed(ChallengeOutcome.UNAVAILABLE_COPY)
        StripeBridge.configure(context, publishableKey)
        return StripeBridge.confirmPayment(secret, action.paymentMethodId)
    }
}
