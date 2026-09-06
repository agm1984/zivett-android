package com.zivett.app.features.referrals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.zivett.app.core.Money
import com.zivett.app.design.ZMonoBody
import com.zivett.app.design.ZRadius
import com.zivett.app.design.ZTheme
import com.zivett.app.design.ZType

/// The referral wallet, shared by the customer and company kits
/// (`BankedCredits.vue`): how many earned credits are waiting, what
/// they add up to, and which single one spends next. Credits spend ONE
/// at a time, so the bank total and the next credit are different
/// numbers — saying both is the whole point of this panel.
@Composable
fun BankedCreditsPanel(count: Int, totalCents: Int, nextCents: Int, applies: String, per: String) {
    if (count <= 0) return
    val colors = ZTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.tile)).background(colors.successSoft).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$count ${if (count == 1) "credit" else "credits"} banked", style = ZType.bodyStrong, color = colors.success, modifier = Modifier.weight(1f))
            ZMonoBody(Money.format(totalCents), color = colors.success, weight = FontWeight.SemiBold)
        }
        val line = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(Money.format(nextCents)) }
            append(" $applies")
            if (count > 1) append(" — credits spend one per $per, so the rest stay banked for your next ${per}s")
            append(".")
        }
        Text(line, style = ZType.caption, color = colors.inkMuted)
    }
}
