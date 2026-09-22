package com.zivett.app.features.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zivett.app.app.Areas
import com.zivett.app.app.CredentialGuideRoute
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.app.OrgProfileRoute
import com.zivett.app.app.PassportRoute
import com.zivett.app.core.Loadable
import com.zivett.app.core.auth.AuthSession
import com.zivett.app.core.models.CompanyEndpoints
import com.zivett.app.core.models.CompanyOrganizationResponse
import com.zivett.app.core.models.CompanySetup
import com.zivett.app.core.models.OrganizationRole
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.userMessage
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZCheckLine
import com.zivett.app.design.ZDisplay
import com.zivett.app.design.ZDivider
import com.zivett.app.design.ZIconTile
import com.zivett.app.design.ZLabel
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZMono
import com.zivett.app.design.ZNavRow
import com.zivett.app.design.ZPageTitle
import com.zivett.app.design.ZScreen
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZToastBox
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZTopBar
import com.zivett.app.features.shared.LocalNav
import kotlinx.coroutines.launch

class CompanySetupModel(private val client: ApiClient) {
    var state by mutableStateOf<Loadable<CompanyOrganizationResponse>>(Loadable.Loading)
    var toast by mutableStateOf<String?>(null)
    var submitting by mutableStateOf(false)
    var justSubmitted by mutableStateOf(false)

    suspend fun load() { state = state.reloaded { client.send(CompanyEndpoints.organization()) } }

    suspend fun submit(session: AuthSession) {
        submitting = true
        try {
            state = Loadable.Loaded(client.send(CompanyEndpoints.submitOrganization()))
            justSubmitted = true
            // The shell keys off organization.submitted_at.
            runCatching { session.refreshUser() }
        } catch (e: Exception) { toast = e.userMessage } finally { submitting = false }
    }

    companion object {
        /// What still blocks submission, named — the web wizard's "Needs
        /// attention" line. Empty = complete, submit unlocks.
        fun missingSummary(steps: CompanySetup.Steps): List<String> {
            val missing = mutableListOf<String>()
            missing += steps.profile.missing ?: (if (steps.profile.complete) emptyList() else listOf("business profile"))
            missing += steps.details.missing ?: (if (steps.details.complete) emptyList() else listOf("services & rates"))
            if (!steps.credentials.complete) {
                val uploaded = steps.credentials.uploaded ?: 0
                val total = steps.credentials.total ?: 4
                missing += "${total - uploaded} of $total required documents"
            }
            return missing
        }
    }
}

/// The company setup flow: profile → services & rates → credentials →
/// review & submit. Each step is a screen it links out to; this page is
/// the checklist + submit, like the web's review step. The web wizard's
/// plan step is deliberately absent here — the app never offers a plan
/// (store policy, see PARITY.md); the server's "finish later" path
/// already submits without one, and the approval email covers the pick.
@Composable
fun CompanySetupScreen(onBack: (() -> Unit)?) {
    val environment = LocalAppEnvironment.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val colors = ZTheme.colors
    val model = remember { CompanySetupModel(environment.client) }
    LaunchedEffect(model) { model.load() }
    androidx.lifecycle.compose.LifecycleResumeEffect(model) { scope.launch { model.load() }; onPauseOrDispose { } }

    Column {
        ZTopBar("Setup", onBack = onBack)
        ZToastBox(model.toast, { model.toast = null }) {
            ZScreen(onRefresh = { model.load() }) {
                ZLoadable(model.state, retry = { scope.launch { model.load() } }) { response ->
                    Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.md)) {
                        if (model.justSubmitted) {
                            Icon(Icons.Filled.Verified, contentDescription = null, tint = colors.brandGold, modifier = Modifier.padding(top = ZSpacing.xl).size(40.dp))
                            ZMono("Application submitted")
                            ZDisplay("Your application is in review")
                            ZBody("Our team checks every company before it goes live — that review is what the ZiVETT badge means to bookers. You'll hear back by notification; approval opens the job feed.", tone = ZTextTone.SOFT)
                        } else {
                            ZPageTitle("Set up your business", "This is your application to become a Verified Pro. Finish the three steps and submit — our team takes it from there.")
                            SetupChecklist(response.setup)
                            ZCard(padding = 0.dp) {
                                ZNavRow("Business profile", "Name, address, phone — what customers see", onClick = { nav.navigate(OrgProfileRoute(Areas.COMPANY)) }) { ZIconTile(Icons.Outlined.Business) }
                                ZDivider(Modifier.padding(start = 60.dp))
                                ZNavRow("Services & rates", "Trades, hourly rates, radius, availability", onClick = { nav.navigate(PassportRoute) }) { ZIconTile(Icons.Outlined.Build) }
                                ZDivider(Modifier.padding(start = 60.dp))
                                ZNavRow("Credentials", "Photo ID, license, insurance, registration", onClick = { nav.navigate(PassportRoute) }) { ZIconTile(Icons.Outlined.UploadFile) }
                                ZDivider(Modifier.padding(start = 60.dp))
                                ZNavRow("What each document needs", "What a good upload shows, per document", onClick = { nav.navigate(CredentialGuideRoute) }) { ZIconTile(Icons.Outlined.Checklist) }
                            }
                            // Submit is gated on completeness, like the web review step.
                            val missing = CompanySetupModel.missingSummary(response.setup.steps)
                            if (missing.isNotEmpty()) ZBanner("Needs attention — ${missing.joinToString(", ")}.", tone = ZTone.WARNING)
                            if (environment.session.user?.organizationRole == OrganizationRole.ADMIN) {
                                ZButton("Submit application", style = ZButtonStyle.SUCCESS, loading = model.submitting, enabled = missing.isEmpty()) { scope.launch { model.submit(environment.session) } }
                            } else ZCaption("Only organization admins can submit the application.")
                        }
                        Spacer(Modifier.padding(ZSpacing.lg))
                    }
                }
            }
        }
    }
}

/// The per-document upload guide (`fieldGuide.credentials` on the web).
@Composable
fun CredentialGuideScreen(onBack: () -> Unit) {
    Column {
        ZTopBar("Document guide", onBack = onBack)
        ZScreen {
            ZPageTitle("Document guide", "What our review team looks for in each upload — get these right and approval is fast.")
            for (guide in CredentialFieldGuide.entries) {
                ZCard {
                    ZBodyStrong(guide.title)
                    ZCaption(guide.purpose)
                    ZLabel("A good upload shows")
                    for (item in guide.checklist) ZCheckLine(item)
                    ZCaption(guide.formats, tone = ZTextTone.FAINT)
                    guide.fallback?.let { ZCaption(it, tone = ZTextTone.FAINT) }
                }
            }
            Spacer(Modifier.padding(ZSpacing.lg))
        }
    }
}
