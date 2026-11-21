package com.zivett.app.features.company

import com.zivett.app.core.models.CompanySetup
import com.zivett.app.features.customer.book.BookingWizardEngine
import java.time.LocalDate

/// Credential expiry dates (web `CredentialsStep` / `ReviewStep` /
/// company `Dashboard`, server `OnboardingState`): a REQUIRED document
/// that runs out isn't complete without its date. Which ones still lack
/// one is the server's `missing_expiry` list, never re-derived here, so
/// the app blocks exactly what submitting refuses.
object CredentialExpiry {
    /// Kinds that carry an expiry date at all (`CredentialKind::expires`).
    val expiringKinds = setOf("license", "insurance", "worksafebc")

    /// "Insurance" → "Insurance expiry date", the wording every blocker uses.
    fun neededItems(missingExpiry: List<String>): List<String> = missingExpiry.map { "$it expiry date" }

    /// The line that stops the credentials step, null when nothing is missing.
    fun stillNeededLine(missingExpiry: List<String>): String? =
        if (missingExpiry.isEmpty()) null else "Still needed before you can continue: ${neededItems(missingExpiry).joinToString(", ")}."

    /// The draft checklist's Credentials detail: once every file is in, the
    /// missing dates are what's left.
    fun credentialsDetail(step: CompanySetup.Steps.Step): String {
        val uploaded = step.uploaded ?: 0
        val total = step.total ?: 4
        val missing = step.missingExpiry.orEmpty()
        return if (uploaded == total && missing.isNotEmpty()) "Add the expiry date for: ${missing.joinToString(", ")}"
        else "$uploaded of $total documents uploaded"
    }

    /// Whether a passport/setup row (by its label) is waiting on its date.
    fun needsDate(label: String, missingExpiry: List<String>): Boolean = label in missingExpiry

    /// A verified document that already has a date can't be redated in
    /// place (the server 422s): renewing it is a re-upload.
    fun dateLocked(status: String?, expiresAt: String?): Boolean =
        expiresAt != null && (status == "approved" || status == "expired")

    /// What the expiry control reads: with a file on record, the STORED
    /// date; before any upload, the date picked to ride the upload.
    fun controlLabel(hasDocument: Boolean, stored: String?, picked: LocalDate?): String =
        (if (hasDocument) stored else picked?.let(BookingWizardEngine::dateKey)) ?: "Set expiry date"

    /// Where the date picker opens: the stored date, else the picked one,
    /// else a year out (most policies run a year).
    fun initialDate(stored: String?, picked: LocalDate?, today: LocalDate): LocalDate =
        stored?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: picked ?: today.plusYears(1)

    /// A date the server would refuse, caught before sending it. Same words
    /// as the server's message.
    fun dateProblem(date: LocalDate, today: LocalDate): String? =
        if (!date.isAfter(today)) "The expiry date has to be after today." else null
}
