package com.zivett.app.core

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
}
