package com.zivett.app.features.plans

import com.zivett.app.core.models.SubscriptionResponse
import com.zivett.app.core.store.StoreBillingPlan
import com.zivett.app.core.store.StoreProduct
import java.time.Instant

/// What the plan screen shows, decided from the server's subscription
/// payload plus Google Play's products. Pure so the rules — above all
/// "never offer a second biller for a running contract" — are unit-tested.
/// Port of iOS `PlanShelf.swift`; the differences are Play's: monthly is
/// cancel-anytime (no commitment), and plan changes happen in the app
/// (Play's subscription center can't switch products), not a store sheet.
object PlanShelf {
    const val GOOGLE_PLAY = "google_play"
    const val APP_STORE = "app_store"

    enum class Mode {
        /// Google Play bills the plan: show it, Manage in Google Play, and
        /// the shelf with "Switch" in place of "Buy".
        STORE_BILLED,
        /// Billed somewhere this app can't touch — a PAID web term, or a
        /// plan bought in the iPhone app. Read-only, no purchase buttons
        /// (a second biller for one org = a double charge) and no mention
        /// of where it was bought.
        BILLED_ELSEWHERE,
        /// Nothing paid is running: the shelf is open.
        SHELF,
    }

    data class Option(val billing: StoreBillingPlan, val price: String, val detail: String, val isCurrent: Boolean = false)

    data class Card(
        val plan: SubscriptionResponse.Plan,
        /// null for free plans (chosen through our API).
        val productId: String?,
        val options: List<Option>,
        val isCurrent: Boolean,
        /// false: a paid plan Google Play didn't return (shown, not buyable).
        val available: Boolean,
    )

    fun mode(response: SubscriptionResponse, now: Instant = Instant.now()): Mode {
        val subscription = response.subscription
        return when (subscription.provider) {
            GOOGLE_PLAY -> Mode.STORE_BILLED
            APP_STORE -> Mode.BILLED_ELSEWHERE
            else -> {
                // The server knows whether the term was actually paid for —
                // every org has a term on paper.
                subscription.webTermPaid?.let { return if (it) Mode.BILLED_ELSEWHERE else Mode.SHELF }
                val current = currentPlan(response)
                val ends = subscription.termEndsAt
                if (current != null && !current.isFree && hasChosen(response) && ends != null && ends.isAfter(now)) Mode.BILLED_ELSEWHERE else Mode.SHELF
            }
        }
    }

    fun currentPlan(response: SubscriptionResponse): SubscriptionResponse.Plan? =
        response.plans.firstOrNull { it.id == response.subscription.planId }

    /// A plan counts as theirs only once they've chosen it — an org that
    /// never picked sits on Basic by default.
    fun hasChosen(response: SubscriptionResponse): Boolean =
        response.subscription.changedAt != null || response.subscription.provider == GOOGLE_PLAY || response.subscription.provider == APP_STORE

    fun cards(response: SubscriptionResponse, products: Map<String, StoreProduct>): List<Card> {
        val productIds = response.googlePlay?.products.orEmpty().associate { it.planId to it.productId }
        val chosen = hasChosen(response)
        val playBilled = response.subscription.provider == GOOGLE_PLAY
        val currentBilling = if (response.subscription.interval == "monthly") StoreBillingPlan.MONTHLY else StoreBillingPlan.YEARLY

        return response.plans.sortedBy { it.sortOrder }.mapNotNull { plan ->
            val isCurrent = chosen && plan.id == response.subscription.planId
            if (plan.isFree) return@mapNotNull Card(plan, null, emptyList(), isCurrent, true)
            // A priced plan the server doesn't sell through Play isn't on
            // the app's shelf at all.
            val productId = productIds[plan.id] ?: return@mapNotNull null
            val product = products[productId] ?: return@mapNotNull Card(plan, productId, emptyList(), isCurrent, false)
            val options = options(product).map { it.copy(isCurrent = playBilled && isCurrent && it.billing == currentBilling) }
            Card(plan, productId, options, isCurrent, true)
        }
    }

    /// Yearly FIRST — the price sales calls anchor on, with what it works
    /// out to per month and the saving — then monthly.
    fun options(product: StoreProduct): List<Option> = StoreBillingPlan.entries.mapNotNull { billing ->
        val term = product.term(billing) ?: return@mapNotNull null
        when (billing) {
            StoreBillingPlan.YEARLY -> Option(billing, "${term.displayPrice}/year", yearlyDetail(product))
            StoreBillingPlan.MONTHLY -> Option(billing, "${term.displayPrice}/month", "Billed monthly · cancel anytime")
        }
    }

    /// "Billed once a year · works out to $48.33/month · save 17%".
    fun yearlyDetail(product: StoreProduct): String {
        val parts = mutableListOf("Billed once a year")
        if (product.term(StoreBillingPlan.MONTHLY) != null) product.yearlyPerMonthDisplayPrice?.let { parts += "works out to $it/month" }
        product.yearlySavingsPercent?.let { parts += "save $it%" }
        return parts.joinToString(" · ")
    }

    /// Switching a Play-billed plan: a higher tier lands NOW (the unused
    /// time is credited); a lower tier or another billing period waits for
    /// renewal — the web's upgrade/downgrade rule.
    fun isImmediateChange(response: SubscriptionResponse, target: SubscriptionResponse.Plan): Boolean {
        val current = currentPlan(response) ?: return true
        return target.sortOrder > current.sortOrder
    }

    /// Google Play's own subscription page for this app (cancel, update
    /// the payment method), opened on the plan's product when known.
    fun manageUrl(packageName: String, productId: String?): String =
        "https://play.google.com/store/account/subscriptions?package=$packageName" + (productId?.let { "&sku=$it" } ?: "")
}
