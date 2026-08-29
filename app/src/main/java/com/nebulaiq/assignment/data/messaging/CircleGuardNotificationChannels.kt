package com.nebulaiq.assignment.data.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object CircleGuardNotificationChannels {
    const val ALERTS_CHANNEL_ID = "circleguard-alerts"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            ALERTS_CHANNEL_ID,
            "CircleGuard alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts when a group member leaves the shared boundary"
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
