package com.tutorbridge.app.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tutorbridge.app.TutorBridgeApp
import java.util.concurrent.TimeUnit

// Reference: Android Developers. 2026. Getting started with WorkManager. [Online].
//   Available at: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started [Accessed 21 October 2026].
/**
 * Empties the outbox in the background. WorkManager runs it when the phone has
 * a connection, keeps it across app restarts, and re-runs it with a growing
 * delay when the server could not be reached - which is what makes "do it now,
 * send it when I'm back online" work without the app being open.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val processor = (applicationContext as TutorBridgeApp).outboxProcessor
        val report = processor.drain()
        Log.i(TAG, "Drain finished: sent=${report.sent} rejected=${report.rejected} stoppedBy=${report.stoppedBy}")

        return when (report.stoppedBy) {
            // The server could not be reached or is struggling: try again, later and later.
            StopReason.RETRY_LATER -> Result.retry()
            // Nothing to do until the user signs in; sign-in asks for a sync itself.
            StopReason.AUTH_REQUIRED, StopReason.NOT_SIGNED_IN, null -> Result.success()
        }
    }

    private companion object {
        const val TAG = "Sync"
    }
}

/** Asks for the outbox to be sent. Safe to call as often as is convenient. */
object SyncScheduler {

    private const val TAG = "Sync"
    private const val UNIQUE_NAME = "outbox-sync"
    private const val BACKOFF_SECONDS = 30L

    fun request(context: Context) {
        val work = OneTimeWorkRequestBuilder<SyncWorker>()
            // WorkManager holds it until the phone really has a network.
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        // REPLACE, not KEEP: if an earlier run is sitting out a retry delay, the
        // connection coming back is a reason to go now, not to wait out the delay.
        // Replaying a half-finished item is harmless - see RepositoryOutboxSender.
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, work)
        Log.i(TAG, "Sync requested")
    }
}
