package com.tutorbridge.app

import android.app.Application
import com.tutorbridge.app.push.NotificationChannels

class TutorBridgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
    }
}