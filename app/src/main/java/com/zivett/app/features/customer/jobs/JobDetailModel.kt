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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
    /// Why accepting a quote failed — shown INSIDE the accept sheet. It
    /// used to go to `toast`, which renders underneath the modal sheet
    /// and is gone in four seconds, so a refusal (no card, the card
    /// couldn't be saved, already assigned, throttled, offline) looked
    /// like a Confirm button that did nothing.
    var acceptError by mutableStateOf<String?>(null)

    val job: Job? get() = state.value

    suspend fun load() { state = state.reloaded { client.send(area.job(jobId)).job } }

    // Actions

    /// NonCancellable like the other money calls — a cancellation past
    /// the grace window charges its fee on the spot.
    suspend fun cancel() {
        withContext(NonCancellable) {
            mutate("Could not cancel this job — work may have already started.") {
                val job = client.send(area.cancel(jobId)).job
                toast = "${job.code ?: "Job"} cancelled"
                job
            }
        }
    }

    /// What Pay & close came back with — the sheet stays up on a decline
    /// and hands the booker the bank's message + a way to swap cards.
    sealed interface CloseOutcome {
        data object Closed : CloseOutcome
        data class Declined(val message: String) : CloseOutcome
        data object Failed : CloseOutcome
        /// The call ended without an answer and the job still reads
        /// unpaid — the sheet stays up with this and they can retry.
        data class Unconfirmed(val message: String) : CloseOutcome
        /// A second tap while the first charge is still in flight — ignore it.
        data object InFlight : CloseOutcome
    }

    // `busy` is shared by every action on the page; the two calls that
    // commit money get a guard of their own so neither can ever be
    // entered twice, whatever else flips `busy`.
    private var closing = false
    private var accepting = false

    /// Pay & close. On 409 `payment_action_required` the SDK re-confirms
    /// the charge in-app (the bank's 3DS challenge); the
    /// `payment_intent.succeeded` webhook then finishes settlement
    /// server-side, so we poll the job until it lands.
    ///
    /// Runs NonCancellable: the caller's scope belongs to a sheet, and a
    /// scope that died mid-request used to cancel the HTTP call and
    /// report "Could not close this job" — about a card that may well
    /// have been charged. The sheet also refuses to dismiss while busy.
    suspend fun close(tipCents: Int): CloseOutcome {
        if (closing) return CloseOutcome.InFlight
        closing = true
        try {
            return withContext(NonCancellable) { closeNow(tipCents) }
        } finally {
            closing = false
        }
    }

    private suspend fun closeNow(tipCents: Int): CloseOutcome {
        var declined: String? = null
        var unconfirmed: String? = null
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
            } catch (error: ApiError) {
                // A timeout, a 5xx or 503 `payment_provider_error`: the
                // charge may have landed anyway. Look before speaking —
                // if the job settled this WAS a success.
                val message = error.unconfirmedPaymentMessage ?: throw error
                val fresh = runCatching { client.send(area.job(jobId)).job }.getOrNull()
                if (fresh != null && fresh.isSettled) {
                    justClosed = fresh.closedAt != null
                    fresh
                } else {
                    fresh?.let { state = Loadable.Loaded(it) }
                    unconfirmed = message
                    throw error
                }
            }
        }
        unconfirmed?.let { return CloseOutcome.Unconfirmed(it) }
        declined?.let { return CloseOutcome.Declined(it) }
        return if (succeeded) CloseOutcome.Closed else CloseOutcome.Failed
    }

    private val Job.isSettled: Boolean get() = invoice?.isPaid == true || closedAt != null

    /// After an in-app 3DS confirmation, the webhook settles the invoice
    /// asynchronously — give it a few beats before giving up.
    private suspend fun pollUntilClosed(): Job? {
        repeat(5) {
            delay(2000)
            val job = runCatching { client.send(area.job(jobId)).job }.getOrNull()
            if (job != null && job.isSettled) {
                justClosed = job.closedAt != null
                return job
            }
        }
        return null
    }

    /// NonCancellable for the same reason as `close`: accepting is the
    /// payment commitment, and must not be abandoned half-sent.
    suspend fun acceptQuote(quote: Quote, scheduledDate: String? = null, window: String? = null, setupIntentId: String? = null): AcceptOutcome? {
        if (accepting) return null
        accepting = true
        try {
            return withContext(NonCancellable) { acceptNow(quote, scheduledDate, window, setupIntentId) }
        } finally {
            accepting = false
        }
    }

    private suspend fun acceptNow(quote: Quote, scheduledDate: String?, window: String?, setupIntentId: String?): AcceptOutcome? {
        busy = true; acceptError = null
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
            acceptError = error.detail ?: "This job already has a pro assigned."
        } catch (error: ApiError) {
            acceptError = error.first("coupon_code") ?: error.first("stripe_setup_intent_id") ?: error.userMessage
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            acceptError = error.userMessage
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
            // The operation already reported this itself (a failed bank
            // confirmation, an unconfirmed charge) — no toast on top.
            if (onDeclined != null && (error.paymentAction != null || error.unconfirmedPaymentMessage != null)) return false
            toast = when (error) {
                is ApiError.Validation -> error.errors.message
                is ApiError.Server -> error.detail ?: fallback
                // The server's own words ("This job has an open dispute")
                // used to be swapped for the generic fallback.
                is ApiError.Conflict -> error.detail ?: fallback
                else -> fallback
            }
        } catch (error: CancellationException) {
            // Never an "ordinary failure": a cancelled call says nothing
            // about what the server did with it.
            throw error
        } catch (_: Exception) {
            toast = fallback
        } finally {
            busy = false
        }
        return false
    }
}
