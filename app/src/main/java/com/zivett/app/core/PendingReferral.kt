package com.zivett.app.core

import android.content.Context
import android.net.Uri

/// The referral code from a tapped `/r/{code}` link (or `?ref=`), held
/// until signup attributes it. The server soft-ignores unresolvable
/// codes, so holding a stale one is harmless.
object PendingReferral {
    private const val PREFS = "zivett.referral"
    private const val KEY = "rp.pendingReferralCode"
    private var context: Context? = null

    fun attach(context: Context) { this.context = context.applicationContext }

    var code: String?
        get() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY, null)
        set(value) {
            val prefs = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
            if (value != null) prefs.edit().putString(KEY, value).apply() else prefs.edit().remove(KEY).apply()
        }

    /// Pulls a referral code out of any app-opening URL:
    /// `https://zivett.com/r/CODE`, `zivett://r/CODE`, or a `?ref=CODE`
    /// query on any path.
    fun code(from: Uri): String? {
        val parts = from.pathSegments.filter { it.isNotEmpty() }
        if (parts.size >= 2 && parts[parts.size - 2] == "r") return parts.last()
        // Custom-scheme form zivett://r/CODE puts "r" in the host slot.
        if (from.host == "r" && parts.isNotEmpty()) return parts.last()
        return from.getQueryParameter("ref")
    }
}
