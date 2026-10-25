package com.zivett.app.features.company

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.RescheduleBody
import com.zivett.app.core.models.RescheduleRequest
import com.zivett.app.core.models.RescheduleSlots
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZRadius
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextArea
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/// The pro's ask to move a confirmed visit (`Company\RescheduleController`):
/// the next 21 days × 3 windows, the ask itself, and taking it back. The
/// booker decides; nothing moves until they accept.
class MoveVisitModel(val jobId: Int, private val client: ApiClient) {
    var state by mutableStateOf<Loadable<RescheduleSlots>>(Loadable.Loading)
    var picked by mutableStateOf<RescheduleSlots.Slot?>(null)
    var reason by mutableStateOf("")
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    val pending: RescheduleRequest? get() = state.value?.rescheduleRequest

    suspend fun load() { state = state.reloaded { client.send(CompanyEndpoints.rescheduleSlots(jobId)) } }

    val canSend: Boolean get() = picked?.let { CalendarLogic.pickable(it) } == true && reason.length <= 500 && !busy

    /// The updated job, or null with `error` set (the sheet stays up).
    suspend fun send(): Job? {
        val slot = picked ?: return null
        return attempt { client.send(CompanyEndpoints.reschedule(jobId, RescheduleBody(slot.date, slot.window, reason.trim().ifEmpty { null }))).job }
            ?.also { job -> state.value?.let { state = Loadable.Loaded(it.copy(rescheduleRequest = job.rescheduleRequest)) } }
    }

    suspend fun withdraw(): Job? = attempt { client.send(CompanyEndpoints.withdrawReschedule(jobId)).job }
        ?.also { state.value?.let { state = Loadable.Loaded(it.copy(rescheduleRequest = null)) } }

    private suspend fun attempt(operation: suspend () -> Job): Job? {
        busy = true; error = null
        try {
            return operation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            error = e.first("date") ?: e.first("window") ?: e.first("reason") ?: e.userMessage
        } catch (e: Exception) {
            error = e.userMessage
        } finally {
            busy = false
        }
        return null
    }
}

/// The move sheet: a pending ask leads (with Withdraw), then the slot
/// picker — closed slots stay visible, disabled, with why (blocked, day
/// off, full); each shows its load against the cap; the agreed slot is
/// marked. `onChanged` gets the job after a send or withdraw.
@Composable
fun MoveVisitSheet(jobId: Int, jobCode: String?, client: ApiClient, onDismiss: () -> Unit, onChanged: (Job, String) -> Unit) {
    val scope = rememberCoroutineScope()
    val model = remember(jobId) { MoveVisitModel(jobId, client) }
    LaunchedEffect(model) { model.load() }
    ZSheet(onDismiss = onDismiss, title = "Move ${jobCode ?: "this visit"}", dismissable = !model.busy) {
        ZLoadable(model.state, retry = { scope.launch { model.load() } }) { loaded ->
            Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                loaded.rescheduleRequest?.let { pending ->
                    ZBanner("Waiting for the booker to accept ${CalendarLogic.slotLine(pending.date, pending.window)}.", tone = ZTone.INFO)
                    ZButton("Withdraw the request", style = ZButtonStyle.OUTLINE, loading = model.busy) {
                        scope.launch { model.withdraw()?.let { onChanged(it, "Move request withdrawn — the visit stays as agreed") } }
                    }
                    ZCaption("Or pick a different time below — a new request replaces this one.")
                }
                ZCaption("Pick the new time. The booker is asked to accept it; until they do, the visit stays where it is.", tone = ZTextTone.SOFT)
                for ((date, slots) in CalendarLogic.slotsByDate(loaded.slots)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ZBodyStrong(CalendarLogic.dayTitle(date))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (slot in slots) SlotCell(slot, selected = model.picked == slot, modifier = Modifier.weight(1f)) { model.picked = slot }
                        }
                    }
                }
                ZTextArea("Reason (optional)", model.reason, { if (it.length <= 500) model.reason = it }, placeholder = "Our crew is held up on another job — could we come the next morning?", minLines = 3)
                ZCaption("${model.reason.length} / 500")
                model.error?.let { ZBanner(it, tone = ZTone.DANGER) }
                ZButton(
                    model.picked?.let { "Ask to move to ${CalendarLogic.slotLine(it.date, it.window)}" } ?: "Pick a time",
                    loading = model.busy, enabled = model.canSend,
                ) {
                    scope.launch { model.send()?.let { onChanged(it, "Asked the booker to move ${jobCode ?: "the visit"}") } }
                }
            }
        }
    }
}

@Composable
private fun SlotCell(slot: RescheduleSlots.Slot, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = ZTheme.colors
    val pickable = CalendarLogic.pickable(slot)
    val sub = when {
        slot.current -> "Current"
        !slot.open -> CalendarLogic.slotReason(slot.reason) ?: "Closed"
        else -> CalendarLogic.slotLoad(slot) ?: "Open"
    }
    val background = when {
        selected -> colors.navy
        slot.current -> colors.infoSoft
        !slot.open -> colors.surfaceSunken
        else -> colors.surface
    }
    val ink = when {
        selected -> colors.onBrand
        !pickable -> colors.inkFaint
        else -> colors.ink
    }
    Column(
        modifier = modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(ZRadius.tile)).background(background)
            .border(if (slot.current) 1.5.dp else 1.dp, if (slot.current) colors.info else colors.border, RoundedCornerShape(ZRadius.tile))
            .clickable(enabled = pickable, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "${CalendarLogic.windowTag(slot.window)}, $sub" }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(CalendarLogic.windowTag(slot.window), style = ZType.caption.copy(fontWeight = FontWeight.SemiBold), color = ink, maxLines = 1)
        Text(sub, style = ZType.caption.copy(fontSize = 11.sp), color = if (selected) colors.onBrand.copy(alpha = 0.8f) else if (pickable) colors.inkMuted else colors.inkFaint, maxLines = 1)
        // Load still matters on a closed-but-busy slot ("Full · 2 of 2").
        if (!slot.open && slot.reason == "full") CalendarLogic.slotLoad(slot)?.let { Text(it, style = ZType.caption.copy(fontSize = 10.sp), color = colors.inkFaint) }
        if (slot.current && slot.capacity != null) CalendarLogic.slotLoad(slot)?.let { Text(it, style = ZType.caption.copy(fontSize = 10.sp), color = colors.inkMuted) }
    }
}

/// The company job page's visit card: the agreed slot, a pending move
/// (and for admins, Withdraw), and "Move visit".
@Composable
fun VisitTimeCard(job: Job, isAdmin: Boolean, busy: Boolean, onMove: () -> Unit, onWithdraw: () -> Unit) {
    if (!CompanyMoves.movable(job) && job.rescheduleRequest == null) return
    com.zivett.app.design.ZCard {
        com.zivett.app.design.ZMono("Visit time")
        ZBodyStrong(job.scheduledDate?.let { CalendarLogic.slotLine(it, job.scheduledWindow) } ?: "Not scheduled yet")
        val pending = job.rescheduleRequest
        if (pending != null) {
            ZBanner("Waiting for the booker to accept ${CalendarLogic.slotLine(pending.date, pending.window)}.", tone = ZTone.INFO)
            pending.reason?.takeIf { it.isNotBlank() }?.let { ZCaption("Your reason: “$it”", tone = ZTextTone.SOFT) }
            if (isAdmin) {
                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                    ZButton("Withdraw", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false, loading = busy, onClick = onWithdraw)
                    ZButton("Pick another time", style = ZButtonStyle.GHOST, compact = true, fullWidth = false, onClick = onMove)
                }
            }
        } else if (isAdmin && CompanyMoves.movable(job)) {
            ZCaption("Need a different time? Ask the booker — the visit only moves if they accept.")
            ZButton("Move visit", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false, onClick = onMove)
        }
    }
}

object CompanyMoves {
    /// The server's `movable`: accepted and not instant work.
    fun movable(job: Job): Boolean = job.status == com.zivett.app.core.models.JobStatus.ACCEPTED && job.mode != "instant"
}
