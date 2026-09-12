package com.zivett.app.features.customer.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.HomeRepairService
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zivett.app.app.AddressesRoute
import com.zivett.app.app.Areas
import com.zivett.app.app.BookRoute
import com.zivett.app.app.ConversationRoute
import com.zivett.app.app.InvoiceDetailRoute
import com.zivett.app.app.JobDetailRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.NotificationsRoute
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.BookingOptions
import com.zivett.app.core.models.CustomerEndpoints
import com.zivett.app.core.models.CustomerHome
import com.zivett.app.core.models.Job
import com.zivett.app.core.models.JobArea
import com.zivett.app.core.models.JobStatus
import com.zivett.app.core.models.NotificationArea
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.userMessage
import com.zivett.app.design.ZAvatar
import com.zivett.app.design.ZBadge
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZColors
import com.zivett.app.design.ZErrorState
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZRadius
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZSpinner
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTitle
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZType
import com.zivett.app.features.customer.book.BookingWizardEngine
import com.zivett.app.features.customer.book.ResumeBookingBanner
import com.zivett.app.features.customer.jobs.JobPresentation
import com.zivett.app.features.customer.notifications.NotificationBell
import com.zivett.app.features.customer.notifications.rememberNotificationsModel
import com.zivett.app.features.shared.JobTrackingMap
import com.zivett.app.features.shared.LocalNav
import com.zivett.app.features.shared.ProRow
import kotlinx.coroutines.launch

/// Loads `GET /api/customer/home` and exposes the resolved hero.
class CustomerHomeModel(private val client: ApiClient) {
    var phase by mutableStateOf<Loadable<CustomerHome>>(Loadable.Loading)
        private set

    val home: CustomerHome? get() = phase.value
    val hero: CustomerHomeLogic.Hero? get() = home?.let { CustomerHomeLogic.resolveHero(it) }

    /// Transient banner (a failed cancel, a paid confirmation).
    var notice by mutableStateOf<String?>(null)

    /// The pending hero's escape hatch — free while nothing is assigned,
    /// which is exactly when the hero shows it.
    suspend fun cancelPending(job: Job) {
        try {
            client.send(JobArea.customer.cancel(job.id))
            notice = "${job.code ?: "Request"} cancelled"
        } catch (error: Exception) {
            notice = error.userMessage
        }
        load()
    }

    suspend fun load() {
        try {
            phase = Loadable.Loaded(client.send(CustomerEndpoints.home()))
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            // A stale screen beats an error card when we already have data.
            if (home == null) phase = Loadable.Failed(error.userMessage)
        }
    }
}

@Composable
fun CustomerHomeScreen(model: CustomerHomeModel) {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val bell = rememberNotificationsModel(NotificationArea.CUSTOMER)
    val user = environment.session.user
    val hero = model.hero
    val greeting = hero?.let { CustomerHomeLogic.greeting(it, user?.firstName ?: "there") }
    val dark = hero?.let { CustomerHomeLogic.usesDarkHeader(it) } ?: false

    LaunchedEffect(model) { model.load() }

    Column(modifier = Modifier.fillMaxWidth()) {
        // The dark/light top bar: avatar, kicker + heading, notification bell.
        Row(
            modifier = Modifier.fillMaxWidth().background(if (dark) ZColors.fixedNavyDeep else colors.cream).statusBarsPadding().padding(horizontal = ZSpacing.md, vertical = ZSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm),
        ) {
            ZAvatar(user?.initials ?: "?", onDark = true)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(greeting?.kicker ?: " ", style = ZType.caption.copy(fontSize = 12.sp), color = if (dark) Color.White.copy(alpha = 0.7f) else colors.inkMuted)
                Text(greeting?.heading ?: " ", style = ZType.headline.copy(fontWeight = FontWeight.Bold), color = if (dark) Color.White else colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            NotificationBell(bell.unread, dark) { nav.navigate(NotificationsRoute(Areas.CUSTOMER)) }
        }

        ZScreen(onRefresh = { model.load() }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = ZSpacing.md)) {
            when (val phase = model.phase) {
                Loadable.Loading -> Box(Modifier.fillMaxWidth().padding(top = ZSpacing.xxl), contentAlignment = Alignment.Center) { ZSpinner() }
                is Loadable.Failed -> ZErrorState(phase.message) { scope.launch { model.load() } }
                is Loadable.Loaded -> {
                    val home = phase.loaded
                    ResumeBookingBanner(BookingWizardEngine.Area.CUSTOMER)
                    model.notice?.let { ZBanner(it, tone = ZTone.INFO) }
                    if (hero != null) {
                        HeroView(hero, home, model)
                        val rows = CustomerHomeLogic.secondaryRows(hero)
                        if (rows.isNotEmpty()) SecondaryStrip(rows)
                        if (!home.hasSavedAddress) SaveAddressNudge()
                    }
                    Spacer(Modifier.padding(ZSpacing.lg))
                }
            }
        }
    }
}

@Composable
private fun HeroView(hero: CustomerHomeLogic.Hero, home: CustomerHome, model: CustomerHomeModel) {
    val scope = rememberCoroutineScope()
    val subject = hero.subject
    when {
        hero.key == CustomerHomeLogic.HeroKey.ENROUTE && subject is CustomerHomeLogic.Subject.JobSubject -> HeroEnRoute(subject.job)
        hero.key == CustomerHomeLogic.HeroKey.PROGRESS && subject is CustomerHomeLogic.Subject.JobSubject -> HeroProgressCard(subject.job)
        hero.key == CustomerHomeLogic.HeroKey.QUOTES && subject is CustomerHomeLogic.Subject.JobSubject -> HeroQuotesCard(subject.job)
        hero.key == CustomerHomeLogic.HeroKey.PENDING && subject is CustomerHomeLogic.Subject.JobSubject -> HeroPendingCard(subject.job) { scope.launch { model.cancelPending(subject.job) } }
        hero.key == CustomerHomeLogic.HeroKey.INVOICE && subject is CustomerHomeLogic.Subject.InvoiceSubject -> HeroInvoiceCard(subject.invoice) { model.load() }
        hero.key == CustomerHomeLogic.HeroKey.FIRSTRUN -> HeroFirstRun()
        else -> HeroEmpty(home.booking)
    }
}

/// Map + the job card, the screenshot's top panel.
@Composable
private fun HeroEnRoute(job: Job) {
    val nav = LocalNav.current
    val colors = ZTheme.colors
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.panel)).background(colors.surface).border(1.dp, colors.border, RoundedCornerShape(ZRadius.panel))) {
        if (job.lat != null && job.lng != null) JobTrackingMap(job, modifier = Modifier.fillMaxWidth().height(240.dp))
        Column(modifier = Modifier.padding(ZSpacing.md), verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
            ZBadge(if (job.status == JobStatus.ARRIVED) "Arrived" else "On the way", ZTone.SUCCESS)
            ZTitle(job.title)
            ZCaption(listOfNotNull(job.code, job.address).joinToString(" · "))
            job.company?.let { ProRow(it) }
            ZButton("Message your pro") { nav.navigate(ConversationRoute(job.id, Areas.CUSTOMER, job.company?.name ?: "Your pro", job.title)) }
        }
    }
}

/// "ALSO ON YOUR PLATE" — demoted concerns as compact rows.
@Composable
private fun SecondaryStrip(rows: List<CustomerHomeLogic.SecondaryRow>) {
    val nav = LocalNav.current
    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
        ZMono("Also on your plate", modifier = Modifier.padding(start = 4.dp))
        for (row in rows) {
            ZCard(onClick = {
                when (val subject = row.subject) {
                    is CustomerHomeLogic.Subject.JobSubject -> nav.navigate(JobDetailRoute(subject.job.id, Areas.CUSTOMER))
                    is CustomerHomeLogic.Subject.InvoiceSubject -> nav.navigate(InvoiceDetailRoute(subject.invoice.id, Areas.CUSTOMER))
                }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ZBodyStrong(row.title)
                        row.subtitle?.let { ZCaption(it) }
                    }
                    ZBadge(row.tag, row.tone)
                }
            }
        }
    }
}

@Composable
private fun SaveAddressNudge() {
    val nav = LocalNav.current
    val colors = ZTheme.colors
    ZCard(onClick = { nav.navigate(AddressesRoute) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Home, contentDescription = null, tint = colors.info)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ZBodyStrong("Save your address")
                ZCaption("Makes every booking a one-tap pick.")
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.inkFaint)
        }
    }
}

/// The booking launchpad (`HeroEmpty.vue`): gradient header, one card
/// per booking mode, then the popular-categories grid. Everything links
/// into the wizard with the choice preset.
@Composable
private fun HeroEmpty(booking: BookingOptions?) {
    val nav = LocalNav.current
    val colors = ZTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.panel))
                .background(Brush.linearGradient(listOf(ZColors.fixedNavy, ZColors.fixedNavyDeep)))
                .background(Brush.radialGradient(listOf(ZColors.fixedGoldBright.copy(alpha = 0.35f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(900f, 0f), radius = 500f))
                .padding(ZSpacing.lg),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("What needs fixing today?", style = ZType.title, color = Color.White)
                Text("Verified local pros, transparent pricing.", style = ZType.body, color = Color.White.copy(alpha = 0.75f))
            }
        }
        for (mode in booking?.modes ?: emptyList()) {
            ModeCard(mode) { nav.navigate(BookRoute(Areas.CUSTOMER, presetMode = mode.key)) }
        }
        val categories = booking?.categories ?: emptyList()
        if (categories.isNotEmpty()) {
            ZMono("Popular right now", modifier = Modifier.padding(top = ZSpacing.xs, start = 4.dp))
            for (rowItems in categories.take(8).chunked(4)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
                    for (category in rowItems) {
                        Column(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(ZRadius.card)).background(colors.surface).border(1.dp, colors.border, RoundedCornerShape(ZRadius.card))
                                .clickable { nav.navigate(BookRoute(Areas.CUSTOMER, presetCategorySlug = category.slug)) }.padding(vertical = ZSpacing.sm),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(ZRadius.tile)).background(ZColors.parse(category.bgColor) ?: colors.infoSoft), contentAlignment = Alignment.Center) {
                                Icon(CategoryIcon.icon(category.icon), contentDescription = null, tint = ZColors.parse(category.fgColor) ?: colors.info, modifier = Modifier.size(18.dp))
                            }
                            Text(category.name, style = ZType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = colors.inkSoft, maxLines = 1)
                        }
                    }
                    repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(mode: BookingOptions.Mode, onClick: () -> Unit) {
    val colors = ZTheme.colors
    val meta = JobPresentation.modeMeta(mode.key)
    val (fg, bg) = colors.tone(meta.tone)
    ZCard(onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(ZRadius.tile)).background(bg), contentAlignment = Alignment.Center) {
                Text(JobPresentation.modeCode(mode.key), style = ZType.monoBody.copy(fontWeight = FontWeight.Bold), color = fg)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ZBodyStrong(mode.name)
                    mode.tag?.let { ZBadge(it, meta.tone) }
                }
                mode.desc?.let { ZCaption(it, tone = ZTextTone.SOFT) }
                mode.eta?.let { Text(it, style = ZType.mono.copy(letterSpacing = 0.sp, fontWeight = FontWeight.Normal), color = colors.inkMuted) }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.inkFaint, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

/// `HeroFirstRun.vue`: the welcome for someone who has never booked.
@Composable
private fun HeroFirstRun() {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val steps = listOf(
        Triple("1", "Describe the job", "A few taps — what's wrong, where, and when."),
        Triple("2", "We match a verified pro", "Identity, licence and insurance checked."),
        Triple("3", "Track, pay, stay covered", "Live status, in-app payment, workmanship warranty."),
    )
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.panel)).background(Brush.linearGradient(listOf(ZColors.fixedNavy, ZColors.fixedNavyDeep))).padding(ZSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ZSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Welcome to ZiVETT${environment.session.user?.let { ", ${it.firstName}" } ?: ""}", style = ZType.title, color = Color.White)
            Text("Home repairs without the runaround.", style = ZType.body, color = Color.White.copy(alpha = 0.75f))
        }
        for ((number, title, blurb) in steps) {
            Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Text(number, style = ZType.monoBody.copy(fontWeight = FontWeight.Bold), color = ZColors.fixedGoldBright)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = ZType.bodyStrong, color = Color.White)
                    Text(blurb, style = ZType.caption, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.button)).background(ZColors.fixedGold).clickable { nav.navigate(BookRoute(Areas.CUSTOMER)) }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text("Book your first job", style = ZType.bodyStrong, color = ZColors.fixedNavyDeep)
        }
    }
}

/// Backend icon names (`service_categories.icon`) → Material icons.
object CategoryIcon {
    fun icon(name: String?): androidx.compose.ui.graphics.vector.ImageVector = when (name) {
        "wrench" -> Icons.Outlined.Build
        "bolt", "zap" -> Icons.Outlined.Bolt
        "wind", "fan", "thermometer" -> Icons.Outlined.Air
        "hammer", "tools" -> Icons.Outlined.Handyman
        "paintbrush", "paint" -> Icons.Outlined.FormatPaint
        "sparkles", "broom" -> Icons.Outlined.CleaningServices
        "leaf" -> Icons.Outlined.Yard
        "snowflake" -> Icons.Outlined.AcUnit
        "drop" -> Icons.Outlined.WaterDrop
        "key", "lock" -> Icons.Outlined.Lock
        else -> Icons.Outlined.HomeRepairService
    }
}
