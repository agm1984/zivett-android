package com.zivett.app

import com.zivett.app.core.models.CompanyQuotesResponse
import com.zivett.app.core.models.Quote
import com.zivett.app.core.models.QuoteJob
import com.zivett.app.core.models.QuoteStatus
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.features.company.CompanyQuotesLogic
import com.zivett.app.features.company.CompanyQuotesLogic.Filter
import com.zivett.app.features.company.CompanyQuotesLogic.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/// My quotes (`docs/mobile-api.md` "My quotes"): the you-keep maths, the
/// state wording, the summary line and the Past filter + search.
class CompanyQuotesLogicTest {
    private val now = Instant.parse("2026-10-25T18:00:00Z")
    private fun ago(days: Long = 0, hours: Long = 0) = now.minus(Duration.ofDays(days).plusHours(hours))

    private fun quote(
        id: Int,
        status: QuoteStatus = QuoteStatus.PENDING,
        amount: Int = 20_000,
        released: Boolean = false,
        jobStatus: String? = null,
        decided: Instant? = null,
        title: String = "Kitchen sink leak",
        code: String = "Z-ABC$id",
    ) = Quote(
        id = id, amountCents = amount, status = status, decidedAt = decided, createdAt = ago(days = 2),
        job = QuoteJob(id = 100 + id, code = code, title = title, status = jobStatus, quotesReleased = released, category = Fixtures.category),
    )

    @Test fun youKeepIsTheAmountLessAHalfUpCommission() {
        assertEquals(17_000, CompanyQuotesLogic.youKeepCents(20_000, 1500))
        // 12,345 × 15% = 1,851.75 → 1,852 commission.
        assertEquals(1_852, CompanyQuotesLogic.commissionCents(12_345, 1500))
        assertEquals(10_493, CompanyQuotesLogic.youKeepCents(12_345, 1500))
        // Exactly half a cent rounds up: 10 × 5% = 0.5 → 1.
        assertEquals(1, CompanyQuotesLogic.commissionCents(10, 500))
        assertEquals(9_999_999, CompanyQuotesLogic.youKeepCents(9_999_999, 0))
    }

    @Test fun waitingStateFollowsTheRelease() {
        assertEquals("With ZiVETT for review", CompanyQuotesLogic.waitingState(quote(1)))
        assertEquals("The booker is choosing", CompanyQuotesLogic.waitingState(quote(1, released = true)))
    }

    @Test fun reviseOnlyBeforeRelease() {
        assertTrue(CompanyQuotesLogic.canRevise(quote(1)))
        assertFalse(CompanyQuotesLogic.canRevise(quote(1, released = true)))
        assertFalse(CompanyQuotesLogic.canRevise(quote(1, status = QuoteStatus.ACCEPTED)))
    }

    @Test fun sentAgoReadsLikeAPerson() {
        assertEquals("Sent just now", CompanyQuotesLogic.sentAgo(now, now))
        assertEquals("Sent 1 minute ago", CompanyQuotesLogic.sentAgo(now.minusSeconds(60), now))
        assertEquals("Sent 3 hours ago", CompanyQuotesLogic.sentAgo(ago(hours = 3), now))
        assertEquals("Sent yesterday", CompanyQuotesLogic.sentAgo(ago(hours = 30), now))
        assertEquals("Sent 2 days ago", CompanyQuotesLogic.sentAgo(ago(days = 2), now))
        // A clock skew never says "-1 minutes".
        assertEquals("Sent just now", CompanyQuotesLogic.sentAgo(now.plusSeconds(90), now))
    }

    @Test fun outcomesWordACancelledJob() {
        assertNull(CompanyQuotesLogic.outcome(quote(1)))
        assertEquals(Outcome.WON, CompanyQuotesLogic.outcome(quote(1, QuoteStatus.ACCEPTED)))
        assertEquals(Outcome.NOT_CHOSEN, CompanyQuotesLogic.outcome(quote(1, QuoteStatus.DECLINED, jobStatus = "accepted")))
        assertEquals(Outcome.CANCELLED, CompanyQuotesLogic.outcome(quote(1, QuoteStatus.DECLINED, jobStatus = "cancelled")))
        assertEquals("Job cancelled", Outcome.CANCELLED.label)
    }

    @Test fun summaryCountsWhatIsOutAndRecentWins() {
        val quotes = listOf(
            quote(1, amount = 20_000), quote(2, amount = 15_050),
            quote(3, QuoteStatus.ACCEPTED, decided = ago(days = 3)),
            quote(4, QuoteStatus.ACCEPTED, decided = ago(days = 31)),
            quote(5, QuoteStatus.DECLINED, decided = ago(days = 1)),
        )
        assertEquals("2 out · $350.50 in play · 1 won in the last 30 days", CompanyQuotesLogic.summary(quotes, now))
        // Zero parts drop out.
        assertEquals("1 won in the last 30 days", CompanyQuotesLogic.summary(quotes.drop(2), now))
        assertEquals("1 out · $200.00 in play", CompanyQuotesLogic.summary(quotes.take(1), now))
        assertNull(CompanyQuotesLogic.summary(emptyList(), now))
        // An older server sends no decided_at: the win can't be dated, so it isn't counted.
        assertNull(CompanyQuotesLogic.summary(listOf(quote(6, QuoteStatus.ACCEPTED)), now))
    }

    @Test fun pastFiltersSearchesAndSortsNewestFirst() {
        val quotes = listOf(
            quote(1),
            quote(2, QuoteStatus.ACCEPTED, decided = ago(days = 5), title = "Deck stain"),
            quote(3, QuoteStatus.DECLINED, decided = ago(days = 1), jobStatus = "cancelled", title = "Furnace check"),
            quote(4, QuoteStatus.DECLINED, decided = ago(days = 3), title = "Tap swap", code = "Z-QQQ9"),
        )
        assertEquals(listOf(3, 4, 2), CompanyQuotesLogic.past(quotes, Filter.ALL, "").map { it.id })
        assertEquals(listOf(2), CompanyQuotesLogic.past(quotes, Filter.WON, "").map { it.id })
        assertEquals(listOf(3, 4), CompanyQuotesLogic.past(quotes, Filter.NOT_CHOSEN, "").map { it.id })
        assertEquals(listOf(2), CompanyQuotesLogic.past(quotes, Filter.ALL, "  deck ").map { it.id })
        assertEquals(listOf(4), CompanyQuotesLogic.past(quotes, Filter.ALL, "qqq").map { it.id })
        assertTrue(CompanyQuotesLogic.past(quotes, Filter.WON, "tap").isEmpty())
        assertEquals(listOf(1), CompanyQuotesLogic.waiting(quotes).map { it.id })
    }

    @Test fun decodesTheNewKeysAndToleratesAnOlderServer() {
        val fresh = JsonCoding.json.decodeFromString<CompanyQuotesResponse>(
            """{"quotes":[{"id":1,"amount_cents":20000,"status":"declined","decided_at":"2026-10-24T17:00:00.000000Z","job":{"id":9,"code":"Z-1","title":"Sink","status":"cancelled","quotes_released":true,"photos":[{"id":3,"kind":"issue","url":"https://x/p.jpg"}]}}],"hourly_rates":[],"commission_bps":1000}""",
        )
        val row = fresh.quotes.single()
        assertEquals(Instant.parse("2026-10-24T17:00:00Z"), row.decidedAt)
        assertTrue(row.job!!.quotesReleased)
        assertEquals("cancelled", row.job.status)
        assertEquals(1, row.job.photos!!.size)
        assertEquals(1000, fresh.commissionBps)

        val old = JsonCoding.json.decodeFromString<CompanyQuotesResponse>(
            """{"quotes":[{"id":1,"amount_cents":20000,"status":"pending","job":{"id":9,"code":"Z-1","title":"Sink"}}]}""",
        )
        val legacy = old.quotes.single()
        assertNull(legacy.decidedAt)
        assertFalse(legacy.job!!.quotesReleased)
        assertNull(legacy.job.status)
        assertTrue(CompanyQuotesLogic.canRevise(legacy))
    }
}
