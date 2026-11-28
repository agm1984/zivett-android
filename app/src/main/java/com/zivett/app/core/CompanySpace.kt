package com.zivett.app.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/// A company running BOTH the marketplace and Business tools works one
/// side at a time (web `lib/companySpace.js`): "Marketplace" or "Your
/// business". The switch at the top of More swaps the whole tab bar; the
/// side is remembered on the device, so the app opens where they left off.
object CompanySpace {
    enum class Side(val wire: String, val label: String) {
        MARKETPLACE("marketplace", "Marketplace"),
        BUSINESS("business", "Your business"),
    }

    private const val PREFS = "zivett.company-space"
    private const val KEY = "space"
    private var context: Context? = null

    var current by mutableStateOf(Side.MARKETPLACE)
        private set

    fun attach(context: Context) {
        this.context = context.applicationContext
        current = if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) == Side.BUSINESS.wire) Side.BUSINESS else Side.MARKETPLACE
    }

    fun remember(side: Side) {
        current = side
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY, side.wire)?.apply()
    }
}
