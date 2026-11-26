package com.zivett.app.features.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
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
import androidx.compose.ui.viewinterop.AndroidView
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.Loadable
import com.zivett.app.core.Money
import com.zivett.app.core.models.AudienceOptions
import com.zivett.app.core.models.CampaignAudience
import com.zivett.app.core.models.CampaignBody
import com.zivett.app.core.models.CampaignEndpoints
import com.zivett.app.core.models.CampaignResponse
import com.zivett.app.core.models.CampaignSummary
import com.zivett.app.core.models.CampaignTemplate
import com.zivett.app.core.models.CampaignsResponse
import com.zivett.app.core.models.ProCampaign
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZCheckbox
import com.zivett.app.design.ZDateField
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZDropdown
import com.zivett.app.design.ZEmptyState
import com.zivett.app.design.ZFlowRow
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSegmented
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZTextAction
import com.zivett.app.design.ZTextArea
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTimeField
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CampaignsLogic {
    val lastJobOptions = listOf(
        "within_6" to "Had a job in the last 6 months",
        "within_12" to "Had a job in the last 12 months",
        "lapsed_6" to "No job in the last 6 months",
        "lapsed_12" to "No job in the last 12 months",
        "lapsed_24" to "No job in the last 2 years",
    )

    fun status(status: String): Pair<String, ZTone> = when (status) {
        "scheduled" -> "Scheduled" to ZTone.WARNING
        "sending" -> "Sending" to ZTone.INFO
        "sent" -> "Sent" to ZTone.SUCCESS
        else -> "Draft" to ZTone.NEUTRAL
    }

    fun customers(count: Int) = ToolsCopy.plural(count, "customer")

    private val pacific = ZoneId.of("America/Vancouver")
    private val whenFormat = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a", Locale.US)

    fun statusLine(campaign: ProCampaign): String? = when (campaign.status) {
        "scheduled" -> campaign.scheduledFor?.let { "Scheduled to go to your recent customers on ${it.atZone(pacific).format(whenFormat).replace("AM", "am").replace("PM", "pm")} (Pacific time)." }
        "sending" -> "Sending to ${customers(campaign.recipientsCount)}, started ${ToolsFormat.day(campaign.sentAt)}."
        "sent" -> "Sent to ${customers(campaign.sentCount)} on ${ToolsFormat.day(campaign.sentAt)}."
        else -> null
    }

    /// "This week: 1 of 2 campaigns."
    fun weekLine(summary: CampaignSummary): String = "${Money.count(summary.sentThisWeek)} of ${Money.count(summary.weeklyLimit)} campaigns sent this week."

    /// "Y-m-d H:i" for `send_at` (the server reads it on the Pacific clock).
    fun sendAt(date: String, time: String): String? = if (date.isBlank() || time.isBlank()) null else "$date $time"
}

// ── List ────────────────────────────────────────────────────────────

/// The company's campaigns (web `Campaigns.vue`, org admins).
@Composable
fun CampaignsScreen(nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Loadable<CampaignsResponse>>(Loadable.Loading) }
    suspend fun load() { state = state.reloaded { environment.client.send(CampaignEndpoints.list()) } }
    LaunchedEffect(Unit) { load() }
    var resumed by remember { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { if (resumed) scope.launch { load() }; resumed = true; onPauseOrDispose { } }

    Column {
        ZTopBar("Campaigns", onBack = { nav.back() })
        ZScreen(onRefresh = { load() }) {
            ZPageTitle("Campaigns", "Promotional emails to your own customers, from you, with your logo and colour. 0% ZiVETT commission on the work they bring in.")
            ZLoadable(state, retry = { scope.launch { load() } }) { data ->
                Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                    val summary = data.summary
                    if (!summary.allowed) {
                        ZCard {
                            ZHeadline("Campaigns come with a higher plan")
                            ZBody(summary.upgrade?.let { "Email campaigns are part of the ${it.name} plan." } ?: "Email campaigns aren't part of your plan.")
                            if (summary.upgrade != null) ZButton("See plans") { nav.plan() }
                        }
                        return@Column
                    }
                    if (data.canEdit) ZButton("New campaign", enabled = summary.blocked == null) { nav.campaign(null) }
                    summary.blocked?.let { ZBanner(it, tone = ZTone.WARNING) }
                    ZCaption(listOfNotNull(summary.audience?.let { "${CampaignsLogic.customers(it)} can get your campaigns now." }, CampaignsLogic.weekLine(summary)).joinToString(" "))
                    if (data.campaigns.isEmpty()) ZEmptyState(Icons.Outlined.Campaign, "No campaigns yet", "Start one from a template: a seasonal check-up, a special offer, or open dates.")
                    for (campaign in data.campaigns) ZCard(onClick = { nav.campaign(campaign.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                            Column(Modifier.weight(1f)) {
                                ZBodyStrong(campaign.subject.ifBlank { "Untitled" }, maxLines = 1)
                                ZCaption(when (campaign.status) { "draft" -> "Not sent"; "scheduled" -> "Not yet"; else -> CampaignsLogic.customers(campaign.sentCount.takeIf { it > 0 } ?: campaign.recipientsCount) })
                            }
                            val (label, tone) = CampaignsLogic.status(campaign.status)
                            ZBadge(label, tone)
                        }
                    }
                }
            }
            Spacer(Modifier.padding(ZSpacing.md))
        }
    }
}

// ── Editor / detail ─────────────────────────────────────────────────

class CampaignEditorModel(var campaignId: Int?, private val client: ApiClient) {
    var list by mutableStateOf<CampaignsResponse?>(null)
    var current by mutableStateOf<CampaignResponse?>(null)
    var loadError by mutableStateOf<String?>(null)
    var template by mutableStateOf("custom")
    var subject by mutableStateOf("")
    var heading by mutableStateOf("")
    var body by mutableStateOf("")
    var buttonLabel by mutableStateOf("")
    var buttonUrl by mutableStateOf("")
    var audience by mutableStateOf(CampaignAudience())
    var reach by mutableStateOf<Int?>(null)
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
    var busy by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)
    var sendError by mutableStateOf<String?>(null)
    var preview by mutableStateOf<String?>(null)

    val campaign: ProCampaign? get() = current?.campaign
    val locked: Boolean get() = campaign != null && campaign?.status != "draft"

    suspend fun load() {
        try {
            list = client.send(CampaignEndpoints.list())
            campaignId?.let { id -> client.send(CampaignEndpoints.show(id)).also { current = it; fill(it.campaign) } }
            loadError = null
            countAudience()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { loadError = e.userMessage }
    }

    private fun fill(c: ProCampaign) {
        template = c.template ?: "custom"; subject = c.subject; heading = c.heading; body = c.body
        buttonLabel = c.buttonLabel.orEmpty(); buttonUrl = c.buttonUrl.orEmpty(); audience = c.audience ?: CampaignAudience()
    }

    fun useTemplate(t: CampaignTemplate) {
        template = t.key; subject = t.subject; heading = t.heading; body = t.body; buttonLabel = t.buttonLabel.orEmpty()
    }

    private fun requestBody() = CampaignBody(template, subject, heading, body, buttonLabel.trim().ifBlank { null }, buttonUrl.trim().ifBlank { null }, audience.takeIf { it.narrows })

    suspend fun countAudience() {
        reach = runCatching { client.send(CampaignEndpoints.audience(audience.takeIf { it.narrows })).count }.getOrNull() ?: reach
    }

    /// Saves the draft; true when it saved.
    suspend fun save(): Boolean {
        busy = "save"; errors = emptyMap()
        return try {
            val id = campaignId
            val saved = if (id == null) client.send(CampaignEndpoints.create(requestBody())) else client.send(CampaignEndpoints.update(id, requestBody()))
            current = saved; campaignId = saved.campaign.id
            toast = if (saved.problems.isEmpty()) "Saved." else "Saved. A few things to fix before it can go."
            true
        } catch (e: CancellationException) { throw e } catch (e: ApiError.Validation) { errors = e.errors.firstMessages; toast = e.errors.message; false }
        catch (e: Exception) { toast = e.userMessage; false } finally { busy = null }
    }

    suspend fun loadPreview() {
        busy = "preview"
        try { preview = client.send(CampaignEndpoints.preview(requestBody())).html } catch (e: CancellationException) { throw e } catch (e: Exception) { toast = e.userMessage } finally { busy = null }
    }

    suspend fun test() {
        val id = campaignId ?: return
        if (!save()) return
        busy = "test"
        try { val sent = client.send(CampaignEndpoints.test(id)); toast = "Test sent to ${sent.sentTo ?: "your email"}." } catch (e: CancellationException) { throw e } catch (e: Exception) { toast = e.userMessage } finally { busy = null }
    }

    suspend fun send(sendAt: String?): Boolean {
        val id = campaignId ?: return false
        busy = "send"; sendError = null
        return try {
            val sent = client.send(CampaignEndpoints.send(id, sendAt))
            current = sent; toast = if (sendAt != null) "Scheduled." else "Sending now."
            true
        } catch (e: CancellationException) { throw e } catch (e: ApiError.Validation) { sendError = e.errors.firstMessages.values.firstOrNull() ?: e.errors.message; false }
        catch (e: Exception) { sendError = e.userMessage; false } finally { busy = null }
    }

    suspend fun unschedule() {
        val id = campaignId ?: return
        busy = "unschedule"
        try { current = client.send(CampaignEndpoints.unschedule(id)); toast = "Back to a draft. Nothing was sent." } catch (e: CancellationException) { throw e } catch (e: Exception) { toast = e.userMessage } finally { busy = null }
    }
}

/// One campaign (web `CampaignEditor.vue`): a template to start from, the
/// words, an optional button, who gets it with a live count, a preview, a
/// test to yourself, send now or schedule, and who clicked and booked after.
@Composable
fun CampaignEditorScreen(campaignId: Int?, nav: ToolsNav) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember(campaignId) { CampaignEditorModel(campaignId, environment.client) }
    LaunchedEffect(model) { model.load() }
    var countJob by remember { mutableStateOf<Job?>(null) }
    var sending by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    fun recount() { countJob?.cancel(); countJob = scope.launch { delay(300); model.countAudience() } }

    Column {
        ZTopBar(if (model.locked) model.campaign?.subject ?: "Campaign" else if (model.campaignId != null) "Edit campaign" else "New campaign", onBack = { nav.back() })
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen {
                val list = model.list
                when {
                    model.loadError != null -> com.zivett.app.design.ZErrorState(model.loadError.orEmpty()) { scope.launch { model.load() } }
                    list == null -> ZSpinner()
                    else -> {
                        val campaign = model.campaign
                        ZCaption("Goes to your recent customers, from you, with your logo and colour. Everyone can unsubscribe.")
                        campaign?.let { c -> CampaignsLogic.statusLine(c)?.let { ZBanner(it, tone = ZTone.INFO) } }
                        campaign?.scheduleFailedReason?.takeIf { campaign.status == "draft" }?.let { ZBanner("It didn't send at its time: $it", tone = ZTone.WARNING) }
                        if (!list.canEdit) ZBanner("Only a company admin can write and send campaigns.", tone = ZTone.INFO)

                        if (!model.locked && list.canEdit) {
                            ZCard {
                                ZHeadline("Start from a template")
                                ZCaption("Every one is yours to change. Words in [square brackets] are for you to fill in.")
                                ZFlowRow { for (t in list.templates) ZButton(t.name, style = if (model.template == t.key) ZButtonStyle.PRIMARY else ZButtonStyle.OUTLINE, compact = true, fullWidth = false) { model.useTemplate(t) } }
                            }
                            ZCard {
                                val problems = model.current?.problems.orEmpty()
                                ZTextField("Subject", model.subject, { model.subject = it.take(120) }, error = model.errors["subject"] ?: problems["subject"])
                                ZTextField("Heading", model.heading, { model.heading = it.take(120) }, error = model.errors["heading"] ?: problems["heading"])
                                ZTextArea("Your email", model.body, { model.body = it }, error = model.errors["body"] ?: problems["body"], minLines = 8)
                                ZLabel("A button (optional)")
                                ZCaption("One link to your website, booking page or a social profile. Links can't go in the text.")
                                ZTextField("Button words", model.buttonLabel, { model.buttonLabel = it.take(40) }, error = model.errors["button_label"] ?: problems["button_label"])
                                ZTextField("Link", model.buttonUrl, { model.buttonUrl = it }, placeholder = "https://", error = model.errors["button_url"] ?: problems["button_url"])
                            }
                            list.audienceOptions?.let { AudienceCard(model, it) { recount() } }
                        } else if (campaign != null) {
                            ZCard {
                                ZHeadline(campaign.heading)
                                ZBody(campaign.body)
                                campaign.buttonLabel?.let { ZBadge(it) }
                            }
                        }
                        model.reach?.let { ZCaption(if (it == 0) "Nobody matches. Widen who gets it." else "${CampaignsLogic.customers(it)} would get it now.") }

                        ZButton("Preview", style = ZButtonStyle.OUTLINE, loading = model.busy == "preview") { scope.launch { model.loadPreview() } }
                        if (list.canEdit && !model.locked) {
                            ZButton("Save draft", style = ZButtonStyle.OUTLINE, loading = model.busy == "save") { scope.launch { model.save() } }
                            if (model.campaignId != null) ZButton("Send me a test", style = ZButtonStyle.OUTLINE, loading = model.busy == "test") { scope.launch { model.test() } }
                            ZButton("Send or schedule", enabled = model.busy == null && list.summary.blocked == null && (model.reach ?: 1) > 0) {
                                scope.launch { if (model.save() && model.current?.problems.isNullOrEmpty()) sending = true }
                            }
                        }
                        if (campaign?.status == "scheduled" && list.canEdit) {
                            ZCaption("To change the words, cancel the schedule first.")
                            ZButton("Cancel the schedule", style = ZButtonStyle.OUTLINE, loading = model.busy == "unschedule") { scope.launch { model.unschedule() } }
                        }
                        model.current?.results?.let { ResultsCard(it, nav) }
                        if (campaign?.status == "draft" && list.canEdit) ZButton("Delete draft", style = ZButtonStyle.GHOST) { deleting = true }
                    }
                }
                Spacer(Modifier.padding(ZSpacing.md))
            }
        }
    }

    model.preview?.let { html ->
        ZSheet({ model.preview = null }, title = "What your customers get") {
            AndroidView(factory = { context -> android.webkit.WebView(context).apply { settings.javaScriptEnabled = false } }, update = { it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) }, modifier = Modifier.fillMaxWidth().height(520.dp))
        }
    }
    if (sending) SendCampaignSheet(model, onDismiss = { sending = false })
    if (deleting) ToolsConfirm("Delete this draft?", "The draft is removed. Nothing was sent.", "Delete draft", danger = true, onDismiss = { deleting = false }) {
        scope.launch { model.campaignId?.let { id -> runCatching { environment.client.send(CampaignEndpoints.delete(id)) }.onSuccess { nav.back() }.onFailure { model.toast = it.userMessage } } }
    }
}

@Composable
private fun AudienceCard(model: CampaignEditorModel, options: AudienceOptions, onChange: () -> Unit) {
    ZCard {
        ZHeadline("Who gets it")
        ZCaption("Everyone who can get your campaigns, unless you narrow it.")
        ZDropdown("Last job", model.audience.lastJob.orEmpty(), listOf("" to "Anyone") + CampaignsLogic.lastJobOptions, placeholder = "Anyone") {
            model.audience = model.audience.copy(lastJob = it.ifBlank { null }); onChange()
        }
        if (options.cities.isNotEmpty()) {
            ZLabel("Cities")
            ZFlowRow {
                for (city in options.cities) {
                    val on = city.name in model.audience.cities
                    ZButton("${city.name} ${Money.count(city.count)}", style = if (on) ZButtonStyle.PRIMARY else ZButtonStyle.OUTLINE, compact = true, fullWidth = false) {
                        model.audience = model.audience.copy(cities = if (on) model.audience.cities - city.name else model.audience.cities + city.name); onChange()
                    }
                }
            }
        }
        if (options.categories.isNotEmpty()) {
            ZLabel("Work you've done for them")
            ZFlowRow {
                for (category in options.categories) {
                    val on = category.id in model.audience.categoryIds
                    ZButton("${category.name} ${Money.count(category.count)}", style = if (on) ZButtonStyle.PRIMARY else ZButtonStyle.OUTLINE, compact = true, fullWidth = false) {
                        model.audience = model.audience.copy(categoryIds = if (on) model.audience.categoryIds - category.id else model.audience.categoryIds + category.id); onChange()
                    }
                }
            }
        }
        ZCheckbox(model.audience.skipUnengaged, { model.audience = model.audience.copy(skipUnengaged = it); onChange() }, "Skip anyone who didn't click their last ${options.unengagedAfter} campaigns")
        ZCaption("Only campaigns with a button count. Fewer emails to people who aren't reading them keeps you out of spam folders.")
    }
}

@Composable
private fun SendCampaignSheet(model: CampaignEditorModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val summary = model.list?.summary ?: CampaignSummary()
    var later by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var time by remember { mutableStateOf("09:00") }
    var consent by remember { mutableStateOf(false) }
    ZSheet(onDismiss, title = if (later) "Schedule this campaign?" else "Send to ${CampaignsLogic.customers(model.reach ?: 0)}?", dismissable = model.busy == null) {
        ZSegmented(listOf(false to "Send now", true to "Schedule"), later) { later = it }
        if (later) {
            ZDateField("Day", date, { date = it })
            ZTimeField("Time (Pacific)", time, { time = it })
            ZCaption("It goes to everyone who can get your campaigns at that moment. You can cancel the schedule until then.")
        }
        ZCheckbox(consent, { consent = it }, "These are my customers: I've done work for them in the last ${summary.relationshipYears} years or they asked about my work in the last ${summary.inquiryMonths} months, and I'm allowed to email them.")
        model.sendError?.let { ZBanner(it, tone = ZTone.DANGER) }
        ZButton(if (later) "Schedule it" else "Send it", loading = model.busy == "send", enabled = consent) {
            scope.launch { if (model.send(if (later) CampaignsLogic.sendAt(date, time) else null)) onDismiss() }
        }
        ZButton("Not yet", style = ZButtonStyle.OUTLINE, onClick = onDismiss)
    }
}

@Composable
private fun ResultsCard(results: com.zivett.app.core.models.CampaignResults, nav: ToolsNav) {
    ZCard {
        ZHeadline("How it did")
        Row {
            Column(Modifier.weight(1f)) { ZLabel("Clicked the button"); ZBodyStrong(Money.count(results.clicked)) }
            Column(Modifier.weight(1f)) { ZLabel("Booked within ${results.withinDays} days"); ZBodyStrong(Money.count(results.booked)) }
            Column(Modifier.weight(1f)) { ZLabel("Those jobs, before tax"); ZBodyStrong(Money.format(results.bookedCents)) }
        }
        ZCaption("Replies come to your business email. Anyone who unsubscribed won't get your next campaign.")
        for (person in results.people.filter { it.clickedAt != null || it.booked != null }) {
            ZDivider()
            ClickRow({ person.booked?.let { nav.job(it.jobId) } ?: person.customer?.id?.let { nav.customer(it) } }) {
                Column {
                    ZBodyStrong(person.customer?.name ?: "Customer")
                    ZCaption(listOfNotNull(person.clickedAt?.let { "Clicked ${ToolsFormat.day(it)}" }, person.booked?.let { "Booked ${it.code ?: ""} ${ToolsFormat.day(it.bookedAt)}" }).joinToString(" · "))
                }
            }
        }
    }
}

@Suppress("unused")
private val keepTone = ZTextTone.SOFT
