@file:UseSerializers(LaravelInstantSerializer::class)

package com.zivett.app.core.models

import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.LaravelInstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

/// A company's own quotes and invoices (Business tools slices 3 and 5) —
/// one table server-side (`pro_documents`, `kind` quote | invoice).

/// `ProDocumentPayload::row`.
@Serializable
data class ProDocumentRow(
    val id: Int,
    /// quote | invoice
    val kind: String = "quote",
    /// Null on a draft invoice (numbered at issue).
    val number: String? = null,
    /// Quotes: draft | sent | accepted | declined | expired.
    /// Invoices: draft | issued | paid | void (+ overdue derived).
    val status: String = "draft",
    val customer: OwnCustomerRef? = null,
    val totalCents: Int = 0,
    val issuedAt: Instant? = null,
    val validUntil: String? = null,
    val paidCents: Int = 0,
    val balanceCents: Int = 0,
    val dueOn: String? = null,
    val createdAt: Instant? = null,
)

@Serializable
data class OwnCustomerRef(val id: Int? = null, val name: String? = null)

/// One line as stored (`QuoteLines::normalize`).
@Serializable
data class ProLine(val type: String = "fee", val description: String = "", val quantity: Double = 1.0, val unitCents: Int = 0, val amountCents: Int = 0)

/// The company's letterhead printed on its documents.
@Serializable
data class Letterhead(
    val name: String = "",
    val logoUrl: String? = null,
    val address: List<String> = emptyList(),
    val phone: String? = null,
    /// The business email; blank = none on the document.
    val email: String? = null,
    val website: String? = null,
    val gstNumber: String? = null,
)

/// Every time it was emailed (`ProDocumentMail::history`).
@Serializable
data class DocumentEmail(
    val id: Int,
    val kind: String? = null,
    val recipient: String? = null,
    val sentAt: Instant? = null,
    val sentBy: String? = null,
    val failed: Boolean = false,
    val bounced: Boolean = false,
    val reported: Boolean = false,
    val paymentCents: Int? = null,
    val automatic: Boolean = false,
)

/// Who a send would go to and whether it can go (`ProDocumentMail::preflight`).
@Serializable
data class EmailPreflight(
    val recipient: String? = null,
    val blocked: String? = null,
    val replyTo: String? = null,
    val copyTo: String? = null,
    val sentToday: Int = 0,
    val dailyLimit: Int = 0,
)

@Serializable
data class DocumentRef(val id: Int, val number: String? = null, val status: String? = null, val code: String? = null, val subtotalCents: Int = 0)

/// `ProDocumentPayload::full` — a quote or an invoice. Kind-specific
/// fields are null on the other kind.
@Serializable
data class ProDocument(
    val id: Int,
    val kind: String = "quote",
    val number: String? = null,
    val status: String = "draft",
    val customer: OwnCustomerRef? = null,
    val totalCents: Int = 0,
    val issuedAt: Instant? = null,
    val validUntil: String? = null,
    val paidCents: Int = 0,
    val balanceCents: Int = 0,
    val dueOn: String? = null,
    val createdAt: Instant? = null,
    val letterhead: Letterhead? = null,
    val billedTo: BilledTo? = null,
    val proCustomerAddressId: Int? = null,
    val lineItems: List<ProLine> = emptyList(),
    val subtotalCents: Int = 0,
    val gstNumber: String? = null,
    val gstBps: Int? = null,
    val gstCents: Int = 0,
    val pstBps: Int? = null,
    val pstCents: Int = 0,
    val pricingType: String? = null,
    val exclusions: String? = null,
    val notes: String? = null,
    val acceptedAt: Instant? = null,
    val declinedAt: Instant? = null,
    val editable: Boolean = false,
    val fromDocumentId: Int? = null,
    /// The own job an accepted quote became.
    val job: JobRef? = null,
    val emailedAt: Instant? = null,
    val emails: List<DocumentEmail> = emptyList(),
    val email: EmailPreflight? = null,
    // Quotes.
    val link: QuoteLink? = null,
    val invoice: DocumentRef? = null,
    val deposits: List<DocumentRef> = emptyList(),
    val depositLeftCents: Int = 0,
    // Invoices.
    val dueTerms: String? = null,
    val isDeposit: Boolean = false,
    val paymentInstructions: String? = null,
    val voidedAt: Instant? = null,
    val voidReason: String? = null,
    val quote: DocumentRef? = null,
    val replaces: DocumentRef? = null,
    val replacedBy: DocumentRef? = null,
    val payments: List<Payment> = emptyList(),
) {
    @Serializable
    data class BilledTo(val name: String? = null, val email: String? = null, val phone: String? = null, val address: String? = null, val unit: String? = null)

    @Serializable
    data class JobRef(val id: Int, val code: String? = null, val status: String? = null)

    /// The customer's approve / decline link (`ProQuoteLinks::summary`).
    @Serializable
    data class QuoteLink(
        val count: Int = 0,
        val canCopy: Boolean = false,
        val firstViewedAt: Instant? = null,
        val lastViewedAt: Instant? = null,
        val answered: Answer? = null,
    ) {
        @Serializable
        data class Answer(val accepted: Boolean = false, val name: String? = null, val at: Instant? = null, val reason: String? = null)
    }

    /// PAY-03: every payment is Pro-reported; removed ones stay listed.
    @Serializable
    data class Payment(
        val id: Int,
        val amountCents: Int = 0,
        val paidOn: String? = null,
        val method: String? = null,
        val methodLabel: String? = null,
        val reference: String? = null,
        val reportedBy: String? = null,
        val removedAt: Instant? = null,
        val removeReason: String? = null,
    ) {
        val removed: Boolean get() = removedAt != null
    }

    val isInvoice: Boolean get() = kind == "invoice"
    val customerFirstName: String get() = customer?.name?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "the customer"
}

@Serializable
data class ProQuoteResponse(val quote: ProDocument)

@Serializable
data class QuoteLinkResponse(val url: String, val quote: ProDocument)

/// `GET api/company/business/quotes`.
@Serializable
data class ProQuotesResponse(
    val quotes: List<ProDocumentRow> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false,
    val counts: Map<String, Int> = emptyMap(),
    /// Null = unlimited.
    val quotesLeftThisMonth: Int? = null,
    val quotesPerMonth: Int? = null,
    val upgrade: PlanOffer? = null,
)

/// `GET api/company/business/quotes/context` (and invoices/context, which
/// adds its own fields).
@Serializable
data class DocumentContext(
    val tax: Tax = Tax(),
    val defaultValidUntil: String? = null,
    val quotesLeftThisMonth: Int? = null,
    val quotesPerMonth: Int? = null,
    val upgrade: PlanOffer? = null,
    val letterhead: Letterhead? = null,
    val paymentInstructions: String? = null,
    val dueTerms: String? = null,
) {
    @Serializable
    data class Tax(val gstAvailable: Boolean = false, val gstNumber: String? = null, val gstBps: Int = 500, val pstBps: Int = 700)

    val atQuoteLimit: Boolean get() = quotesLeftThisMonth != null && quotesLeftThisMonth <= 0
}

@Serializable
data class ProQuoteBody(
    val proCustomerId: Int,
    val proCustomerAddressId: Int? = null,
    val lineItems: List<QuoteLineBody>,
    val pricingType: String = "fixed",
    val validUntil: String? = null,
    val exclusions: String? = null,
    val notes: String? = null,
    val chargeGst: Boolean = false,
    val chargePst: Boolean = false,
)

@Serializable
data class AcceptedBody(val accepted: Boolean)

@Serializable
data class NoteBody(val note: String? = null)

/// A saved set of lines (Settings → line presets).
@Serializable
data class LinePreset(val id: Int, val name: String, val lineItems: List<ProLine> = emptyList(), val totalCents: Int = 0)

@Serializable
data class LinePresetsResponse(val presets: List<LinePreset> = emptyList())

@Serializable
data class LinePresetResponse(val preset: LinePreset)

@Serializable
data class LinePresetBody(val name: String, val lineItems: List<QuoteLineBody>)

object ProQuoteEndpoints {
    private const val BASE = "api/company/business/quotes"

    /// `status`: all | draft | sent | accepted | declined | expired.
    fun list(status: String, q: String?, page: Int = 1, customerId: Int? = null) = ApiRequest.get<ProQuotesResponse>(
        BASE, listOfNotNull("status" to status, q?.takeIf { it.isNotBlank() }?.let { "q" to it }, "page" to page.toString(), customerId?.let { "customer" to it.toString() }),
    )
    fun context() = ApiRequest.get<DocumentContext>("$BASE/context")
    fun show(id: Int) = ApiRequest.get<ProQuoteResponse>("$BASE/$id")
    fun create(body: ProQuoteBody) = ApiRequest.post<ProQuoteResponse, ProQuoteBody>(BASE, body)
    fun update(id: Int, body: ProQuoteBody) = ApiRequest.patch<ProQuoteResponse, ProQuoteBody>("$BASE/$id", body)
    fun send(id: Int) = ApiRequest.post<ProQuoteResponse>("$BASE/$id/send")
    fun answer(id: Int, accepted: Boolean) = ApiRequest.post<ProQuoteResponse, AcceptedBody>("$BASE/$id/answer", AcceptedBody(accepted))
    /// A fresh approve / decline link to text the customer (shown once).
    fun link(id: Int) = ApiRequest.json<QuoteLinkResponse>(ApiRequest.Method.POST, "$BASE/$id/link")
    fun duplicate(id: Int) = ApiRequest.post<ProQuoteResponse>("$BASE/$id/duplicate")
    fun delete(id: Int) = ApiRequest.empty(ApiRequest.Method.DELETE, "$BASE/$id")
    fun pdf(id: Int) = ApiRequest.bytes("$BASE/$id/pdf")
    fun email(id: Int, note: String?) = ApiRequest.post<ProQuoteResponse, NoteBody>("$BASE/$id/email", NoteBody(note?.takeIf { it.isNotBlank() }))
}

object LinePresetEndpoints {
    private const val BASE = "api/company/business/line-presets"
    fun list() = ApiRequest.get<LinePresetsResponse>(BASE)
    fun create(body: LinePresetBody) = ApiRequest.post<LinePresetResponse, LinePresetBody>(BASE, body)
    fun delete(id: Int) = ApiRequest.empty(ApiRequest.Method.DELETE, "$BASE/$id")
}

@Serializable
data class ProInvoiceResponse(val invoice: ProDocument)

/// How the company numbers its invoices (`ProInvoices::numbering`).
@Serializable
data class InvoiceNumbering(val mode: String = "code", val prefix: String? = null, val nextNumber: Int? = null, val locked: Boolean = false, val example: String? = null)

/// `GET api/company/business/invoices`.
@Serializable
data class ProInvoicesResponse(
    val invoices: List<ProDocumentRow> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false,
    val counts: Map<String, Int> = emptyMap(),
    val outstandingCents: Int = 0,
    val numbering: InvoiceNumbering? = null,
)

/// `GET api/company/business/invoices/context`.
@Serializable
data class InvoiceContext(
    val tax: DocumentContext.Tax = DocumentContext.Tax(),
    val numbering: InvoiceNumbering? = null,
    val paymentInstructions: String? = null,
    val letterhead: Letterhead? = null,
    val canManageSettings: Boolean = false,
)

/// Create (from scratch, `quote_id`, `pro_job_id`, or a deposit) and update.
@Serializable
data class ProInvoiceBody(
    val proCustomerId: Int? = null,
    val proCustomerAddressId: Int? = null,
    val lineItems: List<QuoteLineBody>? = null,
    val dueTerms: String? = null,
    val dueOn: String? = null,
    val notes: String? = null,
    val chargeGst: Boolean? = null,
    val chargePst: Boolean? = null,
    val quoteId: Int? = null,
    val proJobId: Int? = null,
    val depositCents: Int? = null,
)

@Serializable
data class VoidBody(val reason: String, val replace: Boolean = false)

@Serializable
data class VoidResponse(val invoice: ProDocument, val replacementId: Int? = null)

@Serializable
data class PaymentBody(val amountCents: Int, val paidOn: String, val method: String, val reference: String? = null, val reason: String? = null, val sendReceipt: Boolean? = null)

@Serializable
data class PaymentRecorded(val invoice: ProDocument, val receiptSent: Boolean = false, val receiptError: String? = null)

@Serializable
data class ReasonBody(val reason: String)

object ProInvoiceEndpoints {
    private const val BASE = "api/company/business/invoices"

    /// `status`: all | open | overdue | paid | draft | void.
    fun list(status: String, q: String?, page: Int = 1) = ApiRequest.get<ProInvoicesResponse>(
        BASE, listOfNotNull("status" to status, q?.takeIf { it.isNotBlank() }?.let { "q" to it }, "page" to page.toString()),
    )
    fun context() = ApiRequest.get<InvoiceContext>("$BASE/context")
    fun show(id: Int) = ApiRequest.get<ProInvoiceResponse>("$BASE/$id")
    fun create(body: ProInvoiceBody) = ApiRequest.post<ProInvoiceResponse, ProInvoiceBody>(BASE, body)
    /// Part of an accepted quote billed now, before tax.
    fun deposit(quoteId: Int, cents: Int) = create(ProInvoiceBody(quoteId = quoteId, depositCents = cents))
    fun update(id: Int, body: ProInvoiceBody) = ApiRequest.patch<ProInvoiceResponse, ProInvoiceBody>("$BASE/$id", body)
    fun delete(id: Int) = ApiRequest.empty(ApiRequest.Method.DELETE, "$BASE/$id")
    fun issue(id: Int) = ApiRequest.post<ProInvoiceResponse>("$BASE/$id/issue")
    fun void(id: Int, reason: String, replace: Boolean) = ApiRequest.post<VoidResponse, VoidBody>("$BASE/$id/void", VoidBody(reason, replace))
    fun pdf(id: Int) = ApiRequest.bytes("$BASE/$id/pdf")
    fun email(id: Int, note: String?) = ApiRequest.post<ProInvoiceResponse, NoteBody>("$BASE/$id/email", NoteBody(note?.takeIf { it.isNotBlank() }))
    fun receipt(id: Int, paymentId: Int) = ApiRequest.post<ProInvoiceResponse>("$BASE/$id/payments/$paymentId/receipt")
    fun recordPayment(id: Int, body: PaymentBody) = ApiRequest.post<PaymentRecorded, PaymentBody>("$BASE/$id/payments", body)
    fun updatePayment(id: Int, paymentId: Int, body: PaymentBody) = ApiRequest.patch<ProInvoiceResponse, PaymentBody>("$BASE/$id/payments/$paymentId", body)
    fun removePayment(id: Int, paymentId: Int, reason: String) = ApiRequest.delete<ProInvoiceResponse, ReasonBody>("$BASE/$id/payments/$paymentId", ReasonBody(reason))
}
