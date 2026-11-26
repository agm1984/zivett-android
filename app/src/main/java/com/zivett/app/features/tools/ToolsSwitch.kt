package com.zivett.app.features.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.auth.AuthSession
import com.zivett.app.core.models.BusinessToolsSummary
import com.zivett.app.core.models.ToolsEndpoints
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.userMessage
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/// The Plan screen's Business tools switch (web `BusinessToolsPlanCard`).
class ToolsSwitchModel(private val client: ApiClient, private val session: AuthSession?) {
    var summary by mutableStateOf<BusinessToolsSummary?>(null)
    var saving by mutableStateOf(false)
        private set

    /// A failed read hides the card: there's nothing a company can do here
    /// without it, and the plan screen is about the plan.
    suspend fun load() {
        summary = runCatching { client.send(ToolsEndpoints.switch()).businessTools }.getOrNull() ?: summary
    }

    /// Returns the toast to show.
    suspend fun setEnabled(enabled: Boolean): String {
        saving = true
        return try {
            summary = client.send(ToolsEndpoints.setSwitch(enabled)).businessTools
            // The tab bar reads `permissions` off the session user.
            runCatching { session?.refreshUser() }
            if (enabled) "Business tools are on." else "Business tools are off. Nothing was deleted."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.userMessage
        } finally {
            saving = false
        }
    }
}

/// Org admins only (the caller checks `canManage`). Hidden while
/// self-serve is off and the tools are off: during the pilot there's
/// nothing for a company to do here.
@Composable
fun BusinessToolsPlanCard(onToast: (String) -> Unit) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember { ToolsSwitchModel(environment.client, environment.session) }
    LaunchedEffect(model) { model.load() }
    val summary = model.summary ?: return
    if (!summary.visible) return

    ZCard {
        ZHeadline("Business tools")
        ZCaption("Keep the customers you find yourself in ZiVETT: their details, quotes, jobs on your calendar and invoices. 0% commission on that work.", tone = ZTextTone.SOFT)
        ZToggleRow(
            "Business tools", summary.allowanceSentence, summary.enabled,
            onCheckedChange = { on -> scope.launch { onToast(model.setEnabled(on)) } },
            enabled = !model.saving && !summary.locked,
        )
        summary.lockedReason?.let { ZCaption(it) }
        ZCaption("Invoices and recording payments are never limited. Moving to a lower plan never deletes or locks anything.", tone = ZTextTone.SOFT)
        if (summary.enabled) ZCaption("Switch to \"Your business\" at the top of More to open it.", tone = ZTextTone.SOFT)
    }
}
