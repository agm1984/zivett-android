package com.zivett.app

import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.models.OrganizationType
import com.zivett.app.core.models.SubscriptionResponse
import com.zivett.app.core.models.UserRole
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.core.store.OwnedPurchase
import com.zivett.app.core.store.StoreBillingPlan
import com.zivett.app.core.store.StoreDelivery
import com.zivett.app.core.store.StoreFront
import com.zivett.app.core.store.StorePricingTerm
import com.zivett.app.core.store.StoreProduct
import com.zivett.app.core.store.StorePurchaseOutcome
import com.zivett.app.core.store.StoreReplacement
import com.zivett.app.features.plans.PlanPickerModel
import com.zivett.app.features.plans.PlanShelf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private const val PRO = "com.zivett.app.pro"
private const val ELITE = "com.zivett.app.elite"
private const val BASIC = "com.zivett.app.basic"

private fun plan(id: Int, key: String, name: String, sort: Int, monthly: Int, yearly: Int) =
    SubscriptionResponse.Plan(id, key, name, monthly, yearly, sortOrder = sort, features = listOf("$name perk"))

private fun companyShelf(
    planId: Int = 1,
    provider: String? = "stripe",
    webTermPaid: Boolean? = false,
    changedAt: Instant? = null,
    interval: String = "yearly",
    accountId: String? = "org-token",
    store: SubscriptionResponse.Subscription.Store? = null,
) = SubscriptionResponse(
    plans = listOf(plan(3, "elite", "Elite", 3, 9900, 99000), plan(1, "basic", "Basic", 1, 0, 2500), plan(2, "pro", "Pro", 2, 4900, 49000)),
    subscription = SubscriptionResponse.Subscription(planId = planId, planKey = "basic", interval = interval, changedAt = changedAt, canManage = accountId != null, provider = provider, webTermPaid = webTermPaid, store = store),
    googlePlay = SubscriptionResponse.GooglePlay(
        products = listOf(SubscriptionResponse.GooglePlay.Product(BASIC, 1, "basic"), SubscriptionResponse.GooglePlay.Product(PRO, 2, "pro"), SubscriptionResponse.GooglePlay.Product(ELITE, 3, "elite")),
        packageName = "com.zivett.app",
        accountId = accountId,
    ),
)

private val proProduct = StoreProduct(PRO, "Pro", listOf(StorePricingTerm(StoreBillingPlan.YEARLY, "$576.99"), StorePricingTerm(StoreBillingPlan.MONTHLY, "$57.99")), "$48.08", 17)
private val basicProduct = StoreProduct(BASIC, "Basic", listOf(StorePricingTerm(StoreBillingPlan.YEARLY, "$29.99")), "$2.50")

class PlanShelfTest {
    @Test fun whoBillsDecidesTheMode() {
        assertEquals(PlanShelf.Mode.SHELF, PlanShelf.mode(companyShelf()))
        assertEquals(PlanShelf.Mode.STORE_BILLED, PlanShelf.mode(companyShelf(provider = "google_play")))
        // A paid web term, or a plan the iPhone app bought: never a
        // second biller on top.
        assertEquals(PlanShelf.Mode.BILLED_ELSEWHERE, PlanShelf.mode(companyShelf(webTermPaid = true)))
        assertEquals(PlanShelf.Mode.BILLED_ELSEWHERE, PlanShelf.mode(companyShelf(provider = "app_store", webTermPaid = false)))
    }

    @Test fun defaultBasicIsNotAChoice() {
        assertFalse(PlanShelf.hasChosen(companyShelf()))
        assertTrue(PlanShelf.hasChosen(companyShelf(changedAt = Instant.EPOCH)))
        assertTrue(PlanShelf.hasChosen(companyShelf(provider = "google_play")))
    }

    @Test fun cardsRunInTierOrderMonthlyFirstWithPlayPrices() {
        val cards = PlanShelf.cards(companyShelf(), mapOf(PRO to proProduct, BASIC to basicProduct))
        assertEquals(listOf("Basic", "Pro", "Elite"), cards.map { it.plan.name })
        assertFalse(cards[0].isCurrent) // defaulted, not chosen
        assertEquals(listOf("$29.99/year"), cards[0].options.map { it.price })
        assertEquals("Billed once a year", cards[0].options[0].detail) // no monthly = no per-month anchor
        assertEquals(listOf("$57.99/month", "$576.99/year"), cards[1].options.map { it.price })
        assertEquals("Billed monthly · cancel anytime", cards[1].options[0].detail)
        assertEquals("Billed once a year · works out to $48.08/month · save 17%", cards[1].options[1].detail)
        // Play didn't return Elite: shown, not buyable.
        assertFalse(cards[2].available)
    }

    @Test fun aPlayBilledPlanMarksTheOptionItIsOn() {
        val cards = PlanShelf.cards(companyShelf(planId = 2, provider = "google_play", interval = "monthly"), mapOf(PRO to proProduct))
        val pro = cards.first { it.plan.key == "pro" }
        assertTrue(pro.isCurrent)
        assertEquals(listOf(true, false), pro.options.map { it.isCurrent })
    }

    @Test fun upgradesLandNowEverythingElseAtRenewal() {
        val onPro = companyShelf(planId = 2, provider = "google_play")
        assertTrue(PlanShelf.isImmediateChange(onPro, onPro.plans.first { it.key == "elite" }))
        assertFalse(PlanShelf.isImmediateChange(onPro, onPro.plans.first { it.key == "basic" }))
        assertFalse(PlanShelf.isImmediateChange(onPro, onPro.plans.first { it.key == "pro" })) // interval change
    }

    @Test fun manageLinkOpensPlaysSubscriptionPage() {
        assertEquals("https://play.google.com/store/account/subscriptions?package=com.zivett.app&sku=$PRO", PlanShelf.manageUrl("com.zivett.app", PRO))
    }
}

/// A Play store that does what the test says.
private class FakeStore(
    var products: List<StoreProduct> = listOf(proProduct, basicProduct),
    var outcome: StorePurchaseOutcome = StorePurchaseOutcome.Purchased(OwnedPurchase(PRO, "tok-1", acknowledged = false, accountId = "org-token")),
    var owned: List<OwnedPurchase> = emptyList(),
) : StoreFront {
    override val updates: SharedFlow<OwnedPurchase> = MutableSharedFlow()
    val purchases = mutableListOf<Triple<String, StoreBillingPlan, StoreReplacement?>>()
    val acknowledged = mutableListOf<String>()

    override suspend fun products(ids: List<String>) = products.filter { it.id in ids }
    override suspend fun purchase(productId: String, plan: StoreBillingPlan, accountId: String, replacing: StoreReplacement?): StorePurchaseOutcome {
        purchases += Triple(productId, plan, replacing)
        return outcome
    }
    override suspend fun owned() = owned
    override suspend fun acknowledge(purchase: OwnedPurchase) { acknowledged += purchase.purchaseToken }
}

class PlanPickerModelTest {
    private val bought = companyShelf(planId = 2, provider = "google_play", changedAt = Instant.EPOCH)

    @Test fun buyingHandsTheTokenToTheServerThenAcknowledges() = runTest {
        val client = PreviewApiClient(mapOf("api/company/subscription" to companyShelf()))
        val store = FakeStore()
        val model = PlanPickerModel("company", client, store)
        model.load()
        client.responses["api/company/subscription/google-play"] = bought

        assertTrue(model.buy(model.cards.first { it.plan.key == "pro" }, StoreBillingPlan.MONTHLY))
        assertEquals(listOf(Triple(PRO, StoreBillingPlan.MONTHLY, null as StoreReplacement?)), store.purchases)
        assertTrue("POST api/company/subscription/google-play" in client.sent)
        assertEquals(listOf("tok-1"), store.acknowledged)
        assertEquals("You're on Pro.", model.notice)
        assertEquals(PlanShelf.Mode.STORE_BILLED, PlanShelf.mode(model.state.value!!))
    }

    @Test fun aFailedHandOffNeverAcknowledgesAndPointsAtRestore() = runTest {
        val client = PreviewApiClient(mapOf("api/company/subscription" to companyShelf()))
        val store = FakeStore()
        val model = PlanPickerModel("company", client, store)
        model.load()
        client.errors["api/company/subscription/google-play"] = ApiError.Transport("offline")

        assertFalse(model.buy(model.cards.first { it.plan.key == "pro" }, StoreBillingPlan.YEARLY))
        assertTrue(store.acknowledged.isEmpty())
        assertTrue(model.toast!!.endsWith("Your purchase is safe — tap Restore purchases to finish."))
    }

    @Test fun membersCantBuyAndPendingPaymentsSaySo() = runTest {
        val member = PlanPickerModel("company", PreviewApiClient(mapOf("api/company/subscription" to companyShelf(accountId = null))), FakeStore())
        member.load()
        assertFalse(member.buy(member.cards.first { it.plan.key == "pro" }, StoreBillingPlan.YEARLY))
        assertEquals("Only organization admins can change the plan.", member.toast)

        val pending = PlanPickerModel("company", PreviewApiClient(mapOf("api/company/subscription" to companyShelf())), FakeStore(outcome = StorePurchaseOutcome.Pending))
        pending.load()
        assertFalse(pending.buy(pending.cards.first { it.plan.key == "pro" }, StoreBillingPlan.YEARLY))
        assertTrue(pending.notice!!.startsWith("Your payment is pending"))
    }

    @Test fun switchingAPlayBilledPlanReplacesTheOwnedPurchase() = runTest {
        val client = PreviewApiClient(mapOf("api/company/subscription" to bought.copy(subscription = bought.subscription.copy(store = SubscriptionResponse.Subscription.Store(productId = PRO, status = "active")))))
        val store = FakeStore(
            products = listOf(proProduct, proProduct.copy(id = ELITE, displayName = "Elite")),
            outcome = StorePurchaseOutcome.Purchased(OwnedPurchase(ELITE, "tok-2", false, "org-token")),
            owned = listOf(OwnedPurchase(PRO, "tok-1", true, "org-token")),
        )
        val model = PlanPickerModel("company", client, store)
        model.load()
        client.responses["api/company/subscription/google-play"] = bought

        assertTrue(model.buy(model.cards.first { it.plan.key == "elite" }, StoreBillingPlan.YEARLY))
        assertEquals(StoreReplacement("tok-1", immediate = true), store.purchases.single().third)

        // A downgrade waits for renewal.
        assertTrue(model.buy(model.cards.first { it.plan.key == "basic" }.copy(productId = PRO), StoreBillingPlan.YEARLY))
        assertEquals(StoreReplacement("tok-1", immediate = false), store.purchases.last().third)
        assertEquals("You'll move to Basic when your current period ends.", model.notice)
    }

    @Test fun switchingFromAnotherGoogleAccountExplainsInsteadOfDoubleBilling() = runTest {
        val store = FakeStore(owned = emptyList())
        val model = PlanPickerModel("company", PreviewApiClient(mapOf("api/company/subscription" to bought)), store)
        model.load()
        assertFalse(model.buy(model.cards.first { it.plan.key == "pro" }, StoreBillingPlan.MONTHLY))
        assertTrue(store.purchases.isEmpty())
        assertTrue(model.toast!!.contains("different Google account"))
    }

    @Test fun restoreResendsEveryOwnedSubscription() = runTest {
        val client = PreviewApiClient(mapOf("api/company/subscription" to companyShelf(), "api/company/subscription/google-play" to bought))
        val store = FakeStore(owned = listOf(OwnedPurchase(PRO, "tok-1", false, "org-token")))
        val model = PlanPickerModel("company", client, store)
        model.load()
        model.restore()
        assertEquals("Purchases restored.", model.toast)
        assertEquals(listOf("tok-1"), store.acknowledged)

        val empty = PlanPickerModel("company", client, FakeStore(owned = emptyList()))
        empty.restore()
        assertTrue(empty.toast!!.startsWith("There are no Google Play subscriptions"))
    }

    @Test fun freePlansGoThroughOurApi() = runTest {
        val business = SubscriptionResponse(
            plans = listOf(plan(10, "business_basic", "Business Basic", 1, 0, 0), plan(11, "business_premium", "Business Premium", 2, 9900, 99000)),
            subscription = SubscriptionResponse.Subscription(planId = 10, planKey = "business_basic", canManage = true, provider = "stripe", webTermPaid = false),
            googlePlay = SubscriptionResponse.GooglePlay(listOf(SubscriptionResponse.GooglePlay.Product("com.zivett.app.premium", 11, "business_premium")), "com.zivett.app", "org-token"),
        )
        val client = PreviewApiClient(mapOf("api/business/subscription" to business))
        val store = FakeStore()
        val model = PlanPickerModel("business", client, store)
        model.load()
        client.responses["api/business/subscription"] = business.copy(subscription = business.subscription.copy(changedAt = Instant.EPOCH))

        val basic = model.cards.first()
        assertNull(basic.productId)
        assertTrue(model.chooseFree(basic))
        assertTrue(store.purchases.isEmpty())
        assertTrue("POST api/business/subscription" in client.sent)
        assertTrue(PlanShelf.hasChosen(model.state.value!!))
    }
}

class StoreDeliveryTest {
    private val companyAdmin = Fixtures.user(role = UserRole.COMPANY, organization = Fixtures.organization())

    @Test fun onlyOrgAdminsDeliver() {
        assertEquals("company", StoreDelivery.area(companyAdmin))
        assertEquals("business", StoreDelivery.area(Fixtures.user(role = UserRole.BUSINESS, organization = Fixtures.organization(OrganizationType.BUSINESS))))
        assertNull(StoreDelivery.area(Fixtures.user(role = UserRole.COMPANY, organization = Fixtures.organization(), organizationRole = OrganizationRole.MEMBER)))
        assertNull(StoreDelivery.area(Fixtures.user()))
        assertNull(StoreDelivery.area(null))
    }

    @Test fun unacknowledgedPurchasesArePostedThenAcknowledged() = runTest {
        val client = PreviewApiClient(mapOf("api/company/subscription/google-play" to companyShelf()))
        val store = FakeStore(owned = listOf(OwnedPurchase(PRO, "tok-1", false), OwnedPurchase(ELITE, "tok-0", true)))
        StoreDelivery.deliverUnfinished(store, client, companyAdmin)
        assertEquals(listOf("POST api/company/subscription/google-play"), client.sent)
        assertEquals(listOf("tok-1"), store.acknowledged)

        // The server refused it: left unacknowledged for the next try.
        val refusing = PreviewApiClient()
        refusing.errors["api/company/subscription/google-play"] = ApiError.Transport("offline")
        val later = FakeStore(owned = listOf(OwnedPurchase(PRO, "tok-1", false)))
        StoreDelivery.deliverUnfinished(later, refusing, companyAdmin)
        assertTrue(later.acknowledged.isEmpty())
    }
}
