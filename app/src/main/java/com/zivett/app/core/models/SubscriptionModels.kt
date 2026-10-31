@file:UseSerializers(LaravelInstantSerializer::class)

package com.zivett.app.core.models

import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.LaravelInstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

/// `GET /api/{company|business}/subscription` — the org's plan, the
/// shelf of plans for its audience, and what Google Play sells of it.
/// The app reads only what the plan screen needs; the web's charge and
/// change history stay on the web. Port of iOS `SubscriptionModels.swift`
/// with the `google_play` block in place of `app_store`.
@Serializable
data class SubscriptionResponse(
    val plans: List<Plan>,
    val subscription: Subscription,
    val googlePlay: GooglePlay? = null,
) {
    @Serializable
    data class Plan(
        val id: Int,
        val key: String,
        val name: String,
        val priceCents: Int = 0,
        val yearlyPriceCents: Int = 0,
        val blurb: String? = null,
        val sortOrder: Int = 0,
        val features: List<String> = emptyList(),
        /// The monthly payout account fee. Absent on older servers.
        val connectFeeCents: Int = 0,
    ) {
        /// Company and Business Basic: nothing to buy, so it's chosen
        /// through our own API, never Google Play.
        val isFree: Boolean get() = priceCents == 0 && yearlyPriceCents == 0
    }

    @Serializable
    data class Subscription(
        val planId: Int,
        val planKey: String,
        /// `monthly` · `yearly`
        val interval: String? = null,
        val changedAt: Instant? = null,
        val canManage: Boolean = false,
        val termStartedAt: Instant? = null,
        val termEndsAt: Instant? = null,
        /// `stripe` (billed by ZiVETT) · `app_store` · `google_play`.
        val provider: String? = null,
        /// A PAID contract ZiVETT bills is running — locks the shelf.
        val webTermPaid: Boolean? = null,
        val store: Store? = null,
    ) {
        @Serializable
        data class Store(
            val provider: String? = null,
            val productId: String,
            /// `active` · `billing_retry`
            val status: String,
            val autoRenew: Boolean = true,
            val expiresAt: Instant? = null,
            val commitmentEndsAt: Instant? = null,
            val environment: String? = null,
        )
    }

    @Serializable
    data class GooglePlay(
        val products: List<Product> = emptyList(),
        val packageName: String? = null,
        /// The org's purchase token (Play Billing's obfuscatedAccountId) —
        /// null for members, who can't buy.
        val accountId: String? = null,
    ) {
        @Serializable data class Product(val productId: String, val planId: Int, val planKey: String)
    }
}

object SubscriptionEndpoints {
    /// `area` is `company` or `business` — the business controller
    /// extends the company one, so the two shelves share one contract.
    fun show(area: String) = ApiRequest.get<SubscriptionResponse>("api/$area/subscription")

    /// Hands Play Billing's purchase token to the server, which asks
    /// Google what it stands for and mirrors the plan.
    fun googlePlayPurchase(area: String, productId: String, purchaseToken: String) =
        ApiRequest.post<SubscriptionResponse, GooglePlayBody>("api/$area/subscription/google-play", GooglePlayBody(productId, purchaseToken))

    /// Free plans only (the Basic tiers) — anything priced is sold through
    /// Google Play in the app.
    fun chooseFree(area: String, planId: Int) =
        ApiRequest.post<SubscriptionResponse, ChooseBody>("api/$area/subscription", ChooseBody(planId, "yearly"))

    @Serializable data class GooglePlayBody(val productId: String, val purchaseToken: String)
    @Serializable data class ChooseBody(val planId: Int, val interval: String)
}
