@file:UseSerializers(LaravelInstantSerializer::class)

package com.zivett.app.core.models

import com.zivett.app.core.network.LaravelInstantSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/// Mirrors `App\Enums\JobStatus`.
@Serializable
enum class JobStatus(val wire: String, val label: String) {
    @SerialName("submitted") SUBMITTED("submitted", "Submitted"),
    @SerialName("matched") MATCHED("matched", "Matched"),
    @SerialName("accepted") ACCEPTED("accepted", "Accepted"),
    @SerialName("en_route") EN_ROUTE("en_route", "On the way"),
    @SerialName("arrived") ARRIVED("arrived", "Arrived"),
    @SerialName("in_progress") IN_PROGRESS("in_progress", "In progress"),
    @SerialName("completed") COMPLETED("completed", "Completed"),
    @SerialName("invoiced") INVOICED("invoiced", "Invoiced"),
    @SerialName("warranty_active") WARRANTY_ACTIVE("warranty_active", "Under warranty"),
    @SerialName("warranty_expired") WARRANTY_EXPIRED("warranty_expired", "Warranty ended"),
    @SerialName("cancelled") CANCELLED("cancelled", "Cancelled");

    val isSettled: Boolean get() = this in settled

    companion object {
        /// `JobStatus::timeline()` — the happy path in order.
        val timeline: List<JobStatus> = listOf(SUBMITTED, MATCHED, ACCEPTED, EN_ROUTE, ARRIVED, IN_PROGRESS, COMPLETED, INVOICED, WARRANTY_ACTIVE)

        /// `JobStatus::settled()`.
        val settled: Set<JobStatus> = setOf(COMPLETED, INVOICED, WARRANTY_ACTIVE, WARRANTY_EXPIRED, CANCELLED)

        /// `JobStatus::customerCancellable()`.
        val customerCancellable: Set<JobStatus> = setOf(SUBMITTED, MATCHED, ACCEPTED, EN_ROUTE, ARRIVED, IN_PROGRESS)

        /// `JobStatus::reviewable()`.
        val reviewable: Set<JobStatus> = setOf(COMPLETED, INVOICED, WARRANTY_ACTIVE, WARRANTY_EXPIRED)
    }
}

@Serializable
enum class QuoteStatus {
    @SerialName("pending") PENDING,
    @SerialName("accepted") ACCEPTED,
    @SerialName("declined") DECLINED,
}

/// A category as it appears on a job (`JobResource.category`).
@Serializable
data class JobCategory(
    val id: Int,
    val code: String? = null,
    val slug: String? = null,
    val name: String,
    val bgColor: String? = null,
    val fgColor: String? = null,
    val icon: String? = null,
)

/// The company on a job/quote (`Organization::ratingSummary()` + plan).
@Serializable
data class CompanySummary(
    val id: Int? = null,
    val name: String,
    /// 'pro' | 'elite' | null. DECODED BUT NEVER RENDERED in the app — the
    /// PRO/ELITE chips and "ZiVETT Elite professional" lines were pulled
    /// 2026-09-21 so nothing plan-shaped is on screen for store review
    /// (see PARITY.md "plan chips"). The web keeps them.
    val plan: String? = null,
    val rating: Double? = null,
    val count: Int? = null,
    val verification: List<Verification>? = null,
) {
    @Serializable
    data class Verification(
        val key: String,
        val label: String,
        val status: String,
        val verifiedAt: String? = null,
        val expiresAt: String? = null,
    )

    val initials: String get() = initialsOf(name)
}

/// `QuoteResource`.
@Serializable
data class Quote(
    val id: Int,
    val amountCents: Int,
    val hourlyRateCents: Int? = null,
    val estimatedHours: Double? = null,
    val crewSize: Int? = null,
    val proposedDate: String? = null,
    val proposedWindow: String? = null,
    val message: String? = null,
    val status: QuoteStatus,
    val company: CompanySummary? = null,
    val authorizationPreview: AuthorizationPreview? = null,
    /// Company-side lists load the quote's job.
    val job: QuoteJob? = null,
    val createdAt: Instant? = null,
) {
    /// `Payments::bookerBreakdown()`.
    @Serializable
    data class AuthorizationPreview(
        val subtotalCents: Int? = null,
        val feeCents: Int? = null,
        val gstBps: Int? = null,
        val gstCents: Int? = null,
        val pstBps: Int? = null,
        val pstCents: Int? = null,
        val referralCreditCents: Int? = null,
        val totalCents: Int? = null,
    )
}

/// `JobPhotoResource`.
@Serializable
data class JobPhoto(
    val id: Int,
    val kind: String,
    val url: String,
    val thumbUrl: String? = null,
    val mine: Boolean = false,
    val createdAt: Instant? = null,
)

/// `ChangeOrderResource`.
@Serializable
data class ChangeOrder(
    val id: Int,
    val label: String? = null,
    val amountCents: Int,
    val status: String,
    val decidedAt: Instant? = null,
    val createdAt: Instant? = null,
) {
    val isProposed: Boolean get() = status == "proposed"
}

/// `ReviewResource`.
@Serializable
data class Review(
    val id: Int,
    val rating: Int,
    val comment: String? = null,
    val reviewer: String? = null,
    val createdAt: Instant? = null,
)

/// `DisputeResource` — warranty claims and reports share the table.
@Serializable
data class Dispute(
    val id: Int,
    val code: String? = null,
    val kind: String,
    val status: String,
    val body: String? = null,
    val resolution: String? = null,
    val createdAt: Instant? = null,
    val resolvedAt: Instant? = null,
)

/// Category-intake snapshot shown as chips.
@Serializable
data class IntakeAnswer(
    val type: String? = null,
    val prompt: String,
    val answers: List<String> = emptyList(),
)

/// `JobResource` — the full shape; list endpoints omit `whenLoaded`
/// sections, so everything relational is optional.
@Serializable
data class Job(
    val id: Int,
    /// Null on jobs minted before codes existed — never assume one.
    val code: String? = null,
    val title: String,
    val issue: String? = null,
    val intakeAnswers: List<IntakeAnswer>? = null,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val accessNotes: String? = null,
    val mode: String? = null,
    val urgency: String? = null,
    val scheduledDate: String? = null,
    val scheduledWindow: String? = null,
    val availabilityWindows: List<AvailabilityWindow>? = null,
    val status: JobStatus,
    val estimateMin: Int? = null,
    val estimateMax: Int? = null,
    val category: JobCategory,
    val warrantyEndsAt: Instant? = null,
    val closedAt: Instant? = null,
    val autoCloseAt: Instant? = null,
    val cancellationFeeCents: Int? = null,
    val unit: String? = null,
    val company: CompanySummary? = null,
    val review: Review? = null,
    val photos: List<JobPhoto>? = null,
    val disputes: List<Dispute>? = null,
    val invoice: Invoice? = null,
    val quotes: List<Quote>? = null,
    val quotesReleasedAt: Instant? = null,
    /// Booker payloads while unquoted: how many matched pros were
    /// notified. Absent = zero supply, which stays generic in copy.
    val matchedPros: Int? = null,
    val changeOrders: List<ChangeOrder>? = null,
    val createdAt: Instant? = null,
    /// Opportunities feed only: company pin → job pin.
    val distanceKm: Double? = null,
    /// Opportunities feed only: the booker's windows against this
    /// company's live calendar.
    val companyWindows: List<CompanyWindow>? = null,
    val tenant: String? = null,
    val bookerType: String? = null,
    /// Feed-only: the booker is a Business Premium org — pinned first,
    /// with a Priority badge.
    val priority: Boolean? = null,
    val bookerPlan: String? = null,
    val property: PropertyRef? = null,
    /// Company viewers get the booker's name.
    val customer: CustomerRef? = null,
    /// The tech who shows up; null until the company assigns one.
    val assignedTech: AssignedTech? = null,
) {
    /// Whether this job is assigned to the given user (a company member
    /// checking "is this one mine").
    fun isAssigned(to: Int?): Boolean = to != null && assignedTech?.id == to

    val pendingQuotes: List<Quote> get() = (quotes ?: emptyList()).filter { it.status == QuoteStatus.PENDING }
    val acceptedQuote: Quote? get() = (quotes ?: emptyList()).firstOrNull { it.status == QuoteStatus.ACCEPTED }
    val isCancellable: Boolean get() = status in JobStatus.customerCancellable
    val isReviewable: Boolean get() = status in JobStatus.reviewable && company != null
    val headline: String get() = listOfNotNull(code, address).joinToString(" · ")
    /// `customer.name` — only serialized for company viewers.
    val customerName: String? get() = customer?.name
}

/// The team member a won job is assigned to (`JobResource.assigned_tech`)
/// — photo + abbreviated name, the booker-trust card.
@Serializable
data class AssignedTech(val id: Int, val name: String, val photoUrl: String? = null)

@Serializable
data class PropertyRef(val id: Int, val name: String)

@Serializable
data class CustomerRef(val name: String)

@Serializable
data class AvailabilityWindow(val date: String, val window: String)

/// A booker-offered window annotated against this company's calendar
/// (`Scheduling::windowsFor`).
@Serializable
data class CompanyWindow(val date: String, val window: String, val available: Boolean = true, val booked: Int = 0)

/// `InvoiceResource` (customer side — no commission fields).
@Serializable
data class Invoice(
    val id: Int,
    val number: String? = null,
    val lineItems: List<LineItem>? = null,
    val totalCents: Int,
    val customerFeeBps: Int? = null,
    val customerFeeCents: Int? = null,
    /// The server's name for the booker-side fee, when it sends one —
    /// see `feeLabel`.
    val customerFeeLabel: String? = null,
    val gstBps: Int? = null,
    val gstCents: Int? = null,
    val customerFeeGstCents: Int? = null,
    val gstNumber: String? = null,
    val pstBps: Int? = null,
    val pstCents: Int? = null,
    val tipCents: Int? = null,
    val referralCreditCents: Int? = null,
    val customerTotalCents: Int? = null,
    val refundedCents: Int? = null,
    val status: String? = null,
    val issuedAt: Instant? = null,
    val dueAt: Instant? = null,
    val paidAt: Instant? = null,
    // Company/admin-side economics (absent for bookers).
    val commissionRateBps: Int? = null,
    val commissionCents: Int? = null,
    val trustFeeBps: Int? = null,
    val trustFeeCents: Int? = null,
    val companyReferralCreditCents: Int? = null,
    val netCents: Int? = null,
    val job: JobSummary? = null,
    val detail: Detail? = null,
) {
    @Serializable
    data class LineItem(val label: String, val amount: Int)

    @Serializable
    data class JobSummary(
        val id: Int,
        val code: String? = null,
        val title: String,
        val company: String? = null,
        /// Company viewers see the booker's name.
        val customer: String? = null,
    )

    @Serializable
    data class Detail(
        // Null on platform-issued documents (cancellation fees).
        val company: Company? = null,
        val billedTo: BilledTo,
        val job: JobInfo,
    ) {
        /// Name + logo only — the invoice letterhead is ZiVETT's, and the
        /// pro's phone/address deliberately don't appear on the document.
        @Serializable
        data class Company(val name: String, val logoUrl: String? = null)

        @Serializable
        data class BilledTo(val name: String? = null, val attention: String? = null, val address: String? = null, val unit: String? = null)

        @Serializable
        data class JobInfo(val id: Int, val code: String? = null, val title: String, val category: String? = null, val completedAt: Instant? = null)
    }

    val amountDueCents: Int get() = customerTotalCents ?: totalCents

    /// What every surface calls the booker-side fee. The server's label
    /// wins when present (an invoice issued under an older fee can name
    /// itself); otherwise today's name — it was renamed from "Booking &
    /// support fee", which the invoice screen and PDF still printed.
    val feeLabel: String get() = customerFeeLabel?.takeIf { it.isNotBlank() } ?: "Trust & support fee"
    val isPaid: Boolean get() = paidAt != null
}

/// `GET /api/customer/home`.
@Serializable
data class CustomerHome(
    val firstTime: Boolean = false,
    val hasSavedAddress: Boolean = true,
    val activeJobs: List<Job> = emptyList(),
    val warranties: List<Job> = emptyList(),
    val invoicesDue: List<Invoice> = emptyList(),
    val unreadMessages: Int = 0,
    /// `BookingOptions::payload()` — powers the booking launchpad that
    /// fills the screen when nothing else is live.
    val booking: BookingOptions? = null,
) {
    companion object {
        val empty = CustomerHome()
    }
}

/// `GET /api/customer/jobs/{job}/location`.
@Serializable
data class JobLocation(val points: List<Point> = emptyList(), val updatedAt: Instant? = null) {
    @Serializable
    data class Point(val lat: Double, val lng: Double, val at: Instant? = null)

    val latest: Point? get() = points.lastOrNull()
}

/// `GET /api/customer/conversations` — rows are keyed by JOB; `id` (the
/// conversation id) is null until someone opens the thread.
@Serializable
data class ConversationSummary(
    val id: Int? = null,
    val unreadCount: Int = 0,
    val lastMessage: String? = null,
    val lastMessageAt: Instant? = null,
    val readOnly: Boolean = false,
    val job: JobRef,
    val company: String? = null,
    val companyPlan: String? = null,
) {
    @Serializable
    data class JobRef(val id: Int, val code: String? = null, val title: String, val status: JobStatus, val category: Category? = null) {
        @Serializable
        data class Category(val code: String? = null, val bgColor: String? = null, val fgColor: String? = null, val icon: String? = null)
    }

    val conversationId: Int? get() = id
}

/// `GET /api/customer/jobs/{job}/conversation`.
@Serializable
data class Conversation(
    val id: Int,
    val messages: List<Message> = emptyList(),
    /// The other side of the thread as this viewer sees it — who they
    /// can block, and who they already have. Absent on older servers.
    val participants: List<ConversationParticipant> = emptyList(),
)

/// One person on the other side of a thread (`participants` on the
/// conversation and block/unblock responses).
@Serializable
data class ConversationParticipant(val id: Int, val name: String, val blocked: Boolean = false)

@Serializable
data class Message(
    val id: Int,
    val senderId: Int,
    val sender: String,
    val body: String,
    val mine: Boolean = false,
    val read: Boolean = false,
    val createdAt: Instant? = null,
)

/// `CustomerAddressResource`.
@Serializable
data class CustomerAddress(
    val id: Int,
    val label: String,
    val address: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val pinAdjustedAt: Instant? = null,
    val accessNotes: String? = null,
    val isDefault: Boolean = false,
)

/// `GET /api/customer/profile`.
@Serializable
data class CustomerProfile(
    val profile: Profile,
    val notificationPreferences: List<Preference> = emptyList(),
    val smsEnabled: Boolean = false,
) {
    @Serializable
    data class Profile(
        val firstName: String = "",
        val lastName: String = "",
        /// Derived full name — read-only; edits go through first/last.
        val name: String,
        val email: String,
        val emailVerified: Boolean = false,
        val phone: String? = null,
        val phoneVerified: Boolean = false,
        /// Company members: the face bookers see on assigned jobs —
        /// required to take jobs. Null for customers and businesses.
        val photoUrl: String? = null,
    )

    @Serializable
    data class Preference(val category: String, val label: String, val description: String = "", val email: Boolean = false)
}

/// `GET /api/customer/referral`.
@Serializable
data class Referral(
    val code: String,
    val link: String,
    val rewardCents: Int,
    val discount: Discount,
    val stats: Stats,
) {
    @Serializable
    data class Discount(val mode: String, val cents: Int = 0, val bps: Int = 0, val jobs: Int = 0)

    @Serializable
    data class Stats(val friendsJoined: Int = 0, val rewardsEarnedCents: Int = 0, val bankedCount: Int = 0, val availableCents: Int = 0, val nextCreditCents: Int = 0)
}

/// `GET /api/notifications`.
@Serializable
data class NotificationFeed(val notifications: List<AppNotification> = emptyList(), val unread: Int = 0)

@Serializable
data class AppNotification(
    val id: String,
    val title: String,
    val body: String? = null,
    val routeName: String? = null,
    val routeParams: Map<String, RouteParam>? = null,
    val read: Boolean = false,
    val createdAt: Instant? = null,
) {
    /// The job this notification points at, when it does. The server
    /// sends the job's public CODE (`Z-93U4H3`) as the route param —
    /// never coerce it to Int (`Notify::jobRouteFor`); legacy rows may
    /// still carry a numeric id, which stringifies. The three job routes
    /// (`Notify.php`) are customer.jobs.show / business.requests.show /
    /// company.jobs.show — message notifications reuse them too.
    val jobRef: String?
        get() {
            val route = routeName ?: return null
            if (!(route.startsWith("customer.jobs") || route.startsWith("business.requests") || route.startsWith("company.jobs"))) return null
            return routeParams?.get("id")?.stringValue
        }
}

/// A route param that may arrive as a number (legacy rows) or a string
/// (the public code).
@Serializable(with = RouteParam.Serializer::class)
data class RouteParam(val stringValue: String, val intValue: Int? = stringValue.toIntOrNull()) {
    object Serializer : KSerializer<RouteParam> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RouteParam", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): RouteParam {
            val json = decoder as? JsonDecoder
            if (json != null) {
                val primitive = json.decodeJsonElement().jsonPrimitive
                return RouteParam(primitive.content, primitive.intOrNull)
            }
            return RouteParam(decoder.decodeString())
        }

        override fun serialize(encoder: Encoder, value: RouteParam) {
            encoder.encodeString(value.stringValue)
        }
    }
}

/// `GET /api/customer/booking-options`.
@Serializable
data class BookingOptions(
    val categories: List<Category> = emptyList(),
    val modes: List<Mode> = emptyList(),
    val pricing: List<PriceLine>? = null,
    val warrantyDays: Int? = null,
    val trustFeeBps: Int? = null,
    val gstBps: Int? = null,
    val pstBps: Int? = null,
    /// Absent in the home payload's copy.
    val addresses: List<CustomerAddress>? = null,
    /// The business variant ships properties instead of addresses.
    val properties: List<Property>? = null,
) {
    @Serializable
    data class Category(
        val id: Int,
        val code: String,
        val slug: String,
        val name: String,
        val blurb: String? = null,
        val bgColor: String? = null,
        val fgColor: String? = null,
        val icon: String? = null,
        val emergencyFeeCents: Int? = null,
        /// The cheapest approved pro's hourly rate for the trade —
        /// the tile's "From $X/hr" line. Null until someone serves it.
        val minHourlyRateCents: Int? = null,
        val imageUrl: String? = null,
        val pstApplicable: Boolean? = null,
        val questions: List<Question> = emptyList(),
    )

    @Serializable
    data class Question(
        val id: Int,
        val prompt: String,
        /// `single` · `multi` · `short` · `description`
        val type: String,
        val options: List<String>? = null,
        val placeholder: String? = null,
    )

    @Serializable
    data class Mode(
        val key: String,
        val tag: String? = null,
        val name: String,
        val desc: String? = null,
        val eta: String? = null,
        val priceNote: String? = null,
    )

    @Serializable
    data class PriceLine(val label: String, val amount: Int)
}

// MARK: - Wrapped list/detail responses

@Serializable data class JobsResponse(val jobs: List<Job> = emptyList())
@Serializable data class JobResponse(val job: Job)
@Serializable data class InvoicesResponse(val invoices: List<Invoice> = emptyList())
@Serializable data class InvoiceResponse(val invoice: Invoice)
@Serializable data class ConversationsResponse(val conversations: List<ConversationSummary> = emptyList())
@Serializable data class ConversationResponse(val conversation: Conversation)
@Serializable data class AddressesResponse(val addresses: List<CustomerAddress> = emptyList())
@Serializable data class AddressResponse(val address: CustomerAddress)
@Serializable data class MessageResponse(val message: Message)
@Serializable data class BlockResponse(val participants: List<ConversationParticipant> = emptyList())

@Suppress("unused")
private val keepJsonPrimitiveImport: JsonPrimitive? = null
