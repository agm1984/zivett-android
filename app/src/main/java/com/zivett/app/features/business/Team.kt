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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
                        for (member in team.members) {
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

    editing?.let { member -> EditMemberSheet(member, model, editingSelf = member.id == environment.session.user?.id) { editing = null } }
}

@Composable
private fun EditMemberSheet(member: Team.Member, model: TeamModel, editingSelf: Boolean, onDismiss: () -> Unit) {
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
            ZToggleRow("Takes jobs", "Won jobs are auto-assigned to teammates who go out on site. Turn this off for office staff — you can still assign them by hand.", takesJobs, { takesJobs = it })
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
    }
}
