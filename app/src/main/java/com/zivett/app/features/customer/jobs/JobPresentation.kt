package com.zivett.app.features.customer.jobs

import com.zivett.app.core.Money
import com.zivett.app.core.models.CompanySummary
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.Quote
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.design.ZTone
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/// Port of `resources/js/lib/jobs.js` + `lib/warranty.js` (via the iOS
/// `JobPresentation.swift`): the label, tone, price and copy rules every
/// job surface shares. Pure so it's unit-tested.
object JobPresentation {
    data class Meta(val label: String, val tone: ZTone)

    /// `statusMetaMap`.
    fun meta(status: JobStatus): Meta = when (status) {
        JobStatus.SUBMITTED -> Meta("Submitted", ZTone.INFO)
        JobStatus.MATCHED -> Meta("Matched", ZTone.INFO)
        JobStatus.ACCEPTED -> Meta("Accepted", ZTone.INFO)
        JobStatus.EN_ROUTE -> Meta("En route", ZTone.DANGER)
        JobStatus.ARRIVED -> Meta("Arrived", ZTone.WARNING)
        JobStatus.IN_PROGRESS -> Meta("In progress", ZTone.WARNING)
        JobStatus.COMPLETED -> Meta("Completed", ZTone.SUCCESS)
        JobStatus.INVOICED -> Meta("Invoice issued", ZTone.SUCCESS)
        JobStatus.WARRANTY_ACTIVE -> Meta("Closed", ZTone.SUCCESS)
        JobStatus.WARRANTY_EXPIRED -> Meta("Closed", ZTone.NEUTRAL)
        JobStatus.CANCELLED -> Meta("Cancelled", ZTone.NEUTRAL)
    }

    /// `statusDisplay(job)` — an open job with quotes in shows the count.
    fun display(job: Job): Meta {
        if ((job.status == JobStatus.SUBMITTED || job.status == JobStatus.MATCHED) && job.pendingQuotes.isNotEmpty()) {
            val n = job.pendingQuotes.size
            return Meta("$n ${if (n == 1) "quote" else "quotes"} in", ZTone.WARNING)
        }
        return meta(job.status)
    }

    /// `modeMetaMap`.
    fun modeMeta(mode: String?): Meta = when (mode) {
        "instant" -> Meta("Instant Dispatch", ZTone.DANGER)
        "scheduled" -> Meta("Scheduled Booking", ZTone.SUCCESS)
        "quote" -> Meta("Quote / Project", ZTone.INFO)
        else -> Meta(mode ?: "—", ZTone.NEUTRAL)
    }

    /// The mono chip on mode tiles (IN / SC / QT).
    fun modeCode(key: String): String = when (key) {
        "instant" -> "IN"
        "scheduled" -> "SC"
        "quote" -> "QT"
        else -> key.take(2).uppercase()
    }

    fun invoiceStatusMeta(status: String?): Meta = when (status) {
        "paid" -> Meta("Paid", ZTone.SUCCESS)
        "overdue" -> Meta("Overdue", ZTone.DANGER)
        else -> Meta("Due", ZTone.WARNING)
    }

    fun disputeStatusMeta(status: String): Meta = when (status) {
        "open" -> Meta("Open", ZTone.DANGER)
        "under_review" -> Meta("Under review", ZTone.WARNING)
        "awaiting_evidence" -> Meta("Awaiting evidence", ZTone.DANGER)
        "resolved" -> Meta("Resolved", ZTone.SUCCESS)
        "declined" -> Meta("Declined", ZTone.NEUTRAL)
        else -> Meta(status.replace("_", " ").replaceFirstChar { it.uppercase() }, ZTone.NEUTRAL)
    }

    fun disputeKindLabel(kind: String): String = when (kind) {
        "warranty_claim" -> "Warranty claim"
        "rework" -> "Rework"
        "billing" -> "Billing"
        "quality" -> "Work quality"
        "conduct" -> "Conduct or behaviour"
        "safety" -> "Safety concern"
        else -> kind.replaceFirstChar { it.uppercase() }
    }

    val reportKinds: List<Pair<String, String>> = listOf("quality" to "Work quality", "conduct" to "Conduct or behaviour", "safety" to "Safety concern")

    // Timeline

    enum class StepState { DONE, CURRENT, TODO }

    data class Step(val status: JobStatus, val title: String, val state: StepState)

    /// The 9-stage stepper. `warranty_expired` renders fully done;
    /// `cancelled` isn't a stage (the caller shows the cancelled panel).
    fun timeline(status: JobStatus): List<Step> {
        val stages = JobStatus.timeline
        val index = if (status == JobStatus.WARRANTY_EXPIRED) stages.size else stages.indexOf(status)
        return stages.mapIndexed { i, stage ->
            Step(stage, meta(stage).label, if (i < index) StepState.DONE else if (i == index) StepState.CURRENT else StepState.TODO)
        }
    }

    // Money & price

    /// `priceLabel(job)` — most-settled first; never the config estimate.
    fun priceLabel(job: Job): String? {
        job.invoice?.let { return Money.format(it.amountDueCents) }
        job.acceptedQuote?.let { return Money.format(it.amountCents) }
        val lowest = job.pendingQuotes.minOfOrNull { it.amountCents } ?: return null
        return "from $${(lowest / 100.0).roundToInt()}"
    }

    // Windows

    val windows = listOf("morning", "afternoon", "evening")

    fun windowLabel(window: String): String = when (window) {
        "morning" -> "Morning (8am–12pm)"
        "afternoon" -> "Afternoon (12pm–5pm)"
        "evening" -> "Evening (5pm–8pm)"
        else -> window.replaceFirstChar { it.uppercase() }
    }

    fun windowShortLabel(window: String): String = when (window) {
        "morning" -> "Morning"
        "afternoon" -> "Afternoon"
        "evening" -> "Evening"
        else -> window.replaceFirstChar { it.uppercase() }
    }

    private val shortDay = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US).withZone(ZoneOffset.UTC)
    private val longDay = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US).withZone(ZoneOffset.UTC)

    /// `formatWindowSlot` → "Sat, Aug 9 · Morning".
    fun windowSlot(date: String, window: String?): String {
        val day = JsonCoding.parseDate(date)?.let { shortDay.format(it) } ?: date
        if (window == null) return day
        return "$day · ${windowShortLabel(window)}"
    }

    /// `arrivalLabel` → "Saturday, August 9 — Morning (8am–12pm)".
    fun arrivalLabel(date: String, window: String?): String {
        val day = JsonCoding.parseDate(date)?.let { longDay.format(it) } ?: date
        if (window == null) return day
        return "$day — ${windowLabel(window)}"
    }

    // Warranty copy (`lib/warranty.js`) — never hardcode a number.

    fun warrantyLength(days: Int?): String? {
        days ?: return null
        return if (days < 3) "48 hours" else "90 days"
    }

    fun warrantyPhrase(days: Int?): String = when {
        days == null -> "workmanship warranty"
        days < 3 -> "48-hour workmanship warranty"
        else -> "90-day workmanship warranty"
    }

    // Jobs list copy (`Jobs.vue`)

    fun liveLine(job: Job): String {
        val company = job.company?.name ?: "Your pro"
        return when (job.status) {
            JobStatus.SUBMITTED, JobStatus.MATCHED -> {
                val n = job.pendingQuotes.size
                if (n > 0) "$n ${if (n == 1) "quote" else "quotes"} to review" else "Finding your pro"
            }
            JobStatus.ACCEPTED -> "$company is booked in"
            JobStatus.EN_ROUTE -> "$company is on the way"
            JobStatus.ARRIVED -> "Your pro has arrived"
            JobStatus.IN_PROGRESS -> "Repair under way"
            JobStatus.COMPLETED -> "Done — invoice on its way"
            JobStatus.INVOICED -> "Invoice ready to pay"
            else -> meta(job.status).label
        }
    }

    fun isLive(status: JobStatus): Boolean = status in setOf(JobStatus.EN_ROUTE, JobStatus.ARRIVED, JobStatus.IN_PROGRESS)

    /// Jobs-page "settled" split (`SETTLED` in Jobs.vue).
    val historyStatuses: Set<JobStatus> = setOf(JobStatus.WARRANTY_ACTIVE, JobStatus.WARRANTY_EXPIRED, JobStatus.CANCELLED)

    // Cancel copy (`JobDetailView.vue` manage row + modal)

    fun cancelManageCopy(job: Job): String {
        if (!job.isCancellable) return "Something wrong? Reporting a problem brings support in, and any pending payment stays held until it's resolved."
        val fee = job.cancellationFeeCents
        if (fee != null && fee > 0) return "Cancelling now has a ${Money.format(fee)} cancellation fee — your pro has already committed to this job."
        return "Free to cancel right now — once a pro is committed, a cancellation fee applies."
    }

    fun cancelConfirmCopy(job: Job): String {
        val fee = job.cancellationFeeCents
        if (fee != null && fee > 0) {
            val why = if (job.status == JobStatus.ARRIVED || job.status == JobStatus.IN_PROGRESS) "Covers your pro's crew time on site so far." else "Your pro has already committed to this job."
            return "$why The fee is charged to your payment hold when you confirm."
        }
        val n = job.pendingQuotes.size
        return "Cancelling is free right now, but it can't be undone — the $n pending ${if (n == 1) "quote" else "quotes"} on this job will be declined."
    }

    fun cancelButtonTitle(job: Job): String {
        val fee = job.cancellationFeeCents
        if (fee != null && fee > 0) return "Cancel & pay ${Money.format(fee)}"
        return "Cancel it"
    }

    /// `phase` on the detail page — drives card ordering.
    enum class Phase { RECORD, SETTLING, WORKING, DECIDING }

    fun phase(job: Job): Phase {
        if (job.status == JobStatus.CANCELLED || job.status == JobStatus.WARRANTY_ACTIVE || job.status == JobStatus.WARRANTY_EXPIRED || job.invoice?.status == "paid") return Phase.RECORD
        if (job.invoice != null || job.status == JobStatus.COMPLETED) return Phase.SETTLING
        if (job.status in setOf(JobStatus.ACCEPTED, JobStatus.EN_ROUTE, JobStatus.ARRIVED, JobStatus.IN_PROGRESS)) return Phase.WORKING
        return Phase.DECIDING
    }

    /// The quoting company by name — recognition for the pros (the old
    /// pre-acceptance alias is retired); the booking still runs through
    /// ZiVETT.
    fun quoteCompanyName(quote: Quote): String = quote.company?.name ?: "Your ZiVETT quote"

    /// `proLine` on the quotes hero. The web reads the tier off
    /// `company.plan` ("ZiVETT Elite"); the app says "ZiVETT-verified" for
    /// every tier — plan chips were pulled for store review (PARITY.md
    /// "plan chips").
    fun proLine(company: CompanySummary?): String {
        val tier = "ZiVETT-verified"
        val rating = company?.rating
        val count = company?.count ?: 0
        if (rating != null && count > 0) return "$tier professional · ★ ${rating.oneDecimal()} ($count ${if (count == 1) "review" else "reviews"})"
        return "$tier professional · new to the platform"
    }

    fun percent(bps: Int): String {
        val value = bps / 100.0
        return if (value == value.roundToInt().toDouble()) "${value.roundToInt()}%" else String.format(Locale.US, "%.2f%%", value)
    }
}

fun Double.oneDecimal(): String = String.format(Locale.US, "%.1f", this)

/// Hours formatted the way the web prints them: "2" or "2.5".
fun Double.hoursLabel(): String = if (this == this.toLong().toDouble()) this.toLong().toString() else String.format(Locale.US, "%.1f", this)

fun Double.kmLabel(): String = if (this == this.toLong().toDouble()) this.toLong().toString() else String.format(Locale.US, "%.1f", this)
