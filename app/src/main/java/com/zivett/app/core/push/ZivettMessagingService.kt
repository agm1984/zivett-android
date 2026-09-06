package com.zivett.app.core.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.zivett.app.MainActivity
import com.zivett.app.R

/// The FCM entry points: token rotation (re-sync) and message delivery.
/// Messages are sent as DATA payloads (title/body/route_name/route_params)
/// so the app builds the banner itself — that way foreground and
/// background taps route identically through `PushRouting`.
class ZivettMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushRegistration.attach(applicationContext)
        PushRegistration.deviceTokenReceived(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "ZiVETT"
        val body = data["body"] ?: message.notification?.body ?: ""

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            PushRouting.jobRef(data)?.let { ref ->
                putExtra(PushRouting.EXTRA_ROUTE_NAME, data["route_name"])
                putExtra(PushRouting.EXTRA_ROUTE_ID, ref)
            }
        }
        val pending = PendingIntent.getActivity(this, message.messageId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, PushRegistration.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()

        if (PushRegistration.hasPermission(this)) {
            try {
                NotificationManagerCompat.from(this).notify(message.messageId.hashCode(), notification)
            } catch (_: SecurityException) {
                // Permission revoked between the check and the post — quiet by design.
            }
        }
    }
}
