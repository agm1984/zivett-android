package com.zivett.app

import com.zivett.app.core.models.CompanySummary
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.Quote
import com.zivett.app.core.models.QuoteStatus
import com.zivett.app.design.ZTone
import com.zivett.app.features.company.CalendarChips
import com.zivett.app.features.company.CompanyPresentation
import com.zivett.app.features.company.CredentialFile
import com.zivett.app.features.customer.jobs.JobPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobPresentationTest {
    private val pending = Quote(id = 1, amountCents = 18_000, status = QuoteStatus.PENDING)

    @Test fun statusLabelsMatchTheWeb() {
        assertEquals(JobPresentation.Meta("En route", ZTone.DANGER), JobPresentation.meta(JobStatus.EN_ROUTE))
        assertEquals(JobPresentation.Meta("Invoice issued", ZTone.SUCCESS), JobPresentation.meta(JobStatus.INVOICED))
        assertEquals(JobPresentation.Meta("Closed", ZTone.SUCCESS), JobPresentation.meta(JobStatus.WARRANTY_ACTIVE))
        assertEquals(JobPresentation.Meta("Closed", ZTone.NEUTRAL), JobPresentation.meta(JobStatus.WARRANTY_EXPIRED))
    }

    @Test fun openJobsWithQuotesShowTheCount() {
        assertEquals(JobPresentation.Meta("1 quote in", ZTone.WARNING), JobPresentation.display(Fixtures.job(status = JobStatus.SUBMITTED, quotes = listOf(pending))))
        assertEquals("2 quotes in", JobPresentation.display(Fixtures.job(status = JobStatus.MATCHED, quotes = listOf(pending, Quote(id = 2, amountCents = 1, status = QuoteStatus.PENDING)))).label)
        assertEquals("Accepted", JobPresentation.display(Fixtures.job(status = JobStatus.ACCEPTED, quotes = listOf(pending))).label)
    }

    @Test fun timelineMarksDoneCurrentTodo() {
        val states = JobPresentation.timeline(JobStatus.EN_ROUTE).map { it.state }
        assertEquals(listOf(JobPresentation.StepState.DONE, JobPresentation.StepState.DONE, JobPresentation.StepState.DONE, JobPresentation.StepState.CURRENT) + List(5) { JobPresentation.StepState.TODO }, states)
        assertTrue(JobPresentation.timeline(JobStatus.WARRANTY_EXPIRED).all { it.state == JobPresentation.StepState.DONE })
        assertTrue(JobPresentation.timeline(JobStatus.CANCELLED).all { it.state == JobPresentation.StepState.TODO })
    }

    @Test fun priceLabelPrefersTheMostSettledFigure() {
        var job = Fixtures.job(status = JobStatus.SUBMITTED, quotes = listOf(Quote(id = 1, amountCents = 18_050, status = QuoteStatus.PENDING), Quote(id = 2, amountCents = 25_000, status = QuoteStatus.PENDING)))
        assertEquals("from $181", JobPresentation.priceLabel(job))
        job = job.copy(quotes = listOf(Quote(id = 1, amountCents = 18_050, status = QuoteStatus.ACCEPTED)))
        assertEquals("$180.50", JobPresentation.priceLabel(job))
        job = job.copy(invoice = Fixtures.invoice())
        assertEquals("$135.00", JobPresentation.priceLabel(job))
        assertNull(JobPresentation.priceLabel(Fixtures.job()))
    }

    @Test fun windowFormatting() {
        assertEquals("Sun, Aug 9 · Morning", JobPresentation.windowSlot("2026-08-09", "morning"))
        assertEquals("Sun, Aug 9", JobPresentation.windowSlot("2026-08-09", null))
        assertEquals("Sunday, August 9 — Afternoon (12pm–5pm)", JobPresentation.arrivalLabel("2026-08-09", "afternoon"))
    }

    @Test fun warrantyCopyNeverHardcodesANumber() {
        assertEquals("48-hour workmanship warranty", JobPresentation.warrantyPhrase(2))
        assertEquals("90-day workmanship warranty", JobPresentation.warrantyPhrase(90))
        assertEquals("workmanship warranty", JobPresentation.warrantyPhrase(null))
        assertEquals("48 hours", JobPresentation.warrantyLength(2))
    }

    @Test fun liveLines() {
        assertEquals("Finding your pro", JobPresentation.liveLine(Fixtures.job(status = JobStatus.SUBMITTED)))
        assertEquals("1 quote to review", JobPresentation.liveLine(Fixtures.job(status = JobStatus.SUBMITTED, quotes = listOf(pending))))
        assertEquals("Ravensworth Plumbing is on the way", JobPresentation.liveLine(Fixtures.job(status = JobStatus.EN_ROUTE, company = Fixtures.ravensworth)))
        assertTrue(JobPresentation.isLive(JobStatus.ARRIVED))
        assertFalse(JobPresentation.isLive(JobStatus.ACCEPTED))
    }

    @Test fun cancelCopyFollowsTheFee() {
        var job = Fixtures.job(status = JobStatus.SUBMITTED, quotes = listOf(pending, Quote(id = 2, amountCents = 1, status = QuoteStatus.PENDING)))
        assertTrue(JobPresentation.cancelManageCopy(job).startsWith("Free to cancel"))
        assertTrue(JobPresentation.cancelConfirmCopy(job).contains("the 2 pending quotes"))
        assertEquals("Cancel it", JobPresentation.cancelButtonTitle(job))
        job = Fixtures.job(status = JobStatus.ARRIVED, company = Fixtures.ravensworth).copy(cancellationFeeCents = 12_500)
        assertTrue(JobPresentation.cancelManageCopy(job).contains("$125.00 cancellation fee"))
        assertTrue(JobPresentation.cancelConfirmCopy(job).startsWith("Covers your pro's crew time"))
        assertEquals("Cancel & pay $125.00", JobPresentation.cancelButtonTitle(job))
        assertTrue(JobPresentation.cancelManageCopy(Fixtures.job(status = JobStatus.COMPLETED, company = Fixtures.ravensworth)).startsWith("Something wrong?"))
    }

    @Test fun phaseDrivesCardOrder() {
        assertEquals(JobPresentation.Phase.DECIDING, JobPresentation.phase(Fixtures.job(status = JobStatus.SUBMITTED)))
        assertEquals(JobPresentation.Phase.WORKING, JobPresentation.phase(Fixtures.job(status = JobStatus.IN_PROGRESS, company = Fixtures.ravensworth)))
        assertEquals(JobPresentation.Phase.SETTLING, JobPresentation.phase(Fixtures.job(status = JobStatus.COMPLETED, company = Fixtures.ravensworth)))
        val paid = Fixtures.job(status = JobStatus.INVOICED, company = Fixtures.ravensworth).copy(invoice = Fixtures.invoice().copy(status = "paid"))
        assertEquals(JobPresentation.Phase.RECORD, JobPresentation.phase(paid))
        assertEquals(JobPresentation.Phase.RECORD, JobPresentation.phase(Fixtures.job(status = JobStatus.CANCELLED)))
    }

    @Test fun pendingQuotesCarryTheCompanyName() {
        val quote = Quote(id = 1, amountCents = 1, status = QuoteStatus.PENDING, company = CompanySummary(id = 4, name = "Pro A", plan = "pro", rating = 4.8, count = 12))
        assertEquals("Pro A", JobPresentation.quoteCompanyName(quote))
        // Tier never shows, even for a Pro-plan company (PARITY.md "plan chips").
        assertEquals("ZiVETT-verified professional · ★ 4.8 (12 reviews)", JobPresentation.proLine(quote.company))
        assertEquals("ZiVETT-verified professional · new to the platform", JobPresentation.proLine(CompanySummary(id = 1, name = "New", count = 0)))
    }

    @Test fun companyRules() {
        assertEquals(JobStatus.EN_ROUTE, CompanyPresentation.nextStage(JobStatus.ACCEPTED))
        assertNull(CompanyPresentation.nextStage(JobStatus.COMPLETED))
        assertNull(CompanyPresentation.nextStage(JobStatus.INVOICED))
        assertEquals("15%", CompanyPresentation.percent(1500))
        assertEquals("12.5%", CompanyPresentation.percent(1250).replace(".50", ".5"))
        assertEquals(36_000, CompanyPresentation.quoteTotal(12_000, 1.5, 2))
        assertEquals(5_400, CompanyPresentation.commission(36_000, 1500))
    }

    @Test fun calendarChips() {
        assertEquals("Now", CalendarChips.tag(null))
        assertEquals("M·Z-93U4H3", CalendarChips.chipLabel("morning", "Z-93U4H3"))
        assertEquals("A·Z-1?", CalendarChips.chipLabel("afternoon", "Z-1", tentative = true))
        assertEquals("N·—", CalendarChips.chipLabel("", null))
    }

    @Test fun credentialFileTypes() {
        assertEquals("pdf", CredentialFile.fileExtension("application/pdf", ByteArray(0)))
        assertEquals("jpg", CredentialFile.fileExtension(null, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)))
        assertEquals("png", CredentialFile.fileExtension(null, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
        assertEquals("webp", CredentialFile.fileExtension(null, "RIFF....WEBPVP8 ".toByteArray()))
        assertEquals("pdf", CredentialFile.fileExtension(null, "RIFF....WAVEfmt ".toByteArray()))
        assertEquals("pdf", CredentialFile.fileExtension("application/octet-stream", "%PDF-1.4".toByteArray()))
    }
}
