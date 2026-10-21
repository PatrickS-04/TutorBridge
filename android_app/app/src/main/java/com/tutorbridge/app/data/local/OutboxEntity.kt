package com.tutorbridge.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Reference: Android Developers. 2026. Save data in a local database using Room. [Online].
//   Available at: https://developer.android.com/training/data-storage/room [Accessed 20 October 2026].

/** What a queued item does when it reaches the server. */
enum class OutboxType { SEND_MESSAGE, CREATE_REQUEST, SAVE_NOTES, SAVE_SETTINGS }

/** An item is waiting its turn, or the server refused it and it needs the user's attention. */
object OutboxState {
    const val PENDING = "PENDING"
    const val FAILED = "FAILED"
}

/**
 * One thing the user did that has not reached the server yet. Unlike the
 * cache, this is the user's own work and exists nowhere else, so rows are only
 * ever removed when the server has accepted them or the user discards them.
 *
 * [payload] is the JSON of what to send (see OutboxPayloads). [dedupeKey], when
 * set, means "a newer one of these replaces an older unsent one" - used for
 * things that are a whole document, where only the latest matters.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val type: String,
    val payload: String,
    val dedupeKey: String? = null,
    val createdAtMillis: Long,
    val attempts: Int = 0,
    val state: String = OutboxState.PENDING,
    val lastError: String? = null
)

@Dao
interface OutboxDao {

    @Insert
    suspend fun insert(item: OutboxEntity): Long

    /** What still has to be sent, oldest first - the order the user did it in. */
    @Query("SELECT * FROM outbox WHERE userId = :userId AND state = 'PENDING' ORDER BY id ASC")
    suspend fun pending(userId: String): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE userId = :userId ORDER BY id ASC")
    fun observeAll(userId: String): Flow<List<OutboxEntity>>

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE outbox SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun recordAttempt(id: Long, error: String)

    @Query("UPDATE outbox SET state = 'FAILED', attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    /** Put everything the server refused back in the queue, for another go. */
    @Query("UPDATE outbox SET state = 'PENDING', lastError = NULL WHERE userId = :userId AND state = 'FAILED'")
    suspend fun retryFailed(userId: String)

    /** A newer save of the same document replaces an older one that has not been sent. */
    @Query("DELETE FROM outbox WHERE userId = :userId AND dedupeKey = :key AND state = 'PENDING'")
    suspend fun deletePendingByKey(userId: String, key: String)

    /** Account deletion: nothing of this user stays on the phone. */
    @Query("DELETE FROM outbox WHERE userId = :userId")
    suspend fun clearUser(userId: String)
}
