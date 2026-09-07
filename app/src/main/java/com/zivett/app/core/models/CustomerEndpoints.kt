@file:UseSerializers(LaravelInstantSerializer::class)

package com.zivett.app.core.models

import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.ApiRequest.Method
import com.zivett.app.core.network.LaravelInstantSerializer
import com.zivett.app.core.network.MultipartForm
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/// Every `/api/customer/*` call plus the cross-role ones a customer uses
/// (notifications, billing context). Paths mirror `routes/api.php`.
object CustomerEndpoints {
    // Home & lists
    fun home() = ApiRequest.get<CustomerHome>("api/customer/home")
    fun jobs() = ApiRequest.get<JobsResponse>("api/customer/jobs")
    fun job(id: Int) = ApiRequest.get<JobResponse>("api/customer/jobs/$id")
    fun warranties() = ApiRequest.get<JobsResponse>("api/customer/warranties")
    fun invoices() = ApiRequest.get<InvoicesResponse>("api/customer/invoices")
    fun invoice(id: Int) = ApiRequest.get<InvoiceResponse>("api/customer/invoices/$id")
    fun location(jobId: Int) = ApiRequest.get<JobLocation>("api/customer/jobs/$jobId/location")

    // Job actions
    fun cancel(jobId: Int) = ApiRequest.post<JobResponse>("api/customer/jobs/$jobId/cancel")
    fun close(jobId: Int, tipCents: Int) = ApiRequest.post<JobResponse, Map<String, Int>>("api/customer/jobs/$jobId/close", mapOf("tip_cents" to tipCents))
    fun acceptQuote(jobId: Int, quoteId: Int, body: AcceptQuoteBody) = ApiRequest.post<JobResponse, AcceptQuoteBody>("api/customer/jobs/$jobId/quotes/$quoteId/accept", body)
    fun couponPreview(jobId: Int, code: String) = ApiRequest.post<CouponPreviewResponse, Map<String, String>>("api/customer/jobs/$jobId/coupon-preview", mapOf("code" to code))
    fun approveChangeOrder(jobId: Int, changeOrderId: Int) = ApiRequest.post<ChangeOrderResponse>("api/customer/jobs/$jobId/change-orders/$changeOrderId/approve")
    fun declineChangeOrder(jobId: Int, changeOrderId: Int) = ApiRequest.post<ChangeOrderResponse>("api/customer/jobs/$jobId/change-orders/$changeOrderId/decline")
    fun review(jobId: Int, rating: Int, comment: String?) = ApiRequest.post<ReviewResponse, ReviewBody>("api/customer/jobs/$jobId/review", ReviewBody(rating, comment))
    fun updateReview(reviewId: Int, rating: Int, comment: String?) = ApiRequest.patch<ReviewResponse, ReviewBody>("api/customer/reviews/$reviewId", ReviewBody(rating, comment))
    fun warrantyClaim(jobId: Int, body: String) = ApiRequest.post<DisputeResponse, Map<String, String>>("api/customer/jobs/$jobId/claim", mapOf("body" to body))
    fun report(jobId: Int, kind: String, body: String) = ApiRequest.post<DisputeResponse, Map<String, String>>("api/customer/jobs/$jobId/report", mapOf("kind" to kind, "body" to body))
    fun deletePhoto(id: Int) = ApiRequest.empty(Method.DELETE, "api/customer/photos/$id")
    /// `photos[]` multipart — JPEG data from the picker.
    fun uploadPhotos(jobId: Int, photos: List<ByteArray>): ApiRequest<PhotosResponse> {
        val form = MultipartForm()
        photos.forEachIndexed { i, data -> form.addFile("photos[]", "photo-$i.jpg", "image/jpeg", data) }
        return ApiRequest.multipart("api/customer/jobs/$jobId/photos", form)
    }

    // Booking
    fun bookingOptions() = ApiRequest.get<BookingOptions>("api/customer/booking-options")

    /// The pre-accept trust signal: any authenticated user weighing a
    /// pro may read their reviews (cross-role marketplace data).
    fun companyReviews(organizationId: Int) = ApiRequest.get<CompanyReviewsResponse>("api/companies/$organizationId/reviews")

    /// The anonymous supply probe (`ProSupplyController`): count + rate
    /// floor for the wizard's supply line, per-day availability counts
    /// (`days`) for greying dead days on the schedule grid. Aggregates
    /// only — never company data.
    fun proSupply(categorySlug: String?, lat: Double?, lng: Double?, days: Int? = null): ApiRequest<ProSupplyResponse> {
        val query = mutableListOf<Pair<String, String>>()
        if (categorySlug != null) query += "category" to categorySlug
        if (lat != null && lng != null) { query += "lat" to lat.toString(); query += "lng" to lng.toString() }
        if (days != null) query += "days" to days.toString()
        return ApiRequest.get("api/pros", query)
    }

    fun book(body: BookJobBody) = ApiRequest.jsonElement<JobResponse>(Method.POST, "api/customer/jobs", body.toJson())

    // Invoices
    fun pay(invoiceId: Int, couponCode: String?, tipCents: Int) = ApiRequest.post<InvoiceResponse, PayBody>("api/customer/invoices/$invoiceId/pay", PayBody(couponCode, tipCents))

    // Messages
    fun conversations() = ApiRequest.get<ConversationsResponse>("api/customer/conversations")
    fun conversation(jobId: Int) = ApiRequest.get<ConversationResponse>("api/customer/jobs/$jobId/conversation")
    fun sendMessage(jobId: Int, body: String) = ApiRequest.post<MessageResponse, Map<String, String>>("api/customer/jobs/$jobId/conversation/messages", mapOf("body" to body))
    fun blockUser(jobId: Int, userId: Int) = ApiRequest.post<BlockResponse, Map<String, Int>>("api/customer/jobs/$jobId/conversation/block", mapOf("user_id" to userId))
    fun unblockUser(jobId: Int, userId: Int) = ApiRequest.delete<BlockResponse>("api/customer/jobs/$jobId/conversation/block/$userId")

    // Account
    fun profile() = ApiRequest.get<CustomerProfile>("api/customer/profile")
    fun updateProfile(body: UpdateProfileBody) = ApiRequest.patch<CustomerProfile, UpdateProfileBody>("api/customer/profile", body)
    fun updatePreferences(email: Map<String, Boolean>?, sms: Boolean?) = ApiRequest.patch<CustomerProfile, PreferencesBody>("api/customer/profile/notifications", PreferencesBody(email, sms))
    fun referral() = ApiRequest.get<Referral>("api/customer/referral")
    fun addresses() = ApiRequest.get<AddressesResponse>("api/customer/addresses")
    fun createAddress(body: AddressBody) = ApiRequest.post<AddressResponse, AddressBody>("api/customer/addresses", body)
    fun updateAddress(id: Int, body: AddressBody) = ApiRequest.patch<AddressResponse, AddressBody>("api/customer/addresses/$id", body)
    fun deleteAddress(id: Int) = ApiRequest.empty(Method.DELETE, "api/customer/addresses/$id")

    // Cross-role
    fun notifications() = ApiRequest.get<NotificationFeed>("api/notifications")
    fun markRead(id: String) = ApiRequest.post<UnreadCount>("api/notifications/$id/read")
    fun markAllRead() = ApiRequest.post<UnreadCount>("api/notifications/read-all")
    fun billingContext() = ApiRequest.post<BillingContext>("api/billing/setup-intent")
    /// Save a confirmed SetupIntent's card as the booker's payment
    /// method outside quote acceptance (the invoice-pay path).
    fun saveCard(setupIntentId: String) = ApiRequest.post<SavedCardResponse, Map<String, String>>("api/billing/card", mapOf("stripe_setup_intent_id" to setupIntentId))
    fun geocode(address: String) = ApiRequest.post<Geocode, Map<String, String>>("api/geocode", mapOf("address" to address))
}

// MARK: - Request bodies

/// `GET /api/pros` — `count`, the market's rate floor, and (with `days`)
/// per-day counts of matched pros able to attend.
@Serializable
data class ProSupplyResponse(val count: Int = 0, val minHourlyRateCents: Int? = null, val availableDates: List<DayCount>? = null) {
    @Serializable
    data class DayCount(val date: String, val available: Int)
}

@Serializable
data class AcceptQuoteBody(
    val couponCode: String? = null,
    val scheduledDate: String? = null,
    val scheduledWindow: String? = null,
    val stripeSetupIntentId: String? = null,
)

@Serializable data class ReviewBody(val rating: Int, val comment: String? = null)
@Serializable data class PayBody(val couponCode: String? = null, val tipCents: Int)

/// `StoreJobRequest`. Encoded by hand: intake answers go as
/// `{ id: string | [string] }`, which no plain data class spells.
data class BookJobBody(
    val serviceCategoryId: Int,
    /// `instant` · `scheduled` · `quote`
    val mode: String,
    /// `asap` · `today` · `this_week`
    val urgency: String,
    val issue: String,
    val intakeAnswers: List<IntakeAnswerBody>? = null,
    val availabilityWindows: List<AvailabilityWindow>? = null,
    /// Customer bookings; business jobs derive it from the property.
    val address: String? = null,
    val unit: String? = null,
    val accessNotes: String? = null,
    val customerAddressId: Int? = null,
    /// Business bookings.
    val propertyId: Int? = null,
    val tenant: String? = null,
) {
    data class IntakeAnswerBody(val questionId: Int, val answers: List<String>)

    fun toJson(): JsonObject = buildJsonObject {
        put("service_category_id", serviceCategoryId)
        put("mode", mode)
        put("urgency", urgency)
        put("issue", issue)
        address?.let { put("address", it) }
        unit?.let { put("unit", it) }
        accessNotes?.let { put("access_notes", it) }
        customerAddressId?.let { put("customer_address_id", it) }
        propertyId?.let { put("property_id", it) }
        tenant?.let { put("tenant", it) }
        availabilityWindows?.let { windows ->
            put("availability_windows", JsonArray(windows.map { w -> buildJsonObject { put("date", w.date); put("window", w.window) } }))
        }
        intakeAnswers?.let { answers ->
            putJsonObject("intake_answers") {
                for (answer in answers) {
                    if (answer.answers.size == 1) put(answer.questionId.toString(), answer.answers[0])
                    else put(answer.questionId.toString(), JsonArray(answer.answers.map { JsonPrimitive(it) }))
                }
            }
        }
    }
}

/// Always the split pair — the server ignores a single `name`.
@Serializable data class UpdateProfileBody(val firstName: String? = null, val lastName: String? = null, val email: String? = null, val phone: String? = null)
@Serializable data class PreferencesBody(val email: Map<String, Boolean>? = null, val sms: Boolean? = null)

@Serializable
data class AddressBody(
    val label: String,
    val address: String,
    val accessNotes: String? = null,
    val isDefault: Boolean? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val pinAdjusted: Boolean? = null,
)

// MARK: - Small responses

@Serializable data class ChangeOrderResponse(val changeOrder: ChangeOrder)
@Serializable data class PhotosResponse(val photos: List<JobPhoto> = emptyList())

/// `409 { code: 'schedule_conflict', valid_windows }` on quote accept.
@Serializable
data class ScheduleConflict(val message: String? = null, val code: String? = null, val validWindows: List<AvailabilityWindow>? = null)

/// The `{ code, client_secret? }` shape a 409 body carries (schedule
/// conflicts, 3DS challenges) — shared by every payer path.
@Serializable
data class ConflictCode(val code: String? = null, val clientSecret: String? = null)

@Serializable data class ReviewResponse(val review: Review)
@Serializable data class DisputeResponse(val dispute: Dispute)
@Serializable data class UnreadCount(val unread: Int = 0)

@Serializable
data class CouponPreviewResponse(val matches: List<Match> = emptyList()) {
    @Serializable
    data class Match(val quoteId: Int, val discountCents: Int, val totalCents: Int, val authorizationPreview: Quote.AuthorizationPreview? = null)
}

/// `POST /api/billing/card`.
@Serializable
data class SavedCardResponse(val savedCard: BillingContext.SavedCard? = null)

/// `POST /api/billing/setup-intent`.
@Serializable
data class BillingContext(
    val driver: String = "simulated",
    val publishableKey: String? = null,
    val clientSecret: String? = null,
    val savedCard: SavedCard? = null,
) {
    @Serializable
    data class SavedCard(val brand: String? = null, val last4: String? = null)

    /// A stored card (or the simulated driver) means accept/pay can go
    /// straight through without collecting a card natively.
    val canChargeWithoutCardForm: Boolean get() = driver != "stripe" || savedCard != null
}

@Serializable data class Geocode(val lat: Double? = null, val lng: Double? = null)

/// `GET /api/companies/{org}/reviews` — summary over every review, list
/// capped at the latest 50 server-side.
@Serializable
data class CompanyReviewsResponse(val company: Header, val summary: Summary, val reviews: List<Item> = emptyList()) {
    @Serializable data class Header(val id: Int, val name: String, val plan: String? = null)
    @Serializable data class Summary(val rating: Double? = null, val count: Int = 0)
    @Serializable
    data class Item(val id: Int, val rating: Int, val comment: String? = null, val reviewer: String? = null, val job: JobRef? = null, val createdAt: java.time.Instant? = null) {
        @Serializable data class JobRef(val title: String? = null)
    }
}

/// `GET /api/marketing` — only the signup-fee slice is modelled here.
@Serializable
data class Marketing(val signupFee: SignupFee) {
    @Serializable data class SignupFee(val cents: Int = 0, val waived: Boolean = false)
}

/// `GET /api/referrals/{code}` — a customer invite carries the
/// first-job discount; a company invite is a pro vouching for a pro.
@Serializable
data class ReferralInvite(val kind: String, val firstName: String? = null, val name: String? = null, val discount: Discount? = null) {
    @Serializable data class Discount(val mode: String? = null, val cents: Int? = null, val bps: Int? = null, val jobs: Int? = null)

    val discountLabel: String
        get() {
            val discount = discount ?: return ""
            return if (discount.mode == "percent") "${(discount.bps ?: 0) / 100}% off" else "$${(discount.cents ?: 0) / 100} off"
        }
}

object MarketingEndpoints {
    fun marketing() = ApiRequest.get<Marketing>("api/marketing", authenticated = false)

    /// Resolve a referral code for the invite banner. 404s stay silent —
    /// signup proceeds unpersonalized and the backend soft-ignores
    /// unresolvable codes.
    fun referral(code: String) = ApiRequest.get<ReferralInvite>("api/referrals/$code", authenticated = false)
}
