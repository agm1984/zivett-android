package com.zivett.app.features.customer.jobs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.AcceptQuoteBody
import com.zivett.app.core.models.AvailabilityWindow
import com.zivett.app.core.models.ChangeOrder
import com.zivett.app.core.models.CustomerEndpoints
import com.zivett.app.core.models.Dispute
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobArea
import com.zivett.app.core.models.JobPhoto
import com.zivett.app.core.models.Quote
import com.zivett.app.core.models.ScheduleConflict
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.payments.ChallengeOutcome
import com.zivett.app.core.payments.PaymentChallenger
import com.zivett.app.core.payments.StripeChallenger
import com.zivett.app.core.reloaded
import kotlinx.coroutines.delay

/// Everything the job page can do. Every mutation swaps the whole job
/// from the response (the API returns the full show-shaped payload), so
/// no partial state can blank the UI.
class JobDetailModel(
    val jobId: Int,
    private val client: ApiClient,
    val area: JobArea = JobArea.customer,
    private val stripeContext: android.content.Context? = null,
    /// Runs the bank's confirmation on a 409 `payment_action_required`.
    private val challenger: PaymentChallenger? = stripeContext?.let { StripeChallenger(it) },
) {
    sealed interface AcceptOutcome {
        data class Accepted(val holdCents: Int, val company: String, val date: String?, val window: String?) : AcceptOutcome
        data class ScheduleConflictOutcome(val validWindows: List<AvailabilityWindow>) : AcceptOutcome
    }

    var state by mutableStateOf<Loadable<Job>>(Loadable.Loading)
    var toast by mutableStateOf<String?>(null)
    var justClosed by mutableStateOf(false)
    var busy by mutableStateOf(false)

    val job: Job? get() = state.value

    suspend fun load() { state = state.reloaded { client.send(area.job(jobId)).job } }

    // Actions

    suspend fun cancel() {
        mutate("Could not cancel this job — work may have already started.") {
            val job = client.send(area.cancel(jobId)).job
            toast = "${job.code ?: "Job"} cancelled"
            job
        }
    }

    /// What Pay & close came back with — the sheet stays up on a decline
    /// and hands the booker the bank's message + a way to swap cards.
    sealed interface CloseOutcome {
        data object Closed : CloseOutcome
        data class Declined(val message: String) : CloseOutcome
        data object Failed : CloseOutcome
    }

    /// Pay & close. On 409 `payment_action_required` the SDK re-confirms
    /// the charge in-app (the bank's 3DS challenge); the
    /// `payment_intent.succeeded` webhook then finishes settlement
    /// server-side, so we poll the job until it lands.
    suspend fun close(tipCents: Int): CloseOutcome {
        var declined: String? = null
        val succeeded = mutate("Could not close this job. Please try again.", onDeclined = { declined = it }) {
            try {
                val job = client.send(area.close(jobId, tipCents)).job
                justClosed = job.closedAt != null
                job
            } catch (error: ApiError.Conflict) {
                val action = error.paymentAction ?: throw error
                // The in-app bank confirmation needs the publishable key;
                // without one (billing fetch failed) explain instead.
                val key = runCatching { client.send(CustomerEndpoints.billingContext()).publishableKey }.getOrNull()
                val challenger = challenger
                if (key == null || challenger == null) throw ApiError.Server(409, ChallengeOutcome.UNAVAILABLE_COPY)
                when (val outcome = challenger.confirm(key, action)) {
                    ChallengeOutcome.Succeeded -> pollUntilClosed() ?: throw ApiError.Server(409, ChallengeOutcome.SETTLING_COPY)
                    ChallengeOutcome.Canceled -> throw ApiError.Server(409, ChallengeOutcome.CANCELED_COPY)
                    // The bank said no: same way out as a decline — the
                    // sheet stays up with their message and the card row.
                    is ChallengeOutcome.Failed -> { declined = outcome.message ?: ChallengeOutcome.FAILED_COPY; throw error }
                    // The result never reached us, so "nothing was
                    // charged" would be a guess. Look before saying anything.
                    ChallengeOutcome.Unknown -> pollUntilClosed() ?: throw ApiError.Server(409, ChallengeOutcome.UNKNOWN_COPY)
                }
            }
        }
        declined?.let { return CloseOutcome.Declined(it) }
        return if (succeeded) CloseOutcome.Closed else CloseOutcome.Failed
    }

    /// After an in-app 3DS confirmation, the webhook settles the invoice
    /// asynchronously — give it a few beats before giving up.
    private suspend fun pollUntilClosed(): Job? {
        repeat(5) {
            delay(2000)
            val job = runCatching { client.send(area.job(jobId)).job }.getOrNull()
            if (job != null && (job.invoice?.isPaid == true || job.closedAt != null)) {
                justClosed = job.closedAt != null
                return job
            }
        }
        return null
    }

    suspend fun acceptQuote(quote: Quote, scheduledDate: String? = null, window: String? = null, setupIntentId: String? = null): AcceptOutcome? {
        busy = true
        try {
            val body = AcceptQuoteBody(couponCode = null, scheduledDate = scheduledDate, scheduledWindow = window, stripeSetupIntentId = setupIntentId)
            val job = client.send(area.acceptQuote(jobId, quote.id, body)).job
            state = Loadable.Loaded(job)
            toast = "Your ZiVETT Pro is on the way — quote accepted"
            val hold = quote.authorizationPreview?.totalCents ?: quote.amountCents
            return AcceptOutcome.Accepted(hold, job.company?.name ?: "Your pro", job.scheduledDate, job.scheduledWindow)
        } catch (error: ApiError.Conflict) {
            val conflict = runCatching { JsonCoding.json.decodeFromString<ScheduleConflict>(error.body.decodeToString()) }.getOrNull()
            if (conflict?.code == "schedule_conflict") return AcceptOutcome.ScheduleConflictOutcome(conflict.validWindows ?: emptyList())
            toast = error.detail ?: "This job already has a pro assigned."
        } catch (error: ApiError) {
            toast = error.first("coupon_code") ?: error.userMessage
        } catch (error: Exception) {
            toast = error.userMessage
        } finally {
            busy = false
        }
        return null
    }

    suspend fun decideChangeOrder(order: ChangeOrder, approve: Boolean) {
        busy = true
        try {
            val request = if (approve) area.approveChangeOrder(jobId, order.id) else area.declineChangeOrder(jobId, order.id)
            val updated = client.send(request).changeOrder
            job?.let { current ->
                state = Loadable.Loaded(current.copy(changeOrders = current.changeOrders?.map { if (it.id == order.id) updated else it }))
            }
            toast = if (approve) "Change order approved" else "Change order declined"
        } catch (error: ApiError) {
            toast = if (error is ApiError.Validation) "Could not save that decision. Please try again." else error.userMessage
        } catch (_: Exception) {
            toast = "Could not save that decision. Please try again."
        } finally {
            busy = false
        }
    }

    suspend fun submitReview(rating: Int, comment: String): Boolean {
        busy = true
        try {
            val trimmed = comment.trim().ifEmpty { null }
            val existing = job?.review
            val review = if (existing != null) client.send(area.updateReview(existing.id, rating, trimmed)).review
            else client.send(area.review(jobId, rating, trimmed)).review
            job?.let { state = Loadable.Loaded(it.copy(review = review)) }
            toast = "Thanks — your review helps keep pros accountable"
            // A 5-star review auto-closes the job server-side.
            if (rating == 5) load()
            return true
        } catch (error: ApiError) {
            toast = error.first("rating") ?: error.first("comment") ?: error.userMessage
        } catch (error: Exception) {
            toast = error.userMessage
        } finally {
            busy = false
        }
        return false
    }

    suspend fun report(kind: String, body: String): Dispute? {
        busy = true
        try {
            val dispute = client.send(area.report(jobId, kind, body)).dispute
            job?.let { state = Loadable.Loaded(it.copy(disputes = (it.disputes ?: emptyList()) + dispute)) }
            return dispute
        } catch (error: ApiError) {
            toast = error.first("body") ?: error.userMessage
        } catch (_: Exception) {
            toast = "Could not send that report. Please try again."
        } finally {
            busy = false
        }
        return null
    }

    suspend fun claim(body: String): Dispute? {
        busy = true
        try {
            val dispute = client.send(area.warrantyClaim(jobId, body)).dispute
            job?.let { state = Loadable.Loaded(it.copy(disputes = (it.disputes ?: emptyList()) + dispute)) }
            return dispute
        } catch (error: ApiError) {
            toast = error.first("body") ?: error.userMessage
        } catch (_: Exception) {
            toast = "Could not open that claim. Please try again."
        } finally {
            busy = false
        }
        return null
    }

    suspend fun upload(photos: List<ByteArray>) {
        if (photos.isEmpty()) return
        busy = true
        try {
            val added = client.send(area.uploadPhotos(jobId, photos)).photos
            job?.let { state = Loadable.Loaded(it.copy(photos = (it.photos ?: emptyList()) + added)) }
            toast = "Photos added — pros can now see the problem"
        } catch (error: ApiError) {
            toast = error.first("photos") ?: error.first("photos.0") ?: error.userMessage
        } catch (error: Exception) {
            toast = error.userMessage
        } finally {
            busy = false
        }
    }

    suspend fun deletePhoto(photo: JobPhoto) {
        try {
            client.send(area.deletePhoto(photo.id))
            job?.let { state = Loadable.Loaded(it.copy(photos = it.photos?.filter { p -> p.id != photo.id })) }
        } catch (error: Exception) {
            toast = error.userMessage
        }
    }

    /// `onDeclined` receives the bank's message for a declined charge
    /// INSTEAD of a toast — the pay sheet shows it beside the card.
    private suspend fun mutate(fallback: String, onDeclined: ((String) -> Unit)? = null, operation: suspend () -> Job): Boolean {
        busy = true
        try {
            state = Loadable.Loaded(operation())
            return true
        } catch (error: ApiError) {
            val declinedMessage = error.paymentDeclinedMessage
            if (onDeclined != null && declinedMessage != null) {
                onDeclined(declinedMessage)
                return false
            }
            // The operation already reported a decline of its own (a
            // failed bank confirmation) — no toast on top of it.
            if (onDeclined != null && error.paymentAction != null) return false
            toast = when (error) {
                is ApiError.Validation -> error.errors.message
                is ApiError.Server -> error.detail ?: fallback
                else -> fallback
            }
        } catch (_: Exception) {
            toast = fallback
        } finally {
            busy = false
        }
        return false
    }
}
