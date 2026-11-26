@file:UseSerializers(LaravelInstantSerializer::class)

package com.zivett.app.core.models

import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.LaravelInstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

/// Promotional emails to the company's own customers (ProCampaigns).

/// Who gets it; null fields don't narrow (null overall = everyone eligible).
@Serializable
data class CampaignAudience(
    /// within_6 | within_12 | lapsed_6 | lapsed_12 | lapsed_24
    val lastJob: String? = null,
    val cities: List<String> = emptyList(),
    val categoryIds: List<Int> = emptyList(),
    val skipUnengaged: Boolean = false,
) {
    val narrows: Boolean get() = lastJob != null || cities.isNotEmpty() || categoryIds.isNotEmpty() || skipUnengaged
}

/// `ProCampaigns::row`.
@Serializable
data class ProCampaign(
    val id: Int,
    val template: String? = null,
    val subject: String = "",
    val heading: String = "",
    val body: String = "",
    val buttonLabel: String? = null,
    val buttonUrl: String? = null,
    /// draft | scheduled | sending | sent
    val status: String = "draft",
    val recipientsCount: Int = 0,
    val sentCount: Int = 0,
    val failedCount: Int = 0,
    val skippedCount: Int = 0,
    val sentAt: Instant? = null,
    val finishedAt: Instant? = null,
    val scheduledFor: Instant? = null,
    val audience: CampaignAudience? = null,
    val scheduleFailedReason: String? = null,
    val updatedAt: Instant? = null,
)

@Serializable
data class CampaignSummary(
    val allowed: Boolean = false,
    val upgrade: PlanOffer? = null,
    val weeklyLimit: Int = 0,
    val sentThisWeek: Int = 0,
    val dailyEmails: Int = 0,
    /// Customers who can get campaigns now.
    val audience: Int? = null,
    val blocked: String? = null,
    val relationshipYears: Int = 2,
    val inquiryMonths: Int = 6,
)

@Serializable
data class CampaignTemplate(val key: String, val name: String = "", val about: String? = null, val subject: String = "", val heading: String = "", val body: String = "", val buttonLabel: String? = null)

@Serializable
data class AudienceOptions(val cities: List<Option> = emptyList(), val categories: List<Category> = emptyList(), val lastJob: List<String> = emptyList(), val unengagedAfter: Int = 3) {
    @Serializable data class Option(val name: String, val count: Int = 0)
    @Serializable data class Category(val id: Int, val name: String, val count: Int = 0)
}

@Serializable
data class CampaignsResponse(
    val campaigns: List<ProCampaign> = emptyList(),
    val summary: CampaignSummary = CampaignSummary(),
    val templates: List<CampaignTemplate> = emptyList(),
    val audienceOptions: AudienceOptions? = null,
    val canEdit: Boolean = false,
)

@Serializable
data class CampaignResponse(
    val campaign: ProCampaign,
    /// field → what to fix before it can go.
    @Serializable(with = com.zivett.app.core.network.PhpStringMapSerializer::class) val problems: Map<String, String> = emptyMap(),
    val results: CampaignResults? = null,
    val summary: CampaignSummary? = null,
)

@Serializable
data class CampaignResults(val clicked: Int = 0, val booked: Int = 0, val bookedCents: Int = 0, val withinDays: Int = 30, val people: List<Person> = emptyList()) {
    @Serializable data class Person(val customer: OwnCustomerRef? = null, val clickedAt: Instant? = null, val booked: Booked? = null)
    @Serializable data class Booked(val jobId: Int, val code: String? = null, val title: String? = null, val bookedAt: Instant? = null, val priceCents: Int? = null)
}

@Serializable
data class CampaignBody(
    val template: String,
    val subject: String,
    val heading: String,
    val body: String,
    val buttonLabel: String? = null,
    val buttonUrl: String? = null,
    val audience: CampaignAudience? = null,
)

@Serializable data class AudienceBody(val audience: CampaignAudience?)
@Serializable data class AudienceCount(val count: Int = 0)
@Serializable data class PreviewHtml(val html: String = "")
@Serializable data class TestSent(val sentTo: String? = null)
/// `send_at` "Y-m-d H:i" on the marketplace clock (Pacific); null = now.
@Serializable data class SendBody(val consent: Boolean, val sendAt: String? = null)

object CampaignEndpoints {
    private const val BASE = "api/company/business/campaigns"
    fun list() = ApiRequest.get<CampaignsResponse>(BASE)
    fun show(id: Int) = ApiRequest.get<CampaignResponse>("$BASE/$id")
    fun create(body: CampaignBody) = ApiRequest.post<CampaignResponse, CampaignBody>(BASE, body)
    fun update(id: Int, body: CampaignBody) = ApiRequest.patch<CampaignResponse, CampaignBody>("$BASE/$id", body)
    fun delete(id: Int) = ApiRequest.empty(ApiRequest.Method.DELETE, "$BASE/$id")
    fun preview(body: CampaignBody) = ApiRequest.post<PreviewHtml, CampaignBody>("$BASE/preview", body)
    fun audience(audience: CampaignAudience?) = ApiRequest.post<AudienceCount, AudienceBody>("$BASE/audience", AudienceBody(audience))
    fun test(id: Int) = ApiRequest.post<TestSent>("$BASE/$id/test")
    fun send(id: Int, sendAt: String?) = ApiRequest.post<CampaignResponse, SendBody>("$BASE/$id/send", SendBody(true, sendAt))
    fun unschedule(id: Int) = ApiRequest.json<CampaignResponse>(ApiRequest.Method.DELETE, "$BASE/$id/schedule")
}
