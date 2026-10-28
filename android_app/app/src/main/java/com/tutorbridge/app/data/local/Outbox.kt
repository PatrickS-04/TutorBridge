package com.tutorbridge.app.data.local

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** What the sync engine needs from the outbox. Small, so a fake stands in for it in tests. */
interface OutboxStore {
    suspend fun pending(userId: String): List<OutboxEntity>
    suspend fun delete(id: Long)
    suspend fun recordAttempt(id: Long, error: String)
    suspend fun markFailed(id: Long, error: String)
}

// Reference: Google. 2024. Gson (Version 2.11.0). [Computer program].
//   Available at: https://github.com/google/gson [Accessed 21 September 2026].
/**
 * The queue of things done offline, waiting to reach the server. Everything
 * the user did while offline lands here instead of being lost, and the sync
 * engine (OutboxProcessor, run by WorkManager) empties it when the connection
 * is back.
 *
 * Rows belong to a user. [userId] is read on every call, as in LocalCache, so
 * one phone shared between accounts never sends one person's work as another's.
 * Signing out does not discard them: the person who queued them gets them sent
 * the next time they sign in.
 */
class Outbox(
    private val dao: OutboxDao,
    private val userId: () -> String?,
    private val gson: Gson = Gson(),
    private val clock: () -> Long = System::currentTimeMillis
) : OutboxStore {

    /**
     * Queues [payload] to be sent as [type]. With a [dedupeKey], an unsent item
     * with the same key is replaced rather than joined - saving the same notes
     * five times offline sends them once. Returns the new row's id, or null if
     * nobody is signed in.
     */
    suspend fun <P : Any> enqueue(type: OutboxType, payload: P, dedupeKey: String? = null): Long? {
        val user = userId() ?: return null
        if (dedupeKey != null) dao.deletePendingByKey(user, dedupeKey)
        val id = dao.insert(
            OutboxEntity(
                userId = user,
                type = type.name,
                payload = gson.toJson(payload),
                dedupeKey = dedupeKey,
                createdAtMillis = clock()
            )
        )
        Log.i(TAG, "Queued $type #$id")
        return id
    }

    /** Everything queued for the signed-in user, as it changes. */
    fun observe(): Flow<List<OutboxEntity>> = userId()?.let { dao.observeAll(it) } ?: emptyFlow()

    /** Another go at everything the server refused. */
    suspend fun retryFailed() {
        val user = userId() ?: return
        dao.retryFailed(user)
        Log.i(TAG, "Failed items put back in the queue")
    }

    /** The user gives up on one item. */
    suspend fun discard(id: Long) {
        dao.delete(id)
        Log.i(TAG, "Discarded #$id")
    }

    suspend fun clearUser() {
        val user = userId() ?: return
        dao.clearUser(user)
    }

    override suspend fun pending(userId: String) = dao.pending(userId)
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun recordAttempt(id: Long, error: String) = dao.recordAttempt(id, error)
    override suspend fun markFailed(id: Long, error: String) = dao.markFailed(id, error)

    private companion object {
        const val TAG = "Outbox"
    }
}
