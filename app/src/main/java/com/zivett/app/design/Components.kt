package com.zivett.app.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zivett.app.core.Loadable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// MARK: - Page chrome

/// Cream-backed scrolling page with the standard gutter. Every full
/// screen sits in one of these so the background and insets never drift.
/// `onRefresh` turns on Material's pull-to-refresh.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZScreen(
    modifier: Modifier = Modifier,
    scrolls: Boolean = true,
    onRefresh: (suspend () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = ZSpacing.gutter),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ZTheme.colors
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    val body: @Composable () -> Unit = {
        if (scrolls) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(ZSpacing.md),
                content = content,
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding), verticalArrangement = Arrangement.spacedBy(ZSpacing.md), content = content)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.cream)) {
        if (onRefresh != null) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { scope.launch { refreshing = true; onRefresh(); refreshing = false } },
            ) { body() }
        } else {
            body()
        }
    }
}

/// The screen-level app bar: a back arrow (when there's somewhere to go),
/// the title, and optional actions.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZTopBar(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val colors = ZTheme.colors
    TopAppBar(
        title = {
            if (subtitle == null) {
                Text(title, style = ZType.headline, color = colors.ink, maxLines = 1)
            } else {
                Column {
                    Text(title, style = ZType.headline, color = colors.ink, maxLines = 1)
                    Text(subtitle, style = ZType.caption, color = colors.inkMuted, maxLines = 1)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink) }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.cream, scrolledContainerColor = colors.cream),
    )
}

/// "ZIVETT" in the brand's heavy, tight-tracked style.
@Composable
fun BrandWordmark(color: Color = ZTheme.colors.navy) {
    Text("ZiVETT", style = ZType.wordmark, color = color, modifier = Modifier.semantics { contentDescription = "ZiVETT" })
}

/// The ZiVETT mark — the app icon's checkmark tile, drawn natively so
/// it's crisp at any size: deep charcoal tile, gold hairline border, gold
/// gradient check. Deliberately identical in both appearances.
@Composable
fun BrandMark(size: Dp = 96.dp) {
    val goldGradient = Brush.verticalGradient(0f to Color(0xFFFFE27A), 0.45f to Color(0xFFD4AF37), 1f to Color(0xFFA97910))
    val tileGradient = Brush.linearGradient(listOf(Color(0xFF151B23), Color(0xFF080C11)))
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.width
        val radius = androidx.compose.ui.geometry.CornerRadius(s * 0.22f)
        drawRoundRect(tileGradient, cornerRadius = radius)
        val inset = s * 0.024f
        drawRoundRect(
            goldGradient,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(s - inset * 2, s - inset * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.20f),
            style = Stroke(width = maxOf(1f, s * 0.02f)),
        )
        // The check, from the icon's 1024-space path, fitted into the padded box.
        val pad = s * 0.16f
        val box = s - pad * 2
        val glyphW = 670f; val glyphH = 570f
        val scale = minOf(box / glyphW, box / glyphH)
        val offX = pad + (box - glyphW * scale) / 2
        val offY = pad + (box - glyphH * scale) / 2
        val points = listOf(248f to 500f, 405f to 702f, 790f to 265f, 850f to 330f, 405f to 835f, 180f to 545f)
        val path = Path()
        points.forEachIndexed { i, (x, y) ->
            val px = offX + (x - 180f) * scale
            val py = offY + (y - 265f) * scale
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        drawPath(path, goldGradient)
    }
}

/// The quiet "← Back to home" link the auth pages open with.
@Composable
fun ZBackLink(title: String, onClick: () -> Unit) {
    val colors = ZTheme.colors
    Row(
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(14.dp))
        ZCaption(title)
    }
}

// MARK: - Buttons

enum class ZButtonStyle { PRIMARY, SUCCESS, OUTLINE, SOFT, GHOST, DANGER }

/// Primary action button. Mirrors `RpButton`: charcoal/green fills, an
/// outline, a ghost link — with a built-in spinner for async work.
@Composable
fun ZButton(
    title: String,
    modifier: Modifier = Modifier,
    style: ZButtonStyle = ZButtonStyle.PRIMARY,
    compact: Boolean = false,
    loading: Boolean = false,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = ZTheme.colors
    val foreground = when (style) {
        ZButtonStyle.PRIMARY, ZButtonStyle.SUCCESS, ZButtonStyle.DANGER -> colors.onBrand
        ZButtonStyle.OUTLINE, ZButtonStyle.SOFT -> colors.ink
        ZButtonStyle.GHOST -> colors.link
    }
    val background = when (style) {
        ZButtonStyle.PRIMARY -> colors.navy
        // Semantic success (accept actions) — not the brand gold, whose
        // fills take charcoal text, not white.
        ZButtonStyle.SUCCESS -> colors.success
        ZButtonStyle.DANGER -> colors.danger
        ZButtonStyle.OUTLINE -> colors.surface
        ZButtonStyle.SOFT -> colors.surfaceSunken
        ZButtonStyle.GHOST -> Color.Transparent
    }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.then(if (fullWidth) Modifier.fillMaxWidth() else Modifier),
        shape = RoundedCornerShape(ZRadius.button),
        colors = ButtonDefaults.buttonColors(
            containerColor = background, contentColor = foreground,
            disabledContainerColor = background.copy(alpha = if (style == ZButtonStyle.GHOST) 0f else 0.6f), disabledContentColor = foreground.copy(alpha = 0.7f),
        ),
        border = if (style == ZButtonStyle.OUTLINE) BorderStroke(1.5.dp, colors.borderStrong) else null,
        contentPadding = if (compact) PaddingValues(horizontal = 14.dp, vertical = 8.dp) else PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        elevation = null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                title,
                style = if (compact) ZType.label else ZType.bodyStrong,
                modifier = Modifier.then(if (loading) Modifier.semantics { contentDescription = title } else Modifier),
                color = if (loading) Color.Transparent else foreground,
                maxLines = 1,
            )
            if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = foreground, strokeWidth = 2.dp)
        }
    }
}

/// Inline text link ("Create an account", "Forgot?").
@Composable
fun ZLinkButton(title: String, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.SemiBold, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        title,
        style = ZType.body.copy(fontWeight = weight),
        color = ZTheme.colors.link.copy(alpha = if (enabled) 1f else 0.5f),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick).padding(vertical = 4.dp),
    )
}

/// A small tappable label in the link color (the "Edit" / "Manage" affordances).
@Composable
fun ZTextAction(title: String, modifier: Modifier = Modifier, color: Color = ZTheme.colors.link, enabled: Boolean = true, onClick: () -> Unit) {
    Text(title, style = ZType.label, color = color.copy(alpha = if (enabled) 1f else 0.5f), modifier = modifier.clickable(enabled = enabled, onClick = onClick).padding(4.dp))
}

/// The gold action band — the card-footer CTA, mirroring the web's
/// `RpActionBand`: a full-width gold bar with the label in charcoal and a
/// trailing arrow, reserved for the screen's ONE winning action (accept a
/// quote, pay, submit the booking). Never place two on a screen, and
/// never use it for destructive actions. Gold fills take navyDeep text.
@Composable
fun ZActionBand(
    title: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
    flushBottom: Boolean = false,
    radius: Dp = ZRadius.card,
    onClick: () -> Unit,
) {
    val colors = ZTheme.colors
    val shape = if (flushBottom) RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = radius - 1.dp, bottomEnd = radius - 1.dp) else RoundedCornerShape(radius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ZColors.fixedGold.copy(alpha = if (enabled) 1f else 0.6f))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = ZSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = ZType.bodyStrong.copy(fontWeight = FontWeight.ExtraBold), color = ZColors.fixedNavyDeep, modifier = Modifier.weight(1f))
        if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = ZColors.fixedNavyDeep, strokeWidth = 2.dp)
        else Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = ZColors.fixedNavyDeep, modifier = Modifier.size(18.dp))
    }
}

// MARK: - Inputs

/// Labelled input with the web's treatment: 13sp semibold label, 10dp
/// radius, strong border, charcoal focus ring, inline error. `corner`
/// takes a trailing accessory next to the label (the login page's "Forgot?").
@Composable
fun ZTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    secure: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    corner: (@Composable () -> Unit)? = null,
) {
    val colors = ZTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ZSpacing.xs)) {
        if (label.isNotEmpty() || corner != null) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (label.isNotEmpty()) ZLabel(label)
                Spacer(Modifier.weight(1f))
                corner?.invoke()
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder, style = ZType.body, color = colors.inkFaint) }),
            isError = error != null,
            singleLine = singleLine,
            minLines = minLines,
            enabled = enabled,
            visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secure) KeyboardType.Password else keyboardType, capitalization = capitalization, imeAction = imeAction, autoCorrectEnabled = false),
            textStyle = ZType.body.copy(color = colors.ink),
            shape = RoundedCornerShape(ZRadius.field),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.navy, unfocusedBorderColor = colors.borderStrong, errorBorderColor = colors.danger,
                focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface, errorContainerColor = colors.surface, disabledContainerColor = colors.surfaceAlt,
                cursorColor = colors.navy, focusedTextColor = colors.ink, unfocusedTextColor = colors.ink,
            ),
        )
        if (error != null) ZCaption(error, color = colors.danger)
    }
}

/// Multi-line input matching `ZTextField`.
@Composable
fun ZTextArea(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "", error: String? = null, minLines: Int = 4) {
    ZTextField(label, value, onValueChange, modifier, placeholder, error, singleLine = false, minLines = minLines, capitalization = KeyboardCapitalization.Sentences)
}

/// Square check with trailing (possibly rich) label — "Keep me signed
/// in", the terms consent row.
@Composable
fun ZCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, label: @Composable () -> Unit) {
    val colors = ZTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, colors = CheckboxDefaults.colors(checkedColor = colors.navy, uncheckedColor = colors.inkFaint, checkmarkColor = colors.onBrand))
        Box(Modifier.weight(1f)) { label() }
    }
}

@Composable
fun ZCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, text: String, modifier: Modifier = Modifier) =
    ZCheckbox(checked, onCheckedChange, modifier) { ZCaption(text, tone = ZTextTone.SOFT) }

@Composable
fun ZToggleRow(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val colors = ZTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ZBodyStrong(title)
            if (subtitle != null) ZCaption(subtitle)
        }
        Switch(
            checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.brandGold, checkedThumbColor = colors.navyDeep, uncheckedTrackColor = colors.surfaceSunken, uncheckedBorderColor = colors.borderStrong),
        )
    }
}

/// One of the "I'm signing up as…" radio tiles: a mono code chip, a
/// title, a one-line description, and a filled check when selected.
@Composable
fun ZChoiceTile(code: String, title: String, subtitle: String, selected: Boolean, tone: ZTone = ZTone.INFO, onClick: () -> Unit) {
    val colors = ZTheme.colors
    val (fg, bg) = colors.tone(tone)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ZRadius.card))
            .background(if (selected) colors.surface else colors.surfaceAlt)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.navy else colors.border, RoundedCornerShape(ZRadius.card))
            .clickable(onClick = onClick)
            .padding(ZSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm),
    ) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(bg), contentAlignment = Alignment.Center) {
            Text(code, style = ZType.monoBody.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp), color = fg)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ZBodyStrong(title)
            if (subtitle.isNotEmpty()) ZCaption(subtitle)
        }
        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = colors.navy, modifier = Modifier.size(22.dp))
    }
}

/// A selectable row (the wizard's single/multi choices, saved places).
@Composable
fun ZSelectableRow(selected: Boolean, onClick: () -> Unit, enabled: Boolean = true, radius: Dp = ZRadius.tile, content: @Composable RowScope.() -> Unit) {
    val colors = ZTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(colors.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.navy else colors.border, RoundedCornerShape(radius))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(ZSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm),
    ) {
        content()
        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = colors.navy, modifier = Modifier.size(22.dp))
    }
}

/// Inline star picker/display.
@Composable
fun ZStars(rating: Int, onSelect: ((Int) -> Unit)? = null) {
    val colors = ZTheme.colors
    val size = if (onSelect == null) 14.dp else 30.dp
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.semantics { contentDescription = "$rating of 5 stars" }) {
        for (star in 1..5) {
            val filled = star <= rating
            Icon(
                if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (filled) colors.star else colors.inkFaint,
                modifier = Modifier.size(size).then(if (onSelect != null) Modifier.clickable { onSelect(star) } else Modifier),
            )
        }
    }
}

// MARK: - Surfaces

/// White surface with the soft border and 16dp radius (`RpCard`).
@Composable
fun ZCard(modifier: Modifier = Modifier, padding: Dp = ZSpacing.md, radius: Dp = ZRadius.card, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = ZTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(radius))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(ZSpacing.xs),
        content = content,
    )
}

/// Tinted notice row (the signup fee banner).
@Composable
fun ZBanner(text: String, tone: ZTone = ZTone.SUCCESS, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val colors = ZTheme.colors
    val (_, bg) = colors.tone(tone)
    Box(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.tile)).background(bg).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(ZSpacing.md),
    ) { Text(text, style = ZType.caption, color = colors.ink) }
}

@Composable
fun ZBanner(text: AnnotatedString, tone: ZTone = ZTone.SUCCESS, modifier: Modifier = Modifier) {
    val colors = ZTheme.colors
    val (_, bg) = colors.tone(tone)
    Box(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(ZRadius.tile)).background(bg).padding(ZSpacing.md)) {
        Text(text, style = ZType.caption, color = colors.ink)
    }
}

/// The mono status pill (`RpBadge`): "ON THE WAY", "PAY", "DECIDE".
@Composable
fun ZBadge(text: String, tone: ZTone = ZTone.NEUTRAL, modifier: Modifier = Modifier) {
    val (fg, bg) = ZTheme.colors.tone(tone)
    Text(
        text.uppercase(),
        style = ZType.mono,
        color = fg,
        maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(ZRadius.badge)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/// Small tag for the plan marker next to a company name. Gold outranks
/// charcoal — ELITE/PREMIUM get the shinier mark (RpCompanyName's rule).
@Composable
fun ZPlanTag(plan: String) {
    val colors = ZTheme.colors
    val gold = plan.lowercase() in setOf("elite", "premium")
    Text(
        plan.uppercase(),
        style = ZType.mono.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        color = if (gold) colors.navyDeep else colors.onBrand,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (gold) colors.star else colors.navy).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

enum class ZAvatarShape { PERSON, COMPANY }

/// Initials tile. Round for people, rounded-square for companies.
@Composable
fun ZAvatar(initials: String, shape: ZAvatarShape = ZAvatarShape.PERSON, size: Dp = 40.dp, onDark: Boolean = false) {
    val colors = ZTheme.colors
    val clipShape: Shape = if (shape == ZAvatarShape.PERSON) CircleShape else RoundedCornerShape(ZRadius.tile)
    Box(
        modifier = Modifier.size(size).clip(clipShape).background(if (onDark) ZColors.fixedNavy else colors.infoSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            style = ZType.bodyStrong.copy(fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.ExtraBold, fontFamily = if (shape == ZAvatarShape.COMPANY) FontFamily.Monospace else null),
            color = if (onDark) Color.White else colors.info,
        )
    }
}

/// A photo avatar with initials fallback (team members, assigned techs).
@Composable
fun ZPhotoAvatar(url: String?, initials: String, size: Dp = 40.dp, shape: ZAvatarShape = ZAvatarShape.PERSON) {
    val clipShape: Shape = if (shape == ZAvatarShape.PERSON) CircleShape else RoundedCornerShape(ZRadius.tile)
    if (url == null) {
        ZAvatar(initials, shape, size)
    } else {
        coil3.compose.AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.size(size).clip(clipShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    }
}

/// Icon in a soft square (the account rows' leading glyph).
@Composable
fun ZIconTile(icon: ImageVector, size: Dp = 32.dp, tone: ZTone = ZTone.INFO) {
    val (fg, bg) = ZTheme.colors.tone(tone)
    Box(modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(size / 2))
    }
}

/// A list row with chevron.
@Composable
fun ZNavRow(title: String, subtitle: String? = null, onClick: () -> Unit, leading: @Composable () -> Unit = {}) {
    val colors = ZTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(ZSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm),
    ) {
        leading()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ZBodyStrong(title)
            if (subtitle != null) ZCaption(subtitle, maxLines = 2)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.inkFaint)
    }
}

/// Section heading in the mono eyebrow style.
@Composable
fun ZSectionHeader(title: String, modifier: Modifier = Modifier) {
    ZMono(title, modifier = modifier.padding(start = 4.dp))
}

/// Page title in the standard position.
@Composable
fun ZPageTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().padding(top = ZSpacing.xs)) {
        ZTitle(title)
        if (subtitle != null) ZBody(subtitle, tone = ZTextTone.SOFT)
    }
}

@Composable
fun ZDivider(modifier: Modifier = Modifier) = HorizontalDivider(modifier = modifier, color = ZTheme.colors.borderSoft)

/// The error state for a failed request with a retry.
@Composable
fun ZErrorState(message: String, retry: () -> Unit) {
    ZCard {
        ZHeadline("Something went wrong")
        ZBody(message, tone = ZTextTone.SOFT)
        ZButton("Try again", style = ZButtonStyle.OUTLINE, compact = true, fullWidth = false, onClick = retry)
    }
}

/// Empty-list placeholder.
@Composable
fun ZEmptyState(icon: ImageVector, title: String, message: String) {
    val colors = ZTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = ZSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZSpacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = colors.inkFaint, modifier = Modifier.size(32.dp))
        ZHeadline(title)
        ZCaption(message, textAlign = TextAlign.Center)
    }
}

/// Renders a `Loadable` with the standard spinner / error card.
@Composable
fun <T> ZLoadable(state: Loadable<T>, retry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        Loadable.Loading -> Box(modifier = Modifier.fillMaxWidth().padding(top = ZSpacing.xxl), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ZTheme.colors.navy)
        }
        is Loadable.Failed -> ZErrorState(state.message, retry)
        is Loadable.Loaded -> content(state.loaded)
    }
}

@Composable
fun ZSpinner(modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = ZTheme.colors.navy) {
    CircularProgressIndicator(modifier = modifier.size(size), color = color, strokeWidth = 2.5.dp)
}

/// Stat tile: mono value over an eyebrow label (dashboards, referrals).
@Composable
fun ZStatTile(value: String, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    ZCard(modifier = modifier, padding = ZSpacing.sm, onClick = onClick) {
        Text(value, style = ZType.stat, color = ZTheme.colors.ink, maxLines = 1)
        ZMono(label)
    }
}

/// The dark gradient hero panel (job headers, the passport card).
@Composable
fun ZHeroPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ZRadius.panel))
            .background(Brush.linearGradient(listOf(ZColors.fixedNavy, ZColors.fixedNavyDeep)))
            .padding(ZSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ZSpacing.xs),
        content = content,
    )
}

// MARK: - Sheets & toasts

/// A modal bottom sheet with the app's ground color, keyboard-aware, that
/// opens fully expanded — the Android home for what iOS presents as a
/// sheet. `title` renders the standard sheet header.
/// `dismissable = false` pins the sheet open — swipe, scrim and back are
/// all refused. Pay sheets pass `!busy`: a charge must never be walked
/// away from mid-request.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZSheet(onDismiss: () -> Unit, title: String? = null, dismissable: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val colors = ZTheme.colors
    val canDismiss by rememberUpdatedState(dismissable)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { canDismiss || it != SheetValue.Hidden })
    ModalBottomSheet(
        onDismissRequest = { if (canDismiss) onDismiss() },
        sheetState = sheetState,
        containerColor = colors.cream,
        contentColor = colors.ink,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissable),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZSpacing.gutter)
                .padding(bottom = ZSpacing.xl)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(ZSpacing.md),
        ) {
            if (title != null) ZTitle(title)
            content()
        }
    }
}

/// The toast pill at the bottom of a screen — mirrors the web's
/// `RpToastHost` and Material's snackbar: charcoal with white text and a
/// gold dot, auto-dismissed after four seconds. Every screen's
/// success/error toast goes through this.
@Composable
fun BoxScope.ZToast(message: String?, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        if (message != null) { delay(4000); onDismiss() }
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = ZSpacing.md).padding(bottom = ZSpacing.lg),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        var shown by remember { mutableStateOf(message ?: "") }
        if (message != null) shown = message
        Snackbar(
            containerColor = ZColors.fixedNavyDeep,
            contentColor = Color.White,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.clickable(onClick = onDismiss),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(ZColors.fixedGoldBright))
                Text(shown, style = ZType.caption.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = Color.White, maxLines = 3)
            }
        }
    }
}

/// A screen body plus the toast overlay, the standard pairing.
@Composable
fun ZToastBox(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.fillMaxSize()) {
        content()
        ZToast(message, onDismiss)
    }
}

/// Simple wrapping row for chips.
@Composable
fun ZFlowRow(spacing: Dp = 6.dp, content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

/// A chip pill (intake answers).
@Composable
fun ZChip(text: String) {
    Text(text, style = ZType.caption, color = ZTheme.colors.ink, modifier = Modifier.clip(CircleShape).background(ZTheme.colors.surfaceSunken).padding(horizontal = 10.dp, vertical = 4.dp))
}

/// The checkmark glyph used in feature lists.
@Composable
fun ZCheckLine(text: String, tone: ZTextTone = ZTextTone.MUTED) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = ZTheme.colors.success, modifier = Modifier.size(14.dp).padding(top = 2.dp))
        ZCaption(text, tone = tone)
    }
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun HSpace(width: Dp) = Spacer(Modifier.width(width))

@Suppress("unused")
private val keepSurface: @Composable () -> Unit = { Surface {} ; LocalContentColor.current; MaterialTheme.colorScheme }
