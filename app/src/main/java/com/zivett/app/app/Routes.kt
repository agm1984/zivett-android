package com.zivett.app.app

import kotlinx.serialization.Serializable

// Signed-out flow.
@Serializable object WelcomeRoute
@Serializable object LoginRoute
@Serializable data class SignupRoute(val role: String? = null)
@Serializable object ForgotPasswordRoute
@Serializable data class InvitationRoute(val token: String)

// Customer shell tabs.
@Serializable object CustomerHomeTab
@Serializable object CustomerJobsTab
@Serializable object CustomerBookTab
@Serializable object CustomerMessagesTab
@Serializable object CustomerAccountTab

// Business shell tabs.
@Serializable object BusinessOverviewTab
@Serializable object BusinessRequestsTab
@Serializable object BusinessBookTab
@Serializable object BusinessPropertiesTab
@Serializable object BusinessAccountTab

// Company shell tabs (three lifecycles).
@Serializable object CompanyDashboardTab
@Serializable object CompanyOpportunitiesTab
@Serializable object CompanyQuotesTab
@Serializable object CompanyJobsTab
@Serializable object CompanyMoreTab
@Serializable object CompanyPassportTab
@Serializable object CompanySetupTab
@Serializable object CompanyTeamTab
@Serializable object CompanyAccountTab

// Shared booker screens (`area` is "customer" or "business").
@Serializable data class JobDetailRoute(val jobId: Int, val area: String)
@Serializable data class ConversationRoute(val jobId: Int, val area: String, val title: String, val subtitle: String? = null, val readOnly: Boolean = false, val showJobLink: Boolean = false)
@Serializable data class InvoiceDetailRoute(val invoiceId: Int, val area: String)
@Serializable data class PayInvoiceRoute(val invoiceId: Int, val area: String)
@Serializable data class ProfileRoute(val area: String)
@Serializable data class NotificationPrefsRoute(val area: String)
@Serializable object AddressesRoute
@Serializable object ReferralRoute
@Serializable data class InvoicesRoute(val area: String)
@Serializable data class WarrantiesRoute(val area: String)
@Serializable data class NotificationsRoute(val area: String)
@Serializable data class BookRoute(val area: String, val presetMode: String? = null, val presetCategorySlug: String? = null, val presetPropertyId: Int? = null, val resume: Boolean = false)
@Serializable data class TeamRoute(val area: String)
@Serializable data class MembershipRoute(val area: String)
@Serializable data class OrgProfileRoute(val area: String)

// Business.
@Serializable object BusinessSetupRoute
@Serializable object BusinessInvoicesRoute
@Serializable object PropertiesRoute

// Company.
@Serializable data class CompanyJobRoute(val jobId: Int)
@Serializable object CompanyCalendarRoute
@Serializable object CompanyInvoicesRoute
@Serializable data class CompanyInvoiceRoute(val invoiceId: Int)
@Serializable object PayoutsRoute
@Serializable object PassportRoute
@Serializable object CompanySetupRoute
@Serializable object CredentialGuideRoute
@Serializable object OpportunitiesRoute
@Serializable object CompanyQuotesRoute

/// Area strings on routes ↔ `JobArea.Kind`.
object Areas {
    const val CUSTOMER = "customer"
    const val BUSINESS = "business"
    const val COMPANY = "company"

    fun kind(area: String): com.zivett.app.core.models.JobArea.Kind = when (area) {
        BUSINESS -> com.zivett.app.core.models.JobArea.Kind.BUSINESS
        COMPANY -> com.zivett.app.core.models.JobArea.Kind.COMPANY
        else -> com.zivett.app.core.models.JobArea.Kind.CUSTOMER
    }

    fun jobArea(area: String) = com.zivett.app.core.models.JobArea.forKind(kind(area))
    fun profileArea(area: String) = com.zivett.app.core.models.ProfileArea.forKind(kind(area))
    fun teamArea(area: String) = com.zivett.app.core.models.TeamArea.forKind(kind(area))
    fun notificationArea(area: String) = when (kind(area)) {
        com.zivett.app.core.models.JobArea.Kind.CUSTOMER -> com.zivett.app.core.models.NotificationArea.CUSTOMER
        com.zivett.app.core.models.JobArea.Kind.BUSINESS -> com.zivett.app.core.models.NotificationArea.BUSINESS
        com.zivett.app.core.models.JobArea.Kind.COMPANY -> com.zivett.app.core.models.NotificationArea.COMPANY
    }
}
