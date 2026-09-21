package com.zivett.app.core.payments

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.BillingContext
import com.zivett.app.core.models.CustomerEndpoints
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/// The booker's card on a PAY surface (Pay & close, Pay invoice, the
/// home pay hero): what will be charged, and the way to change it.
///
/// Before this, the only place the app could take a card was quote
/// acceptance, and the pay screens offered "Add a payment card" only when
/// NO card was on file. So when a saved card declined — "Retry or use a
/// different card" — there was nowhere to do it: the booker was stuck
/// with a dead card until the 48-hour auto-close failed on it too.
///
/// Showing the card is a plain read (`GET /api/billing/card`). Saving
/// goes `POST /api/billing/setup-intent` (minted per attempt, at the tap)
/// → PaymentSheet in setup mode → `POST /api/billing/card`, which also re-pins the new
/// card on the booker's live holds, so the very next attempt uses it.
class PaymentCardModel(private val client: ApiClient, private val context: Context? = null) {
    var billing by mutableStateOf<Loadable<BillingContext>>(Loadable.Loading)
    var changing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    /// A stored card, or the simulated driver, means a charge can go
    /// straight through.
    val canCharge: Boolean get() = billing.value?.canChargeWithoutCardForm ?: false
    val publishableKey: String? get() = billing.value?.publishableKey

    /// The ONE pay gate all three surfaces share: only a context that
    /// actually LOADED and says "Stripe, no card" blocks the Pay button.
    /// While it loads, or when the fetch failed, paying stays possible —
    /// the server is the real gate, and a failed fetch must not strand a
    /// good card. (The invoice screen and home hero used to require
    /// `canCharge`, so one dropped request disabled Pay with no way back.)
    val blocksPay: Boolean get() = billing.value?.let { !it.canChargeWithoutCardForm } ?: false

    /// A READ — never mints a SetupIntent (that's `changeCard`'s job).
    suspend fun load() { billing = billing.reloaded { client.billingCard() } }

    /// From the failed state: back to the spinner, then try again.
    suspend fun retry() { billing = Loadable.Loading; load() }

    /// Collect a card and make it the booker's payment method. Works with
    /// or without one already on file. True when a card was saved (false =
    /// they backed out of the sheet, or it failed — see `error`).
    suspend fun changeCard(): Boolean {
        if (changing) return false
        changing = true; error = null
        try {
            // The one moment a SetupIntent is minted: the card form is
            // about to open. Each secret is single-use, so every attempt
            // gets its own.
            val fresh = client.send(CustomerEndpoints.cardSetupIntent())
            val secret = fresh.clientSecret
            val key = fresh.publishableKey
            val androidContext = context
            if (secret == null || key == null || androidContext == null) {
                error = "Card setup isn't available right now — try again in a moment."
                return false
            }
            StripeBridge.configure(androidContext, key)
            val setupIntentId = StripeBridge.collectCard(secret) ?: return false
            client.send(CustomerEndpoints.saveCard(setupIntentId))
            load()
            return true
        } catch (apiError: ApiError) {
            error = apiError.userMessage
            load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.userMessage
        } finally {
            changing = false
        }
        return false
    }
}

/// The payment context for DISPLAY — driver, publishable key, saved card
/// — without touching Stripe. A server from before `GET /api/billing/card`
/// answers 404/405; only then fall back to the setup-intent POST that
/// used to double as the read.
suspend fun ApiClient.billingCard(): BillingContext = try {
    send(CustomerEndpoints.billingCard())
} catch (error: ApiError) {
    if (error is ApiError.NotFound || (error is ApiError.Server && error.status == 405)) send(CustomerEndpoints.cardSetupIntent()) else throw error
}

/// The card row every pay surface embeds. `declined` is the bank's
/// message from a charge that just failed — it leads the card with the
/// reason and promotes the change-card action from a quiet link to the
/// main button.
@Composable
fun PaymentCardSection(model: PaymentCardModel, declined: String? = null, modifier: Modifier = Modifier, onSaved: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    ZCard(modifier = modifier) {
        when (val billing = model.billing) {
            Loadable.Loading -> ZSpinner()
            is Loadable.Failed -> Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                ZBodyStrong("We couldn't load your card")
                ZCaption("${billing.message} You can still pay — we'll charge the card on file.")
                ZButton("Retry", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { scope.launch { model.retry() } }
            }
            is Loadable.Loaded -> {
                val context = billing.loaded
                if (context.driver != "stripe") {
                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CreditCard, contentDescription = null, tint = colors.ink)
                        ZBody("Test payments (simulated)")
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                        declined?.let { ZBanner(it, tone = ZTone.DANGER) }

                        val card = context.savedCard
                        val expired = card?.isExpired() == true
                        SavedCardLine(card)
                        if (expired) ZCaption("This card has expired — use a different card before you pay.", color = colors.danger)

                        model.error?.let { ZCaption(it, color = colors.danger) }

                        ZButton(
                            if (model.changing) "Opening…" else if (card == null) "Add a payment card" else "Use a different card",
                            style = if (declined != null || card == null || expired) ZButtonStyle.OUTLINE else ZButtonStyle.GHOST,
                            compact = true,
                            enabled = !model.changing,
                            fullWidth = false,
                        ) { scope.launch { if (model.changeCard()) onSaved() } }

                        if (context.publishableKey?.startsWith("pk_test_") == true) {
                            ZCaption("Test mode — use card 4242 4242 4242 4242, any future expiry and CVC.", tone = ZTextTone.FAINT)
                        }
                    }
                }
            }
        }
    }
}

/// The card on file wherever it renders: brand •••• last4, "exp MM/YY"
/// when the server sent one, and a danger EXPIRED badge once it lapses.
@Composable
fun SavedCardLine(card: BillingContext.SavedCard?, modifier: Modifier = Modifier) {
    val colors = ZTheme.colors
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CreditCard, contentDescription = null, tint = colors.ink)
        if (card == null) {
            ZBodyStrong("No card on file")
        } else {
            val expired = card.isExpired()
            ZBodyStrong(card.label)
            if (expired) ZBadge("Expired", ZTone.DANGER)
            else card.expiryLabel?.let { ZCaption(it) }
        }
    }
}
