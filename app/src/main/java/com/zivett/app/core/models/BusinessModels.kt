@file:UseSerializers(LaravelInstantSerializer::class)

package com.zivett.app.core.models

import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.ApiRequest.Method
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.network.LaravelInstantSerializer
import com.zivett.app.core.network.MultipartForm
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/// A managed property (`PropertyResource`).
@Serializable
data class Property(
    val id: Int,
    val name: String,
    val kind: String? = null,
    val address: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val pinAdjustedAt: Instant? = null,
    val openJobs: Int? = null,
    val jobsCount: Int? = null,
    val createdAt: Instant? = null,
)

/// Setup gate for a business org: profile + at least one property.
@Serializable
data class BusinessSetup(val status: String, val setupCompletedAt: Instant? = null, val steps: Steps) {
    @Serializable
    // The server's `plan` step is deliberately NOT modelled: the mobile
    // wizard has no plan step (store policy — see MembershipScreen).
    data class Steps(val profile: Profile, val properties: Properties) {
        @Serializable data class Profile(val complete: Boolean = false, val missing: List<String>? = null)
        @Serializable data class Properties(val complete: Boolean = false, val count: Int? = null)
    }

    val isComplete: Boolean get() = status == "complete"
}

/// `GET /api/business/dashboard`.
@Serializable
data class BusinessDashboard(val stats: Stats, val properties: List<Property> = emptyList(), val requests: List<Job> = emptyList(), val setup: BusinessSetup) {
    @Serializable data class Stats(val properties: Int = 0, val openRequests: Int = 0, val spendMonth: Int = 0, val jobsYtd: Int = 0)
}

/// Shared organization profile shape (company includes approval fields,
/// business includes setup_completed_at — both decode here).
@Serializable
data class OrganizationProfile(
    val id: Int,
    val name: String,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val city: String? = null,
    val region: String? = null,
    val postalCode: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val phone: String? = null,
    val website: String? = null,
    val socialX: String? = null,
    val socialInstagram: String? = null,
    val socialFacebook: String? = null,
    val gstNumber: String? = null,
    val about: String? = null,
    /// Business orgs only — a `BusinessIndustry` enum value.
    val industry: String? = null,
    val logoUrl: String? = null,
    val approvedAt: Instant? = null,
    val submittedAt: Instant? = null,
    val setupCompletedAt: Instant? = null,
)

@Serializable
data class BusinessOrganizationResponse(
    val organization: OrganizationProfile,
    val setup: BusinessSetup,
    /// The industry picker's choices — server-owned so every client
    /// shows the same list.
    val industryOptions: List<IndustryOption>? = null,
)

@Serializable data class IndustryOption(val value: String, val label: String)

/// `GET /api/{company|business}/team` (shared TeamController).
@Serializable
data class Team(val organization: OrgRef, val canManage: Boolean = false, val members: List<Member> = emptyList(), val invitations: List<Invitation> = emptyList()) {
    @Serializable data class OrgRef(val id: Int, val name: String, val type: OrganizationType)
    @Serializable
    data class Member(
        val id: Int,
        val firstName: String = "",
        val lastName: String = "",
        /// Derived full name — read-only; edits go through first/last.
        val name: String,
        val email: String,
        val role: OrganizationRole,
        val status: String,
        val joinedAt: Instant? = null,
        /// The assigned-tech face (company orgs; null for business).
        /// A photo-less company member can't be assigned jobs.
        val photoUrl: String? = null,
        val hasPhoto: Boolean? = null,
    )
    @Serializable
    data class Invitation(val id: Int, val email: String, val invitedBy: String? = null, val expiresAt: Instant? = null, val status: String)
}

@Serializable data class PropertiesResponse(val properties: List<Property> = emptyList())
@Serializable data class PropertyResponse(val property: Property)

@Serializable
data class PropertyBody(
    val name: String,
    val kind: String? = null,
    val address: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val pinAdjusted: Boolean? = null,
)

@Serializable data class MemberResponse(val member: Team.Member)
@Serializable data class LogoResponse(val logoUrl: String? = null)

object BusinessEndpoints {
    fun dashboard() = ApiRequest.get<BusinessDashboard>("api/business/dashboard")
    fun organization() = ApiRequest.get<BusinessOrganizationResponse>("api/business/organization")
    fun uploadLogo(jpeg: ByteArray): ApiRequest<LogoResponse> {
        val form = MultipartForm()
        form.addFile("file", "logo.jpg", "image/jpeg", jpeg)
        return ApiRequest.multipart("api/business/organization/logo", form)
    }
    fun deleteLogo() = ApiRequest.delete<LogoResponse>("api/business/organization/logo")
    fun updateOrganization(body: Map<String, String?>) = ApiRequest.json<BusinessOrganizationResponse, Map<String, String?>>(Method.PATCH, "api/business/organization", body, coder = JsonCoding.explicitNulls)
    fun completeSetup() = ApiRequest.post<BusinessOrganizationResponse>("api/business/organization/setup/complete")

    // The business membership — the same contract machine (and response
    // shape) as the company tiers, business shelf.
    // Read-only in the app, like CompanyEndpoints.subscription().
    fun subscription() = ApiRequest.get<SubscriptionResponse>("api/business/subscription")

    fun properties() = ApiRequest.get<PropertiesResponse>("api/business/properties")
    fun createProperty(body: PropertyBody) = ApiRequest.post<PropertyResponse, PropertyBody>("api/business/properties", body)
    fun updateProperty(id: Int, body: PropertyBody) = ApiRequest.patch<PropertyResponse, PropertyBody>("api/business/properties/$id", body)
    fun deleteProperty(id: Int) = ApiRequest.empty(Method.DELETE, "api/business/properties/$id")

    // The request lifecycle mirrors the customer's job endpoints under
    // the business prefix — same payloads, same responses.
    fun requests() = ApiRequest.get<JobsResponse>("api/business/requests")
    fun request(id: Int) = ApiRequest.get<JobResponse>("api/business/requests/$id")
    fun bookingOptions() = ApiRequest.get<BookingOptions>("api/business/booking-options")
    fun book(body: BookJobBody) = ApiRequest.jsonElement<JobResponse>(Method.POST, "api/business/requests", body.toJson())
    fun cancel(jobId: Int) = ApiRequest.post<JobResponse>("api/business/requests/$jobId/cancel")
    fun close(jobId: Int, tipCents: Int) = ApiRequest.post<JobResponse, Map<String, Int>>("api/business/requests/$jobId/close", mapOf("tip_cents" to tipCents))
    fun acceptQuote(jobId: Int, quoteId: Int, body: AcceptQuoteBody) = ApiRequest.post<JobResponse, AcceptQuoteBody>("api/business/requests/$jobId/quotes/$quoteId/accept", body)
    fun approveChangeOrder(jobId: Int, changeOrderId: Int) = ApiRequest.post<ChangeOrderResponse>("api/business/requests/$jobId/change-orders/$changeOrderId/approve")
    fun declineChangeOrder(jobId: Int, changeOrderId: Int) = ApiRequest.post<ChangeOrderResponse>("api/business/requests/$jobId/change-orders/$changeOrderId/decline")
    fun review(jobId: Int, rating: Int, comment: String?) = ApiRequest.post<ReviewResponse, ReviewBody>("api/business/requests/$jobId/review", ReviewBody(rating, comment))
    fun updateReview(reviewId: Int, rating: Int, comment: String?) = ApiRequest.patch<ReviewResponse, ReviewBody>("api/business/reviews/$reviewId", ReviewBody(rating, comment))
    fun warrantyClaim(jobId: Int, body: String) = ApiRequest.post<DisputeResponse, Map<String, String>>("api/business/requests/$jobId/claim", mapOf("body" to body))
    fun report(jobId: Int, kind: String, body: String) = ApiRequest.post<DisputeResponse, Map<String, String>>("api/business/requests/$jobId/report", mapOf("kind" to kind, "body" to body))
    fun conversation(jobId: Int) = ApiRequest.get<ConversationResponse>("api/business/requests/$jobId/conversation")
    fun sendMessage(jobId: Int, body: String) = ApiRequest.post<MessageResponse, Map<String, String>>("api/business/requests/$jobId/conversation/messages", mapOf("body" to body))
    fun blockUser(jobId: Int, userId: Int) = ApiRequest.post<BlockResponse, Map<String, Int>>("api/business/requests/$jobId/conversation/block", mapOf("user_id" to userId))
    fun unblockUser(jobId: Int, userId: Int) = ApiRequest.delete<BlockResponse>("api/business/requests/$jobId/conversation/block/$userId")
    fun location(jobId: Int) = ApiRequest.get<JobLocation>("api/business/requests/$jobId/location")
    fun uploadPhotos(jobId: Int, photos: List<ByteArray>): ApiRequest<PhotosResponse> {
        val form = MultipartForm()
        photos.forEachIndexed { i, data -> form.addFile("photos[]", "photo-$i.jpg", "image/jpeg", data) }
        return ApiRequest.multipart("api/business/requests/$jobId/photos", form)
    }
    fun deletePhoto(id: Int) = ApiRequest.empty(Method.DELETE, "api/business/photos/$id")

    fun invoices() = ApiRequest.get<InvoicesResponse>("api/business/invoices")
    fun invoice(id: Int) = ApiRequest.get<InvoiceResponse>("api/business/invoices/$id")
    fun pay(invoiceId: Int, couponCode: String?, tipCents: Int) = ApiRequest.post<InvoiceResponse, PayBody>("api/business/invoices/$invoiceId/pay", PayBody(couponCode, tipCents))
    fun warranties() = ApiRequest.get<JobsResponse>("api/business/warranties")

    fun profile() = ApiRequest.get<CustomerProfile>("api/business/profile")
    fun updateProfile(body: UpdateProfileBody) = ApiRequest.patch<CustomerProfile, UpdateProfileBody>("api/business/profile", body)
    fun updatePreferences(email: Map<String, Boolean>?, sms: Boolean?) = ApiRequest.patch<CustomerProfile, PreferencesBody>("api/business/profile/notifications", PreferencesBody(email, sms))

    fun team() = ApiRequest.get<Team>("api/business/team")
    fun invite(email: String) = ApiRequest.empty(Method.POST, "api/business/team/invitations", mapOf("email" to email))
    fun revoke(invitationId: Int) = ApiRequest.empty(Method.DELETE, "api/business/team/invitations/$invitationId")
    fun updateMember(id: Int, firstName: String, lastName: String, role: String, status: String) = ApiRequest.patch<MemberResponse, Map<String, String>>("api/business/team/members/$id", mapOf("first_name" to firstName, "last_name" to lastName, "role" to role, "status" to status))
}
