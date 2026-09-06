package com.zivett.app.core.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.ApiRequest.Method
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable

/// Device push: the notification channel, the FCM registration token,
/// and syncing it to `POST /api/push/tokens` (registering IS the opt-in
/// server-side). Registration only runs while signed in; logout
/// releases the token so a shared device stops buzzing for the old
/// account.
///
/// NEEDS-MANUAL (once): drop the Firebase project's google-services.json
/// into app/ so FCM initializes — until then `enable()` finds no
/// FirebaseApp and quietly does nothing. See README "Push notifications".
object PushRegistration {
    const val CHANNEL_ID = "jobs"
    private const val PREFS = "zivett.push"
    private const val TOKEN_KEY = "rp.pushDeviceToken"

    private var context: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /// The client used for the async token round-trips; set by the app
    /// on launch (and rebuilt with the environment on backend switch).
    var client: ApiClient? = null

    fun attach(context: Context) {
        this.context = context.applicationContext
        createChannel(context.applicationContext)
    }

    /// The FCM token the SDK last handed us — kept so logout can release
    /// it server-side even after the session state is gone.
    private var storedToken: String?
        get() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(TOKEN_KEY, null)
        set(value) {
            val prefs = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
            if (value != null) prefs.edit().putString(TOKEN_KEY, value).apply() else prefs.edit().remove(TOKEN_KEY).apply()
        }

    /// Whether Firebase is configured for this build (google-services.json present).
    fun isAvailable(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    /// Whether the OS-level permission is granted (Android 13+ prompts;
    /// older versions are implicitly granted).
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /// Fetch the current FCM token and sync it up. Called when a
    /// signed-in shell appears (after the permission prompt); the token
    /// re-syncs on every launch — re-registration is idempotent.
    fun enable() {
        val context = context ?: return
        if (!isAvailable(context)) return
        scope.launch {
            val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull() ?: return@launch
            deviceTokenReceived(token)
        }
    }

    /// FCM handed us a (new) token — sync it up.
    fun deviceTokenReceived(token: String) {
        storedToken = token
        scope.launch { runCatching { client?.send(PushEndpoints.register(token)) } }
    }

    /// Best-effort release on logout — awaited BEFORE the auth token is
    /// revoked (the DELETE needs it). The server also prunes tokens FCM
    /// reports dead, so a missed call self-heals.
    suspend fun release() {
        val token = storedToken ?: return
        storedToken = null
        runCatching { client?.send(PushEndpoints.release(token)) }
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Job updates", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Quotes, arrivals, invoices, and messages about your jobs"
        }
        manager.createNotificationChannel(channel)
    }
}

object PushEndpoints {
    fun register(token: String) = ApiRequest.post<PushRegistered, Map<String, String>>("api/push/tokens", mapOf("token" to token, "platform" to "android"))

    fun release(token: String) = ApiRequest.empty(Method.DELETE, "api/push/tokens", mapOf("token" to token))
}

@Serializable
data class PushRegistered(val registered: Boolean = false)
