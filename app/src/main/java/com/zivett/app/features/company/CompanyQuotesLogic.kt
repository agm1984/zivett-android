package com.zivett.app.features.company

import com.zivett.app.core.Money
import com.zivett.app.core.models.Quote
import com.zivett.app.core.models.QuoteStatus
import java.time.Duration
import java.time.Instant

/// The My quotes screen's rules (`docs/mobile-api.md` "My quotes"), pure so
/// they're tested in `CompanyQuotesLogicTest`: what the company keeps, how a
/// quote's state is worded, the summary line, and the Past filter + search.
object CompanyQuotesLogic {
    /// The platform's cut, half-up to the cent (`Commission::cents`).
    fun commissionCents(amountCents: Int, commissionBps: Int): Int =
        ((amountCents.toLong() * commissionBps + 5_000) / 10_000).toInt()

    /// "You keep ≈" — the amount less the commission.
    fun youKeepCents(amountCents: Int, commissionBps: Int): Int = amountCents - commissionCents(amountCents, commissionBps)

    /// Revising is allowed only before ZiVETT releases the quotes (the
    /// PATCH 422s after). Older servers omit the flag → revisable.
    fun canRevise(quote: Quote): Boolean = quote.status == QuoteStatus.PENDING && quote.job?.quotesReleased != true

    /// Where a pending quote is.
    fun waitingState(quote: Quote): String =
        if (quote.job?.quotesReleased == true) "The booker is choosing" else "With ZiVETT for review"

    /// "Sent 2 days ago" — how long the quote has been out.
    fun sentAgo(sent: Instant, now: Instant): String {
        val minutes = Duration.between(sent, now).toMinutes().coerceAtLeast(0)
        return when {
            minutes < 1 -> "Sent just now"
            minutes < 60 -> "Sent ${plural(minutes, "minute")} ago"
            minutes < 24 * 60 -> "Sent ${plural(minutes / 60, "hour")} ago"
            minutes < 48 * 60 -> "Sent yesterday"
            else -> "Sent ${plural(minutes / (24 * 60), "day")} ago"
        }
    }

    enum class Outcome(val label: String) { WON("Won"), NOT_CHOSEN("Not chosen"), CANCELLED("Job cancelled") }

    /// A settled quote's outcome; null while pending.
    fun outcome(quote: Quote): Outcome? = when (quote.status) {
        QuoteStatus.PENDING -> null
        QuoteStatus.ACCEPTED -> Outcome.WON
        QuoteStatus.DECLINED -> if (quote.job?.status == "cancelled") Outcome.CANCELLED else Outcome.NOT_CHOSEN
    }

    fun waiting(quotes: List<Quote>): List<Quote> = quotes.filter { it.status == QuoteStatus.PENDING }

    /// "N out · $X in play · N won in the last 30 days" — zero parts are
    /// left out; null when every part is zero.
    fun summary(quotes: List<Quote>, now: Instant): String? {
        val pending = waiting(quotes)
        val cutoff = now.minus(Duration.ofDays(30))
        val won = quotes.count { it.status == QuoteStatus.ACCEPTED && it.decidedAt?.let { at -> !at.isBefore(cutoff) } == true }
        val parts = buildList {
            if (pending.isNotEmpty()) {
                add("${pending.size} out")
                pending.sumOf { it.amountCents }.takeIf { it > 0 }?.let { add("${Money.format(it)} in play") }
            }
            if (won > 0) add("$won won in the last 30 days")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    enum class Filter(val label: String) { ALL("All"), WON("Won"), NOT_CHOSEN("Not chosen") }

    /// Settled quotes, newest decision first, through the filter and a
    /// title/code search. "Not chosen" includes cancelled jobs — neither was won.
    fun past(quotes: List<Quote>, filter: Filter, query: String): List<Quote> {
        val needle = query.trim()
        return quotes
            .filter { outcome(it) != null }
            .filter {
                when (filter) {
                    Filter.ALL -> true
                    Filter.WON -> outcome(it) == Outcome.WON
                    Filter.NOT_CHOSEN -> outcome(it) != Outcome.WON
                }
            }
            .filter { needle.isEmpty() || it.job?.title?.contains(needle, ignoreCase = true) == true || it.job?.code?.contains(needle, ignoreCase = true) == true }
            .sortedByDescending { it.decidedAt ?: it.createdAt ?: Instant.EPOCH }
    }

    private fun plural(n: Long, word: String) = if (n == 1L) "1 $word" else "$n ${word}s"
}
