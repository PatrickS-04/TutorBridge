package com.tutorbridge.app.sync

import android.util.Log
import com.tutorbridge.app.data.local.OutboxEntity
import com.tutorbridge.app.data.local.OutboxStore

/** What happened when one queued item was sent. */
sealed class SendResult {
    /** The server has it. The item can go. */
    object Sent : SendResult()

    /** The server could not be reached, or is having a bad moment (timeout, 429, 5xx). Try again later. */
    data class Retry(val message: String) : SendResult()

    /** The server understood and said no (400, 403, 404...). Trying again would get the same answer. */
    data class Rejected(val message: String) : SendResult()

    /** The sign-in has lapsed. Nothing more can be sent until the user signs in again. */
    object AuthRequired : SendResult()
}

/** Sends one queued item to the API. The real one calls Retrofit; tests use a fake. */
fun interface OutboxSender {
    suspend fun send(item: OutboxEntity): SendResult
}

/** Why a drain stopped before the queue was empty, if it did. */
enum class StopReason { RETRY_LATER, AUTH_REQUIRED, NOT_SIGNED_IN }

data class DrainReport(
    val sent: Int = 0,
    val rejected: Int = 0,
    val stoppedBy: StopReason? = null
)

/**
 * The sync engine: sends what is waiting in the outbox, in the order the user
 * did it, and decides what each answer means.
 *
 *  - Sent: the item is removed.
 *  - Rejected: the item is marked FAILED with the server's reason, and the
 *    drain carries on - one bad item must not hold up the rest.
 *  - Retry: the drain stops. Everything behind it waits, because sending a
 *    later item ahead of an earlier one that did not go would reorder what the
 *    user did (a reply landing before the question it answers).
 *  - AuthRequired: the drain stops until the user signs in again.
 *
 * It knows nothing about Android or WorkManager, which is what lets the rules
 * above be unit-tested directly.
 */
class OutboxProcessor(
    private val store: OutboxStore,
    private val sender: OutboxSender,
    private val userId: () -> String?
) {

    suspend fun drain(): DrainReport {
        val user = userId() ?: return DrainReport(stoppedBy = StopReason.NOT_SIGNED_IN)
        var sent = 0
        var rejected = 0

        // Re-read after each pass: the user may have queued more while this ran.
        while (true) {
            val batch = store.pending(user)
            if (batch.isEmpty()) break

            for (item in batch) {
                when (val result = sender.send(item)) {
                    SendResult.Sent -> {
                        store.delete(item.id)
                        sent++
                        Log.i(TAG, "Sent ${item.type} #${item.id}")
                    }
                    is SendResult.Rejected -> {
                        store.markFailed(item.id, result.message)
                        rejected++
                        Log.w(TAG, "Rejected ${item.type} #${item.id}: ${result.message}")
                    }
                    is SendResult.Retry -> {
                        store.recordAttempt(item.id, result.message)
                        Log.i(TAG, "Will retry ${item.type} #${item.id}: ${result.message}")
                        return DrainReport(sent, rejected, StopReason.RETRY_LATER)
                    }
                    SendResult.AuthRequired -> {
                        Log.w(TAG, "Signed out; ${item.type} #${item.id} waits for the next sign-in")
                        return DrainReport(sent, rejected, StopReason.AUTH_REQUIRED)
                    }
                }
            }
        }
        return DrainReport(sent, rejected)
    }

    private companion object {
        const val TAG = "Sync"
    }
}
