package com.zivett.app.features.company

import com.zivett.app.core.models.JobStatus
import com.zivett.app.design.ZTone
import com.zivett.app.features.customer.jobs.JobPresentation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/// Company-side presentation rules from `lib/jobs.js` + the pages.
object CompanyPresentation {
    /// `nextStage` — the advance button's target, capped at completed.
    fun nextStage(after: JobStatus): JobStatus? {
        val timeline = JobStatus.timeline
        val index = timeline.indexOf(after)
        val completed = timeline.indexOf(JobStatus.COMPLETED)
        if (index < 0 || index >= completed) return null
        return timeline[index + 1]
    }

    /// `companyWithdrawable`.
    val withdrawable: Set<JobStatus> = setOf(JobStatus.ACCEPTED, JobStatus.EN_ROUTE)

    /// Statuses a dispatcher can (re)assign in — `Assignments::ASSIGNABLE_STATUSES`.
    val assignable: Set<JobStatus> = setOf(JobStatus.ACCEPTED, JobStatus.EN_ROUTE, JobStatus.ARRIVED, JobStatus.IN_PROGRESS)

    fun urgencyBadge(urgency: String?): JobPresentation.Meta? = when (urgency) {
        "asap" -> JobPresentation.Meta("URGENT", ZTone.DANGER)
        "today" -> JobPresentation.Meta("Today", ZTone.WARNING)
        "next_few_weeks" -> JobPresentation.Meta("Next few weeks", ZTone.NEUTRAL)
        "planning" -> JobPresentation.Meta("Still planning", ZTone.NEUTRAL)
        else -> null
    }

    fun percent(bps: Int): String {
        val value = bps / 100.0
        return (if (value == value.roundToInt().toDouble()) value.roundToInt().toString() else String.format(Locale.US, "%.2f", value)) + "%"
    }

    /// rate × hours × crew, rounded like the server.
    fun quoteTotal(rateCents: Int, hours: Double, crew: Int): Int = (rateCents * hours * crew).roundToInt()

    fun commission(totalCents: Int, bps: Int): Int = (totalCents * bps + 5000) / 10000

    fun credentialBadge(status: String?): JobPresentation.Meta = when (status) {
        "verified" -> JobPresentation.Meta("Verified", ZTone.SUCCESS)
        "approved" -> JobPresentation.Meta("Approved", ZTone.SUCCESS)
        "active" -> JobPresentation.Meta("Active", ZTone.INFO)
        "tracked" -> JobPresentation.Meta("Tracked", ZTone.WARNING)
        "submitted" -> JobPresentation.Meta("Submitted", ZTone.INFO)
        "rejected" -> JobPresentation.Meta("Needs another look", ZTone.DANGER)
        "expired" -> JobPresentation.Meta("Expired", ZTone.DANGER)
        else -> JobPresentation.Meta("Missing", ZTone.DANGER)
    }

    fun payoutBadge(status: String): JobPresentation.Meta = when (status) {
        "paid" -> JobPresentation.Meta("Paid", ZTone.SUCCESS)
        "pending" -> JobPresentation.Meta("Pending", ZTone.WARNING)
        "cancelled" -> JobPresentation.Meta("Cancelled", ZTone.DANGER)
        else -> JobPresentation.Meta(status.replaceFirstChar { it.uppercase() }, ZTone.NEUTRAL)
    }

    fun documentBadge(status: String): JobPresentation.Meta = when (status) {
        "approved" -> JobPresentation.Meta("Approved", ZTone.SUCCESS)
        "submitted" -> JobPresentation.Meta("In review", ZTone.INFO)
        "rejected" -> JobPresentation.Meta("Needs another look", ZTone.DANGER)
        "expired" -> JobPresentation.Meta("Expired", ZTone.DANGER)
        else -> JobPresentation.Meta("Not uploaded", ZTone.NEUTRAL)
    }
}

/// Date formatting shared by the screens (device time zone, en-US shapes).
object Dates {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val short = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val shortTime = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US)
    private val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun short(instant: Instant): String = short.withZone(zone).format(instant)
    fun shortTime(instant: Instant): String = shortTime.withZone(zone).format(instant)
    fun monthDay(instant: Instant): String = monthDay.withZone(zone).format(instant)
    fun monthYear(instant: Instant): String = monthYear.withZone(zone).format(instant)
    fun time(instant: Instant): String = time.withZone(zone).format(instant)

    /// "3m ago" / "2h ago" / "yesterday" / "Aug 9".
    fun relative(instant: Instant, now: Instant = Instant.now()): String {
        val seconds = now.epochSecond - instant.epochSecond
        return when {
            seconds < 60 -> "just now"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86400 -> "${seconds / 3600}h ago"
            seconds < 172800 -> "yesterday"
            seconds < 7 * 86400 -> "${seconds / 86400}d ago"
            else -> monthDay(instant)
        }
    }
}
