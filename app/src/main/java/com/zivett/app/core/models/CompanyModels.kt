@file:UseSerializers(LaravelInstantSerializer::class)

package com.zivett.app.core.models

import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.ApiRequest.Method
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.network.LaravelInstantSerializer
import com.zivett.app.core.network.PhpRatesSerializer
import com.zivett.app.core.network.MultipartForm
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/// The company setup/approval pipeline (`OrganizationController::payload`
/// and the dashboard). Steps are heterogeneous so everything is optional.
@Serializable
data class CompanySetup(
    /// `draft` · `submitted` · `approved` (see the web's setup banner).
    val status: String,
    val submittedAt: Instant? = null,
    val steps: Steps,
    val actionNeeded: Boolean? = null,
) {
    @Serializable
    data class Document(
        val kind: String,
        val label: String,
        /// `missing` · `pending` · `approved` · `rejected`
        val status: String,
        val rejectionReason: String? = null,
        val documentId: Int? = null,
        val originalName: String? = null,
    )

    @Serializable
    data class Steps(val profile: Step, val details: Step, val credentials: Step, val plan: Step) {
        @Serializable
        data class Step(
            val complete: Boolean = false,
            val missing: List<String>? = null,
            val uploaded: Int? = null,
            val total: Int? = null,
            val documents: List<Document>? = null,
            val confirmed: Boolean? = null,
            val planName: String? = null,
        )
    }
}

/// `GET /api/company/dashboard`.
@Serializable
data class CompanyDashboard(val approved: Boolean = false, val stripePayouts: StripePayouts, val setup: CompanySetup, val stats: Stats) {
    @Serializable data class StripePayouts(val required: Boolean = false, val ready: Boolean = true)
    @Serializable data class Stats(val opportunities: Int = 0, val activeJobs: Int = 0, val rating: Double? = null, val payoutPending: Int = 0)
}

/// `GET /api/company/opportunities` — the open marketplace feed.
@Serializable
data class OpportunitiesResponse(
    val opportunities: List<Job> = emptyList(),
    val cooldownUntil: Instant? = null,
    val leads: Leads = Leads(),
    val commissionBps: Int = 1500,
    /// Published labour rate per category id (JSON object keys).
    @Serializable(with = PhpRatesSerializer::class) val hourlyRates: Map<String, Int> = emptyMap(),
    /// False only on the stripe driver before Connect onboarding — the
    /// composer warns up front instead of letting the submit 422.
    val payoutsReady: Boolean? = null,
) {
    @Serializable data class Leads(val limit: Int? = null, val remaining: Int? = null)
}

/// `GET /api/company/quotes`.
@Serializable
data class CompanyQuotesResponse(val quotes: List<Quote> = emptyList(), @Serializable(with = PhpRatesSerializer::class) val hourlyRates: Map<String, Int> = emptyMap(), val commissionBps: Int = 1500)

/// The job summary riding a company-side quote (`QuoteResource` with
/// `job` loaded).
@Serializable
data class QuoteJob(
    val id: Int,
    val code: String? = null,
    val title: String,
    val issue: String? = null,
    val intakeAnswers: List<IntakeAnswer>? = null,
    val mode: String? = null,
    val estimateMin: Int? = null,
    val estimateMax: Int? = null,
    val availabilityWindows: List<AvailabilityWindow>? = null,
    /// Annotated on pending quotes so the revise picker stays live.
    val companyWindows: List<CompanyWindow>? = null,
    val category: JobCategory? = null,
)

/// `GET /api/company/payouts`.
@Serializable
data class PayoutsResponse(val summary: Summary, val payouts: List<Payout> = emptyList()) {
    @Serializable data class Summary(val pendingCents: Int = 0, val paidCents: Int = 0, val paidThisMonthCents: Int = 0)
    @Serializable
    data class Payout(
        val id: Int,
        val amountCents: Int,
        val status: String,
        val scheduledAt: Instant? = null,
        val paidAt: Instant? = null,
        val invoiceNumber: String? = null,
        val jobCode: String? = null,
        val jobTitle: String? = null,
    )
}

/// `GET /api/company/availability` + calendar.
@Serializable
data class AvailabilityResponse(val workingDays: List<Int>? = null, val blockedDates: List<BlockedDate> = emptyList()) {
    @Serializable data class BlockedDate(val id: Int, val date: String)
}

/// `GET /api/company/calendar` — confirmed jobs, tentative quote
/// proposals, blocked days, working days.
@Serializable
data class CompanyCalendar(
    val month: String,
    val jobs: List<CalendarJob> = emptyList(),
    val tentative: List<Tentative>? = null,
    val blockedDates: List<AvailabilityResponse.BlockedDate> = emptyList(),
    val workingDays: List<Int>? = null,
) {
    /// Fields mirror `Company\CalendarController::index`'s jobs map.
    @Serializable
    data class CalendarJob(
        val id: Int,
        val code: String? = null,
        val title: String,
        val status: JobStatus,
        val mode: String? = null,
        /// Scheduled date, or the acceptance day for instant work — the
        /// server drops rows with neither.
        val date: String,
        val window: String? = null,
        val categoryCode: String? = null,
    )

    /// `{quote_id, job_code, title, amount_cents, date, window}`.
    @Serializable
    data class Tentative(val quoteId: Int, val jobCode: String? = null, val title: String? = null, val amountCents: Int = 0, val date: String, val window: String? = null)
}

/// `GET /api/company/passport`.
@Serializable
data class PassportResponse(val passport: Passport, val editable: CompanyDetails? = null) {
    @Serializable
    data class Passport(val name: String, val number: String? = null, val approved: Boolean = false, val documents: Documents, val credentials: List<Credential> = emptyList()) {
        @Serializable data class Documents(val uploaded: Int = 0, val total: Int = 4)
        @Serializable
        data class Credential(
            val field: String,
            /// Absent on informational rows (address, service area…).
            val key: String? = null,
            val value: String? = null,
            val status: String? = null,
            val verifiedAt: String? = null,
            val expiresAt: String? = null,
            val rejectionReason: String? = null,
            /// Document rows only (photo_id, license, insurance…).
            val kind: String? = null,
            val documentId: Int? = null,
            /// The uploaded file's mime, for the in-app viewer.
            val documentMime: String? = null,
        ) {
            val rowId: String get() = key ?: kind ?: this.field
        }
    }
}

/// Trade categories, rates, radius, modes — the "details" the passport
/// and organization pages edit.
@Serializable
data class CompanyDetails(
    val canManage: Boolean? = null,
    val categoryOptions: List<CategoryOption> = emptyList(),
    val selectedCategoryIds: List<Int> = emptyList(),
    @Serializable(with = PhpRatesSerializer::class) val categoryRates: Map<String, Int>? = null,
    val serviceRadiusKm: Int? = null,
    val availabilityModes: List<String>? = null,
    /// ISO weekdays 1–7; null = every day.
    val workingDays: List<Int>? = null,
    val modeOptions: List<ModeOption> = emptyList(),
    val jobAlertEmails: List<String>? = null,
) {
    @Serializable data class CategoryOption(val id: Int, val name: String)
    @Serializable data class ModeOption(val value: String, val label: String)
}

@Serializable
data class CompanyOrganizationResponse(val organization: OrganizationProfile, val setup: CompanySetup, val details: CompanyDetails? = null)

/// `GET /api/company/subscription`.
@Serializable
data class SubscriptionResponse(val plans: List<Plan> = emptyList(), val subscription: Current, val charges: List<Charge>? = null, val changes: List<Change>? = null) {
    @Serializable
    data class Plan(
        val id: Int,
        val key: String,
        val name: String,
        val priceCents: Int = 0,
        val yearlyPriceCents: Int = 0,
        /// Company tiers only — business plans carry no commission.
        val commissionBps: Int? = null,
        /// Business tiers: the plan-owned trust & support rate + perks.
        val trustFeeBps: Int? = null,
        val waivesEmergencyFee: Boolean? = null,
        val priorityBooking: Boolean? = null,
        val blurb: String? = null,
        val features: List<String>? = null,
        /// The tier ladder — what decides "Upgrade" vs "at renewal"
        /// (admin repricing can't invert it).
        val sortOrder: Int? = null,
    ) {
        val isFree: Boolean get() = priceCents == 0 && yearlyPriceCents == 0
    }

    @Serializable
    data class Current(
        val planId: Int,
        val planKey: String,
        val interval: String,
        val priceCents: Int = 0,
        val canManage: Boolean? = null,
        val termStartedAt: Instant? = null,
        val termEndsAt: Instant? = null,
        val renewalCents: Int? = null,
        val pending: Pending? = null,
        val featured: Boolean? = null,
    ) {
        @Serializable data class Pending(val planId: Int? = null, val planName: String? = null, val interval: String? = null)
    }

    @Serializable
    data class Change(val id: Int, val from: String? = null, val to: String, val interval: String? = null, val by: String? = null, val at: Instant? = null)

    /// One subscription billing period (`platform_charges`).
    @Serializable
    data class Charge(
        val id: Int,
        val plan: String? = null,
        val interval: String? = null,
        val amountCents: Int = 0,
        val periodStart: Instant? = null,
        val periodEnd: Instant? = null,
        /// pending · paid · waived
        val status: String,
        val paidAt: Instant? = null,
    )
}

/// `GET /api/company/referral` (pro-to-pro — different stats keys from
/// the customer referral).
@Serializable
data class CompanyReferral(val code: String, val link: String, val reward: Reward, val stats: Stats) {
    @Serializable data class Reward(val mode: String, val cents: Int = 0, val bps: Int = 0)
    @Serializable data class Stats(val companiesJoined: Int = 0, val rewardsEarnedCents: Int = 0, val bankedCount: Int = 0, val pendingCents: Int = 0, val nextCreditCents: Int = 0)
}

@Serializable data class QuoteResponse(val quote: Quote)
@Serializable data class StripeLink(val url: String)
@Serializable data class PayoutsReady(val payoutsReady: Boolean = false)
@Serializable data class WithdrawnResponse(val withdrawn: Boolean = false)
@Serializable data class BlockedDateResponse(val blockedDate: AvailabilityResponse.BlockedDate)

/// `StoreQuoteRequest` — the server derives `amount_cents` from the
/// published rate × hours × crew. Encoded with EXPLICIT nulls: a
/// revision must be able to CLEAR the proposed slot (and message) —
/// Laravel's validated() skips absent keys, so an omitted proposal would
/// silently keep the old one (QuoteController::update).
@Serializable
data class QuoteBody(
    val estimatedHours: Double,
    val crewSize: Int,
    val message: String? = null,
    val proposedDate: String? = null,
    val proposedWindow: String? = null,
)

@Serializable data class ProfilePhotoResponse(val photoUrl: String? = null)

/// `GET/POST /api/company/jobs/{job}/assignment` — the dispatcher's
/// picker. Members arrive in suggested-fairness order (fewest open
/// jobs, longest since last assignment), so "Auto-assign next" is just
/// the head of the list.
@Serializable
data class AssignmentRoster(val assignedUserId: Int? = null, val members: List<RosterMember> = emptyList())

@Serializable
data class RosterMember(
    val id: Int,
    val name: String,
    val photoUrl: String? = null,
    val assignable: Boolean = false,
    val needsPhoto: Boolean = false,
    val openJobs: Int = 0,
    val assigned: Boolean = false,
)

@Serializable data class AssignmentResult(val job: Job, val members: List<RosterMember> = emptyList())

/// `updateDetails` body.
@Serializable
data class DetailsBody(
    val categoryIds: List<Int>? = null,
    @Serializable(with = PhpRatesSerializer::class) val categoryRates: Map<String, Int>? = null,
    val serviceRadiusKm: Int? = null,
    val availabilityModes: List<String>? = null,
    val workingDays: List<Int>? = null,
    val jobAlertEmails: List<String>? = null,
)

object CompanyEndpoints {
    fun dashboard() = ApiRequest.get<CompanyDashboard>("api/company/dashboard")

    fun opportunities() = ApiRequest.get<OpportunitiesResponse>("api/company/opportunities")
    // Direct opportunity accept is retired server-side (410) — quoting is
    // the only path onto a job. Don't reintroduce an accept endpoint.
    fun decline(jobId: Int) = ApiRequest.empty(Method.POST, "api/company/opportunities/$jobId/decline")
    fun quote(jobId: Int, body: QuoteBody) = ApiRequest.json<QuoteResponse, QuoteBody>(Method.POST, "api/company/opportunities/$jobId/quote", body, coder = JsonCoding.explicitNulls)

    fun quotes() = ApiRequest.get<CompanyQuotesResponse>("api/company/quotes")
    fun updateQuote(id: Int, body: QuoteBody) = ApiRequest.json<QuoteResponse, QuoteBody>(Method.PATCH, "api/company/quotes/$id", body, coder = JsonCoding.explicitNulls)
    fun withdrawQuote(id: Int) = ApiRequest.empty(Method.DELETE, "api/company/quotes/$id")

    fun jobs() = ApiRequest.get<JobsResponse>("api/company/jobs")
    fun job(id: Int) = ApiRequest.get<JobResponse>("api/company/jobs/$id")
    /// Advances to the NEXT stage server-side — no body.
    fun advance(jobId: Int) = ApiRequest.post<JobResponse>("api/company/jobs/$jobId/advance")
    fun assignment(jobId: Int) = ApiRequest.get<AssignmentRoster>("api/company/jobs/$jobId/assignment")
    fun assign(jobId: Int, userId: Int) = ApiRequest.post<AssignmentResult, Map<String, Int>>("api/company/jobs/$jobId/assignment", mapOf("user_id" to userId))
    fun withdraw(jobId: Int) = ApiRequest.post<WithdrawnResponse>("api/company/jobs/$jobId/withdraw")
    fun reportLocation(jobId: Int, lat: Double, lng: Double) = ApiRequest.empty(Method.POST, "api/company/jobs/$jobId/location", mapOf("lat" to lat, "lng" to lng))
    fun invoiceJob(jobId: Int) = ApiRequest.post<InvoiceResponse>("api/company/jobs/$jobId/invoice")
    fun changeOrder(jobId: Int, label: String, amountCents: Int, materials: Boolean = false) =
        // `materials` = pass-through at cost: excluded from the commission base (Commission::stamp).
        ApiRequest.jsonElement<ChangeOrderResponse>(Method.POST, "api/company/jobs/$jobId/change-orders", buildJsonObject { put("label", label); put("amount_cents", amountCents); put("materials", materials) })
    fun report(jobId: Int, kind: String, body: String) = ApiRequest.post<DisputeResponse, Map<String, String>>("api/company/jobs/$jobId/report", mapOf("kind" to kind, "body" to body))
    fun conversation(jobId: Int) = ApiRequest.get<ConversationResponse>("api/company/jobs/$jobId/conversation")
    fun sendMessage(jobId: Int, body: String) = ApiRequest.post<MessageResponse, Map<String, String>>("api/company/jobs/$jobId/conversation/messages", mapOf("body" to body))
    fun blockUser(jobId: Int, userId: Int) = ApiRequest.post<BlockResponse, Map<String, Int>>("api/company/jobs/$jobId/conversation/block", mapOf("user_id" to userId))
    fun unblockUser(jobId: Int, userId: Int) = ApiRequest.delete<BlockResponse>("api/company/jobs/$jobId/conversation/block/$userId")
    fun location(jobId: Int) = ApiRequest.get<JobLocation>("api/company/jobs/$jobId/location")
    /// `kind` is `before` or `after` (evidence photos).
    fun uploadPhotos(jobId: Int, kind: String, photos: List<ByteArray>): ApiRequest<PhotosResponse> {
        val form = MultipartForm()
        form.addField("kind", kind)
        photos.forEachIndexed { i, data -> form.addFile("photos[]", "photo-$i.jpg", "image/jpeg", data) }
        return ApiRequest.multipart("api/company/jobs/$jobId/photos", form)
    }
    fun deletePhoto(id: Int) = ApiRequest.empty(Method.DELETE, "api/company/photos/$id")

    fun invoices() = ApiRequest.get<InvoicesResponse>("api/company/invoices")
    fun invoice(id: Int) = ApiRequest.get<InvoiceResponse>("api/company/invoices/$id")
    fun payouts() = ApiRequest.get<PayoutsResponse>("api/company/payouts")
    fun stripeOnboardingLink() = ApiRequest.post<StripeLink>("api/company/stripe/onboarding-link")
    fun refreshStripeStatus() = ApiRequest.post<PayoutsReady>("api/company/stripe/refresh-status")

    fun availability() = ApiRequest.get<AvailabilityResponse>("api/company/availability")
    fun blockDate(date: String) = ApiRequest.post<BlockedDateResponse, Map<String, String>>("api/company/availability/blocked-dates", mapOf("date" to date))
    fun unblockDate(id: Int) = ApiRequest.empty(Method.DELETE, "api/company/availability/blocked-dates/$id")
    fun monthCalendar(month: String? = null) = ApiRequest.get<CompanyCalendar>("api/company/calendar", month?.let { listOf("month" to it) } ?: emptyList())

    fun passport() = ApiRequest.get<PassportResponse>("api/company/passport")
    fun organization() = ApiRequest.get<CompanyOrganizationResponse>("api/company/organization")
    fun updateOrganization(body: Map<String, String?>) = ApiRequest.json<CompanyOrganizationResponse, Map<String, String?>>(Method.PATCH, "api/company/organization", body, coder = JsonCoding.explicitNulls)
    fun submitOrganization() = ApiRequest.post<CompanyOrganizationResponse>("api/company/organization/submit")
    fun updateDetails(body: DetailsBody) = ApiRequest.patch<CompanyOrganizationResponse, DetailsBody>("api/company/organization/details", body)
    fun uploadCredential(kind: String, fileName: String, mimeType: String, data: ByteArray, expiresAt: String?): ApiRequest<Unit> {
        val form = MultipartForm()
        form.addField("kind", kind)
        if (expiresAt != null) form.addField("expires_at", expiresAt)
        form.addFile("file", fileName, mimeType, data)
        return ApiRequest.empty(Method.POST, "api/company/credentials", form.body(), form.contentType)
    }

    /// The uploaded document's bytes (`Storage::download` stream) — the
    /// pro checking what they submitted.
    fun downloadCredential(documentId: Int) = ApiRequest.bytes("api/company/credentials/$documentId/download")

    /// The member's own profile photo — required before they can be
    /// assigned jobs (bookers see the face).
    fun uploadProfilePhoto(jpeg: ByteArray): ApiRequest<ProfilePhotoResponse> {
        val form = MultipartForm()
        form.addFile("photo", "photo.jpg", "image/jpeg", jpeg)
        return ApiRequest.multipart("api/company/profile/photo", form)
    }
    fun deleteProfilePhoto() = ApiRequest.delete<ProfilePhotoResponse>("api/company/profile/photo")

    /// Admin-on-behalf teammate photo (`storeForMember`) — how a
    /// dispatcher unblocks a "needs photo" member from the roster.
    fun uploadMemberPhoto(userId: Int, jpeg: ByteArray): ApiRequest<ProfilePhotoResponse> {
        val form = MultipartForm()
        form.addFile("photo", "photo.jpg", "image/jpeg", jpeg)
        return ApiRequest.multipart("api/company/team/$userId/photo", form)
    }

    fun uploadLogo(jpeg: ByteArray): ApiRequest<LogoResponse> {
        // The server's multipart field is `file` (OrganizationLogoController).
        val form = MultipartForm()
        form.addFile("file", "logo.jpg", "image/jpeg", jpeg)
        return ApiRequest.multipart("api/company/organization/logo", form)
    }
    fun deleteLogo() = ApiRequest.delete<LogoResponse>("api/company/organization/logo")

    fun subscription() = ApiRequest.get<SubscriptionResponse>("api/company/subscription")
    fun updateSubscription(planId: Int, interval: String) = ApiRequest.jsonElement<SubscriptionResponse>(Method.POST, "api/company/subscription", buildJsonObject { put("plan_id", planId); put("interval", interval) })
    fun cancelPendingSubscription() = ApiRequest.delete<SubscriptionResponse>("api/company/subscription/pending")

    /// The ORG's billing card (distinct from the booker card): the one
    /// subscriptions charge. Same response shape as BillingContext.
    fun orgBillingContext() = ApiRequest.post<BillingContext>("api/company/billing/setup-intent")
    fun saveOrgBillingCard(setupIntentId: String) = ApiRequest.post<SavedCardResponse, Map<String, String>>("api/company/billing/card", mapOf("stripe_setup_intent_id" to setupIntentId))

    fun referral() = ApiRequest.get<CompanyReferral>("api/company/referral")
    fun profile() = ApiRequest.get<CustomerProfile>("api/company/profile")
    fun updateProfile(body: UpdateProfileBody) = ApiRequest.patch<CustomerProfile, UpdateProfileBody>("api/company/profile", body)
    fun updatePreferences(email: Map<String, Boolean>?, sms: Boolean?) = ApiRequest.patch<CustomerProfile, PreferencesBody>("api/company/profile/notifications", PreferencesBody(email, sms))

    fun team() = ApiRequest.get<Team>("api/company/team")
    fun invite(email: String) = ApiRequest.empty(Method.POST, "api/company/team/invitations", mapOf("email" to email))
    fun revoke(invitationId: Int) = ApiRequest.empty(Method.DELETE, "api/company/team/invitations/$invitationId")
    fun updateMember(id: Int, firstName: String, lastName: String, role: String, status: String) = ApiRequest.patch<MemberResponse, Map<String, String>>("api/company/team/members/$id", mapOf("first_name" to firstName, "last_name" to lastName, "role" to role, "status" to status))
}
