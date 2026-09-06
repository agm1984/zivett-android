package com.zivett.app.core.auth

import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.models.User
import com.zivett.app.core.models.UserRole

/// Client-side mirror of the backend's authorization rules
/// (`EnsureUserHasRole`, `EnsureUserHasOrganization`,
/// `EnsureCompanyIsApproved`, the `verified` route middleware). The
/// server is still the authority — these exist so the app can route to
/// the right screen *before* a request 403s, and so the rules are unit
/// testable without a network. Ported 1:1 from the iOS `Access.swift`.
object Access {
    /// Where a signed-in user lands. Evaluated in the same order the
    /// backend middleware runs, so the first blocking condition wins.
    sealed interface Destination {
        /// `suspended_at` on the user or their organization.
        data class Suspended(val reason: String) : Destination
        /// `deactivated_at` — removed from their organization.
        object Deactivated : Destination
        /// A company/business user whose organization is gone.
        object MissingOrganization : Destination
        /// Booking is the one verified-only action; the app holds
        /// unverified accounts on the code screen like the SPA does.
        object VerifyEmail : Destination
        object Customer : Destination
        object Business : Destination
        data class Company(val approved: Boolean) : Destination
        /// Admins have no native surface.
        data class Unsupported(val role: UserRole) : Destination
    }

    fun destination(user: User): Destination {
        if (user.suspendedAt != null) return Destination.Suspended("This account has been suspended. Contact support.")
        user.organization?.let { if (it.isSuspended) return Destination.Suspended("This organization has been suspended. Contact support.") }
        if (user.deactivatedAt != null) return Destination.Deactivated

        return when (user.role) {
            UserRole.CUSTOMER -> if (user.isEmailVerified) Destination.Customer else Destination.VerifyEmail
            UserRole.BUSINESS -> {
                if (user.organization == null) return Destination.MissingOrganization
                if (user.isEmailVerified) Destination.Business else Destination.VerifyEmail
            }
            UserRole.COMPANY -> {
                val organization = user.organization ?: return Destination.MissingOrganization
                if (user.isEmailVerified) Destination.Company(organization.isApproved) else Destination.VerifyEmail
            }
            UserRole.ADMIN -> Destination.Unsupported(UserRole.ADMIN)
        }
    }

    /// Finer-grained checks for buttons and tabs inside a shell.
    enum class Capability {
        /// `POST /customer/jobs` and `POST /business/requests` (`verified`).
        BOOK_JOB,
        /// The company job market (`company.approved`).
        COMPANY_MARKETPLACE,
        /// Team management is admin-of-org only.
        MANAGE_TEAM,
    }

    fun can(user: User, capability: Capability): Boolean {
        if (!isLive(destination(user))) return false

        return when (capability) {
            Capability.BOOK_JOB -> (user.role == UserRole.CUSTOMER || user.role == UserRole.BUSINESS) && user.isEmailVerified
            Capability.COMPANY_MARKETPLACE -> user.role == UserRole.COMPANY && user.organization?.isApproved == true
            Capability.MANAGE_TEAM -> user.organization != null && user.organizationRole == OrganizationRole.ADMIN
        }
    }

    private fun isLive(destination: Destination): Boolean = when (destination) {
        Destination.Customer, Destination.Business, is Destination.Company -> true
        else -> false
    }
}

fun User.can(capability: Access.Capability): Boolean = Access.can(this, capability)
