package com.zivett.app.features.company

import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.SubscriptionResponse
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

/// The plan card's action wording, ported 1:1 from the web's
/// `PlanCards.vue` — every tier is a 12-month contract, so the button
/// has to say what a tap actually does: upgrades land today, everything
/// else waits for the renewal date.
object SubscriptionPresentation {
    /// A plan with no monthly price is yearly-only (Basic): it bills
    /// yearly under both toggle positions.
    fun intervalFor(plan: SubscriptionResponse.Plan, browsingYearly: Boolean): String =
        if (plan.priceCents == 0 || browsingYearly) "yearly" else "monthly"

    /// Upgrades land immediately; everything else waits for renewal.
    /// Sort order is the tier ladder — the same rule the server applies.
    fun isUpgrade(plan: SubscriptionResponse.Plan, plans: List<SubscriptionResponse.Plan>, currentPlanId: Int): Boolean {
        val current = plans.firstOrNull { it.id == currentPlanId } ?: return true
        return (plan.sortOrder ?: 0) > (current.sortOrder ?: 0)
    }

    fun actionLabel(plan: SubscriptionResponse.Plan, plans: List<SubscriptionResponse.Plan>, subscription: SubscriptionResponse.Current, browsingYearly: Boolean): String {
        if (subscription.pending?.planId == plan.id) {
            val renewal = subscription.termEndsAt
            return if (renewal != null) "Scheduled for ${Dates.short(renewal)}" else "Scheduled"
        }
        val isCurrentPlan = plan.id == subscription.planId
        // Defaulted onto this tier with no contract started yet: the
        // button starts the term, it doesn't "switch" anything.
        if (isCurrentPlan && subscription.termEndsAt == null) return "Start your ${plan.name} plan"
        if (isCurrentPlan) return "Switch to ${intervalFor(plan, browsingYearly)} billing"
        return if (isUpgrade(plan, plans, subscription.planId)) "Upgrade to ${plan.name}" else "Move to ${plan.name} at renewal"
    }

    /// Whether the card being browsed is the org's live selection —
    /// same plan AND same interval (a yearly-only plan matches both
    /// toggle positions, like the web's `isCurrent`).
    fun isCurrent(plan: SubscriptionResponse.Plan, subscription: SubscriptionResponse.Current, browsingYearly: Boolean): Boolean =
        plan.id == subscription.planId && (subscription.interval == intervalFor(plan, browsingYearly) || plan.priceCents == 0)

    /// The change confirmation's body for an immediate upgrade
    /// (`SubscriptionPage.upgradeMessage`).
    fun upgradeMessage(planKey: String, planName: String, interval: String, company: Boolean): String {
        val badge = when (planKey) {
            "pro", "elite" -> planKey.uppercase()
            "business_premium" -> "PREMIUM"
            else -> null
        }
        val badgeLine = badge?.let { tag ->
            if (company) " The $tag badge now shows beside your name everywhere customers see it."
            else " The PREMIUM badge now shows beside your business name, and your jobs go to the top of every pro's feed."
        } ?: ""
        return "A fresh 12-month term started today, billed $interval.$badgeLine"
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
