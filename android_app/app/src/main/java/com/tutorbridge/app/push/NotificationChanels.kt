package com.tutorbridge.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.tutorbridge.app.R

object NotificationChannels {
    const val UPDATES = "tutorbridge_updates"

    // Idempotent. minSdk is 26, so channels always exist and no version check is needed.
    fun ensure(context: Context) {
        val channel = NotificationChannel(
            UPDATES, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH
        ).apply { description = context.getString(R.string.notification_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}