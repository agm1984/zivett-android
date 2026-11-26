@file:UseSerializers(LaravelInstantSerializer::class)

package com.zivett.app.core.models

import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.ApiRequest.Method
import com.zivett.app.core.network.LaravelInstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

/// Business tools (web docs/plans/business-tools.md): a company's own
/// customers, quotes, jobs and invoices, 0% commission. This file holds
/// the switch, the Overview and the Requests inbox; the work itself lives
/// in ProCustomerModels / ProDocumentModels / ProJobModels / BooksModels.

/// `BusinessTools::summary` — `GET/PATCH api/company/business-tools` and
/// `business_tools` on the Overview.
@Serializable
data class BusinessToolsSummary(
    val enabled: Boolean = false,
    val enabledAt: Instant? = null,
    val enabledBy: String? = null,
    val unlimited: Boolean = false,
    /// Never applied to the marketplace: the tools can't be switched off.
    val toolsOnly: Boolean = false,
    /// Companies may switch the tools themselves (else the ZiVETT team does).
    val selfServe: Boolean = false,
    val allowances: Allowances? = null,
    /// The plan's name ("Basic").
    val plan: String? = null,
) {
    @Serializable
    data class Allowances(
        /// Null = unlimited.
        val customers: Int? = null,
        val quotesPerMonth: Int? = null,
        val members: Boolean = true,
        val reminders: Boolean = true,
        val reports: Boolean = true,
        val unlimited: Boolean = false,
    )

    /// The Plan screen's card shows only when there's something to do or say.
    val visible: Boolean get() = selfServe || enabled
    val locked: Boolean get() = !selfServe || toolsOnly

    val lockedReason: String?
        get() = when {
            toolsOnly -> "Your account only uses business tools, so they stay on. Contact support to close the account."
            !selfServe -> "Switched on by the ZiVETT team for the pilot. Contact support to switch them off."
            else -> null
        }

    /// "Up to 50 customers and 10 quotes a month, for the whole team."
    /// (web `allowanceSentence`).
    val allowanceSentence: String?
        get() {
            val allowances = allowances ?: return null
            if (allowances.unlimited) return "Unlimited customers and quotes for everyone on the team (pilot)."
            fun phrase(limit: Int?, noun: String, period: String = "") = if (limit == null) "unlimited $noun" else "${com.zivett.app.core.Money.count(limit)} $noun$period"
            val who = if (allowances.members) "for the whole team" else "for company admins only"
            val lead = if (allowances.customers == null) "" else "Up to "
            val sentence = "$lead${phrase(allowances.customers, "customers")} and ${phrase(allowances.quotesPerMonth, "quotes", " a month")}, $who."
            return sentence.replaceFirstChar { it.uppercase() }
        }
}

@Serializable
data class BusinessToolsResponse(val businessTools: BusinessToolsSummary)

@Serializable
data class EnabledBody(val enabled: Boolean)

/// `GET api/company/business` — the Overview.
@Serializable
data class ToolsOverview(
    val businessTools: BusinessToolsSummary = BusinessToolsSummary(),
    val reminders: Reminders? = null,
    val stats: Stats = Stats(),
) {
    @Serializable
    data class Stats(
        val customers: Int = 0,
        val activeJobs: Int = 0,
        val pendingQuotes: Int = 0,
        val outstandingInvoices: Int = 0,
        val outstandingCents: Int = 0,
        val paidThisMonthCents: Int = 0,
    )
}

/// `ProReminders::summary` (+ `can_edit`): automatic emails to the
/// company's own customers.
@Serializable
data class Reminders(
    val allowed: Boolean = false,
    val upgrade: Upgrade? = null,
    val settings: Map<String, Boolean> = emptyMap(),
    /// Nobody has changed them: they were switched on for you.
    val defaulted: Boolean = false,
    val followUpDays: Int = 3,
    val canEdit: Boolean = false,
) {
    @Serializable
    data class Upgrade(val key: String? = null, val name: String)

    companion object {
        const val APPOINTMENT = "appointment"
        const val QUOTE_FOLLOW_UP = "quote_follow_up"
        const val INVOICE_OVERDUE = "invoice_overdue"
    }
}

@Serializable
data class RemindersResponse(val reminders: Reminders)

/// `ProRequests::row` — someone asking for work through the company's
/// own request form.
@Serializable
data class ProRequest(
    val id: Int,
    val name: String = "",
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val message: String? = null,
    val category: Category? = null,
    /// The intake snapshot, shaped like a job's (`[{prompt, type, answers}]`).
    val intakeAnswers: List<IntakeAnswer>? = null,
    val mode: String? = null,
    val urgency: String? = null,
    val availabilityWindows: List<AvailabilityWindow>? = null,
    /// Free text older requests carry.
    val timing: String? = null,
    val marketingOptIn: Boolean = false,
    /// new | added | dismissed
    val status: String = "new",
    val customerId: Int? = null,
    val existingCustomer: ExistingCustomer? = null,
    val createdAt: Instant? = null,
    val handledAt: Instant? = null,
) {
    @Serializable
    data class Category(val id: Int, val name: String, val code: String? = null, val bgColor: String? = null, val fgColor: String? = null, val icon: String? = null)

    @Serializable
    data class ExistingCustomer(val id: Int, val name: String, val archived: Boolean = false)

    @Serializable
    data class IntakeAnswer(val prompt: String = "", val type: String? = null, val answers: List<String> = emptyList())

    @Serializable
    data class AvailabilityWindow(val date: String, val window: String)

    val isNew: Boolean get() = status == "new"
}

@Serializable
data class ProRequestsResponse(
    val requests: List<ProRequest> = emptyList(),
    val counts: Counts = Counts(),
    val form: RequestForm = RequestForm(),
) {
    @Serializable
    data class Counts(val new: Int = 0, val handled: Int = 0)
}

/// The company's public request form: its link (null = switched off).
@Serializable
data class RequestForm(val url: String? = null, val canEdit: Boolean = false)

@Serializable
data class ProRequestResponse(val request: ProRequest)

@Serializable
data class RequestFormResponse(val form: RequestForm)

@Serializable
data class CustomerIdBody(val customerId: Int)

@Serializable
data class ActionBody(val action: String)

object ToolsEndpoints {
    fun switch() = ApiRequest.get<BusinessToolsResponse>("api/company/business-tools")
    fun setSwitch(enabled: Boolean) = ApiRequest.patch<BusinessToolsResponse, EnabledBody>("api/company/business-tools", EnabledBody(enabled))

    fun overview() = ApiRequest.get<ToolsOverview>("api/company/business")
    /// Any subset of the three reminder kinds.
    fun setReminder(kind: String, on: Boolean) = ApiRequest.jsonElement<RemindersResponse>(
        Method.PATCH, "api/company/business/reminders",
        kotlinx.serialization.json.buildJsonObject { put(kind, kotlinx.serialization.json.JsonPrimitive(on)) },
    )

    /// `status`: new | handled.
    fun requests(status: String) = ApiRequest.get<ProRequestsResponse>("api/company/business/requests", listOf("status" to status))
    fun requestAdded(id: Int, customerId: Int) = ApiRequest.post<ProRequestResponse, CustomerIdBody>("api/company/business/requests/$id/added", CustomerIdBody(customerId))
    fun dismissRequest(id: Int) = ApiRequest.post<ProRequestResponse>("api/company/business/requests/$id/dismiss")
    /// enable | new_link | disable (org admins).
    fun requestForm(action: String) = ApiRequest.post<RequestFormResponse, ActionBody>("api/company/business/requests/form", ActionBody(action))
}

/// `GET/PATCH api/company/business/settings`.
@Serializable
data class ToolsSettings(
    val gstNumber: String? = null,
    val paymentInstructions: String? = null,
    val invoiceNumbering: InvoiceNumbering = InvoiceNumbering(),
    /// "#1f6f5c" or null (charcoal).
    val brandColor: String? = null,
)

@Serializable data class ToolsSettingsResponse(val settings: ToolsSettings)

/// Only the keys sent change (explicit nulls clear GST / instructions / colour).
@Serializable
data class ToolsSettingsBody(
    val gstNumber: String? = null,
    val paymentInstructions: String? = null,
    val invoiceNumbering: Numbering? = null,
    val brandColor: String? = null,
) {
    @Serializable data class Numbering(val mode: String, val prefix: String? = null, val nextNumber: Int? = null)
}

object ToolsSettingsEndpoints {
    fun show() = ApiRequest.get<ToolsSettingsResponse>("api/company/business/settings")
    /// `body` is a hand-built object so only the edited keys go.
    fun update(body: kotlinx.serialization.json.JsonObject) = ApiRequest.jsonElement<ToolsSettingsResponse>(Method.PATCH, "api/company/business/settings", body)
}
