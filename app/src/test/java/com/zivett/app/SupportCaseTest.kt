package com.zivett.app

import com.zivett.app.core.models.Dispute
import com.zivett.app.core.models.DisputeResponse
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobArea
import com.zivett.app.core.models.JobResponse
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.features.company.CompanyJobDetailModel
import com.zivett.app.features.customer.jobs.JobDetailModel
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.customer.jobs.JobPresentation.CaseSide
import com.zivett.app.features.customer.jobs.JobPresentation.StepState
import com.zivett.app.design.ZTone
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/// The open-support-case card on both job pages (`SupportCaseCard.vue`):
/// tolerant decoding (the complaint text rides only for its author), the
/// labels, the three-step progress, and the copy — which says "paused",
/// never "held".
class SupportCaseTest {
    private fun case(status: String = "open", mine: Boolean = false, kind: String = "quality") =
        Dispute(id = 9, code = "DSP-Y82AAK", kind = kind, status = status, mine = mine, createdAt = Instant.parse("2026-10-09T18:00:00Z"))

    @Test fun decodesACaseWithAndWithoutItsBody() {
        val author = JsonCoding.json.decodeFromString<Dispute>("""{"id":9,"code":"DSP-Y82AAK","kind":"quality","status":"open","body":"Leak is back","mine":true,"resolution":null,"created_at":"2026-10-09T18:00:00.000000Z","resolved_at":null}""")
        assertEquals("Leak is back", author.body)
        assertTrue(author.mine)

        val other = JsonCoding.json.decodeFromString<Dispute>("""{"id":9,"code":"DSP-Y82AAK","kind":"conduct","status":"under_review","mine":false,"created_at":"2026-10-09T18:00:00.000000Z"}""")
        assertNull(other.body)
        assertFalse(other.mine)

        // An older server sends no `mine` at all.
        assertFalse(JsonCoding.json.decodeFromString<Dispute>("""{"id":1,"kind":"billing","status":"open"}""").mine)
    }

    @Test fun theCompanyJobDecodesItsDisputes() {
        val job = JsonCoding.json.decodeFromString<JobResponse>("""{"job":{"id":5,"title":"Leak","status":"invoiced","category":{"id":1,"code":"PL","slug":"plumbing","name":"Plumbing"},"disputes":[{"id":9,"code":"DSP-Y82AAK","kind":"safety","status":"awaiting_evidence","mine":false,"created_at":"2026-10-09T18:00:00.000000Z","resolved_at":null}]}}""").job
        assertEquals(listOf("DSP-Y82AAK"), JobPresentation.openCases(job).map { it.code })
    }

    @Test fun labelsMatchTheWeb() {
        assertEquals("Support case DSP-Y82AAK — Work quality", JobPresentation.caseTitle(case()))
        assertEquals("Rework requested", JobPresentation.disputeKindLabel("rework"))
        assertEquals("Billing query", JobPresentation.disputeKindLabel("billing"))
        assertEquals("Conduct or behaviour", JobPresentation.disputeKindLabel("conduct"))
        assertEquals(JobPresentation.Meta("Open", ZTone.DANGER), JobPresentation.disputeStatusMeta("open"))
        assertEquals(JobPresentation.Meta("Under review", ZTone.WARNING), JobPresentation.disputeStatusMeta("under_review"))
        assertEquals(JobPresentation.Meta("Awaiting evidence", ZTone.DANGER), JobPresentation.disputeStatusMeta("awaiting_evidence"))
    }

    @Test fun onlyUnresolvedCasesCount() {
        val job = Fixtures.job(status = JobStatus.INVOICED).copy(disputes = listOf(case(), case(status = "resolved").copy(id = 10), case().copy(id = 11, resolvedAt = Instant.now())))
        assertEquals(listOf(9), JobPresentation.openCases(job).map { it.id })
        assertTrue(JobPresentation.openCases(Fixtures.job()).isEmpty())
    }

    @Test fun stepsFollowTheStatus() {
        assertEquals(listOf("Reported" to StepState.DONE, "Under review" to StepState.TODO, "Resolved" to StepState.TODO), JobPresentation.caseSteps(case("open")))
        assertEquals(StepState.CURRENT, JobPresentation.caseSteps(case("under_review"))[1].second)
        assertEquals("More details needed" to StepState.WARN, JobPresentation.caseSteps(case("awaiting_evidence"))[1])
    }

    @Test fun reporterLineNamesWhoReportedIt() {
        assertEquals("You reported this on October 9.", JobPresentation.caseReporterLine(case(mine = true), CaseSide.BOOKER, ZoneOffset.UTC))
        assertEquals("Your pro reported a problem on October 9.", JobPresentation.caseReporterLine(case(), CaseSide.BOOKER, ZoneOffset.UTC))
        assertEquals("The customer reported a problem on October 9.", JobPresentation.caseReporterLine(case(), CaseSide.COMPANY, ZoneOffset.UTC))
    }

    @Test fun nextStepCopySaysPausedNeverHeld() {
        assertEquals("Support needs more from you — check your notifications and reply with photos or details.", JobPresentation.caseNextStep(case("awaiting_evidence", mine = true), CaseSide.BOOKER))
        assertEquals("Support has asked for more details and may reach out to you.", JobPresentation.caseNextStep(case("awaiting_evidence"), CaseSide.COMPANY))
        assertEquals("We'll notify you when support starts reviewing it and when it's resolved. Payment and closing the job are paused while the case is open.", JobPresentation.caseNextStep(case(mine = true), CaseSide.BOOKER))
        assertEquals("Support may reach out to you while they look into it. Your payout and closing the job are paused while the case is open.", JobPresentation.caseNextStep(case("under_review"), CaseSide.COMPANY))
        assertEquals("Payment is paused while support case DSP-Y82AAK is open — your card isn't charged until it's resolved.", JobPresentation.paymentPausedCopy(case()))

        val manage = JobPresentation.cancelManageCopy(Fixtures.job(status = JobStatus.INVOICED).copy(disputes = listOf(case("under_review"))))
        assertEquals("Support case DSP-Y82AAK is under review — payment and closing are paused until it's resolved.", manage)
        assertFalse(JobPresentation.cancelManageCopy(Fixtures.job(status = JobStatus.INVOICED)).contains("held"))
    }

    /// A report from either side shows its case card at once — and a live
    /// refetch that already carried it never doubles it.
    @Test fun aNewReportIsGraftedIntoTheJob() = runTest {
        val company = PreviewApiClient(mapOf("api/company/jobs/5" to JobResponse(Fixtures.job(id = 5, status = JobStatus.IN_PROGRESS)), "api/company/jobs/5/report" to DisputeResponse(case(mine = true))))
        val companyModel = CompanyJobDetailModel(5, company)
        companyModel.load()
        companyModel.report("conduct", "Abusive on site")
        assertEquals(listOf(9), companyModel.job?.disputes?.map { it.id })

        val already: Job = Fixtures.job(id = 5, status = JobStatus.INVOICED).copy(disputes = listOf(case(mine = true)))
        val booker = PreviewApiClient(mapOf("api/customer/jobs/5" to JobResponse(already), "api/customer/jobs/5/report" to DisputeResponse(case(mine = true))))
        val bookerModel = JobDetailModel(5, booker, JobArea.customer)
        bookerModel.load()
        bookerModel.report("quality", "Leak is back")
        assertEquals(listOf(9), bookerModel.job?.disputes?.map { it.id })
    }
}
