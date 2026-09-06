package com.zivett.app.features.customer.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.zivett.app.core.Loadable
import com.zivett.app.core.models.CompanyReviewsResponse
import com.zivett.app.core.models.CustomerEndpoints
import com.zivett.app.core.reloaded
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZBodyStrong
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCard
import com.zivett.app.design.ZLoadable
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZStars
import com.zivett.app.design.ZTextTone
import com.zivett.app.features.company.Dates
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/// What "4.9 · 176 reviews" actually says — the web's CompanyReviewsModal,
/// opened from the pro card and from quote rows.
@Composable
fun CompanyReviewsSheet(organizationId: Int, companyName: String, onDismiss: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    var state by remember(organizationId) { mutableStateOf<Loadable<CompanyReviewsResponse>>(Loadable.Loading) }

    suspend fun load() { state = state.reloaded { environment.client.send(CustomerEndpoints.companyReviews(organizationId)) } }
    LaunchedEffect(organizationId) { load() }

    ZSheet(onDismiss = onDismiss, title = companyName) {
        ZLoadable(state, retry = { scope.launch { load() } }) { response ->
            Column(verticalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                response.summary.rating?.let { rating ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ZStars(rating.roundToInt())
                        ZBodyStrong("${rating.oneDecimal()} · ${response.summary.count} ${if (response.summary.count == 1) "review" else "reviews"}")
                    }
                }
                if (response.reviews.isEmpty()) ZBody("No reviews yet — this pro is new to ZiVETT.", tone = ZTextTone.SOFT)
                for (review in response.reviews) {
                    ZCard(padding = ZSpacing.sm) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            ZStars(review.rating)
                            Spacer(Modifier.weight(1f))
                            review.createdAt?.let { ZCaption(Dates.short(it), tone = ZTextTone.FAINT) }
                        }
                        review.comment?.takeIf { it.isNotEmpty() }?.let { ZBody(it) }
                        ZCaption(listOfNotNull(review.reviewer, review.job?.title).joinToString(" · "))
                    }
                }
            }
        }
    }
}
