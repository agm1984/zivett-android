package com.zivett.app.core.models

import com.zivett.app.core.network.ApiRequest

/// The booker job screens are shared between the customer and business
/// areas — same payloads, different URL prefix. A `JobArea` bundles the
/// endpoints so `JobDetailModel`, the conversation, and the pay flow are
/// written once. (`Business.vue` does the same with `area='business'`.)
class JobArea(
    val kind: Kind = Kind.CUSTOMER,
    val job: (Int) -> ApiRequest<JobResponse>,
    val cancel: (Int) -> ApiRequest<JobResponse>,
    val close: (Int, Int) -> ApiRequest<JobResponse>,
    val acceptQuote: (Int, Int, AcceptQuoteBody) -> ApiRequest<JobResponse>,
    val approveChangeOrder: (Int, Int) -> ApiRequest<ChangeOrderResponse>,
    val declineChangeOrder: (Int, Int) -> ApiRequest<ChangeOrderResponse>,
    val review: (Int, Int, String?) -> ApiRequest<ReviewResponse>,
    val updateReview: (Int, Int, String?) -> ApiRequest<ReviewResponse>,
    val warrantyClaim: (Int, String) -> ApiRequest<DisputeResponse>,
    val report: (Int, String, String) -> ApiRequest<DisputeResponse>,
    val uploadPhotos: (Int, List<ByteArray>) -> ApiRequest<PhotosResponse>,
    val deletePhoto: (Int) -> ApiRequest<Unit>,
    val conversation: (Int) -> ApiRequest<ConversationResponse>,
    val sendMessage: (Int, String) -> ApiRequest<MessageResponse>,
    val location: (Int) -> ApiRequest<JobLocation>,
    val invoiceDetail: (Int) -> ApiRequest<InvoiceResponse>,
    val payInvoice: (Int, String?, Int) -> ApiRequest<InvoiceResponse>,
    val warranties: () -> ApiRequest<JobsResponse> = { CustomerEndpoints.warranties() },
) {
    enum class Kind { CUSTOMER, BUSINESS, COMPANY }

    /// "job" or "request" — the web renames the noun per area.
    val noun: String get() = if (kind == Kind.BUSINESS) "request" else "job"

    /// Which notification/job-ref area this maps to.
    val notificationArea: NotificationArea
        get() = when (kind) {
            Kind.CUSTOMER -> NotificationArea.CUSTOMER
            Kind.BUSINESS -> NotificationArea.BUSINESS
            Kind.COMPANY -> NotificationArea.COMPANY
        }

    companion object {
        val customer = JobArea(
            job = { CustomerEndpoints.job(it) },
            cancel = { CustomerEndpoints.cancel(it) },
            close = { id, tip -> CustomerEndpoints.close(id, tip) },
            acceptQuote = { job, quote, body -> CustomerEndpoints.acceptQuote(job, quote, body) },
            approveChangeOrder = { job, order -> CustomerEndpoints.approveChangeOrder(job, order) },
            declineChangeOrder = { job, order -> CustomerEndpoints.declineChangeOrder(job, order) },
            review = { job, rating, comment -> CustomerEndpoints.review(job, rating, comment) },
            updateReview = { review, rating, comment -> CustomerEndpoints.updateReview(review, rating, comment) },
            warrantyClaim = { job, body -> CustomerEndpoints.warrantyClaim(job, body) },
            report = { job, kind, body -> CustomerEndpoints.report(job, kind, body) },
            uploadPhotos = { job, photos -> CustomerEndpoints.uploadPhotos(job, photos) },
            deletePhoto = { CustomerEndpoints.deletePhoto(it) },
            conversation = { CustomerEndpoints.conversation(it) },
            sendMessage = { job, body -> CustomerEndpoints.sendMessage(job, body) },
            location = { CustomerEndpoints.location(it) },
            invoiceDetail = { CustomerEndpoints.invoice(it) },
            payInvoice = { invoice, coupon, tip -> CustomerEndpoints.pay(invoice, coupon, tip) },
        )

        val business = JobArea(
            kind = Kind.BUSINESS,
            job = { BusinessEndpoints.request(it) },
            cancel = { BusinessEndpoints.cancel(it) },
            close = { id, tip -> BusinessEndpoints.close(id, tip) },
            acceptQuote = { job, quote, body -> BusinessEndpoints.acceptQuote(job, quote, body) },
            approveChangeOrder = { job, order -> BusinessEndpoints.approveChangeOrder(job, order) },
            declineChangeOrder = { job, order -> BusinessEndpoints.declineChangeOrder(job, order) },
            review = { job, rating, comment -> BusinessEndpoints.review(job, rating, comment) },
            updateReview = { review, rating, comment -> BusinessEndpoints.updateReview(review, rating, comment) },
            warrantyClaim = { job, body -> BusinessEndpoints.warrantyClaim(job, body) },
            report = { job, kind, body -> BusinessEndpoints.report(job, kind, body) },
            uploadPhotos = { job, photos -> BusinessEndpoints.uploadPhotos(job, photos) },
            deletePhoto = { BusinessEndpoints.deletePhoto(it) },
            conversation = { BusinessEndpoints.conversation(it) },
            sendMessage = { job, body -> BusinessEndpoints.sendMessage(job, body) },
            location = { BusinessEndpoints.location(it) },
            invoiceDetail = { BusinessEndpoints.invoice(it) },
            payInvoice = { invoice, coupon, tip -> BusinessEndpoints.pay(invoice, coupon, tip) },
            warranties = { BusinessEndpoints.warranties() },
        )

        /// A minimal area for the company's job thread (only conversation +
        /// location are ever used from here).
        val companyThread = JobArea(
            kind = Kind.COMPANY,
            job = { CompanyEndpoints.job(it) },
            cancel = customer.cancel,
            close = customer.close,
            acceptQuote = customer.acceptQuote,
            approveChangeOrder = customer.approveChangeOrder,
            declineChangeOrder = customer.declineChangeOrder,
            review = customer.review,
            updateReview = customer.updateReview,
            warrantyClaim = customer.warrantyClaim,
            report = { job, kind, body -> CompanyEndpoints.report(job, kind, body) },
            uploadPhotos = customer.uploadPhotos,
            deletePhoto = { CompanyEndpoints.deletePhoto(it) },
            conversation = { CompanyEndpoints.conversation(it) },
            sendMessage = { job, body -> CompanyEndpoints.sendMessage(job, body) },
            location = { CompanyEndpoints.location(it) },
            invoiceDetail = { CompanyEndpoints.invoice(it) },
            payInvoice = customer.payInvoice,
        )

        fun forKind(kind: Kind): JobArea = when (kind) {
            Kind.CUSTOMER -> customer
            Kind.BUSINESS -> business
            Kind.COMPANY -> companyThread
        }
    }
}

/// The account endpoints each role mounts under its own prefix
/// (`ProfileController` is shared server-side).
class ProfileArea(
    val kind: JobArea.Kind,
    val profile: () -> ApiRequest<CustomerProfile>,
    val updateProfile: (UpdateProfileBody) -> ApiRequest<CustomerProfile>,
    val updatePreferences: (Map<String, Boolean>?, Boolean?) -> ApiRequest<CustomerProfile>,
    /// Company only: the member's profile photo — the face bookers see on
    /// assigned jobs. Null areas render no photo UI.
    val uploadPhoto: ((ByteArray) -> ApiRequest<ProfilePhotoResponse>)? = null,
    val deletePhoto: (() -> ApiRequest<ProfilePhotoResponse>)? = null,
) {
    companion object {
        val customer = ProfileArea(JobArea.Kind.CUSTOMER, { CustomerEndpoints.profile() }, { CustomerEndpoints.updateProfile(it) }, { email, sms -> CustomerEndpoints.updatePreferences(email, sms) })
        val business = ProfileArea(JobArea.Kind.BUSINESS, { BusinessEndpoints.profile() }, { BusinessEndpoints.updateProfile(it) }, { email, sms -> BusinessEndpoints.updatePreferences(email, sms) })
        val company = ProfileArea(
            JobArea.Kind.COMPANY,
            { CompanyEndpoints.profile() },
            { CompanyEndpoints.updateProfile(it) },
            { email, sms -> CompanyEndpoints.updatePreferences(email, sms) },
            uploadPhoto = { CompanyEndpoints.uploadProfilePhoto(it) },
            deletePhoto = { CompanyEndpoints.deleteProfilePhoto() },
        )

        fun forKind(kind: JobArea.Kind): ProfileArea = when (kind) {
            JobArea.Kind.CUSTOMER -> customer
            JobArea.Kind.BUSINESS -> business
            JobArea.Kind.COMPANY -> company
        }
    }
}

/// Team endpoints per role prefix (shared TeamController server-side).
class TeamArea(
    val kind: JobArea.Kind,
    val team: () -> ApiRequest<Team>,
    val invite: (String) -> ApiRequest<Unit>,
    val revoke: (Int) -> ApiRequest<Unit>,
    val updateMember: (Int, String, String, String, String) -> ApiRequest<MemberResponse>,
    /// Company-only: teammate photos are the assigned-tech trust signal.
    val uploadMemberPhoto: ((Int, ByteArray) -> ApiRequest<ProfilePhotoResponse>)? = null,
) {
    companion object {
        val business = TeamArea(JobArea.Kind.BUSINESS, { BusinessEndpoints.team() }, { BusinessEndpoints.invite(it) }, { BusinessEndpoints.revoke(it) }, { id, first, last, role, status -> BusinessEndpoints.updateMember(id, first, last, role, status) })
        val company = TeamArea(
            JobArea.Kind.COMPANY,
            { CompanyEndpoints.team() },
            { CompanyEndpoints.invite(it) },
            { CompanyEndpoints.revoke(it) },
            { id, first, last, role, status -> CompanyEndpoints.updateMember(id, first, last, role, status) },
            uploadMemberPhoto = { id, jpeg -> CompanyEndpoints.uploadMemberPhoto(id, jpeg) },
        )

        fun forKind(kind: JobArea.Kind): TeamArea = if (kind == JobArea.Kind.COMPANY) company else business
    }
}

/// Which role area's endpoints a notification surface talks to. The
/// feed itself is shared (`/api/notifications`); only the job lookup a
/// tap performs differs per area.
enum class NotificationArea { CUSTOMER, BUSINESS, COMPANY }

/// Job lookup by ROUTE REFERENCE — the public code (`Z-93U4H3`) the
/// server puts in notification `route_params`, or a numeric id on
/// legacy rows. Laravel's route binding resolves either on the same
/// endpoints the by-id factories hit (`Job::resolveRouteBinding`).
object JobRefEndpoints {
    fun job(area: NotificationArea, ref: String): ApiRequest<JobResponse> = when (area) {
        NotificationArea.CUSTOMER -> ApiRequest.get("api/customer/jobs/$ref")
        NotificationArea.BUSINESS -> ApiRequest.get("api/business/requests/$ref")
        NotificationArea.COMPANY -> ApiRequest.get("api/company/jobs/$ref")
    }
}
