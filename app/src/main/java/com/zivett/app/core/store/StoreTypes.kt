package com.zivett.app.core.store

import kotlinx.coroutines.flow.SharedFlow

/// SDK-free store types — everything outside `StoreBridge.kt` speaks
/// these, so the plan screen is unit-testable with a fake `StoreFront`.

/// How the buyer pays. Each is a Play base plan of the plan's one
/// subscription product; the server maps `monthly` → our monthly plan,
/// `yearly` → yearly. Unlike iOS there's no 12-month commitment on
/// Play (installment plans aren't offered in Canada): monthly is plain
/// month-to-month.
enum class StoreBillingPlan(val basePlanId: String) { YEARLY("yearly"), MONTHLY("monthly") }

/// One way to pay for a product, priced by Google Play in the buyer's
/// currency — display strings come from Play, never us.
data class StorePricingTerm(val plan: StoreBillingPlan, val displayPrice: String)

/// A Play product as the plan screen needs it.
data class StoreProduct(
    val id: String,
    val displayName: String,
    val terms: List<StorePricingTerm>,
    /// What yearly billing works out to per month ("$48.33").
    val yearlyPerMonthDisplayPrice: String? = null,
    /// Yearly vs 12 monthly payments, as a whole percent; null without a
    /// monthly base plan.
    val yearlySavingsPercent: Int? = null,
) {
    fun term(plan: StoreBillingPlan): StorePricingTerm? = terms.firstOrNull { it.plan == plan }
}

/// A subscription this device's Google account holds.
data class OwnedPurchase(
    val productId: String,
    val purchaseToken: String,
    val acknowledged: Boolean,
    /// The org token it was bought under (Play's obfuscatedAccountId).
    val accountId: String? = null,
)

/// Switching an existing Play subscription to another product or base
/// plan. `immediate`: an upgrade, charged now with the unused time
/// credited; otherwise the change waits for the renewal date (the web's
/// rule: upgrades land now, downgrades and interval changes at renewal).
data class StoreReplacement(val purchaseToken: String, val immediate: Boolean)

sealed interface StorePurchaseOutcome {
    /// Paid; post the token to the server.
    data class Purchased(val purchase: OwnedPurchase) : StorePurchaseOutcome
    /// A pending payment method — the purchase completes later and
    /// `StoreHost` delivers it.
    data object Pending : StorePurchaseOutcome
    data object Cancelled : StorePurchaseOutcome
}

class StoreError(val kind: Int) : Exception() {
    override val message: String get() = when (kind) {
        UNAVAILABLE -> "Google Play isn't available right now, so plans can't be bought. Try again in a moment."
        ALREADY_OWNED -> "This Google account already has this plan. Tap Restore purchases to link it."
        else -> "Google Play couldn't complete that purchase. If you were charged, tap Restore purchases."
    }

    companion object {
        const val UNAVAILABLE = 1
        const val ALREADY_OWNED = 2
        const val FAILED = 3
    }
}

/// What the plan screen needs from Google Play.
interface StoreFront {
    val updates: SharedFlow<OwnedPurchase>
    suspend fun products(ids: List<String>): List<StoreProduct>
    /// `accountId` is the org's purchase token from the server — Google
    /// stamps it on the purchase so notifications find the org.
    suspend fun purchase(productId: String, plan: StoreBillingPlan, accountId: String, replacing: StoreReplacement? = null): StorePurchaseOutcome
    /// Every subscription this Google account currently holds.
    suspend fun owned(): List<OwnedPurchase>
    /// Tell Google we delivered it (the server does too; whichever lands
    /// first wins — an unacknowledged purchase is refunded after 3 days).
    suspend fun acknowledge(purchase: OwnedPurchase)
}
