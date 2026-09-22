package com.zivett.app

import com.zivett.app.core.models.CompanySummary
import com.zivett.app.core.models.Invoice
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.Quote
import com.zivett.app.core.models.QuoteStatus
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.core.Loadable
import com.zivett.app.design.ZTone
import com.zivett.app.features.customer.home.ChecklistLogic
import com.zivett.app.features.customer.home.CustomerHomeLogic
import com.zivett.app.features.customer.home.CustomerHomeModel
import com.zivett.app.features.customer.home.HomeHeroLogic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CustomerHomeLogicTest {
    private val pending = Quote(id = 1, amountCents = 18000, status = QuoteStatus.PENDING)

    @Test fun enRouteBeatsEverything() {
        val home = Fixtures.home(
            activeJobs = listOf(Fixtures.job(1, JobStatus.SUBMITTED), Fixtures.job(2, JobStatus.IN_PROGRESS, Fixtures.ravensworth), Fixtures.job(3, JobStatus.EN_ROUTE, Fixtures.ravensworth)),
            invoicesDue = listOf(Fixtures.invoice()),
        )
        val hero = CustomerHomeLogic.resolveHero(home)
        assertEquals(CustomerHomeLogic.HeroKey.ENROUTE, hero.key)
        assertEquals(CustomerHomeLogic.Subject.JobSubject(home.activeJobs[2]), hero.subject)
        assertTrue(CustomerHomeLogic.usesDarkHeader(hero))
    }

    @Test fun arrivedCountsAsEnRoute() {
        assertEquals(CustomerHomeLogic.HeroKey.ENROUTE, CustomerHomeLogic.resolveHero(Fixtures.home(activeJobs = listOf(Fixtures.job(status = JobStatus.ARRIVED, company = Fixtures.ravensworth)))).key)
    }

    @Test fun moneyOwedBeatsDecisionsAndWaiting() {
        val home = Fixtures.home(activeJobs = listOf(Fixtures.job(1, JobStatus.SUBMITTED, quotes = listOf(pending)), Fixtures.job(2, JobStatus.SUBMITTED)), invoicesDue = listOf(Fixtures.invoice()))
        assertEquals(CustomerHomeLogic.HeroKey.INVOICE, CustomerHomeLogic.resolveHero(home).key)
    }

    @Test fun quotesNeedNoCompanyAndAPendingQuote() {
        val chosen = Fixtures.job(1, JobStatus.ACCEPTED, Fixtures.ravensworth, listOf(pending))
        assertEquals(CustomerHomeLogic.HeroKey.EMPTY, CustomerHomeLogic.resolveHero(Fixtures.home(activeJobs = listOf(chosen))).key)
        val hero = CustomerHomeLogic.resolveHero(Fixtures.home(activeJobs = listOf(Fixtures.job(2, JobStatus.SUBMITTED, quotes = listOf(pending)))))
        assertEquals(CustomerHomeLogic.HeroKey.QUOTES, hero.key)
        assertFalse(CustomerHomeLogic.usesDarkHeader(hero))
    }

    @Test fun pendingIsSubmittedWithNothingElse() {
        val hero = CustomerHomeLogic.resolveHero(Fixtures.home(activeJobs = listOf(Fixtures.job(status = JobStatus.SUBMITTED))))
        assertEquals(CustomerHomeLogic.HeroKey.PENDING, hero.key)
        assertTrue(CustomerHomeLogic.usesDarkHeader(hero))
    }

    @Test fun fallbacksDependOnWhetherTheyHaveEverBooked() {
        assertEquals(CustomerHomeLogic.HeroKey.FIRSTRUN, CustomerHomeLogic.resolveHero(Fixtures.home(firstTime = true)).key)
        assertEquals(CustomerHomeLogic.HeroKey.EMPTY, CustomerHomeLogic.resolveHero(Fixtures.home(firstTime = false)).key)
    }

    @Test fun greetingsMatchTheWeb() {
        val enroute = CustomerHomeLogic.resolveHero(Fixtures.home(activeJobs = listOf(Fixtures.job(status = JobStatus.EN_ROUTE, company = Fixtures.ravensworth))))
        assertEquals(CustomerHomeLogic.Greeting("Your pro is close", "Ravensworth Plumbing is on the way"), CustomerHomeLogic.greeting(enroute, "Amara"))
        val quotes = CustomerHomeLogic.resolveHero(Fixtures.home(activeJobs = listOf(Fixtures.job(status = JobStatus.SUBMITTED, quotes = listOf(pending, Quote(id = 2, amountCents = 1, status = QuoteStatus.PENDING))))))
        assertEquals("2 new quotes", CustomerHomeLogic.greeting(quotes, "Amara").kicker)
        val firstrun = CustomerHomeLogic.resolveHero(Fixtures.home(firstTime = true))
        assertEquals("Let's get you set up, Amara", CustomerHomeLogic.greeting(firstrun, "Amara").heading)
        assertEquals(CustomerHomeLogic.Greeting("Good to see you", "Hi, Amara"), CustomerHomeLogic.greeting(CustomerHomeLogic.resolveHero(Fixtures.home()), "Amara"))
    }

    @Test fun secondaryRowsAreTheDemotedConcernsInPriorityOrder() {
        val home = Fixtures.home(
            activeJobs = listOf(
                Fixtures.job(1, JobStatus.EN_ROUTE, Fixtures.ravensworth, title = "Kitchen sink leak"),
                Fixtures.job(2, JobStatus.SUBMITTED, quotes = listOf(pending, pending), title = "need quote"),
                Fixtures.job(3, JobStatus.SUBMITTED, title = "I need to light switches installed"),
            ),
            invoicesDue = listOf(Fixtures.invoice("Dishwasher install")),
        )
        val rows = CustomerHomeLogic.secondaryRows(CustomerHomeLogic.resolveHero(home))
        assertEquals(listOf("PAY", "DECIDE", "WAIT"), rows.map { it.tag })
        assertEquals(listOf("Invoice due", "2 quotes waiting", "Finding your pro"), rows.map { it.title })
        assertEquals(listOf("Dishwasher install", "need quote", "I need to light switches installed"), rows.map { it.subtitle })
        assertEquals(listOf(ZTone.DANGER, ZTone.INFO, ZTone.WARNING), rows.map { it.tone })
    }

    @Test fun theHeroIsNeverAlsoASecondaryRow() {
        val hero = CustomerHomeLogic.resolveHero(Fixtures.home(activeJobs = listOf(Fixtures.job(status = JobStatus.IN_PROGRESS, company = Fixtures.ravensworth))))
        assertEquals(CustomerHomeLogic.HeroKey.PROGRESS, hero.key)
        assertTrue(CustomerHomeLogic.secondaryRows(hero).isEmpty())
    }

    @Test fun freshnessLabels() {
        val now = Instant.ofEpochSecond(10_000)
        assertEquals("No location yet", CustomerHomeLogic.freshness(null, now))
        assertEquals("Last update just now", CustomerHomeLogic.freshness(now.minusSeconds(20), now))
        assertEquals("Last update 3m ago", CustomerHomeLogic.freshness(now.minusSeconds(3 * 60), now))
        assertEquals("Last update 2h ago", CustomerHomeLogic.freshness(now.minusSeconds(2 * 3600), now))
    }

    @Test fun modelLoadsAndKeepsStaleDataOnFailedRefresh() = runTest {
        val client = PreviewApiClient(mapOf("api/customer/home" to Fixtures.home(activeJobs = listOf(Fixtures.job(status = JobStatus.EN_ROUTE, company = Fixtures.ravensworth)))))
        val model = CustomerHomeModel(client)
        model.load()
        assertEquals(CustomerHomeLogic.HeroKey.ENROUTE, model.hero?.key)
        client.errors["api/customer/home"] = ApiError.Server(500, null)
        model.load()
        assertNotNull(model.home)

        val offline = PreviewApiClient()
        offline.errors["api/customer/home"] = ApiError.Transport("offline")
        val failed = CustomerHomeModel(offline)
        failed.load()
        assertEquals(Loadable.Failed(ApiError.Transport("offline").userMessage), failed.phase)
    }
}

class HomeHeroLogicTest {
    private fun quote(id: Int, rating: Double?, count: Int? = null) =
        Quote(id = id, amountCents = 10_000 + id, status = QuoteStatus.PENDING, company = CompanySummary(id = id, name = "Company $id", plan = "pro", rating = rating, count = count))

    @Test fun pendingStepsPhraseTheQuoteFlow() {
        val steps = ChecklistLogic.pendingSteps(quoteMode = true)
        assertEquals(listOf("Request submitted", "Collecting quotes from pros", "You choose who does the work"), steps.map { it.label })
        assertEquals(listOf(ChecklistLogic.StepState.DONE, ChecklistLogic.StepState.CURRENT, ChecklistLogic.StepState.TODO), steps.map { it.state })
        assertEquals("now", steps[0].time)
        assertEquals("Finding your nearest pro", ChecklistLogic.pendingSteps(false)[1].label)
    }

    @Test fun progressChecklistTracksTheStage() {
        assertEquals(listOf(ChecklistLogic.StepState.CURRENT, ChecklistLogic.StepState.TODO, ChecklistLogic.StepState.TODO, ChecklistLogic.StepState.TODO), ChecklistLogic.progressSteps(JobStatus.ARRIVED).map { it.state })
        assertEquals(listOf(ChecklistLogic.StepState.DONE, ChecklistLogic.StepState.CURRENT, ChecklistLogic.StepState.TODO, ChecklistLogic.StepState.TODO), ChecklistLogic.progressSteps(JobStatus.IN_PROGRESS).map { it.state })
        val completed = ChecklistLogic.progressSteps(JobStatus.COMPLETED)
        assertEquals(listOf(ChecklistLogic.StepState.DONE, ChecklistLogic.StepState.DONE, ChecklistLogic.StepState.CURRENT, ChecklistLogic.StepState.TODO), completed.map { it.state })
        assertEquals("Invoice & payment", completed.last().label)
        assertEquals("Work is finished", ChecklistLogic.progressHeadline(JobStatus.COMPLETED))
    }

    @Test fun recommendsTheTopRatedWhenComparing() {
        assertEquals(2, HomeHeroLogic.recommendedQuoteId(listOf(quote(1, 4.2), quote(2, 4.9), quote(3, 4.9))))
        assertNull(HomeHeroLogic.recommendedQuoteId(listOf(quote(1, 5.0))))
        assertNull(HomeHeroLogic.recommendedQuoteId(listOf(quote(1, null), quote(2, null))))
    }

    @Test fun proLineFramesTheTierAndRating() {
        assertEquals("Company 1 · ZiVETT-verified professional · ★ 4.7 (3 reviews)", HomeHeroLogic.proLine(quote(1, 4.7, 3)))
        assertTrue(HomeHeroLogic.proLine(quote(2, 5.0, 1)).endsWith("★ 5.0 (1 review)"))
        assertTrue(HomeHeroLogic.proLine(quote(3, null)).endsWith("new to the platform"))
        // Tier never shows, even for Elite (PARITY.md "plan chips").
        val elite = Quote(id = 1, amountCents = 1, status = QuoteStatus.PENDING, company = CompanySummary(id = 1, name = "Peak", plan = "elite"))
        assertTrue(HomeHeroLogic.proLine(elite).contains("ZiVETT-verified professional"))
        assertTrue(!HomeHeroLogic.proLine(elite).lowercase().contains("elite"))
    }

    @Test fun cancellationFeeInvoicesWearADifferentFace() {
        var invoice = Fixtures.invoice().copy(lineItems = listOf(Invoice.LineItem("Cancellation fee", 4500)))
        assertTrue(HomeHeroLogic.isCancellationFee(invoice))
        invoice = invoice.copy(lineItems = listOf(Invoice.LineItem("Labour", 12000)))
        assertFalse(HomeHeroLogic.isCancellationFee(invoice))
        assertFalse(HomeHeroLogic.isCancellationFee(invoice.copy(lineItems = null)))
    }
}
