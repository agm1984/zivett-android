package com.zivett.app

import com.zivett.app.core.models.BookingOptions
import com.zivett.app.core.models.CompanySummary
import com.zivett.app.core.models.CustomerAddress
import com.zivett.app.core.models.CustomerHome
import com.zivett.app.core.models.Invoice
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobCategory
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.Organization
import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.models.OrganizationType
import com.zivett.app.core.models.Quote
import com.zivett.app.core.models.User
import com.zivett.app.core.models.UserRole
import java.time.Instant

/// Canned models, the Kotlin twin of the iOS test fixtures.
object Fixtures {
    val ravensworth = CompanySummary(id = 4, name = "Ravensworth Plumbing", plan = "pro", rating = 4.8, count = 12)

    fun organization(type: OrganizationType = OrganizationType.COMPANY, approved: Boolean = true, suspended: Boolean = false) =
        Organization(id = 1, type = type, name = "Ravensworth Plumbing", approvedAt = if (approved) Instant.EPOCH else null, suspendedAt = if (suspended) Instant.EPOCH else null)

    fun user(
        role: UserRole = UserRole.CUSTOMER,
        verified: Boolean = true,
        organization: Organization? = null,
        organizationRole: OrganizationRole? = null,
        suspendedAt: Instant? = null,
        deactivatedAt: Instant? = null,
    ) = User(
        id = 2, firstName = "Amara", lastName = "Okafor", name = "Amara Okafor", email = "amara@example.test", role = role,
        organizationRole = organizationRole ?: organization?.let { OrganizationRole.ADMIN }, organization = organization,
        emailVerifiedAt = if (verified) Instant.EPOCH else null, suspendedAt = suspendedAt, deactivatedAt = deactivatedAt,
    )

    val category = JobCategory(id = 1, code = "PL", slug = "plumbing", name = "Plumbing")

    fun job(id: Int = 1, status: JobStatus = JobStatus.SUBMITTED, company: CompanySummary? = null, quotes: List<Quote>? = null, title: String = "Kitchen sink leak") =
        Job(id = id, code = "Z-TEST0$id", title = title, address = "14 Alder Court", status = status, category = category, company = company, quotes = quotes)

    fun invoice(title: String = "Dishwasher install") = Invoice(id = 1, number = "INV-1", totalCents = 12000, customerTotalCents = 13500, job = Invoice.JobSummary(id = 1, title = title))

    fun home(firstTime: Boolean = false, activeJobs: List<Job> = emptyList(), invoicesDue: List<Invoice> = emptyList()) =
        CustomerHome(firstTime = firstTime, hasSavedAddress = true, activeJobs = activeJobs, invoicesDue = invoicesDue)

    fun bookingOptions() = BookingOptions(
        categories = listOf(
            BookingOptions.Category(id = 1, code = "PL", slug = "plumbing", name = "Plumbing", emergencyFeeCents = 3000, questions = listOf(BookingOptions.Question(id = 1, prompt = "What needs attention?", type = "single", options = listOf("Faucet")))),
            BookingOptions.Category(id = 2, code = "EL", slug = "electrical", name = "Electrical", emergencyFeeCents = 0),
        ),
        modes = listOf(BookingOptions.Mode(key = "instant", tag = "Emergency", name = "Instant Dispatch"), BookingOptions.Mode(key = "quote", name = "Quote / Project")),
        warrantyDays = 2, trustFeeBps = 750, gstBps = 500, pstBps = 700,
        addresses = listOf(CustomerAddress(id = 1, label = "Home", address = "14 Alder Court, Millbrook", lat = 49.88, lng = -97.14, accessNotes = "Gate code 4471", isDefault = true)),
    )
}
