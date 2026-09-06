package com.zivett.app.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text

/// Tone → color, resolved against the current palette.
@Composable
fun ZTextTone.color(): Color = when (this) {
    ZTextTone.INK -> ZTheme.colors.ink
    ZTextTone.SOFT -> ZTheme.colors.inkSoft
    ZTextTone.MUTED -> ZTheme.colors.inkMuted
    ZTextTone.FAINT -> ZTheme.colors.inkFaint
    ZTextTone.LABEL -> ZTheme.colors.label
    ZTextTone.LINK -> ZTheme.colors.link
}

@Composable
fun ZDisplay(text: String, modifier: Modifier = Modifier, color: Color = ZTheme.colors.ink) =
    Text(text, modifier, style = ZType.display, color = color)

@Composable
fun ZTitle(text: String, modifier: Modifier = Modifier, color: Color = ZTheme.colors.ink, maxLines: Int = Int.MAX_VALUE) =
    Text(text, modifier, style = ZType.title, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

@Composable
fun ZHeadline(text: String, modifier: Modifier = Modifier, tone: ZTextTone = ZTextTone.INK, color: Color? = null) =
    Text(text, modifier, style = ZType.headline, color = color ?: tone.color())

@Composable
fun ZBody(text: String, modifier: Modifier = Modifier, tone: ZTextTone = ZTextTone.INK, color: Color? = null, maxLines: Int = Int.MAX_VALUE, textAlign: TextAlign? = null) =
    Text(text, modifier, style = ZType.body, color = color ?: tone.color(), maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = textAlign)

@Composable
fun ZBodyStrong(text: String, modifier: Modifier = Modifier, tone: ZTextTone = ZTextTone.INK, color: Color? = null, maxLines: Int = Int.MAX_VALUE) =
    Text(text, modifier, style = ZType.bodyStrong, color = color ?: tone.color(), maxLines = maxLines, overflow = TextOverflow.Ellipsis)

@Composable
fun ZLabel(text: String, modifier: Modifier = Modifier, tone: ZTextTone = ZTextTone.LABEL, color: Color? = null) =
    Text(text, modifier, style = ZType.label, color = color ?: tone.color())

@Composable
fun ZCaption(text: String, modifier: Modifier = Modifier, tone: ZTextTone = ZTextTone.MUTED, color: Color? = null, maxLines: Int = Int.MAX_VALUE, textAlign: TextAlign? = null) =
    Text(text, modifier, style = ZType.caption, color = color ?: tone.color(), maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = textAlign)

@Composable
fun ZCaption(text: AnnotatedString, modifier: Modifier = Modifier, tone: ZTextTone = ZTextTone.MUTED) =
    Text(text, modifier, style = ZType.caption, color = tone.color())

/// The mono, uppercase, letter-spaced eyebrow ("ALSO ON YOUR PLATE").
@Composable
fun ZMono(text: String, modifier: Modifier = Modifier, tone: ZTextTone = ZTextTone.MUTED, color: Color? = null, maxLines: Int = Int.MAX_VALUE) =
    Text(text.uppercase(), modifier, style = ZType.mono, color = color ?: tone.color(), maxLines = maxLines, overflow = TextOverflow.Ellipsis)

/// Mono money/number readout.
@Composable
fun ZMonoLarge(text: String, modifier: Modifier = Modifier, color: Color = ZTheme.colors.ink, style: TextStyle = ZType.monoLarge) =
    Text(text, modifier, style = style, color = color)

@Composable
fun ZMonoBody(text: String, modifier: Modifier = Modifier, color: Color = ZTheme.colors.ink, weight: FontWeight = FontWeight.Medium) =
    Text(text, modifier, style = ZType.monoBody.copy(fontWeight = weight), color = color)
