package com.zivett.app.features.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.MyDayEndpoints
import com.zivett.app.core.models.OwnJob
import com.zivett.app.core.models.OwnJobStatus
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZFlowRow
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZHeroPanel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZPhotoAvatar
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.design.ZType
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.shared.LocalNavBadges
import com.zivett.app.features.shared.PhotoGrid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

/// The own job page's pure rules (web `pages/company/OwnJobCrew.vue`).
object OwnJobLogic {
    fun isOpen(job: OwnJob): Boolean = job.status == OwnJobStatus.SCHEDULED || job.status == OwnJobStatus.IN_PROGRESS

    /// The one status button: Start (scheduled) or Finish (in progress),
    /// only when the server lists that move for this person.
    fun nextStatus(job: OwnJob): Pair<String, String>? = when {
        OwnJobStatus.IN_PROGRESS in job.next -> OwnJobStatus.IN_PROGRESS to "Start job"
        OwnJobStatus.COMPLETED in job.next -> OwnJobStatus.COMPLETED to "Finish job"
        else -> null
    }

    /// The crew runs a job timer on open jobs.
    fun showsClock(job: OwnJob, me: Int?): Boolean = isOpen(job) && job.onCrew(me)

    fun statusTone(status: String): ZTone = when (status) {
        OwnJobStatus.COMPLETED -> ZTone.SUCCESS
        OwnJobStatus.IN_PROGRESS -> ZTone.WARNING
        OwnJobStatus.CANCELLED -> ZTone.DANGER
        else -> ZTone.INFO
    }

    /// "Thu, Nov 12 · Afternoon · starts 1:00 pm · about 2.5 h".
    fun whenLine(job: OwnJob): String? {
        val date = job.scheduledDate ?: return null
        val parts = mutableListOf(JobPresentation.windowSlot(date, job.scheduledWindow))
        job.startTime?.let { parts += "starts ${MyDayLogic.formatClock(it)}" }
        job.estimatedHours?.takeIf { it > 0 }?.let { parts += "about ${hours(it)}" }
        return parts.joinToString(" · ")
    }

    /// "2.5 h" / "1 h".
    fun hours(value: Double): String = if (value % 1.0 == 0.0) "${value.toInt()} h" else String.format(Locale.US, "%.1f h", value).replace(".0 h", " h")

    /// "1 h 05 min" / "34 min" — a time entry's length.
    fun minutes(total: Int): String = if (total < 60) "$total min" else String.format(Locale.US, "%d h %02d min", total / 60, total % 60)

    /// The total logged, running timers included.
    fun totalMinutes(job: OwnJob): Int = job.time.sumOf { it.minutes }
}

class OwnJobModel(val jobId: Int, private val client: ApiClient) {
    var state by mutableStateOf<Loadable<OwnJob>>(Loadable.Loading)
    var busy by mutableStateOf(false)
        private set
    var toast by mutableStateOf<String?>(null)

    val job: OwnJob? get() = state.value

    suspend fun load() { state = state.reloaded { client.send(MyDayEndpoints.ownJob(jobId)).job } }

    /// A live refetch, skipped mid-action (the response wins).
    suspend fun refresh() { if (!busy) load() }

    private suspend fun act(success: String?, operation: suspend () -> OwnJob) {
        if (busy) return
        busy = true
        try {
            state = Loadable.Loaded(operation())
            success?.let { toast = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast = e.userMessage
        } finally {
            busy = false
        }
    }

    /// Starting runs your job timer; finishing stops every one on the job.
    suspend fun setStatus(status: String) = act(if (status == OwnJobStatus.COMPLETED) "Job finished — your time is on the job" else "Job started — the job timer is running") {
        client.send(MyDayEndpoints.ownJobStatus(jobId, status)).job
    }

    /// The job timer — not the working day (that's My day's clock card).
    suspend fun clockIn() = act("Job timer started on ${job?.code ?: "this job"}") { client.send(MyDayEndpoints.clockIn(jobId)).job }
    suspend fun clockOut() = act("Job timer stopped — your time is on the job") { client.send(MyDayEndpoints.clockOut(jobId)).job }
}

/// One of the company's own jobs as its crew sees it
/// (`GET /api/company/own-jobs/{id}`): what, when, where and who to call,
/// Start / Finish, the person's clock and the hours logged. No prices.
@Composable
fun OwnJobScreen(jobId: Int, onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember(jobId) { OwnJobModel(jobId, environment.client) }
    val me = environment.session.user?.id

    LaunchedEffect(model) { model.load() }
    // Back from the foreground or a live notification (the office moved
    // it, a teammate finished it): re-read, keeping the last good copy.
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(model) {
        if (resumed) scope.launch { model.refresh() }
        resumed = true
        onPauseOrDispose { }
    }
    val liveEvents = LocalNavBadges.current?.liveEvents ?: 0
    LaunchedEffect(liveEvents) { if (liveEvents > 0) model.refresh() }

    Column {
        ZTopBar(model.job?.code ?: "Job", onBack = onBack)
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { job ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        ZHeroPanel {
                            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                                ZBadge(OwnJobStatus.label(job.status), OwnJobLogic.statusTone(job.status))
                                ZBadge("Own job")
                            }
                            Text(job.title.ifBlank { "Job" }, style = ZType.title, color = Color.White)
                            Text(
                                listOfNotNull(job.code, job.category?.name, OwnJobLogic.whenLine(job)).joinToString(" · "),
                                style = ZType.caption, color = Color.White.copy(alpha = 0.75f),
                            )
                        }

                        // What to press: Start / Finish, and the person's job timer.
                        val next = OwnJobLogic.nextStatus(job)
                        val clock = OwnJobLogic.showsClock(job, me)
                        if (next != null || clock) {
                            ZCard {
                                next?.let { (status, label) ->
                                    ZButton(label, style = ZButtonStyle.SUCCESS, loading = model.busy) { scope.launch { model.setStatus(status) } }
                                    ZCaption(if (status == OwnJobStatus.IN_PROGRESS) "Starting runs your job timer." else "Finishing stops every job timer on this job.")
                                }
                                if (clock) {
                                    val running = job.clockRunning(me)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                                        ZBodyStrong(if (running) "Your job timer is running" else "Your job timer isn't running", modifier = Modifier.weight(1f))
                                        if (running) ZButton("Stop job timer", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false, enabled = !model.busy) { scope.launch { model.clockOut() } }
                                        else ZButton("Start job timer", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false, enabled = !model.busy) { scope.launch { model.clockIn() } }
                                    }
                                }
                            }
                        }

                        // Where and who to call.
                        ZCard {
                            ZMono("Customer")
                            ZBodyStrong(job.customer?.name ?: "Customer")
                            job.customer?.phone?.let { phone -> CompanyPresentation.callUri(phone)?.let { uri -> ZTextAction(phone) { openUrl(context, uri) } } }
                            listOfNotNull(job.addressUnit?.takeIf { it.isNotBlank() }, job.address?.takeIf { it.isNotBlank() }).joinToString(", ").takeIf { it.isNotEmpty() }?.let { ZBody(it) }
                            ZFlowRow(spacing = ZSpacing.xs) {
                                job.customer?.phone?.let { phone -> CompanyPresentation.callUri(phone)?.let { uri -> ZButton("Call", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { openUrl(context, uri) } } }
                                MyDayLogic.directionsUrl(job.lat, job.lng, job.address)?.let { url -> ZButton("Directions", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { openUrl(context, url) } }
                            }
                        }

                        if (job.crew.isNotEmpty()) {
                            ZCard {
                                ZMono(if (job.crew.size > 1) "Crew" else "On this job")
                                job.crew.forEachIndexed { index, tech ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                        ZPhotoAvatar(tech.photoUrl, tech.name.take(1), 36.dp)
                                        ZBodyStrong(tech.name + if (tech.id == me) " (you)" else "", modifier = Modifier.weight(1f))
                                        if (job.crew.size > 1 && index == 0) ZBadge("Lead")
                                    }
                                }
                            }
                        }

                        if (!job.description.isNullOrBlank() || !job.workNotes.isNullOrBlank()) {
                            ZCard {
                                job.description?.takeIf { it.isNotBlank() }?.let { ZMono("The job"); ZBody(it) }
                                job.workNotes?.takeIf { it.isNotBlank() }?.let { ZMono("What was done"); ZBody(it, tone = ZTextTone.SOFT) }
                            }
                        }

                        ZCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ZHeadline("Time", modifier = Modifier.weight(1f))
                                if (job.time.isNotEmpty()) ZCaption(OwnJobLogic.minutes(OwnJobLogic.totalMinutes(job)))
                            }
                            if (job.time.isEmpty()) ZCaption("No time logged yet. Starting the job or the job timer logs time here.")
                            for (entry in job.time) {
                                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                    ZPhotoAvatar(entry.user?.photoUrl, entry.user?.name?.take(1) ?: "?", 28.dp)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        ZBodyStrong(entry.user?.name ?: "Teammate", maxLines = 1)
                                        ZCaption(listOfNotNull(entry.workedOn?.let { CalendarLogic.dayTitle(it) }, entry.note?.takeIf { it.isNotBlank() }).joinToString(" · "), maxLines = 2)
                                    }
                                    if (entry.running) ZBadge("Timer running", ZTone.SUCCESS)
                                    ZCaption(OwnJobLogic.minutes(entry.minutes), color = com.zivett.app.design.ZTheme.colors.ink)
                                }
                            }
                        }

                        if (job.photos.isNotEmpty()) {
                            ZCard {
                                ZHeadline("Photos")
                                PhotoGrid(job.photos, canDelete = { false })
                            }
                        }
                        Spacer(Modifier.padding(ZSpacing.lg))
                    }
                }
            }
        }
    }
}
