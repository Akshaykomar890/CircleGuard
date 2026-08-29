package com.nebulaiq.assignment.data.messaging

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nebulaiq.assignment.MainActivity
import com.nebulaiq.assignment.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CircleGuardMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        serviceScope.launch {
            FirebasePushTokenRepository().registerToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        CircleGuardNotificationChannels.ensureCreated(this)
        val openAppIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_REQUEST_CODE,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CircleGuardNotificationChannels.ALERTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(message.notification?.title ?: "CircleGuard alert")
            .setContentText(message.notification?.body ?: "A member left the shared area")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .build()
        NotificationManagerCompat.from(this).notify(
            message.messageId?.hashCode() ?: NOTIFICATION_REQUEST_CODE,
            notification,
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val NOTIFICATION_REQUEST_CODE = 7100
    }
}
