package com.zivett.app.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/// Spacing scale (dp) — the iOS `ZSpacing` values.
object ZSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    /// Horizontal screen gutter.
    val gutter = 20.dp
}

object ZRadius {
    val field = 10.dp
    val button = 11.dp
    val badge = 6.dp
    val tile = 12.dp
    val card = 16.dp
    val panel = 24.dp
}

object ZTheme {
    val colors: ZColors
        @Composable get() = LocalZColors.current
}

/// The Material 3 theme built from the brand tokens: charcoal primary,
/// gold secondary, cream ground. Dynamic color is deliberately off so
/// the app wears the brand, not the wallpaper.
@Composable
fun ZivettTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) ZColors.dark else ZColors.light
    val scheme: ColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.navy, onPrimary = colors.onBrand, primaryContainer = colors.surfaceSunken, onPrimaryContainer = colors.ink,
            secondary = colors.brandGold, onSecondary = colors.navyDeep, secondaryContainer = colors.warningSoft, onSecondaryContainer = colors.ink,
            tertiary = colors.info, onTertiary = colors.onBrand, tertiaryContainer = colors.infoSoft, onTertiaryContainer = colors.info,
            background = colors.cream, onBackground = colors.ink,
            surface = colors.cream, onSurface = colors.ink, surfaceVariant = colors.surfaceSunken, onSurfaceVariant = colors.inkSoft,
            surfaceContainer = colors.surface, surfaceContainerLow = colors.surfaceAlt, surfaceContainerHigh = colors.surfaceSunken, surfaceContainerHighest = colors.surfaceSunken, surfaceContainerLowest = colors.surface,
            error = colors.danger, onError = colors.onBrand, errorContainer = colors.dangerSoft, onErrorContainer = colors.danger,
            outline = colors.borderStrong, outlineVariant = colors.border,
            inverseSurface = ZColors.fixedNavyDeep, inverseOnSurface = Color.White,
        )
    } else {
        lightColorScheme(
            primary = colors.navy, onPrimary = colors.onBrand, primaryContainer = colors.surfaceSunken, onPrimaryContainer = colors.ink,
            secondary = colors.brandGold, onSecondary = colors.navyDeep, secondaryContainer = colors.warningSoft, onSecondaryContainer = colors.ink,
            tertiary = colors.info, onTertiary = colors.onBrand, tertiaryContainer = colors.infoSoft, onTertiaryContainer = colors.info,
            background = colors.cream, onBackground = colors.ink,
            surface = colors.cream, onSurface = colors.ink, surfaceVariant = colors.surfaceSunken, onSurfaceVariant = colors.inkSoft,
            surfaceContainer = colors.surface, surfaceContainerLow = colors.surfaceAlt, surfaceContainerHigh = colors.surfaceSunken, surfaceContainerHighest = colors.surfaceSunken, surfaceContainerLowest = colors.surface,
            error = colors.danger, onError = colors.onBrand, errorContainer = colors.dangerSoft, onErrorContainer = colors.danger,
            outline = colors.borderStrong, outlineVariant = colors.border,
            inverseSurface = ZColors.fixedNavyDeep, inverseOnSurface = Color.White,
        )
    }

    val typography = Typography(
        displaySmall = ZType.display, headlineMedium = ZType.title, titleMedium = ZType.headline,
        bodyLarge = ZType.body, bodyMedium = ZType.caption, labelLarge = ZType.label, labelMedium = ZType.label, labelSmall = ZType.mono,
    )

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(ZRadius.badge),
        small = RoundedCornerShape(ZRadius.field),
        medium = RoundedCornerShape(ZRadius.tile),
        large = RoundedCornerShape(ZRadius.card),
        extraLarge = RoundedCornerShape(ZRadius.panel),
    )

    CompositionLocalProvider(LocalZColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}
