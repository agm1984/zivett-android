package com.zivett.app

import com.zivett.app.core.auth.Access
import com.zivett.app.core.auth.can
import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.models.OrganizationType
import com.zivett.app.core.models.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AccessTest {
    @Test fun verifiedCustomerLandsOnTheCustomerShell() {
        assertEquals(Access.Destination.Customer, Access.destination(Fixtures.user()))
    }

    @Test fun unverifiedAccountsAreHeldAtTheCodeScreen() {
        assertEquals(Access.Destination.VerifyEmail, Access.destination(Fixtures.user(verified = false)))
        assertEquals(Access.Destination.VerifyEmail, Access.destination(Fixtures.user(role = UserRole.COMPANY, verified = false, organization = Fixtures.organization())))
    }

    @Test fun companyApprovalIsCarriedToTheShell() {
        assertEquals(Access.Destination.Company(true), Access.destination(Fixtures.user(role = UserRole.COMPANY, organization = Fixtures.organization(approved = true))))
        assertEquals(Access.Destination.Company(false), Access.destination(Fixtures.user(role = UserRole.COMPANY, organization = Fixtures.organization(approved = false))))
    }

    @Test fun businessNeedsAnOrganization() {
        assertEquals(Access.Destination.Business, Access.destination(Fixtures.user(role = UserRole.BUSINESS, organization = Fixtures.organization(type = OrganizationType.BUSINESS))))
        assertEquals(Access.Destination.MissingOrganization, Access.destination(Fixtures.user(role = UserRole.BUSINESS, organization = null)))
    }

    @Test fun suspensionWinsOverEverythingElse() {
        val personal = Fixtures.user(role = UserRole.COMPANY, verified = false, organization = Fixtures.organization(), suspendedAt = Instant.now())
        assertEquals(Access.Destination.Suspended("This account has been suspended. Contact support."), Access.destination(personal))
        val organization = Fixtures.user(role = UserRole.COMPANY, organization = Fixtures.organization(suspended = true))
        assertEquals(Access.Destination.Suspended("This organization has been suspended. Contact support."), Access.destination(organization))
    }

    @Test fun deactivatedMembersAreBlocked() {
        assertEquals(Access.Destination.Deactivated, Access.destination(Fixtures.user(role = UserRole.COMPANY, organization = Fixtures.organization(), deactivatedAt = Instant.now())))
    }

    @Test fun adminsAreUnsupported() {
        assertEquals(Access.Destination.Unsupported(UserRole.ADMIN), Access.destination(Fixtures.user(role = UserRole.ADMIN)))
    }

    @Test fun bookingRequiresAVerifiedBooker() {
        assertTrue(Fixtures.user().can(Access.Capability.BOOK_JOB))
        assertFalse(Fixtures.user(verified = false).can(Access.Capability.BOOK_JOB))
        assertTrue(Fixtures.user(role = UserRole.BUSINESS, organization = Fixtures.organization(type = OrganizationType.BUSINESS)).can(Access.Capability.BOOK_JOB))
        assertFalse(Fixtures.user(role = UserRole.COMPANY, organization = Fixtures.organization()).can(Access.Capability.BOOK_JOB))
    }

    @Test fun marketplaceRequiresAnApprovedCompany() {
        assertTrue(Fixtures.user(role = UserRole.COMPANY, organization = Fixtures.organization(approved = true)).can(Access.Capability.COMPANY_MARKETPLACE))
        assertFalse(Fixtures.user(role = UserRole.COMPANY, organization = Fixtures.organization(approved = false)).can(Access.Capability.COMPANY_MARKETPLACE))
        assertFalse(Fixtures.user().can(Access.Capability.COMPANY_MARKETPLACE))
    }

    @Test fun teamManagementIsForOrganizationAdmins() {
        assertTrue(Fixtures.user(role = UserRole.COMPANY, organization = Fixtures.organization(), organizationRole = OrganizationRole.ADMIN).can(Access.Capability.MANAGE_TEAM))
        assertFalse(Fixtures.user(role = UserRole.COMPANY, organization = Fixtures.organization(), organizationRole = OrganizationRole.MEMBER).can(Access.Capability.MANAGE_TEAM))
        assertFalse(Fixtures.user().can(Access.Capability.MANAGE_TEAM))
    }

    @Test fun blockedAccountsHaveNoCapabilities() {
        assertFalse(Fixtures.user(suspendedAt = Instant.now()).can(Access.Capability.BOOK_JOB))
    }
}
