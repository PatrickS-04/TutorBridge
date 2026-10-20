package com.tutorbridge.app

import android.app.Application
import android.util.Log
import com.tutorbridge.app.data.local.AppDatabase
import com.tutorbridge.app.push.NotificationChannels

class TutorBridgeApp : Application() {

    /** The phone's local database: the offline cache now, the outbox later. Opened on first use. */
    val database: AppDatabase by lazy { AppDatabase.build(this) }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Application created")
        NotificationChannels.ensure(this)
    }

    private companion object {
        const val TAG = "TutorBridgeApp"
    }
}
