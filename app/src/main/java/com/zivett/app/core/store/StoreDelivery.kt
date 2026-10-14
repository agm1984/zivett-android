package com.zivett.app.core.store

import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.models.SubscriptionEndpoints
import com.zivett.app.core.models.SubscriptionResponse
import com.zivett.app.core.models.User
import com.zivett.app.core.models.UserRole
import com.zivett.app.core.network.ApiClient
import kotlinx.coroutines.CancellationException

/// Hands purchases to the server — the Android stand-in for iOS's
/// `StoreTransactionListener`. A purchase is acknowledged only once the
/// server has it (or it was already acknowledged); if nobody can take
/// it (signed out, a member), it waits: Google's notification carries it
/// to the server, and the server acknowledges it there.
object StoreDelivery {
    /// The org shelf a signed-in user can buy for, or null.
    fun area(user: User?): String? {
        if (user?.organizationRole != OrganizationRole.ADMIN) return null
        return when (user.role) {
            UserRole.COMPANY -> "company"
            UserRole.BUSINESS -> "business"
            else -> null
        }
    }

    /// Posts one purchase; returns the server's payload, or null when it
    /// couldn't be delivered (the next resume retries).
    suspend fun deliver(store: StoreFront, client: ApiClient, user: User?, purchase: OwnedPurchase): SubscriptionResponse? {
        val area = area(user) ?: return null
        return try {
            val response = client.send(SubscriptionEndpoints.googlePlayPurchase(area, purchase.productId, purchase.purchaseToken))
            runCatching { store.acknowledge(purchase) }
            response
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /// Anything Google still waits on us to acknowledge.
    suspend fun deliverUnfinished(store: StoreFront, client: ApiClient, user: User?) {
        if (area(user) == null) return
        val owned = try { store.owned() } catch (e: CancellationException) { throw e } catch (_: Exception) { return }
        owned.filter { !it.acknowledged }.forEach { deliver(store, client, user, it) }
    }
}
