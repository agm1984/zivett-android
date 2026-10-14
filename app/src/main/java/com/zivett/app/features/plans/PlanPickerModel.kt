package com.zivett.app.features.plans

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.SubscriptionEndpoints
import com.zivett.app.core.models.SubscriptionResponse
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.core.store.OwnedPurchase
import com.zivett.app.core.store.StoreBillingPlan
import com.zivett.app.core.store.StoreFront
import com.zivett.app.core.store.StoreProduct
import com.zivett.app.core.store.StorePurchaseOutcome
import com.zivett.app.core.store.StoreReplacement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/// The plan screen's logic: the server's subscription payload plus Google
/// Play's products, and the ways to commit — buy (or switch) through
/// Google Play, pick a free plan through our API, or restore. Port of iOS
/// `PlanPickerModel.swift`. `area` is the API path segment.
class PlanPickerModel(val area: String, private val client: ApiClient, private val store: StoreFront) {
    var state by mutableStateOf<Loadable<SubscriptionResponse>>(Loadable.Loading)
    var products by mutableStateOf<Map<String, StoreProduct>>(emptyMap())
    /// Google Play didn't answer — paid cards render unavailable.
    var storeUnavailable by mutableStateOf(false)
    /// The card + billing plan being bought, for its spinner.
    var working by mutableStateOf<String?>(null)
    var restoring by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)

    val cards: List<PlanShelf.Card> get() = state.value?.let { PlanShelf.cards(it, products) } ?: emptyList()

    suspend fun load() {
        state = state.reloaded { client.send(SubscriptionEndpoints.show(area)) }
        loadProducts()
    }

    private suspend fun loadProducts() {
        val ids = state.value?.googlePlay?.products.orEmpty().map { it.productId }
        if (ids.isEmpty()) return
        try {
            val fetched = store.products(ids)
            products = fetched.associateBy { it.id }
            storeUnavailable = fetched.isEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            storeUnavailable = true
        }
    }

    /// Buys a paid plan through Google Play — or, when Play already bills
    /// one, switches it — and hands the purchase to the server. True once
    /// the server has it. The hand-off runs NonCancellable: Google has the
    /// money by then, and leaving the screen mustn't drop it.
    suspend fun buy(card: PlanShelf.Card, billing: StoreBillingPlan): Boolean {
        val productId = card.productId ?: return false
        val response = state.value ?: return false
        val accountId = response.googlePlay?.accountId ?: run {
            toast = "Only organization admins can change the plan."
            return false
        }
        working = "${card.plan.id}-${billing.basePlanId}"
        try {
            var immediate = true
            val replacing = if (PlanShelf.mode(response) == PlanShelf.Mode.STORE_BILLED) {
                val current = currentPurchase(response) ?: run {
                    toast = "Your plan was bought with a different Google account. Sign in to Google Play with that account to change it."
                    return false
                }
                immediate = PlanShelf.isImmediateChange(response, card.plan)
                StoreReplacement(current.purchaseToken, immediate)
            } else null

            return when (val outcome = store.purchase(productId, billing, accountId, replacing)) {
                is StorePurchaseOutcome.Purchased -> withContext(NonCancellable) { handOff(outcome.purchase, card, immediate) }
                StorePurchaseOutcome.Pending -> {
                    notice = "Your payment is pending. Your plan switches on as soon as Google Play confirms it."
                    false
                }
                StorePurchaseOutcome.Cancelled -> false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast = e.userMessage
            return false
        } finally {
            working = null
        }
    }

    private suspend fun handOff(purchase: OwnedPurchase, card: PlanShelf.Card, immediate: Boolean): Boolean = try {
        state = Loadable.Loaded(client.send(SubscriptionEndpoints.googlePlayPurchase(area, purchase.productId, purchase.purchaseToken)))
        runCatching { store.acknowledge(purchase) }
        notice = if (immediate) "You're on ${card.plan.name}." else "You'll move to ${card.plan.name} when your current period ends."
        true
    } catch (e: ApiError) {
        // Google has the money; our mirror failed. Restore re-sends it,
        // and Google's notification reaches the server anyway.
        toast = "${e.userMessage} Your purchase is safe — tap Restore purchases to finish."
        false
    }

    /// The Play purchase behind the org's current store-billed plan, if
    /// this device's Google account holds it.
    private suspend fun currentPurchase(response: SubscriptionResponse): OwnedPurchase? {
        val ours = response.googlePlay?.products.orEmpty().map { it.productId }.toSet()
        val owned = store.owned().filter { it.productId in ours }
        val accountId = response.googlePlay?.accountId
        return owned.firstOrNull { it.productId == response.subscription.store?.productId && (it.accountId == null || it.accountId == accountId) }
            ?: owned.firstOrNull { it.accountId == accountId }
    }

    /// Free plans (Business Basic) never touch Google Play.
    suspend fun chooseFree(card: PlanShelf.Card): Boolean {
        working = "${card.plan.id}-free"
        try {
            state = Loadable.Loaded(client.send(SubscriptionEndpoints.chooseFree(area, card.plan.id)))
            notice = "You're on ${card.plan.name}."
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast = e.userMessage
        } finally {
            working = null
        }
        return false
    }

    /// Re-sends every subscription this Google account holds. The server
    /// ignores products from the other shelf and orders it already has.
    suspend fun restore() {
        restoring = true
        try {
            val owned = store.owned()
            if (owned.isEmpty()) {
                toast = "There are no Google Play subscriptions on this Google account to restore."
                return
            }
            var restored = false
            for (purchase in owned) {
                val response = try {
                    client.send(SubscriptionEndpoints.googlePlayPurchase(area, purchase.productId, purchase.purchaseToken))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                } ?: continue
                state = Loadable.Loaded(response)
                runCatching { store.acknowledge(purchase) }
                restored = true
            }
            // A toast, not the top-of-screen notice: Restore sits at the
            // bottom, where the notice is scrolled out of view.
            if (restored) toast = "Purchases restored."
            else toast = "We couldn't restore a plan for this business from this Google account."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast = e.userMessage
        } finally {
            restoring = false
        }
    }
}
