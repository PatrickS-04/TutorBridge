package com.tutorbridge.app

import android.app.Application
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.tutorbridge.app.Retrofit.RetrofitInstance
import com.tutorbridge.app.data.local.AppDatabase
import com.tutorbridge.app.data.local.Outbox
import com.tutorbridge.app.data.repository.TutorBridgeRepository
import com.tutorbridge.app.push.NotificationChannels
import com.tutorbridge.app.sync.OutboxProcessor
import com.tutorbridge.app.sync.RepositoryOutboxSender
import com.tutorbridge.app.sync.SyncScheduler

/**
 * Holds what has to outlive any one screen or ViewModel, because the background
 * sync worker runs with no ViewModel at all and needs the same database and queue.
 */
class TutorBridgeApp : Application() {

    /** The phone's local database: the offline cache and the outbox. Opened on first use. */
    val database: AppDatabase by lazy { AppDatabase.build(this) }

    /** What the user did offline, waiting to be sent. Rows are per signed-in user. */
    val outbox: Outbox by lazy { Outbox(database.outbox(), userId = { Firebase.auth.currentUser?.uid }) }

    /** The sync engine. No cache is passed: replaying writes never reads. */
    val outboxProcessor: OutboxProcessor by lazy {
        OutboxProcessor(
            store = outbox,
            sender = RepositoryOutboxSender(TutorBridgeRepository(RetrofitInstance.tutorBridgeApi)),
            userId = { Firebase.auth.currentUser?.uid }
        )
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Application created")
        NotificationChannels.ensure(this)
        SyncScheduler.schedulePeriodic(this)
    }

    private companion object {
        const val TAG = "TutorBridgeApp"
    }
}
