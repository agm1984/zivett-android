package com.zivett.app.features.customer.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.userMessage
import com.zivett.app.design.ZBanner
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZSectionHeader
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextTone
import com.zivett.app.design.ZToggleRow
import com.zivett.app.design.ZTone
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/// The Privacy Policy's advertising opt-out — the web's "Don't use my data
/// for advertising" switch on My account (App\Support\AdAudiences). Opting
/// out keeps the person's email out of any list ZiVETT shares with Meta or
/// Google; the app itself has no ad tracking to switch off.
object PrivacyEndpoints {
    @Serializable data class PrivacyRequestsPayload(val adOptOut: Boolean)
    @Serializable data class AdOptOutPayload(val adOptOut: Boolean)
    @Serializable data class AdOptOutBody(val optOut: Boolean)

    fun privacyRequests() = ApiRequest.get<PrivacyRequestsPayload>("api/privacy-requests")

    fun setAdOptOut(optOut: Boolean) = ApiRequest.json<AdOptOutPayload, AdOptOutBody>(ApiRequest.Method.PUT, "api/privacy/advertising", AdOptOutBody(optOut))
}

class AdvertisingPrivacyModel(private val client: ApiClient) {
    var optedOut by mutableStateOf<Boolean?>(null)
    var loadFailed by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    suspend fun load() {
        try { optedOut = client.send(PrivacyEndpoints.privacyRequests()).adOptOut; loadFailed = false }
        catch (_: Exception) { loadFailed = true }
    }

    suspend fun set(optOut: Boolean) {
        val previous = optedOut
        optedOut = optOut
        saving = true
        error = null
        try { optedOut = client.send(PrivacyEndpoints.setAdOptOut(optOut)).adOptOut }
        catch (e: Exception) { optedOut = previous; error = e.userMessage }
        finally { saving = false }
    }
}

/// Every role's Account screen, above the footer.
@Composable
fun AdvertisingPrivacySection() {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember { AdvertisingPrivacyModel(environment.client) }
    LaunchedEffect(model) { model.load() }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
        ZSectionHeader("Your privacy")
        ZCard {
            val optedOut = model.optedOut
            when {
                optedOut != null -> ZToggleRow(
                    "Don't use my data for advertising",
                    "We never include your email in the lists we may share with Meta or Google to measure or target ads.",
                    optedOut,
                    { on -> scope.launch { model.set(on) } },
                    enabled = !model.saving,
                )
                model.loadFailed -> ZCaption(
                    "Couldn't load your privacy settings — tap to retry",
                    tone = ZTextTone.LINK,
                    modifier = Modifier.clickable { scope.launch { model.load() } },
                )
                else -> CircularProgressIndicator(modifier = Modifier.size(20.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.dp)
            }
        }
        model.error?.let { ZBanner(it, tone = ZTone.DANGER) }
    }
}
