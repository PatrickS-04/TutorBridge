package com.tutorbridge.app.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tutorbridge.app.MainActivity
import com.tutorbridge.app.R
import com.tutorbridge.app.Retrofit.RetrofitInstance
import com.tutorbridge.app.data.repository.TutorBridgeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Tells an open app that a push came in, so what is on screen, like the unread count, can catch up. */
object PushEvents {
    private val _arrived = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val arrived: SharedFlow<Unit> = _arrived.asSharedFlow()

    fun notifyArrived() {
        _arrived.tryEmit(Unit)
    }
}

// Reference: Firebase. 2026. Get started with Firebase Cloud Messaging in Android apps. [Online].
//   Available at: https://firebase.google.com/docs/cloud-messaging/android/get-started [Accessed 21 September 2026].
class TutorBridgeMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        // Signed out: the request would 401. completeAuth() registers the current token on next sign-in.
        if (Firebase.auth.currentUser == null) return
        scope.launch { TutorBridgeRepository(RetrofitInstance.tutorBridgeApi).registerPushToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        PushEvents.notifyArrived()
        val data = message.data
        val title = data["title"] ?: return
        val body = data["body"].orEmpty()
        val notificationId = (data["notificationId"] ?: message.messageId ?: title).hashCode()

        val tap = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data["deepLink"]?.let { putExtra(MainActivity.EXTRA_DEEP_LINK, it) }
        }
        // Unique requestCode per notification, otherwise FLAG_UPDATE_CURRENT makes every
        // older notification open the newest one's screen.
        val pending = PendingIntent.getActivity(
            this, notificationId, tap, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.UPDATES)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setColor(ContextCompat.getColor(this, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}