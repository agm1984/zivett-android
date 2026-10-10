package com.zivett.app

import com.zivett.app.core.models.JobArea
import com.zivett.app.core.models.JobResponse
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.PreviewApiClient
import com.zivett.app.features.company.CompanyJobDetailModel
import com.zivett.app.features.customer.jobs.JobDetailModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/// A `job.updated` ping refetches the open job page quietly: the new
/// state swaps in, a failed refetch keeps what's on screen, and a ping
/// that lands mid-action leaves the action's response to win.
class LiveJobTest {
    @Test fun aPingSwapsInTheRefetchedJob() = runTest {
        val client = PreviewApiClient(mapOf("api/customer/jobs/5" to JobResponse(Fixtures.job(id = 5, status = JobStatus.ACCEPTED))))
        val model = JobDetailModel(5, client, JobArea.customer)
        model.load()

        client.responses["api/customer/jobs/5"] = JobResponse(Fixtures.job(id = 5, status = JobStatus.EN_ROUTE))
        model.refresh()
        assertEquals(JobStatus.EN_ROUTE, model.job?.status)
    }

    @Test fun aFailedRefetchKeepsTheLastGoodJob() = runTest {
        val client = PreviewApiClient(mapOf("api/company/jobs/5" to JobResponse(Fixtures.job(id = 5, status = JobStatus.ACCEPTED))))
        val model = CompanyJobDetailModel(5, client)
        model.load()

        client.errors["api/company/jobs/5"] = ApiError.Server(500, null)
        model.refresh()
        assertEquals(JobStatus.ACCEPTED, model.job?.status)
    }

    @Test fun aPingMidActionIsLeftToTheActionsResponse() = runTest {
        val client = PreviewApiClient(mapOf("api/business/requests/5" to JobResponse(Fixtures.job(id = 5, status = JobStatus.MATCHED))))
        val model = JobDetailModel(5, client, JobArea.business)
        model.load()
        val before = client.sent.size

        model.busy = true
        model.refresh()
        assertEquals(before, client.sent.size)
    }
}
