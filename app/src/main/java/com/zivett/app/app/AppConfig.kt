package com.zivett.app.app

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.zivett.app.BuildConfig

/// Build-time configuration. `API_BASE_URL` is a BuildConfig field per
/// build type, so a Debug build points at the local Sail container and a
/// Release build at production without any code change.
data class AppConfig(
    val apiBaseUrl: String,
    /// The name a token is issued under (`device_name` on /api/auth/*).
    /// Human-readable so the account page can list "Pixel 9 (A1B2)".
    val deviceName: String,
) {
    data class Backend(val name: String, val url: String)

    companion object {
        private const val PREFS = "zivett.config"
        const val overrideKey = "apiBaseURLOverride"

        /// The switchable backends a Debug build can point at.
        val debugBackends: List<Backend> = listOf(
            // The emulator reaches the Mac's localhost through 10.0.2.2.
            Backend("Local (Sail)", "http://10.0.2.2:8090"),
            // A physical phone can't reach the Mac's localhost — Bonjour
            // name instead (same Wi-Fi; see local.properties `lan.host`).
            Backend("Local (Sail via LAN)", "http://${BuildConfig.LAN_HOST}:8090"),
            Backend("Production", "https://zivett.com"),
        )

        fun current(context: Context): AppConfig {
            var url = BuildConfig.API_BASE_URL
            if (BuildConfig.BACKEND_SWITCHER) {
                // Debug-only backend switcher (the Welcome screen's "Server"
                // menu): lets a dev build talk to production without
                // touching build settings. Release builds never read this.
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(overrideKey, null)?.let { url = it }
            }
            return AppConfig(url, defaultDeviceName(context))
        }

        fun setOverride(context: Context, url: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(overrideKey, url).apply()
        }

        /// `Build.MODEL` plus a per-install suffix: issuing a token revokes
        /// the previous one with the same name, so two phones of the same
        /// model must not share a device name.
        @Suppress("HardwareIds")
        private fun defaultDeviceName(context: Context): String {
            val installId = runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) }.getOrNull()
            val suffix = installId?.take(4)?.uppercase() ?: "APP"
            return "${Build.MODEL} ($suffix)"
        }
    }
}
