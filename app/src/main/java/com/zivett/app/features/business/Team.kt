package com.zivett.app.features.business

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.media.PhotoImport
import com.zivett.app.core.models.JobArea
import com.zivett.app.core.models.MemberBody
import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.models.Team
import com.zivett.app.core.models.TeamArea
import com.zivett.app.core.models.initialsOf
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLinkButton
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZPhotoAvatar
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSectionHeader
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZToggleRow
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/// Shared team page (business + company): roster, invitations, invite by
/// email, edit member. Your own row edits your name (and, for companies,
/// whether you take jobs) but never your role or status — the rule that
/// keeps an org with an active admin.
class TeamModel(private val client: ApiClient, val area: TeamArea) {
    var state by mutableStateOf<Loadable<Team>>(Loadable.Loading)
    var toast by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)

    suspend fun load() { state = state.reloaded { client.send(area.team()) } }

    suspend fun invite(email: String): Boolean {
        busy = true
        try { client.send(area.invite(email)); toast = "Invitation sent to $email"; load(); return true }
        catch (e: ApiError) { toast = e.first("email") ?: e.userMessage } catch (e: Exception) { toast = e.userMessage } finally { busy = false }
        return false
    }

    suspend fun revoke(invitation: Team.Invitation) {
        runCatching { client.send(area.revoke(invitation.id)) }
        load()
    }

    /// Admin-on-behalf photo upload (company only) — unblocks a "needs photo" member from job assignment.
    suspend fun uploadPhoto(member: Team.Member, jpeg: ByteArray?) {
        val upload = area.uploadMemberPhoto ?: return
        if (jpeg == null) { toast = "Could not read that image. Try a different photo."; return }
        busy = true
        try { client.send(upload(member.id, jpeg)); toast = "Photo saved — ${member.name} can now be assigned jobs."; load() }
        catch (e: ApiError) { toast = e.first("photo") ?: e.userMessage } catch (e: Exception) { toast = e.userMessage } finally { busy = false }
    }

    /// `takesJobs` is company-only (null = leave it); the business
    /// endpoint strips it regardless.
    suspend fun update(member: Team.Member, firstName: String, lastName: String, role: String, status: String, takesJobs: Boolean? = null): Boolean {
        busy = true
        try { client.send(area.updateMember(member.id, MemberBody(firstName.trim(), lastName.trim(), role, status, takesJobs))); toast = "Saved"; load(); return true }
        catch (e: Exception) { toast = e.userMessage } finally { busy = false }
        return false
    }

    /// "Remove from team": off the roster for good (the server keeps the
    /// user row, deactivated). null = removed; otherwise the server's
    /// sentence for the confirm dialog — e.g. still on an open job's crew.
    suspend fun remove(member: Team.Member): String? {
        busy = true
        try {
            client.send(area.removeMember(member.id))
            state.value?.let { state = Loadable.Loaded(it.copy(members = it.members.filter { m -> m.id != member.id })) }
            toast = "${member.name} removed from the team"
            return null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return e.userMessage
        } finally {
            busy = false
        }
    }

    companion object {
        /// Deactivated teammates sit behind "Show deactivated (N)" so the
        /// active team reads cleanly; removed ones never arrive at all.
        fun visibleMembers(members: List<Team.Member>, showDeactivated: Boolean): List<Team.Member> =
            if (showDeactivated) members else members.filter { it.status != "deactivated" }
    }
}

@Composable
fun TeamScreen(area: TeamArea, onBack: (() -> Unit)?) {
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val model = remember(area) { TeamModel(environment.client, area) }
    var inviteEmail by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Team.Member?>(null) }
    var photoFor by remember { mutableStateOf<Team.Member?>(null) }
    var removing by remember { mutableStateOf<Team.Member?>(null) }
    var removeError by remember { mutableStateOf<String?>(null) }
    var showDeactivated by remember { mutableStateOf(false) }
    LaunchedEffect(model) { model.load() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val member = photoFor ?: return@rememberLauncherForActivityResult
        photoFor = null
        if (uri != null) scope.launch { model.uploadPhoto(member, PhotoImport.jpegData(context, listOf(uri), 1024).firstOrNull()) }
    }

    Column {
        if (onBack != null) ZTopBar("Team", onBack = onBack) else ZTopBar("Team")
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { team ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        ZPageTitle("Team", team.organization.name)
                        if (team.canManage) {
                            ZCard {
                                ZBodyStrong("Invite a teammate")
                                ZTextField("", inviteEmail, { inviteEmail = it }, placeholder = "teammate@example.com", keyboardType = KeyboardType.Email)
                                ZButton("Send invite", compact = true, loading = model.busy, fullWidth = false, enabled = inviteEmail.isNotBlank()) {
                                    scope.launch { if (model.invite(inviteEmail.trim())) inviteEmail = "" }
                                }
                            }
                        }
                        ZSectionHeader("Members")
                        val deactivatedCount = team.members.count { it.status == "deactivated" }
                        if (deactivatedCount > 0) ZToggleRow("Show deactivated ($deactivatedCount)", null, showDeactivated, { showDeactivated = it })
                        for (member in TeamModel.visibleMembers(team.members, showDeactivated)) {
                            ZCard(padding = ZSpacing.sm) {
                                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                                    ZPhotoAvatar(member.photoUrl, initialsOf(member.name), 36.dp)
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { ZBodyStrong(member.name); ZCaption(member.email) }
                                    ZBadge(member.role.wire, if (member.role == OrganizationRole.ADMIN) ZTone.INFO else ZTone.NEUTRAL)
                                    if (member.status == "deactivated") ZBadge("Deactivated", ZTone.DANGER)
                                    // Office staff: skipped by automatic assignment, still pickable by hand.
                                    if (area.kind == JobArea.Kind.COMPANY && !member.takesJobs) ZBadge("Office")
                                    // Your own row too — a rename (and "Takes jobs") isn't a permission change.
                                    if (team.canManage) {
                                        IconButton(onClick = { editing = member }) { Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = colors.inkMuted) }
                                    }
                                }
                                // Company roster: a photo-less member can't be assigned jobs — surface it, and let admins fix it right here.
                                if (area.uploadMemberPhoto != null && member.hasPhoto == false && member.status != "deactivated") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                                        ZBadge("Needs photo", ZTone.WARNING)
                                        ZCaption("Can't be assigned jobs without one.", modifier = Modifier.weight(1f))
                                        if (team.canManage) ZLinkButton("Add photo") { photoFor = member; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                    }
                                }
                            }
                        }
                        if (team.invitations.isNotEmpty()) {
                            ZSectionHeader("Invitations")
                            for (invitation in team.invitations) {
                                ZCard(padding = ZSpacing.sm) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            ZBodyStrong(invitation.email)
                                            invitation.invitedBy?.let { ZCaption("Invited by $it") }
                                        }
                                        ZBadge(invitation.status, if (invitation.status == "expired") ZTone.DANGER else ZTone.WARNING)
                                        if (team.canManage) ZTextAction("Revoke", color = colors.danger) { scope.launch { model.revoke(invitation) } }
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

    editing?.let { member ->
        EditMemberSheet(member, model, editingSelf = member.id == environment.session.user?.id, onRemove = {
            // The sheet closes first so only one dialog is ever open.
            editing = null; removeError = null; removing = member
        }) { editing = null }
    }

    removing?.let { member ->
        val first = member.firstName.ifBlank { "They" }
        AlertDialog(
            onDismissRequest = { if (!model.busy) removing = null },
            title = { Text("Remove ${member.name} from the team?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                    Text("$first will be signed out and can no longer log in to ${model.state.value?.organization?.name ?: "your account"}. They'll disappear from your team list.")
                    Text("Jobs, quotes and messages they worked on stay on record. If you only need to pause their access, set them to Deactivated instead — you can reactivate them later.")
                    if (area.kind == JobArea.Kind.COMPANY) Text("Anyone still on an open job's crew has to be swapped off in that job's crew picker first.")
                    removeError?.let { Text(it, color = colors.danger) }
                }
            },
            confirmButton = {
                TextButton(enabled = !model.busy, onClick = {
                    scope.launch { val error = model.remove(member); if (error == null) removing = null else removeError = error }
                }) { Text("Remove from team", color = colors.danger) }
            },
            dismissButton = { TextButton(enabled = !model.busy, onClick = { removing = null }) { Text("Keep them") } },
        )
    }
}

@Composable
private fun EditMemberSheet(member: Team.Member, model: TeamModel, editingSelf: Boolean, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val isCompany = model.area.kind == JobArea.Kind.COMPANY
    var firstName by remember { mutableStateOf(member.firstName) }
    var lastName by remember { mutableStateOf(member.lastName) }
    var role by remember { mutableStateOf(member.role.wire) }
    var active by remember { mutableStateOf(member.status != "deactivated") }
    var takesJobs by remember { mutableStateOf(member.takesJobs) }
    ZSheet(onDismiss = onDismiss, title = "Edit member") {
        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
            ZTextField("First name", firstName, { firstName = it }, Modifier.weight(1f), "Amara", capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
            ZTextField("Last name", lastName, { lastName = it }, Modifier.weight(1f), "Okafor", capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
        }
        if (editingSelf) {
            // The server refuses a self role/status change; role and status are sent back unchanged.
            ZCaption("This is your own account — you can change your name${if (isCompany) " and whether you take jobs" else ""} here, but not your role or status.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                ZLabel("Role")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = role == "admin", onClick = { role = "admin" }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Admin") }
                    SegmentedButton(selected = role == "member", onClick = { role = "member" }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Member") }
                }
            }
            ZToggleRow("Active", "Deactivated members lose access on their next request.", active, { active = it })
        }
        // Field tech vs office staff: steers who won jobs get auto-assigned
        // to. Not a permission, so it's editable on your own row too.
        if (isCompany) {
            ZToggleRow("Takes jobs", "Won jobs are auto-assigned to teammates who go out on site. Turn this off for office staff — auto-assign skips them, but you can still put them on a job yourself from the job's page (the Assigned tech card's Reassign link, or Start job).", takesJobs, { takesJobs = it })
        }
        ZButton("Save", loading = model.busy) {
            scope.launch {
                if (model.update(member, firstName, lastName, role, if (active) "active" else "deactivated", takesJobs.takeIf { isCompany })) {
                    // A self-rename should show in the account header straight away.
                    if (editingSelf) runCatching { environment.session.refreshUser() }
                    onDismiss()
                }
            }
        }
        // Kept apart from Save: leaving the team is permanent from the
        // org's side, so it gets its own confirm.
        if (!editingSelf) {
            ZDivider(Modifier.padding(vertical = ZSpacing.xs))
            ZBodyStrong("Remove from team")
            ZCaption("They lose access and drop off this list for good. Their past jobs and quotes stay on record.")
            ZButton("Remove ${member.firstName.ifBlank { "teammate" }}…", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false, enabled = !model.busy, onClick = onRemove)
        }
    }
}
