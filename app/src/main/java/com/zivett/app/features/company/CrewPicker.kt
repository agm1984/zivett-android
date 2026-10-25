package com.zivett.app.features.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zivett.app.app.Areas
import com.zivett.app.app.ProfileRoute
import com.zivett.app.app.TeamRoute
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.AssignmentRoster
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.autoPick
import com.zivett.app.core.models.canAutoPick
import com.zivett.app.core.models.nextUp
import com.zivett.app.core.models.noFieldTechs
import com.zivett.app.core.models.nobodyAssignable
import com.zivett.app.core.models.togglingCrew
import com.zivett.app.core.models.tooFewAssignable
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZPhotoAvatar
import com.zivett.app.design.ZSelectableRow
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTone
import com.zivett.app.features.shared.LocalNav
import kotlinx.coroutines.launch

/// Who's going (`CrewPickerModal.vue`): the picker behind both "Start job"
/// and the assign card's Reassign/Change crew. Field techs first, then
/// office staff, then teammates who can't be assigned, each in suggested-
/// fairness order (fewest open jobs, longest since last assignment) — the
/// dispatcher always chooses. A quote prices a crew (`crewSize`), so the
/// job is staffed with exactly that many and the first picked leads.
///
/// `starting` is the dispatch moment: it always confirms (never the
/// one-tap pick), opens with whoever was auto-assigned already picked and
/// says so, and saving starts the job — only then can a tech on it mark it
/// en route. Otherwise it just restaffs, one tap on a one-tech job.
@Composable
fun CrewPickerSheet(
    job: Job,
    starting: Boolean,
    roster: Loadable<AssignmentRoster>,
    loadRoster: suspend () -> Unit,
    save: suspend (List<Int>) -> Boolean,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val nav = LocalNav.current
    var saving by remember { mutableStateOf<Int?>(null) }
    // The crew being picked, lead first — seeded from the job's crew, else
    // the roster's (which may know it better than a list row does).
    var selection by remember { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(job.id) { loadRoster() }
    fun submit(userIds: List<Int>) {
        if (saving != null) return
        saving = userIds.first()
        scope.launch { val ok = save(userIds); saving = null; if (ok) onDismiss() }
    }
    val crewSize = job.crewSize ?: roster.value?.crewSize ?: 1
    val isCrew = crewSize > 1
    // A one-tech restaff keeps the one-tap pick; everything else confirms.
    val confirms = isCrew || starting
    val technicians = CompanyPresentation.technicians(crewSize)
    val title = when {
        starting -> "Start job"
        isCrew -> "Assign $technicians"
        else -> "Who's taking this job?"
    }
    ZSheet(onDismiss = onDismiss, title = title) {
        when {
            starting && job.crewMembers.isNotEmpty() -> ZBanner(CompanyPresentation.startPickerIntro(job, crewSize), tone = ZTone.SUCCESS)
            starting -> ZBody(CompanyPresentation.startPickerIntro(job, crewSize), tone = ZTextTone.SOFT)
            isCrew -> ZBody("Pick $technicians — the quote's crew. The first person you pick leads, and the customer sees their photo first.", tone = ZTextTone.SOFT)
            else -> ZBody("Suggested order — fewest open jobs first, then longest since their last one. The booker sees the assigned tech's name and photo.", tone = ZTextTone.SOFT)
        }
        ZLoadable(roster, retry = { scope.launch { loadRoster() } }) { loaded ->
            val picked = selection ?: job.crewMembers.map { it.id }.ifEmpty { loaded.crewUserIds }
            Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                if (confirms) {
                    if (loaded.canAutoPick(picked, crewSize)) {
                        ZButton("Auto-pick ${if (picked.isNotEmpty()) "the rest" else if (isCrew) "the crew" else "next"}", style = ZButtonStyle.OUTLINE) { selection = loaded.autoPick(picked, crewSize) }
                    }
                } else loaded.nextUp()?.let { next -> ZButton("Auto-assign next — ${next.name}", loading = saving == next.id) { submit(listOf(next.id)) } }
                // Nobody pickable is almost always "nobody has a photo yet"
                // — usually a solo owner who signed up without one.
                if (loaded.nobodyAssignable) {
                    ZBanner("Nobody on your team has a profile photo yet, and customers see the photo of who's coming. Add your photo (My account) to take this job yourself, or add teammates' photos from the Team page.", tone = ZTone.WARNING)
                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                        ZTextAction("My account") { onDismiss(); nav.navigate(ProfileRoute(Areas.COMPANY)) }
                        ZTextAction("Team page") { onDismiss(); nav.navigate(TeamRoute(Areas.COMPANY)) }
                    }
                } else if (isCrew && loaded.tooFewAssignable(crewSize)) {
                    ZBanner("This job needs $technicians, but fewer teammates than that have a profile photo. Add photos from the Team page so the whole crew can be assigned.", tone = ZTone.WARNING)
                    ZTextAction("Team page") { onDismiss(); nav.navigate(TeamRoute(Areas.COMPANY)) }
                } else if (loaded.noFieldTechs) {
                    ZBanner("Nobody is set to take jobs, so nothing is auto-assigned. Pick someone below, or turn on “Takes jobs” for your techs on the Team page.", tone = ZTone.WARNING)
                    ZTextAction("Team page") { onDismiss(); nav.navigate(TeamRoute(Areas.COMPANY)) }
                }
                for (member in loaded.members) {
                    val slot = picked.indexOf(member.id)
                    ZSelectableRow(
                        selected = if (confirms) slot >= 0 else member.assigned,
                        enabled = member.assignable && saving == null && (confirms || !member.assigned),
                        onClick = { if (confirms) selection = picked.togglingCrew(member.id, crewSize) else submit(listOf(member.id)) },
                    ) {
                        ZPhotoAvatar(member.photoUrl, member.name.take(1), 40.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                ZBodyStrong(member.name, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                                // Booked off for this slot: auto-assignment skipped
                                // them, but the dispatcher can still pick them.
                                if (member.off) ZBadge("Off that day", ZTone.WARNING)
                            }
                            ZCaption(if (member.needsPhoto) "Needs a profile photo before they can be assigned" else "${member.openJobs} open ${if (member.openJobs == 1) "job" else "jobs"}")
                        }
                        if (confirms && isCrew && slot == 0) ZBadge("Lead", ZTone.SUCCESS)
                        else if (confirms && slot >= 0) ZBadge(if (isCrew) "Crew" else "Going", ZTone.SUCCESS)
                        else if (!confirms && member.assigned) ZBadge("Assigned", ZTone.SUCCESS)
                        else if (!confirms && saving == member.id) ZSpinner(size = 18.dp)
                        else if (member.assignable && !member.takesJobs) ZBadge("Office")
                    }
                }
                if (confirms) {
                    ZDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                        ZCaption("${picked.size} of $crewSize picked", modifier = Modifier.weight(1f))
                        ZButton("Cancel", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false, onClick = onDismiss)
                        ZButton(if (starting) "Start job" else "Assign crew", compact = true, fullWidth = false, loading = saving != null, enabled = picked.size == crewSize) { submit(picked) }
                    }
                }
            }
        }
    }
}
