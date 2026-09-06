package com.zivett.app.features.customer.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zivett.app.app.Areas
import com.zivett.app.app.BookRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.network.ApiClient
import com.zivett.app.design.ZColors
import com.zivett.app.design.ZRadius
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZType
import com.zivett.app.features.shared.LocalNav
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/// The app-wide "booking in progress" strip (`ResumeBookingBanner.vue`):
/// whenever a server-side wizard draft exists, booker screens offer the
/// way back in. Dismissing hides it for the session — it never deletes
/// the draft (that's the wizard's "Start over").
class ResumeBannerModel {
    var draft by mutableStateOf<ServerBookingDraft?>(null)

    suspend fun load(area: BookingWizardEngine.Area, client: ApiClient) {
        // Drafts are a convenience — a failed fetch just means no offer.
        draft = runCatching { client.send(BookingDraftEndpoints.show(area)) }.getOrNull()?.draft
    }

    companion object {
        /// Session-wide dismiss, shared across every mount like the web
        /// store's `dismissed` ref.
        var dismissedThisSession by mutableStateOf(false)
    }
}

@Composable
fun ResumeBookingBanner(area: BookingWizardEngine.Area) {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val model = remember { ResumeBannerModel() }
    LaunchedEffect(model) { model.load(area, environment.client) }
    LifecycleResumeEffect(model) {
        scope.launch { model.load(area, environment.client) }
        onPauseOrDispose { }
    }

    val label = model.draft?.category
    val visible = !ResumeBannerModel.dismissedThisSession && model.draft?.payload?.form?.serviceCategoryId != null
    if (!visible) return

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.card)).background(ZColors.fixedNavyDeep).padding(start = ZSpacing.md, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm),
    ) {
        Text(
            buildAnnotatedString {
                append("You have an unfinished ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(label ?: "booking") }
                if (label != null) append(" booking")
                append(" — your answers and photos are saved.")
            },
            style = ZType.caption, color = Color.White, modifier = Modifier.weight(1f),
        )
        Text(
            "Resume", style = ZType.label.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold), color = ZColors.fixedNavyDeep,
            modifier = Modifier.clip(CircleShape).background(ZColors.fixedGold).clickable { nav.navigate(BookRoute(if (area == BookingWizardEngine.Area.BUSINESS) Areas.BUSINESS else Areas.CUSTOMER, resume = true)) }.padding(horizontal = 14.dp, vertical = 6.dp),
        )
        IconButton(onClick = { ResumeBannerModel.dismissedThisSession = true }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Hide this reminder", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
        }
    }
}
