package com.zivett.app.features.company

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zivett.app.app.CompanyJobRoute
import com.zivett.app.app.CompanyQuotesRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.CompanyCalendar
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSectionHeader
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTopBar
import com.zivett.app.design.ZType
import com.zivett.app.features.customer.book.BookingWizardEngine
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.shared.JobStatusBadge
import com.zivett.app.features.shared.LocalNav
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class CompanyCalendarModel(private val client: ApiClient) {
    var state by mutableStateOf<Loadable<CompanyCalendar>>(Loadable.Loading)
    var toast by mutableStateOf<String?>(null)
    var month by mutableStateOf(YearMonth.now().let { String.format(Locale.US, "%04d-%02d", it.year, it.monthValue) })

    suspend fun load() { state = state.reloaded { client.send(CompanyEndpoints.monthCalendar(month)) } }

    suspend fun shift(delta: Int) {
        val parts = month.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size != 2) return
        val next = YearMonth.of(parts[0], parts[1]).plusMonths(delta.toLong())
        month = String.format(Locale.US, "%04d-%02d", next.year, next.monthValue)
        state = Loadable.Loading
        load()
    }

    suspend fun toggleBlock(dateKey: String) {
        val calendar = state.value ?: return
        try {
            val blocked = calendar.blockedDates.firstOrNull { it.date == dateKey }
            if (blocked != null) { client.send(CompanyEndpoints.unblockDate(blocked.id)); toast = "$dateKey reopened" }
            else { client.send(CompanyEndpoints.blockDate(dateKey)); toast = "$dateKey blocked — scheduled jobs that day leave your feed" }
            load()
        } catch (e: Exception) { toast = e.userMessage }
    }
}

/// Month grid: confirmed jobs, tentative quote proposals, blocked days,
/// non-working days. Tap an empty future day to block/reopen.
@Composable
fun CompanyCalendarScreen(onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val model = remember { CompanyCalendarModel(environment.client) }
    var confirming by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(model) { model.load() }

    fun monthLabel(month: String): String {
        val parts = month.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size != 2) return month
        return YearMonth.of(parts[0], parts[1]).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
    }

    Column {
        ZTopBar("Calendar", onBack = onBack)
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { calendar ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scope.launch { model.shift(-1) } }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month", tint = colors.ink) }
                            Spacer(Modifier.weight(1f))
                            ZHeadline(monthLabel(calendar.month))
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { scope.launch { model.shift(1) } }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next month", tint = colors.ink) }
                        }
                        MonthGrid(calendar, onJob = { nav.navigate(CompanyJobRoute(it)) }, onTentative = { nav.navigate(CompanyQuotesRoute) }) { confirming = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                            Legend(colors.navy, "Job"); Legend(colors.infoSoft, "Proposed"); Legend(colors.dangerSoft, "Blocked"); Legend(colors.surfaceSunken, "Off day")
                        }
                        ZCaption("Tap a job to open it, a pending quote to manage it, or an empty day to block or reopen it. Blocked days drop matching scheduled jobs from your feed, and quotes proposing that day can no longer be accepted at it.")
                        if (calendar.jobs.isNotEmpty()) {
                            ZSectionHeader("This month")
                            for (job in calendar.jobs) {
                                ZCard(padding = ZSpacing.sm, onClick = { nav.navigate(CompanyJobRoute(job.id)) }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            ZBodyStrong(job.title, maxLines = 1)
                                            ZCaption(listOfNotNull(job.code, JobPresentation.windowSlot(job.date, job.window)).joinToString(" · "))
                                        }
                                        JobStatusBadge(job.status)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.padding(ZSpacing.lg))
                    }
                }
            }
        }
    }

    confirming?.let { key ->
        val calendar = model.state.value
        val blocked = calendar?.blockedDates?.any { it.date == key } == true
        val jobs = calendar?.jobs?.filter { it.date == key } ?: emptyList()
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(if (blocked) "Reopen this day?" else "Block this day off?") },
            text = { if (jobs.isNotEmpty()) Text("You have ${jobs.size} confirmed ${if (jobs.size == 1) "job" else "jobs"} this day — blocking does NOT cancel or move them.") },
            confirmButton = { TextButton(onClick = { confirming = null; scope.launch { model.toggleBlock(key) } }) { Text(if (blocked) "Reopen day" else "Block day", color = if (blocked) colors.ink else colors.danger) } },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        ZCaption(label)
    }
}

@Composable
private fun MonthGrid(calendar: CompanyCalendar, onJob: (Int) -> Unit, onTentative: () -> Unit, tap: (String) -> Unit) {
    val colors = ZTheme.colors
    val parts = calendar.month.split("-").mapNotNull { it.toIntOrNull() }
    val first = if (parts.size == 2) YearMonth.of(parts[0], parts[1]) else YearMonth.now()
    val days = first.lengthOfMonth()
    val lead = first.atDay(1).dayOfWeek.value - 1 // Monday-first
    val today = BookingWizardEngine.dateKey(LocalDate.now())

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (name in listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")) ZMono(name, modifier = Modifier.weight(1f))
        }
        for (week in 0 until (lead + days + 6) / 7) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (slot in 0 until 7) {
                    val dayNumber = week * 7 + slot - lead + 1
                    if (dayNumber < 1 || dayNumber > days) { Spacer(Modifier.weight(1f).heightIn(min = 64.dp)); continue }
                    val key = String.format(Locale.US, "%s-%02d", calendar.month, dayNumber)
                    val isoWeekday = slot + 1
                    val working = calendar.workingDays == null || isoWeekday in calendar.workingDays
                    val blocked = calendar.blockedDates.any { it.date == key }
                    val jobs = calendar.jobs.filter { it.date == key }
                    val tentative = (calendar.tentative ?: emptyList()).filter { it.date == key }
                    val isPast = key < today
                    // The whole cell taps to block/reopen; the chips inside are their own targets.
                    Column(
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (blocked) colors.dangerSoft else if (!working) colors.surfaceSunken else colors.surface)
                            .then(if (key == today) Modifier.border(1.dp, colors.navy, RoundedCornerShape(6.dp)) else Modifier)
                            .clickable(enabled = !isPast) { tap(key) }.padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(dayNumber.toString(), style = ZType.caption.copy(fontSize = 11.sp, fontWeight = if (key == today) FontWeight.Bold else FontWeight.Medium), color = if (isPast) colors.inkFaint else colors.ink)
                        for (job in jobs.take(2)) Chip(CalendarChips.chipLabel(job.window, job.code), solid = true) { onJob(job.id) }
                        for (row in tentative.take(2 - minOf(jobs.size, 2))) Chip(CalendarChips.chipLabel(row.window, row.jobCode, tentative = true), solid = false, onClick = onTentative)
                        val overflow = jobs.size + tentative.size - 2
                        if (overflow > 0) ZMono("+$overflow")
                    }
                }
            }
        }
    }
}

/// A cell entry: solid charcoal = confirmed job, dashed outline = tentative quote proposal.
@Composable
private fun Chip(label: String, solid: Boolean, onClick: () -> Unit) {
    val colors = ZTheme.colors
    Text(
        label, style = ZType.mono.copy(fontSize = 8.5.sp, letterSpacing = 0.sp), color = if (solid) colors.onBrand else colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp).clip(RoundedCornerShape(4.dp)).background(if (solid) colors.navy else androidx.compose.ui.graphics.Color.Transparent)
            .then(if (!solid) Modifier.border(1.dp, colors.inkMuted.copy(alpha = 0.6f), RoundedCornerShape(4.dp)) else Modifier).clickable(onClick = onClick).padding(horizontal = 3.dp, vertical = 1.5.dp),
    )
}
