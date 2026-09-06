package com.zivett.app.features.customer.home

import com.zivett.app.core.models.CustomerHome
import com.zivett.app.core.models.Invoice
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.Quote
import com.zivett.app.design.ZTone
import com.zivett.app.features.customer.jobs.oneDecimal
import java.time.Instant

/// Port of `resources/js/lib/customerHome.js`: which concern leads the
/// home screen, what the greeting says, and what's demoted to the
/// "also on your plate" strip. Pure functions over `CustomerHome` so the
/// ladder is unit-tested and retunable without touching views.
object CustomerHomeLogic {
    enum class HeroKey { ENROUTE, PROGRESS, INVOICE, QUOTES, PENDING, FIRSTRUN, EMPTY }

    /// Priority order — things happening in the next minutes beat things
    /// that can wait, and money owed beats reassurance.
    val priority = listOf(HeroKey.ENROUTE, HeroKey.PROGRESS, HeroKey.INVOICE, HeroKey.QUOTES, HeroKey.PENDING)

    /// States where something is actively happening get the dark header.
    val darkHeaderStates = setOf(HeroKey.PENDING, HeroKey.ENROUTE)

    sealed interface Subject {
        data class JobSubject(val job: Job) : Subject
        data class InvoiceSubject(val invoice: Invoice) : Subject
    }

    data class Concerns(val enroute: Job? = null, val progress: Job? = null, val invoice: Invoice? = null, val quotes: Job? = null, val pending: Job? = null) {
        operator fun get(key: HeroKey): Subject? = when (key) {
            HeroKey.ENROUTE -> enroute?.let { Subject.JobSubject(it) }
            HeroKey.PROGRESS -> progress?.let { Subject.JobSubject(it) }
            HeroKey.INVOICE -> invoice?.let { Subject.InvoiceSubject(it) }
            HeroKey.QUOTES -> quotes?.let { Subject.JobSubject(it) }
            HeroKey.PENDING -> pending?.let { Subject.JobSubject(it) }
            HeroKey.FIRSTRUN, HeroKey.EMPTY -> null
        }
    }

    data class Hero(val key: HeroKey, val subject: Subject?, val concerns: Concerns)
    data class Greeting(val kicker: String, val heading: String)
    data class SecondaryRow(val key: HeroKey, val subject: Subject, val title: String, val subtitle: String?, val tag: String, val tone: ZTone)

    val enRouteStatuses = setOf(JobStatus.EN_ROUTE, JobStatus.ARRIVED)
    val inProgressStatuses = setOf(JobStatus.IN_PROGRESS, JobStatus.COMPLETED)

    fun concerns(home: CustomerHome): Concerns {
        val jobs = home.activeJobs
        return Concerns(
            // A pro is physically on the way — the only state that earns a map.
            enroute = jobs.firstOrNull { it.status in enRouteStatuses },
            // On-site and working, through to "done, awaiting invoice".
            progress = jobs.firstOrNull { it.status in inProgressStatuses },
            invoice = home.invoicesDue.firstOrNull(),
            // Quotes are in and nobody has been chosen yet: a decision is owed.
            quotes = jobs.firstOrNull { it.company == null && it.pendingQuotes.isNotEmpty() },
            // Submitted, no company, no quotes — the anxious dead zone.
            pending = jobs.firstOrNull { it.company == null && it.pendingQuotes.isEmpty() && it.status == JobStatus.SUBMITTED },
        )
    }

    fun resolveHero(home: CustomerHome): Hero {
        val concerns = concerns(home)
        val winner = priority.firstOrNull { concerns[it] != null }
        if (winner != null) return Hero(winner, concerns[winner], concerns)
        return Hero(if (home.firstTime) HeroKey.FIRSTRUN else HeroKey.EMPTY, null, concerns)
    }

    fun greeting(hero: Hero, userFirstName: String): Greeting {
        val subject = hero.subject
        return when (hero.key) {
            HeroKey.FIRSTRUN -> Greeting("Welcome", "Let's get you set up, $userFirstName")
            HeroKey.PENDING -> Greeting("Request sent", "Sit tight — finding your pro")
            HeroKey.QUOTES -> {
                val count = (subject as? Subject.JobSubject)?.job?.pendingQuotes?.size ?: 0
                Greeting("$count new ${if (count == 1) "quote" else "quotes"}", "Time to choose")
            }
            HeroKey.ENROUTE -> Greeting("Your pro is close", "${(subject as? Subject.JobSubject)?.job?.company?.name ?: "Your pro"} is on the way")
            HeroKey.PROGRESS -> Greeting("Job in progress", "Almost there")
            HeroKey.INVOICE -> Greeting("Job complete", "One thing left")
            HeroKey.EMPTY -> Greeting("Good to see you", "Hi, $userFirstName")
        }
    }

    fun usesDarkHeader(hero: Hero): Boolean = hero.key in darkHeaderStates

    /// The demoted concerns as compact rows, in priority order.
    fun secondaryRows(hero: Hero): List<SecondaryRow> = priority.filter { it != hero.key }.mapNotNull { key ->
        val subject = hero.concerns[key] ?: return@mapNotNull null
        when {
            key == HeroKey.ENROUTE && subject is Subject.JobSubject -> SecondaryRow(key, subject, "Pro en route", subject.job.title, "LIVE", ZTone.SUCCESS)
            key == HeroKey.PROGRESS && subject is Subject.JobSubject -> SecondaryRow(key, subject, "Work in progress", subject.job.title, "LIVE", ZTone.SUCCESS)
            key == HeroKey.INVOICE && subject is Subject.InvoiceSubject -> SecondaryRow(key, subject, "Invoice due", subject.invoice.job?.title, "PAY", ZTone.DANGER)
            key == HeroKey.QUOTES && subject is Subject.JobSubject -> {
                val count = subject.job.pendingQuotes.size
                SecondaryRow(key, subject, "$count ${if (count == 1) "quote" else "quotes"} waiting", subject.job.title, "DECIDE", ZTone.INFO)
            }
            key == HeroKey.PENDING && subject is Subject.JobSubject -> SecondaryRow(key, subject, "Finding your pro", subject.job.title, "WAIT", ZTone.WARNING)
            else -> null
        }
    }

    /// "Last update 3m ago" — the map's freshness pill.
    fun freshness(date: Instant?, now: Instant = Instant.now()): String {
        date ?: return "No location yet"
        val minutes = maxOf(0L, (now.epochSecond - date.epochSecond) / 60)
        if (minutes < 1) return "Last update just now"
        if (minutes < 60) return "Last update ${minutes}m ago"
        return "Last update ${minutes / 60}h ago"
    }
}

/// Pure rules behind the rich home heroes — ported from
/// `HeroQuotes.vue` / `HeroInvoice.vue` so they're unit-testable.
object HomeHeroLogic {
    /// Recommending needs a real comparison: 2+ quotes and someone
    /// rated. Order stays the server's ranking — never re-sort.
    fun recommendedQuoteId(quotes: List<Quote>): Int? {
        if (quotes.size <= 1) return null
        val topRating = quotes.maxOfOrNull { it.company?.rating ?: 0.0 } ?: 0.0
        if (topRating <= 0) return null
        return quotes.firstOrNull { (it.company?.rating ?: 0.0) == topRating }?.id
    }

    /// The pro behind an option, in ZiVETT's voice (`HeroQuotes.vue`).
    fun proLine(quote: Quote): String {
        val baseTier = when (quote.company?.plan) {
            "elite" -> "ZiVETT Elite professional"
            "pro" -> "ZiVETT Pro professional"
            else -> "ZiVETT-verified professional"
        }
        val name = quote.company?.name
        val tier = if (!name.isNullOrEmpty()) "$name · $baseTier" else baseTier
        val rating = quote.company?.rating
        if (rating == null || rating <= 0) return "$tier · new to the platform"
        val count = quote.company?.count ?: 0
        val reviews = if (count > 0) " ($count ${if (count == 1) "review" else "reviews"})" else ""
        return "$tier · ★ ${rating.oneDecimal()}$reviews"
    }

    /// A cancellation fee is not work — it must never wear the same face
    /// as a repair invoice (`HeroInvoice.vue`).
    fun isCancellationFee(invoice: Invoice): Boolean = (invoice.lineItems ?: emptyList()).any { it.label.lowercase().contains("cancellation") }
}

/// Port of `StatusChecklist.vue` + the step derivations in
/// `HeroPending.vue` / `HeroProgress.vue`: where a job has got to,
/// phrased as things that have happened rather than status names.
object ChecklistLogic {
    enum class StepState { DONE, CURRENT, TODO }
    data class Step(val label: String, val state: StepState, val time: String? = null)

    /// The submitted-but-unclaimed dead zone (`HeroPending.vue`).
    fun pendingSteps(quoteMode: Boolean): List<Step> = listOf(
        Step("Request submitted", StepState.DONE, "now"),
        Step(if (quoteMode) "Collecting quotes from pros" else "Finding your nearest pro", StepState.CURRENT),
        Step(if (quoteMode) "You choose who does the work" else "Pro accepts and heads out", StepState.TODO),
    )

    /// Deliberately vague where the system can't promise (`HeroPending.vue`).
    fun pendingExpectation(quoteMode: Boolean): String =
        if (quoteMode) "Most quotes land within 24 hours. We'll notify you the moment one arrives."
        else "We're matching your job with the nearest verified pro. Usually under two minutes."

    private val progressStages = listOf(JobStatus.ARRIVED to "Arrived on-site", JobStatus.IN_PROGRESS to "Repair in progress", JobStatus.COMPLETED to "Work complete")

    /// On-site progression (`HeroProgress.vue`): the reached stage pulses,
    /// earlier ones are done, and "Invoice & payment" trails as what
    /// happens next — never a surprise ending.
    fun progressSteps(status: JobStatus): List<Step> {
        val reached = progressStages.indexOfFirst { it.first == status }
        return progressStages.mapIndexed { index, (_, label) ->
            Step(label, if (index < reached) StepState.DONE else if (index == reached) StepState.CURRENT else StepState.TODO)
        } + Step("Invoice & payment", StepState.TODO)
    }

    fun progressHeadline(status: JobStatus): String = when (status) {
        JobStatus.ARRIVED -> "Your pro has arrived"
        JobStatus.IN_PROGRESS -> "Repair under way"
        JobStatus.COMPLETED -> "Work is finished"
        else -> "Work under way"
    }

    fun progressBlurb(status: JobStatus, companyName: String?, title: String): String =
        if (status == JobStatus.COMPLETED) "Your pro is wrapping up. The invoice will land here shortly."
        else "${companyName ?: "Your pro"} is on-site working on “$title”."
}
