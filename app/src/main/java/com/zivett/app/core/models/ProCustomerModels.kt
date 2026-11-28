@file:UseSerializers(LaravelInstantSerializer::class)

package com.zivett.app.core.models

import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.ApiRequest.Method
import com.zivett.app.core.network.LaravelInstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

/// A company's own customers (Business tools slice 2,
/// `Company\BusinessTools\CustomerController`).

/// `CustomerController::row`.
@Serializable
data class ProCustomerRow(
    val id: Int,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val source: String? = null,
    val sourceLabel: String? = null,
    /// The first active address, one line.
    val address: String? = null,
    val addressesCount: Int = 0,
    val archived: Boolean = false,
    val balanceCents: Int = 0,
    val createdAt: Instant? = null,
)

@Serializable
data class LabelValue(val value: String, val label: String)

/// `GET api/company/business/customers`.
@Serializable
data class ProCustomersResponse(
    val customers: List<ProCustomerRow> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false,
    val counts: Counts = Counts(),
    val allowance: Allowance = Allowance(),
    val sources: List<LabelValue> = emptyList(),
    val exceptionRequests: List<ExceptionRequest> = emptyList(),
) {
    @Serializable
    data class Counts(val active: Int = 0, val archived: Int = 0)

    /// The plan's customer limit (null = unlimited).
    @Serializable
    data class Allowance(val limit: Int? = null, val used: Int = 0, val upgrade: PlanOffer? = null) {
        val atLimit: Boolean get() = limit != null && used >= limit
    }
}

/// `BusinessTools::upgradeOffer` — the plan that lifts a limit. The app
/// names it and opens the Plan screen; prices come from Google Play there.
@Serializable
data class PlanOffer(val key: String? = null, val name: String)

/// "This was my customer first" (`ProCustomerExceptions::forCompany`).
@Serializable
data class ExceptionRequest(
    val id: Int,
    val customerName: String = "",
    /// Set once approved: the customer it created.
    val customerId: Int? = null,
    val jobCode: String? = null,
    /// pending | approved | declined
    val status: String = "pending",
    val decisionNote: String? = null,
    val createdAt: Instant? = null,
    val decidedAt: Instant? = null,
)

@Serializable
data class ExceptionRequestsResponse(val exceptionRequests: List<ExceptionRequest> = emptyList())

/// `CustomerController::detail`.
@Serializable
data class ProCustomer(
    val id: Int,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val source: String? = null,
    val sourceLabel: String? = null,
    val archived: Boolean = false,
    val balanceCents: Int = 0,
    val createdAt: Instant? = null,
    val notes: String? = null,
    val noReminders: Boolean = false,
    val unsubscribed: Boolean = false,
    val marketing: Marketing? = null,
    val campaigns: List<CampaignSend> = emptyList(),
    /// Locked once they've been sent an invoice (support can change it).
    val sourceLocked: Boolean = false,
    /// Deleting keeps the record (anonymised) once anything went out.
    val deleteAnonymises: Boolean = false,
    val addresses: List<Address> = emptyList(),
    val history: History = History(),
    val protectionMonths: Int? = null,
) {
    val firstName: String get() = name.trim().split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: name
    val activeAddresses: List<Address> get() = addresses.filter { !it.archived }

    @Serializable
    data class Address(
        val id: Int,
        val label: String? = null,
        val addressLine1: String? = null,
        val addressLine2: String? = null,
        val city: String? = null,
        val region: String? = null,
        val postalCode: String? = null,
        val lat: Double? = null,
        val lng: Double? = null,
        val oneLine: String = "",
        val archived: Boolean = false,
    )

    /// `ProCampaigns::status`: getting the company's campaigns or not, and why.
    @Serializable
    data class Marketing(
        val receives: Boolean = false,
        /// express | implied | null
        val basis: String? = null,
        /// opted_in | recent | unsubscribed | not_recent | no_email
        val reason: String? = null,
        val since: Instant? = null,
        /// request_form | link | company (opt-in), or who unsubscribed them.
        val source: String? = null,
        val impliedUntil: String? = null,
        val canEmail: Boolean = false,
    )

    /// `ProCampaigns::customerHistory`.
    @Serializable
    data class CampaignSend(
        val campaignId: Int,
        val subject: String? = null,
        val sentAt: Instant? = null,
        /// failed | bounced | complained | reported | null (it went out)
        val problem: String? = null,
        val hadButton: Boolean = false,
        val clickedAt: Instant? = null,
        val booked: Booked? = null,
    ) {
        @Serializable
        data class Booked(val jobId: Int, val code: String? = null, val bookedAt: Instant? = null)
    }

    @Serializable
    data class History(
        val requests: List<RequestItem> = emptyList(),
        val quotes: List<ProDocumentRow> = emptyList(),
        val jobs: List<ProJobRow> = emptyList(),
        val invoices: List<ProDocumentRow> = emptyList(),
        val payments: List<Payment> = emptyList(),
    )

    @Serializable
    data class RequestItem(val id: Int, val message: String? = null, val trade: String? = null, val timing: String? = null, val address: String? = null, val createdAt: Instant? = null)

    @Serializable
    data class Payment(val id: Int, val invoiceId: Int, val invoiceNumber: String? = null, val amountCents: Int = 0, val paidOn: String? = null, val methodLabel: String? = null)
}

@Serializable
data class ProCustomerResponse(val customer: ProCustomer)

/// The add / edit form. An address (create only) goes as ONE line — the
/// server splits it (`ProCustomerRequest::expandOneLine`).
@Serializable
data class ProCustomerBody(
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val source: String? = null,
    val notes: String? = null,
    val noReminders: Boolean = false,
    val address: OwnAddressBody? = null,
    val confirmDifferentPerson: Boolean? = null,
)

@Serializable
data class OwnAddressBody(
    val address: String,
    val addressLine2: String? = null,
    val label: String? = null,
    val confirmDifferentPerson: Boolean? = null,
)

/// "This was my customer first": the details typed plus why.
@Serializable
data class ExceptionRequestBody(
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val source: String? = null,
    val notes: String? = null,
    val address: OwnAddressBody? = null,
    val reason: String,
    val customerId: Int? = null,
)

@Serializable
data class MarketingBody(val optedIn: Boolean, val confirmed: Boolean? = null)

@Serializable
data class ArchivedBody(val archived: Boolean)

/// The refusals the form tells apart: 422 `zivett_customer` (blocked),
/// 409 `confirm_different_person`, 422 `limit_reached`.
@Serializable
data class CustomerRefusal(val message: String? = null, val code: String? = null, val jobCode: String? = null)

object ProCustomerEndpoints {
    private const val BASE = "api/company/business/customers"

    fun list(status: String, q: String?, page: Int = 1) = ApiRequest.get<ProCustomersResponse>(
        BASE, listOfNotNull("status" to status, q?.takeIf { it.isNotBlank() }?.let { "q" to it }, "page" to page.toString()),
    )
    fun show(id: Int) = ApiRequest.get<ProCustomerResponse>("$BASE/$id")
    fun create(body: ProCustomerBody) = ApiRequest.post<ProCustomerResponse, ProCustomerBody>(BASE, body)
    /// Contact details only (addresses have their own endpoints).
    fun update(id: Int, body: ProCustomerBody) = ApiRequest.patch<ProCustomerResponse, ProCustomerBody>("$BASE/$id", body.copy(address = null))
    fun marketing(id: Int, body: MarketingBody) = ApiRequest.post<ProCustomerResponse, MarketingBody>("$BASE/$id/marketing", body)
    fun archive(id: Int, archived: Boolean) = ApiRequest.post<ProCustomerResponse, ArchivedBody>("$BASE/$id/archive", ArchivedBody(archived))
    fun delete(id: Int) = ApiRequest.empty(Method.DELETE, "$BASE/$id")
    fun addAddress(id: Int, body: OwnAddressBody) = ApiRequest.empty(Method.POST, "$BASE/$id/addresses", body)
    fun updateAddress(id: Int, addressId: Int, body: OwnAddressBody) = ApiRequest.empty(Method.PATCH, "$BASE/$id/addresses/$addressId", body)
    fun archiveAddress(id: Int, addressId: Int) = ApiRequest.empty(Method.DELETE, "$BASE/$id/addresses/$addressId")
    fun exceptionRequest(body: ExceptionRequestBody) = ApiRequest.post<ExceptionRequestsResponse, ExceptionRequestBody>("$BASE/exception-requests", body)
}
