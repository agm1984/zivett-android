package com.zivett.app.features.company

import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.models.User
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

    /// `onCrew`: the user is one of the techs going (lead or crew).
    fun onCrew(job: Job, user: User?): Boolean = user != null && job.isAssigned(user.id)

    /// The company-side primary action (`companyJobAction` in lib/jobs.js),
    /// shared by the Jobs list and the job page. A won job waits at
    /// Accepted until an org admin STARTS it (confirming who's going);
    /// only a tech on the crew may then mark it en route. Later stages
    /// advance for anyone who can see the job. Null = nothing further
    /// (awaiting payment).
    sealed interface JobAction {
        /// Admin, not started — "Start job" opens the crew picker.
        object Start : JobAction
        /// Nothing for THIS user to press yet.
        data class Waiting(val label: String) : JobAction
        /// The next stage.
        data class Advance(val label: String) : JobAction
    }

    fun jobAction(job: Job, user: User?): JobAction? {
        val stage = nextStage(job.status) ?: return null
        if (job.status == JobStatus.ACCEPTED) {
            if (job.dispatchedAt == null) {
                return if (user?.organizationRole == OrganizationRole.ADMIN) JobAction.Start else JobAction.Waiting("Waiting to be started")
            }
            if (!onCrew(job, user)) {
                val lead = job.crew?.firstOrNull()?.name ?: job.assignedTech?.name
                return JobAction.Waiting(if (lead != null) "Waiting for $lead to set off" else "Waiting for the crew to set off")
            }
        }
        // The web's status words ("Mark en route"), as on iOS — not the
        // model's customer-facing label ("On the way").
        return JobAction.Advance("Mark ${JobPresentation.meta(stage).label.lowercase()}")
    }

    /// "Mike R.", "Mike R. and Ana P.", "Mike R., Ana P. and Jo K.".
    fun nameList(names: List<String>): String =
        if (names.size > 1) names.dropLast(1).joinToString(", ") + " and " + names.last() else names.firstOrNull().orEmpty()

    /// The Start job picker's opening line. With a crew already lined up
    /// (auto-assigned at acceptance) it says who — shown success-tinted.
    fun startPickerIntro(job: Job, crewSize: Int): String {
        val crew = job.crewMembers
        if (crew.isNotEmpty()) return "${nameList(crew.map { it.name })} ${if (crew.size > 1) "are" else "is"} doing this job. Confirm to start it — they can then mark it en route — or change who's going."
        return "Pick ${if (crewSize > 1) technicians(crewSize) else "who's doing this job"}. Starting it lets them mark it en route."
    }

    /// The snackbar after a start.
    fun startedToast(code: String?, crewCount: Int): String =
        "${code ?: "Job"} started — ${if (crewCount > 1) "the crew has" else "they've"} been told to head out"

    /// The job page's Start-job notice under the status header — accepted
    /// jobs only, for a tech on the crew or an admin who can start it.
    fun startNotice(job: Job, user: User?): String? {
        if (job.status != JobStatus.ACCEPTED) return null
        val mine = onCrew(job, user)
        val starts = jobAction(job, user) == JobAction.Start
        return when {
            mine && starts -> "You're doing this job. Start job to confirm who's going, then mark it en route when you set off."
            mine && job.dispatchedAt == null -> "You're doing this job. Your team will start it when it's time — you'll get a notification, then you can mark it en route."
            mine -> "Started — you're on the crew. Mark it en route when you set off."
            !starts -> null
            !job.crew.isNullOrEmpty() -> "${job.crew.joinToString(", ") { it.name }} ${if (job.crew.size > 1) "are" else "is"} lined up for this job. Start job to confirm who's going."
            else -> "Start job to pick who's going."
        }
    }

    /// "1 technician" / "3 technicians".
    fun technicians(count: Int): String = if (count == 1) "1 technician" else "$count technicians"

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
        // An optional extra (WSBC, background check) not on file — never a red gap.
        "optional" -> JobPresentation.Meta("Optional", ZTone.NEUTRAL)
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
