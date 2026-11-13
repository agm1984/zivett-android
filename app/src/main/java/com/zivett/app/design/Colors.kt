package com.zivett.app.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/// The palette, ported from `resources/css/app.css` (`@theme`) in
/// ratedpro-web (via the iOS `Colors.swift`). Light values are the web's
/// exactly; dark values are a derived set so the app looks native with
/// the system appearance. Named with a `z` prefix at call sites
/// (`ZTheme.colors.navy`) to keep them distinct from Material's roles.
@Immutable
data class ZColors(
    // Brand — charcoal + gold + white (the ZiVETT brand sheet). The
    // `navy` names are legacy and now hold the charcoal family.
    val navy: Color,
    val navyDeep: Color,
    val navyBright: Color,
    val link: Color,
    val brandGold: Color,
    val goldBright: Color,
    val gold: Color,
    val star: Color,
    // Status (fg on matching soft bg)
    val info: Color,
    val infoSoft: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    // Surfaces
    val cream: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceSunken: Color,
    // Borders
    val border: Color,
    val borderStrong: Color,
    val borderSoft: Color,
    // Text
    val ink: Color,
    val inkSoft: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val label: Color,
    /// Text/icon color on top of the charcoal (`navy`) and semantic
    /// status fills — adaptive because those fills flip light in dark
    /// mode. NOT for gold fills; those take `navyDeep` text.
    val onBrand: Color,
    /// One colour per team member on the calendar (web `TECH_COLORS`, team
    /// order, cycling); jobs wear their LEAD's. Saturated mid-tones that
    /// carry white text on either ground; dark mode lifts them a step.
    val techColors: List<Color>,
    /// A job with nobody on it yet (web `NO_TECH_COLOR`).
    val noTech: Color,
    val isDark: Boolean,
) {
    fun tone(tone: ZTone): Pair<Color, Color> = when (tone) {
        ZTone.INFO -> info to infoSoft
        ZTone.SUCCESS -> success to successSoft
        ZTone.WARNING -> warning to warningSoft
        ZTone.DANGER -> danger to dangerSoft
        ZTone.NEUTRAL -> inkSoft to surfaceSunken
    }

    companion object {
        val light = ZColors(
            navy = Color(0xFF151B23), navyDeep = Color(0xFF0F1115), navyBright = Color(0xFF2A3644), link = Color(0xFF86671A),
            brandGold = Color(0xFFD4AF37), goldBright = Color(0xFFE8C65A), gold = Color(0xFFD4AF37), star = Color(0xFFD4AF37),
            info = Color(0xFF2B4E86), infoSoft = Color(0xFFEAF0F7), success = Color(0xFF1C8C3E), successSoft = Color(0xFFE7F0EA),
            warning = Color(0xFF8A6D2E), warningSoft = Color(0xFFF2EEE6), danger = Color(0xFFC0492B), dangerSoft = Color(0xFFFBEDE7),
            cream = Color(0xFFF7F6F3), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFFBFAF8), surfaceSunken = Color(0xFFEFEDE8),
            border = Color(0xFFE3E1DA), borderStrong = Color(0xFFD9D6CD), borderSoft = Color(0xFFEDEBE4),
            ink = Color(0xFF16181D), inkSoft = Color(0xFF54565F), inkMuted = Color(0xFF8A8578), inkFaint = Color(0xFFA6A399), label = Color(0xFF4A4D57),
            onBrand = Color(0xFFFFFFFF),
            techColors = listOf(
                Color(0xFF2563EB), Color(0xFF16A34A), Color(0xFFC2410C), Color(0xFF7C3AED), Color(0xFFDB2777),
                Color(0xFF0E7490), Color(0xFF4D7C0F), Color(0xFF4F46E5), Color(0xFFB45309), Color(0xFFBE123C),
            ),
            noTech = Color(0xFF6B7280),
            isDark = false,
        )

        val dark = ZColors(
            navy = Color(0xFFD9DDE4), navyDeep = Color(0xFF0B0D11), navyBright = Color(0xFFB8C0CC), link = Color(0xFFD9B75A),
            brandGold = Color(0xFFE0C05C), goldBright = Color(0xFFF0D67E), gold = Color(0xFFE0C05C), star = Color(0xFFE0C05C),
            info = Color(0xFFA7C0F0), infoSoft = Color(0xFF1E2A45), success = Color(0xFF6FD796), successSoft = Color(0xFF16301F),
            warning = Color(0xFFE0BF76), warningSoft = Color(0xFF332B1A), danger = Color(0xFFF08A6F), dangerSoft = Color(0xFF3B1F18),
            cream = Color(0xFF141519), surface = Color(0xFF1E2026), surfaceAlt = Color(0xFF1A1C21), surfaceSunken = Color(0xFF25272E),
            border = Color(0xFF2F323B), borderStrong = Color(0xFF3A3E48), borderSoft = Color(0xFF292C34),
            ink = Color(0xFFF2F0EA), inkSoft = Color(0xFFB9B7AF), inkMuted = Color(0xFF8E8B83), inkFaint = Color(0xFF6E6C66), label = Color(0xFFC5C3BC),
            onBrand = Color(0xFF0F1115),
            techColors = listOf(
                Color(0xFF3B82F6), Color(0xFF22A35A), Color(0xFFD9541C), Color(0xFF8B5CF6), Color(0xFFE0457F),
                Color(0xFF1B8AA6), Color(0xFF5E8F16), Color(0xFF6366F1), Color(0xFFC9700F), Color(0xFFD12A4E),
            ),
            noTech = Color(0xFF7C8494),
            isDark = true,
        )

        /// The brand panels (splash, welcome hero, dark home header) don't
        /// flip with the theme — these are fixed.
        val fixedNavyDeep = Color(0xFF0F1115)
        val fixedNavy = Color(0xFF151B23)
        val fixedGold = Color(0xFFD4AF37)
        val fixedGoldBright = Color(0xFFE8C65A)

        /// For API-provided colors (category `bg_color`/`fg_color`).
        fun parse(hex: String?): Color? {
            var cleaned = hex?.trim() ?: return null
            if (cleaned.startsWith("#")) cleaned = cleaned.drop(1)
            if (cleaned.length != 6) return null
            val value = cleaned.toLongOrNull(16) ?: return null
            return Color(0xFF000000 or value)
        }
    }
}

/// The five semantic tones a badge/banner/tile can take, each a fg +
/// soft-bg pair. Mirrors `RpBadge`'s variants.
enum class ZTone { INFO, SUCCESS, WARNING, DANGER, NEUTRAL }

val LocalZColors = staticCompositionLocalOf { ZColors.light }
