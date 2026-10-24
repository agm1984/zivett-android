package com.zivett.app.features.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zivett.app.core.models.CompanySummary
import com.zivett.app.design.ZFlowRow
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZTone
import com.zivett.app.design.ZType

/// What ZiVETT actually checked about a pro, component by component — the
/// web's `RpVerificationBadges`. `QuoteResource.company.verification`
/// carries only verified or expired items (`Verification::visibleBadges`),
/// so each one is a quiet chip under the company's name; a lapsed item
/// stays visible as expired rather than silently disappearing.
@Composable
fun VerificationBadges(badges: List<CompanySummary.Verification>?, modifier: Modifier = Modifier) {
    if (badges.isNullOrEmpty()) return
    val summary = "Verified by ZiVETT: " + badges.joinToString { if (it.status == "expired") expiredLabel(it.label) else it.label }
    Row(modifier = modifier.semantics(mergeDescendants = true) { contentDescription = summary }) {
        ZFlowRow(spacing = 6.dp) {
            for (badge in badges) {
                val expired = badge.status == "expired"
                val (fg, bg) = ZTheme.colors.tone(if (expired) ZTone.WARNING else ZTone.SUCCESS)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    if (!expired) Icon(Icons.Default.Check, contentDescription = null, tint = fg, modifier = Modifier.size(11.dp))
                    Text(
                        if (expired) expiredLabel(badge.label) else badge.label,
                        style = ZType.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                        color = fg,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/// "Insurance verified" → "Insurance expired"; "Background checked" →
/// "Background check expired".
fun expiredLabel(label: String): String = when {
    label.endsWith(" verified") -> label.removeSuffix(" verified")
    label.endsWith(" checked") -> label.removeSuffix(" checked") + " check"
    else -> label
} + " expired"
