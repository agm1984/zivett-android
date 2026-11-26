@file:UseSerializers(LaravelInstantSerializer::class)

package com.zivett.app.core.models

import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.LaravelInstantSerializer
import com.zivett.app.core.network.MultipartForm
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/// The books (Business tools): expenses, time on jobs, payroll, reports.

/// `ProExpenses::row`.
@Serializable
data class ProExpense(
    val id: Int,
    val spentOn: String,
    val category: String,
    val categoryLabel: String? = null,
    val description: String = "",
    val amountCents: Int = 0,
    val gstCents: Int? = null,
    val receipt: Receipt? = null,
) {
    @Serializable data class Receipt(val id: Int? = null, val url: String, val thumbUrl: String? = null)
}

/// `GET api/company/business/expenses?from=&to=`.
@Serializable
data class ProExpensesResponse(
    val from: String,
    val to: String,
    val expenses: List<ProExpense> = emptyList(),
    val totals: Totals = Totals(),
    val byCategory: List<ByCategory> = emptyList(),
    val categories: List<LabelValue> = emptyList(),
    /// Costs recorded on jobs in the period, read-only (they already count
    /// on each job and in Reports).
    val jobCosts: List<JobCost> = emptyList(),
) {
    @Serializable data class Totals(val amountCents: Int = 0, val gstCents: Int = 0, val count: Int = 0)
    @Serializable data class ByCategory(val category: String, val label: String, val amountCents: Int = 0)
    @Serializable data class JobCost(val id: Int, val description: String = "", val amountCents: Int = 0, val spentOn: String? = null, val jobId: Int? = null, val jobTitle: String? = null, val jobCode: String? = null)
}

@Serializable data class ProExpenseResponse(val expense: ProExpense)

/// `ProTimesheets::sheet` — who worked how long on own jobs, per day.
@Serializable
data class Timesheet(
    val from: String,
    val to: String,
    val people: List<Person> = emptyList(),
    val days: List<String> = emptyList(),
    val totalMinutes: Int = 0,
) {
    @Serializable
    data class Person(
        val user: CompanyCalendar.CrewTech? = null,
        val byDay: Map<String, Int> = emptyMap(),
        val totalMinutes: Int = 0,
        val entries: List<Entry> = emptyList(),
    )

    @Serializable
    data class Entry(val id: Int, val workedOn: String? = null, val minutes: Int = 0, val note: String? = null, val job: JobRef? = null)
    @Serializable data class JobRef(val id: Int, val code: String? = null, val title: String? = null, val customer: String? = null)
}

/// `Payroll::sheet` — the team clock's pay period.
@Serializable
data class PayrollSheet(
    val period: MyHours.Period,
    val previous: String? = null,
    val next: String? = null,
    val people: List<Person> = emptyList(),
    val totals: Totals = Totals(),
) {
    @Serializable
    data class Person(
        val user: User,
        val totals: MyHours.Totals = MyHours.Totals(),
        val pay: Pay? = null,
        val review: Int = 0,
        val open: Boolean = false,
        val approved: Boolean = false,
        val days: List<Day> = emptyList(),
    )

    @Serializable data class User(val id: Int, val name: String, val photoUrl: String? = null, val takesJobs: Boolean = true)

    @Serializable
    data class Pay(val rateCents: Int? = null, val regularCents: Int? = null, val overtimeCents: Int? = null, val doubleCents: Int? = null, val vacationCents: Int? = null, val totalCents: Int? = null)

    @Serializable
    data class Day(
        val date: String,
        val paidMinutes: Int = 0,
        val overtimeMinutes: Int = 0,
        val doubleMinutes: Int = 0,
        val breakMinutes: Int = 0,
        val shifts: List<TeamShift> = emptyList(),
        val jobMinutes: Int = 0,
    )

    @Serializable
    data class Totals(val paidMinutes: Int = 0, val overtimeMinutes: Int = 0, val grossCents: Int? = null, val review: Int = 0, val missingRates: Int? = null)
}

@Serializable data class PayrollApproveBody(val date: String, val userIds: List<Int>? = null)

/// An admin's shift edit: times as ISO local ("2026-11-12T08:00"), as the web sends.
@Serializable
data class PayrollShiftBody(val startedAt: String, val endedAt: String?, val breakMinutes: Int = 0, val note: String? = null, val userId: Int? = null)

/// `GET api/company/business/reports?range=`.
@Serializable
data class ProReport(
    val allowed: Boolean = true,
    val upgrade: PlanOffer? = null,
    val range: String? = null,
    val from: String? = null,
    val to: String? = null,
    val months: List<Month> = emptyList(),
    val totals: Totals = Totals(),
    val quotes: Quotes? = null,
    val daysToPay: DaysToPay? = null,
    val topCustomers: List<TopCustomer> = emptyList(),
    val trades: List<Trade> = emptyList(),
) {
    @Serializable data class Month(val month: String, val invoicedCents: Int = 0, val collectedCents: Int = 0, val jobCostsCents: Int = 0, val expensesCents: Int = 0, val leftCents: Int = 0)
    @Serializable data class Totals(val invoicedCents: Int = 0, val collectedCents: Int = 0, val costsCents: Int = 0, val leftCents: Int = 0, val owedCents: Int = 0)
    @Serializable data class Quotes(val sent: Int = 0, val won: Int = 0, val lost: Int = 0, val open: Int = 0, val winRate: Int? = null)
    @Serializable data class DaysToPay(val median: Int = 0, val invoices: Int = 0)
    @Serializable data class TopCustomer(val id: Int? = null, val name: String? = null, val invoicedCents: Int = 0, val invoices: Int = 0)
    @Serializable data class Trade(val name: String, val jobs: Int = 0)
}

object BooksEndpoints {
    private const val BASE = "api/company/business"

    fun expenses(from: String?, to: String?) = ApiRequest.get<ProExpensesResponse>("$BASE/expenses", listOfNotNull(from?.let { "from" to it }, to?.let { "to" to it }))

    /// Create (`id` null) or update (POST, since PHP can't read multipart PATCH).
    fun saveExpense(id: Int?, spentOn: String, category: String, description: String, amountCents: Int, gstCents: Int?, receipt: ByteArray?, removeReceipt: Boolean = false): ApiRequest<ProExpenseResponse> {
        val form = MultipartForm()
        form.addField("spent_on", spentOn)
        form.addField("category", category)
        form.addField("description", description)
        form.addField("amount_cents", amountCents.toString())
        gstCents?.let { form.addField("gst_cents", it.toString()) }
        if (removeReceipt) form.addField("remove_receipt", "1")
        receipt?.let { form.addFile("receipt", "receipt.jpg", "image/jpeg", it) }
        return ApiRequest.multipart(if (id == null) "$BASE/expenses" else "$BASE/expenses/$id", form)
    }
    fun deleteExpense(id: Int) = ApiRequest.empty(ApiRequest.Method.DELETE, "$BASE/expenses/$id")

    fun timesheets(from: String?, to: String?) = ApiRequest.get<Timesheet>("$BASE/timesheets", listOfNotNull(from?.let { "from" to it }, to?.let { "to" to it }))

    fun payroll(date: String?) = ApiRequest.get<PayrollSheet>("$BASE/payroll", listOfNotNull(date?.let { "date" to it }))
    fun approve(date: String, userIds: List<Int>? = null) = ApiRequest.post<PayrollSheet, PayrollApproveBody>("$BASE/payroll/approve", PayrollApproveBody(date, userIds))
    fun reopen(date: String, userIds: List<Int>? = null) = ApiRequest.delete<PayrollSheet, PayrollApproveBody>("$BASE/payroll/approve", PayrollApproveBody(date, userIds))
    fun addShift(body: PayrollShiftBody) = ApiRequest.post<PayrollSheet, PayrollShiftBody>("$BASE/payroll/shifts", body)
    fun editShift(id: Int, body: PayrollShiftBody) = ApiRequest.patch<PayrollSheet, PayrollShiftBody>("$BASE/payroll/shifts/$id", body.copy(userId = null))
    fun deleteShift(id: Int) = ApiRequest.delete<PayrollSheet>("$BASE/payroll/shifts/$id")

    /// `range`: last_12_months | this_year | last_year.
    fun reports(range: String) = ApiRequest.get<ProReport>("$BASE/reports", listOf("range" to range))
}
