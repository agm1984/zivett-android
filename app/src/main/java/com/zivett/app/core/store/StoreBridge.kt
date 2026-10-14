package com.zivett.app.core.store

import android.app.Activity
import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.zivett.app.app.LocalAppEnvironment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference
import java.text.NumberFormat
import java.util.Currency
import kotlin.math.roundToInt
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/// The ONE file that imports the Play Billing Library — the Android twin
/// of iOS `Core/Store/StoreBridge.swift` and the server's
/// `App\Support\GooglePlay\PlayDeveloperApi` seam (like `StripeBridge` for
/// Stripe). Everything else speaks the SDK-free types in `StoreTypes.kt`,
/// so the plan screen's model is unit-testable with a fake store.
///
/// The server never trusts the app's word: every purchase hands back
/// Play's purchase TOKEN, which the app posts to
/// `POST /api/{company|business}/subscription/google-play`; the server
/// asks Google what it stands for and acknowledges it.
///
/// Products are one subscription per plan, each with base plans `yearly`
/// and (above Basic) `monthly` — set up in Play Console, priced at the
/// web price ÷ 0.85. Display ALWAYS uses Play's formatted prices.
object PlayStore : StoreFront {
    private var client: BillingClient? = null
    private var activity: WeakReference<Activity>? = null
    private val connecting = Mutex()
    private val cache = mutableMapOf<String, ProductDetails>()
    private var inFlight: CompletableDeferred<StorePurchaseOutcome>? = null
    private val _updates = MutableSharedFlow<OwnedPurchase>(extraBufferCapacity = 8)

    /// Purchases that land outside a purchase this app is waiting on: a
    /// pending payment that cleared, a purchase finished after the app
    /// was backgrounded. `StoreHost` posts them to the server.
    override val updates: SharedFlow<OwnedPurchase> = _updates

    fun init(context: Context) {
        if (client != null) return
        client = BillingClient.newBuilder(context.applicationContext)
            .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    override suspend fun products(ids: List<String>): List<StoreProduct> {
        val client = ready()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(ids.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(ProductType.SUBS).build() })
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingResponseCode.OK) throw StoreError(StoreError.UNAVAILABLE)
        val details = result.productDetailsList.orEmpty()
        details.forEach { cache[it.productId] = it }
        return details.map(::summary)
    }

    override suspend fun purchase(productId: String, plan: StoreBillingPlan, accountId: String, replacing: StoreReplacement?): StorePurchaseOutcome {
        val client = ready()
        val activity = activity?.get() ?: throw StoreError(StoreError.UNAVAILABLE)
        val details = cache[productId] ?: products(listOf(productId)).let { cache[productId] } ?: throw StoreError(StoreError.UNAVAILABLE)
        val offer = baseOffer(details, plan) ?: throw StoreError(StoreError.UNAVAILABLE)

        val builder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(offer.offerToken).build()))
            // Google stamps the org on the purchase, so the server can tie
            // a notification to it even if our hand-off never arrives.
            .setObfuscatedAccountId(accountId)
        if (replacing != null) {
            @Suppress("DEPRECATION")
            builder.setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(replacing.purchaseToken)
                    .setSubscriptionReplacementMode(
                        if (replacing.immediate) BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION
                        else BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.DEFERRED,
                    )
                    .build(),
            )
        }

        inFlight?.cancel()
        val deferred = CompletableDeferred<StorePurchaseOutcome>()
        inFlight = deferred
        val launch = client.launchBillingFlow(activity, builder.build())
        if (launch.responseCode != BillingResponseCode.OK) {
            inFlight = null
            throw errorFor(launch)
        }
        return deferred.await()
    }

    override suspend fun owned(): List<OwnedPurchase> {
        val client = ready()
        val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build())
        if (result.billingResult.responseCode != BillingResponseCode.OK) throw errorFor(result.billingResult)
        return result.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.flatMap(::owned)
    }

    override suspend fun acknowledge(purchase: OwnedPurchase) {
        if (purchase.acknowledged) return
        ready().acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
    }

    // Wiring.

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val waiting = inFlight
        inFlight = null
        when (result.responseCode) {
            BillingResponseCode.OK -> {
                val purchase = purchases.orEmpty().firstOrNull()
                val outcome = when {
                    purchase == null -> StorePurchaseOutcome.Cancelled
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED -> StorePurchaseOutcome.Purchased(owned(purchase).first())
                    // Pending payment (cash, a slow bank): it arrives later
                    // through this listener or the next query.
                    else -> StorePurchaseOutcome.Pending
                }
                if (waiting != null) waiting.complete(outcome)
                else purchases.orEmpty().filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.flatMap(::owned).forEach { _updates.tryEmit(it) }
            }
            BillingResponseCode.USER_CANCELED -> waiting?.complete(StorePurchaseOutcome.Cancelled)
            else -> waiting?.completeExceptionally(errorFor(result))
        }
    }

    private fun owned(purchase: Purchase): List<OwnedPurchase> =
        purchase.products.map { OwnedPurchase(it, purchase.purchaseToken, purchase.isAcknowledged, purchase.accountIdentifiers?.obfuscatedAccountId) }

    private fun errorFor(result: BillingResult): StoreError = StoreError(
        when (result.responseCode) {
            BillingResponseCode.ITEM_ALREADY_OWNED -> StoreError.ALREADY_OWNED
            BillingResponseCode.BILLING_UNAVAILABLE, BillingResponseCode.SERVICE_UNAVAILABLE, BillingResponseCode.SERVICE_DISCONNECTED,
            BillingResponseCode.FEATURE_NOT_SUPPORTED, BillingResponseCode.ITEM_UNAVAILABLE -> StoreError.UNAVAILABLE
            else -> StoreError.FAILED
        },
    )

    /// Connects on first use (auto-reconnection keeps it alive after).
    private suspend fun ready(): BillingClient {
        val client = client ?: throw StoreError(StoreError.UNAVAILABLE)
        if (client.isReady) return client
        connecting.withLock {
            if (client.isReady) return client
            val result = suspendCancellableCoroutine { continuation ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) { if (continuation.isActive) continuation.resume(result) }
                    override fun onBillingServiceDisconnected() {}
                })
            }
            if (result.responseCode != BillingResponseCode.OK) throw errorFor(result)
        }
        return client
    }

    /// The base plan's own offer (no promo `offerId`) for a billing plan.
    private fun baseOffer(details: ProductDetails, plan: StoreBillingPlan): ProductDetails.SubscriptionOfferDetails? =
        details.subscriptionOfferDetails.orEmpty().firstOrNull { it.basePlanId == plan.basePlanId && it.offerId == null }

    private fun summary(details: ProductDetails): StoreProduct {
        // The recurring phase is the last one (a free trial or intro
        // price would come first).
        fun phase(plan: StoreBillingPlan) = baseOffer(details, plan)?.pricingPhases?.pricingPhaseList?.lastOrNull()
        val yearly = phase(StoreBillingPlan.YEARLY)
        val monthly = phase(StoreBillingPlan.MONTHLY)
        val terms = listOfNotNull(
            yearly?.let { StorePricingTerm(StoreBillingPlan.YEARLY, it.formattedPrice) },
            monthly?.let { StorePricingTerm(StoreBillingPlan.MONTHLY, it.formattedPrice) },
        )
        // Derived from Play's own prices — never our web figures, since
        // Play prices each country itself.
        val perMonth = yearly?.let { money(it.priceAmountMicros / 12, it.priceCurrencyCode) }
        val savings = if (yearly != null && monthly != null && monthly.priceAmountMicros > 0) {
            ((1 - yearly.priceAmountMicros.toDouble() / (monthly.priceAmountMicros * 12)) * 100).roundToInt().takeIf { it > 0 }
        } else null
        return StoreProduct(details.productId, details.name, terms, perMonth, savings)
    }

    private fun money(micros: Long, currency: String): String =
        NumberFormat.getCurrencyInstance().apply { this.currency = Currency.getInstance(currency) }.format(micros / 1_000_000.0)

    internal fun attach(activity: Activity) { this.activity = WeakReference(activity) }
    internal fun detach(activity: Activity) { if (this.activity?.get() === activity) this.activity = null }
}

/// Mounted once at the root of MainActivity (beside `StripeHost`): gives
/// the bridge the activity `launchBillingFlow` needs, and posts purchases
/// that never reached the plan screen — pending payments that cleared,
/// purchases interrupted before the hand-off — on every return to the
/// foreground and as they arrive. The server is idempotent per order, and
/// Google's real-time notifications reach it anyway; this just makes the
/// plan show up without waiting.
@Composable
fun StoreHost() {
    val activity = LocalActivity.current ?: return
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    DisposableEffect(activity) {
        PlayStore.init(activity)
        PlayStore.attach(activity)
        onDispose { PlayStore.detach(activity) }
    }
    LifecycleResumeEffect(environment) {
        scope.launch { StoreDelivery.deliverUnfinished(PlayStore, environment.client, environment.session.user) }
        onPauseOrDispose { }
    }
    LaunchedEffect(environment) {
        PlayStore.updates.collect { StoreDelivery.deliver(PlayStore, environment.client, environment.session.user, it) }
    }
}
