package com.zivett.app.core

import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
    private val formatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale.CANADA).apply {
        currency = Currency.getInstance("CAD")
    }

    /// Cents → "$1,234.50" — always CAD, which is what every invoice is
    /// denominated in.
    fun format(cents: Int): String {
        val text = formatter.format(cents / 100.0)
        // The Canadian locale writes "CA$" outside Canada; the narrow
        // symbol is what every invoice shows.
        return text.replace("CA$", "$")
    }

    /// A typed dollar amount → cents, or null when it isn't a number.
    /// The decimal keyboard follows the PHONE's locale, so a fr-CA user
    /// types "5,50" — which `toBigDecimalOrNull` reads as nothing, and a
    /// tip silently became $0. Both separators are accepted: when both
    /// appear the LAST one is the decimal point ("1,234.50", "1 234,50");
    /// a lone dot is always the decimal point (it is in both Canadian
    /// locales); a lone comma is too ("5,5"), unless exactly three digits
    /// follow it, which is en-CA grouping ("1,000"). Half-up to the cent.
    fun parseCents(text: String): Int? {
        val cleaned = text.filterNot { it.isWhitespace() || it == '$' || it == '\u00A0' || it == '\u202F' || it == '\'' }
        if (cleaned.isEmpty()) return null
        val lastComma = cleaned.lastIndexOf(',')
        val lastDot = cleaned.lastIndexOf('.')
        val decimalAt = when {
            lastComma >= 0 && lastDot >= 0 -> maxOf(lastComma, lastDot)
            else -> {
                val at = maxOf(lastComma, lastDot)
                val lone = at >= 0 && cleaned.count { it == ',' || it == '.' } == 1
                // "1,000" groups; "5,5" / "5,50" / ",5" are decimals.
                val grouping = cleaned[at.coerceAtLeast(0)] == ',' && cleaned.length - at - 1 == 3 && at > 0
                if (lone && !grouping) at else -1
            }
        }
        // Whatever separators remain are grouping, and have to look like
        // it ("1,2,3" is a typo, not $123).
        val groups = (if (decimalAt >= 0) cleaned.substring(0, decimalAt) else cleaned).trimStart('-').split(',', '.')
        if (groups.size > 1 && (groups.first().length !in 1..3 || groups.drop(1).any { it.length != 3 })) return null
        val normalized = buildString {
            cleaned.forEachIndexed { index, char ->
                when {
                    index == decimalAt -> append('.')
                    char == ',' || char == '.' -> Unit
                    else -> append(char)
                }
            }
        }
        val dollars = normalized.toBigDecimalOrNull() ?: return null
        return runCatching { dollars.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact() }.getOrNull()
    }

    /// An optional tip field → cents: blank or unreadable is no tip,
    /// clamped to the API's $1,000 ceiling. Shared by every pay surface.
    fun tipCents(text: String): Int = (parseCents(text) ?: 0).coerceIn(0, 100_000)
}
