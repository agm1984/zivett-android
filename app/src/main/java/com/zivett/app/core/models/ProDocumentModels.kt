@file:UseSerializers(LaravelInstantSerializer::class)

package com.zivett.app.core.models

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
