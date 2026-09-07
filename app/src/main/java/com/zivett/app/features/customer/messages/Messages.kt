package com.zivett.app.features.customer.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zivett.app.app.Areas
import com.zivett.app.app.ConversationRoute
import com.zivett.app.app.JobDetailRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.ConversationParticipant
import com.zivett.app.core.models.ConversationSummary
import com.zivett.app.core.models.CustomerEndpoints
import com.zivett.app.core.models.JobArea
import com.zivett.app.core.models.Message
import com.zivett.app.core.models.initialsOf
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.realtime.RealtimeEvents
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZAvatar
import com.zivett.app.design.ZAvatarShape
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.design.ZType
import com.zivett.app.features.company.Dates
import com.zivett.app.features.customer.jobs.JobDetailModel
import com.zivett.app.features.customer.jobs.ReportProblemSheet
import com.zivett.app.features.shared.LocalNav
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/// Inbox: one thread per job.
@Composable
fun MessagesScreen() {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    var state by remember { mutableStateOf<Loadable<List<ConversationSummary>>>(Loadable.Loading) }
    // Active vs Past (web's tabs): a thread goes read-only once its job
    // settles — history stays readable, out of the way.
    var showingPast by remember { mutableStateOf(false) }

    suspend fun load() { state = state.reloaded { environment.client.send(CustomerEndpoints.conversations()).conversations } }
    LaunchedEffect(Unit) { load() }

    Column {
        ZTopBar("Messages")
        ZScreen(onRefresh = { load() }) {
            ZLoadable(state, retry = { scope.launch { load() } }) { threads ->
                val active = threads.filter { !it.readOnly }
                val past = threads.filter { it.readOnly }
                val shown = if (showingPast) past else active
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                    if (past.isNotEmpty()) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(selected = !showingPast, onClick = { showingPast = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Active (${active.size})") }
                            SegmentedButton(selected = showingPast, onClick = { showingPast = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Past (${past.size})") }
                        }
                    }
                    if (shown.isEmpty()) {
                        if (showingPast) ZEmptyState(Icons.AutoMirrored.Outlined.Chat, "No past conversations", "Threads move here once their job settles.")
                        else ZEmptyState(Icons.AutoMirrored.Outlined.Chat, "No messages yet", "Once a pro takes your job, you can message them here.")
                    }
                    for (thread in shown) {
                        ZCard(padding = ZSpacing.sm, onClick = {
                            nav.navigate(ConversationRoute(thread.job.id, Areas.CUSTOMER, thread.company ?: thread.job.title, thread.job.title, thread.readOnly, showJobLink = true))
                        }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                ZAvatar(initialsOf(thread.company ?: "?"), ZAvatarShape.COMPANY, 44.dp)
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(thread.company ?: thread.job.title, style = if (thread.unreadCount > 0) ZType.bodyStrong else ZType.body, color = colors.ink, maxLines = 1, modifier = Modifier.weight(1f))
                                        thread.lastMessageAt?.let { ZCaption(Dates.relative(it), tone = ZTextTone.FAINT) }
                                    }
                                    ZCaption(thread.lastMessage ?: thread.job.title, maxLines = 1)
                                }
                                if (thread.unreadCount > 0) {
                                    Text(thread.unreadCount.toString(), style = ZType.label, color = colors.navyDeep, modifier = Modifier.clip(CircleShape).background(colors.brandGold).padding(horizontal = 7.dp, vertical = 2.dp))
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

class ConversationModel(val jobId: Int, private val client: ApiClient, val area: JobArea = JobArea.customer) {
    var state by mutableStateOf<Loadable<List<Message>>>(Loadable.Loading)
    var draft by mutableStateOf("")
    var sending by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    /// The other side of the thread — who can be blocked, who already is.
    var participants by mutableStateOf<List<ConversationParticipant>>(emptyList())
        private set
    /// The realtime channel key (`conversation.{id}`) — known after the
    /// first load; threads are keyed by conversation id, not job id.
    var conversationId by mutableStateOf<Int?>(null)
        private set
    /// For deciding `mine` on pushed messages.
    var currentUserId: Int? = null

    suspend fun load() {
        state = state.reloaded {
            val conversation = client.send(area.conversation(jobId)).conversation
            conversationId = conversation.id
            participants = conversation.participants
            conversation.messages
        }
    }

    val blockable: List<ConversationParticipant> get() = participants.filter { !it.blocked }
    val blocked: List<ConversationParticipant> get() = participants.filter { it.blocked }

    /// A pushed message on this thread. Deduped by id — our own sends
    /// echo back (the app sends no socket-id for exclusion), and the
    /// poll fallback may have raced us. The socket is not filtered
    /// server-side, so a blocked sender's message is dropped here.
    fun receive(event: String, payload: String) {
        if (event != RealtimeEvents.messageSent) return
        val message = RealtimeEvents.message(payload, currentUserId) ?: return
        if (blocked.any { it.id == message.senderId }) return
        val messages = state.value ?: return
        if (messages.any { it.id == message.id }) return
        state = Loadable.Loaded(messages + message)
    }

    val canSend: Boolean get() = !sending && draft.isNotBlank()

    suspend fun send() {
        if (!canSend) return
        val body = draft.trim()
        sending = true
        error = null
        try {
            val message = client.send(area.sendMessage(jobId, body)).message
            state = Loadable.Loaded((state.value ?: emptyList()) + message)
            draft = ""
        } catch (apiError: ApiError) {
            error = apiError.first("body") ?: apiError.userMessage
        } catch (e: Exception) {
            error = e.userMessage
        } finally {
            sending = false
        }
    }

    /// Block someone on the other side: their messages disappear from
    /// this thread (and every other read) until unblocked. The thread
    /// reloads so the server's filtered view is what's shown.
    suspend fun block(participant: ConversationParticipant) {
        sending = true
        error = null
        try {
            participants = client.send(area.blockUser(jobId, participant.id)).participants
            load()
        } catch (apiError: ApiError) {
            error = apiError.first("user_id") ?: apiError.userMessage
        } catch (e: Exception) {
            error = e.userMessage
        } finally {
            sending = false
        }
    }

    suspend fun unblock(participant: ConversationParticipant) {
        sending = true
        error = null
        try {
            participants = client.send(area.unblockUser(jobId, participant.id)).participants
            load()
        } catch (apiError: ApiError) {
            error = apiError.userMessage
        } catch (e: Exception) {
            error = e.userMessage
        } finally {
            sending = false
        }
    }
}

/// One thread. Live over the conversation's private channel (same
/// Reverb feed as the web); the 15s poll runs only while the socket is
/// down. The overflow menu carries the store-policy pair: block
/// (private, hides the person) and report a problem (reaches support).
@Composable
fun ConversationScreen(jobId: Int, title: String, subtitle: String?, readOnly: Boolean, area: JobArea, showJobLink: Boolean, onBack: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val model = remember(jobId) { ConversationModel(jobId, environment.client, area).also { it.currentUserId = environment.session.user?.id } }
    val listState = rememberLazyListState()
    var menu by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf<ConversationParticipant?>(null) }
    var reporting by remember { mutableStateOf(false) }

    LaunchedEffect(model) {
        model.load()
        while (true) {
            delay(15_000)
            if (!environment.realtime.connected) model.load()
        }
    }
    val conversationId = model.conversationId
    DisposableEffect(conversationId) {
        val live = conversationId?.let { id -> environment.realtime.subscribe("conversation.$id") { event, payload -> model.receive(event, payload) } }
        onDispose { live?.cancel() }
    }
    val count = model.state.value?.size ?: 0
    LaunchedEffect(count) { if (count > 0) listState.animateScrollToItem(count - 1) }

    Column(modifier = Modifier.fillMaxSize().background(colors.cream)) {
        ZTopBar(title, onBack = onBack, subtitle = subtitle, actions = {
            if (showJobLink) ZTextAction("View job") { nav.navigate(JobDetailRoute(jobId, if (area.kind == JobArea.Kind.BUSINESS) Areas.BUSINESS else Areas.CUSTOMER)) }
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Conversation options", tint = colors.inkMuted) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                for (participant in model.blockable) {
                    DropdownMenuItem(text = { Text("Block ${participant.name}", color = colors.danger) }, onClick = { menu = false; blocking = participant })
                }
                for (participant in model.blocked) {
                    DropdownMenuItem(text = { Text("Unblock ${participant.name}") }, onClick = { menu = false; scope.launch { model.unblock(participant) } })
                }
                DropdownMenuItem(text = { Text("Report a problem") }, onClick = { menu = false; reporting = true })
            }
        })
        ZToastBox(model.error, { model.error = null }, modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    when (val state = model.state) {
                        is Loadable.Loaded -> LazyColumn(state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(ZSpacing.md), verticalArrangement = Arrangement.spacedBy(ZSpacing.xs), modifier = Modifier.fillMaxSize()) {
                            if (state.loaded.isEmpty()) item { ZEmptyState(Icons.AutoMirrored.Outlined.Chat, "Say hello", "Messages stay attached to this job.") }
                            items(state.loaded, key = { it.id }) { MessageBubble(it) }
                        }
                        else -> Column(modifier = Modifier.padding(ZSpacing.md)) { ZLoadable(state, retry = { scope.launch { model.load() } }) {} }
                    }
                }
                for (participant in model.blocked) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(colors.tone(ZTone.NEUTRAL).second).padding(horizontal = ZSpacing.md, vertical = ZSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm),
                    ) {
                        ZCaption("You blocked ${participant.name}. Their messages are hidden and they can't message you.", modifier = Modifier.weight(1f))
                        ZTextAction("Unblock") { scope.launch { model.unblock(participant) } }
                    }
                }
                if (readOnly) {
                    ZCaption("This conversation is closed.", modifier = Modifier.padding(ZSpacing.md))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(colors.surface.copy(alpha = 0.95f)).padding(ZSpacing.sm).imePadding(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs),
                    ) {
                        OutlinedTextField(
                            value = model.draft, onValueChange = { model.draft = it }, modifier = Modifier.weight(1f), maxLines = 5,
                            placeholder = { Text("Message", style = ZType.body, color = colors.inkFaint) },
                            textStyle = ZType.body.copy(color = colors.ink), shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.navy, unfocusedBorderColor = colors.borderStrong, focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface),
                        )
                        IconButton(
                            onClick = { scope.launch { model.send() } }, enabled = model.canSend,
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(if (model.canSend) colors.navy else colors.inkFaint),
                        ) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Send", tint = colors.onBrand) }
                    }
                }
            }
        }
    }

    blocking?.let { participant ->
        AlertDialog(
            onDismissRequest = { blocking = null },
            title = { Text("Block ${participant.name}?") },
            text = { Text("You won't see their messages and they won't be able to message you. You can unblock them here any time. To reach our support team, use Report a problem.") },
            confirmButton = { TextButton(onClick = { blocking = null; scope.launch { model.block(participant) } }) { Text("Block", color = colors.danger) } },
            dismissButton = { TextButton(onClick = { blocking = null }) { Text("Cancel") } },
        )
    }
    if (reporting) {
        val detail = remember(jobId) { JobDetailModel(jobId, environment.client, area) }
        ReportProblemSheet(detail, onDismiss = { reporting = false })
    }
}

@Composable
fun MessageBubble(message: Message) {
    val colors = ZTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.widthIn(max = 300.dp)) {
            Text(
                message.body, style = ZType.body, color = if (message.mine) Color.White else colors.ink,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (message.mine) colors.navy else colors.surface)
                    .then(if (!message.mine) Modifier.border(1.dp, colors.border, RoundedCornerShape(16.dp)) else Modifier)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            message.createdAt?.let { ZCaption(Dates.time(it), tone = ZTextTone.FAINT) }
        }
    }
}

@Suppress("unused")
private val keepWidth: Modifier = Modifier.width(0.dp)
