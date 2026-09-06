package com.zivett.app.features.company

import com.zivett.app.features.customer.jobs.JobPresentation

/// Cell-chip derivation for the month grid — pure so it's testable.
object CalendarChips {
    /// "Morning" / "Afternoon" / "Evening", or "Now" for an instant job
    /// with no window (the web's `windowTag`).
    fun tag(window: String?): String {
        if (window.isNullOrEmpty()) return "Now"
        return JobPresentation.windowShortLabel(window)
    }

    /// The compact cell chip: "M·Z-93U4H3" — window initial + code, the
    /// truncated variant of the web's "Morning · Z-93U4H3".
    fun chipLabel(window: String?, code: String?, tentative: Boolean = false): String {
        val initial = tag(window).take(1)
        return "$initial·${code ?: "—"}${if (tentative) "?" else ""}"
    }
}

/// Picks the file extension for a downloaded credential so the viewer
/// renders it with the right engine. The server's stored mime wins;
/// otherwise the bytes' magic numbers decide, defaulting to pdf.
object CredentialFile {
    fun fileExtension(mime: String?, data: ByteArray): String = when (mime) {
        "application/pdf" -> "pdf"
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> sniffExtension(data)
    }

    fun sniffExtension(data: ByteArray): String {
        fun startsWith(prefix: ByteArray) = data.size >= prefix.size && prefix.indices.all { data[it] == prefix[it] }
        if (startsWith(byteArrayOf(0x25, 0x50, 0x44, 0x46))) return "pdf" // %PDF
        if (startsWith(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))) return "jpg"
        if (startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))) return "png"
        // RIFF container is only webp when bytes 8–11 say so (a .wav is RIFF too).
        if (data.size >= 12 && startsWith("RIFF".toByteArray()) && data.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())) return "webp"
        return "pdf"
    }

    fun isImage(extension: String): Boolean = extension in setOf("jpg", "png", "webp")
}

/// The per-document upload guidance from the web's field guide
/// (`resources/js/lib/fieldGuide.js` → `credentials`) — what each
/// document is for, what a good upload shows, and accepted formats.
data class CredentialGuide(val kind: String, val title: String, val purpose: String, val checklist: List<String>, val formats: String, val fallback: String?)

object CredentialFieldGuide {
    fun guide(kind: String): CredentialGuide? = entries.firstOrNull { it.kind == kind }

    val entries: List<CredentialGuide> = listOf(
        CredentialGuide(
            "photo_id", "Photo ID",
            "Government-issued photo ID of the owner — it confirms a real person stands behind the account. Seen only by our review team, never by bookers.",
            listOf("Your name, matching this account", "The photo and dates, readable", "The whole document — all four corners, no glare"),
            "JPG, PNG or PDF · up to 10 MB", "A clear phone photo of your driver’s licence or passport works fine.",
        ),
        CredentialGuide(
            "license", "Trade license",
            "Your current trade certification — the document that lets us call you a Verified Pro.",
            listOf("Covers the trades you selected in Services & rates", "The licence number and expiry date, visible", "Your name or business name on it"),
            "JPG, PNG or PDF · up to 10 MB", "If your certification is digital, a full-page screenshot showing the number and expiry is fine.",
        ),
        CredentialGuide(
            "insurance", "Insurance",
            "Your certificate of liability insurance — the document your insurer or broker issues that proves your coverage. It’s what lets us tell bookers you’re insured.",
            listOf("Your business name, matching your profile", "The coverage amount (most trades carry $2M liability)", "The effective and expiry dates, readable"),
            "JPG, PNG or PDF · up to 10 MB", "Don’t have it handy? Your broker can email it to you in minutes — ask for a “certificate of insurance”.",
        ),
        CredentialGuide(
            "registration", "Business registration",
            "Your BC registration or incorporation certificate — it proves the business legally exists.",
            listOf("The legal name (matching, or naming, your operating name)", "The registration or incorporation number, visible"),
            "JPG, PNG or PDF · up to 10 MB", "Registered a while ago? Search “BC Registries” — you can download a copy of your registration there.",
        ),
        CredentialGuide(
            "worksafebc", "WorkSafeBC clearance",
            "Your WorkSafeBC clearance letter — shows your coverage is in good standing. Optional, but it earns a trust badge on your passport.",
            listOf("Issued within the last 90 days", "Your account number, visible"),
            "JPG, PNG or PDF · up to 10 MB", null,
        ),
        CredentialGuide(
            "background_check", "Background check",
            "A criminal record check — an extra trust signal shown as a badge on your passport. Entirely optional.",
            listOf("Issued within the last 12 months", "Your name, matching this account"),
            "JPG, PNG or PDF · up to 10 MB", null,
        ),
    )
}
