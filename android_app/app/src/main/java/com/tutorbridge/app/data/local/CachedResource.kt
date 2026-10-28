package com.tutorbridge.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Reference: Android Developers. 2026. Save data in a local database using Room. [Online].
//   Available at: https://developer.android.com/training/data-storage/room [Accessed 20 October 2026].

/**
 * One API response, kept as the JSON the app parsed it from, plus when it was
 * fetched. [resource] says which response it is ("bookings", "tutors",
 * "messages:<threadId>", ...) and [userId] whose it is, so one phone shared
 * between accounts never shows one person another's data.
 *
 * The API's own shapes are stored, not the screen models built from them, so
 * mapping stays in one place and things computed from the clock ("2h ago",
 * "active now") are worked out when the data is read, not frozen when it was saved.
 */
@Entity(tableName = "cached_resource", primaryKeys = ["userId", "resource"])
data class CachedResourceEntity(
    val userId: String,
    val resource: String,
    val payload: String,
    val updatedAtMillis: Long
)

@Dao
interface CachedResourceDao {

    @Query("SELECT * FROM cached_resource WHERE userId = :userId AND resource = :resource")
    suspend fun get(userId: String, resource: String): CachedResourceEntity?

    @Upsert
    suspend fun put(entry: CachedResourceEntity)

    @Query("DELETE FROM cached_resource WHERE userId = :userId AND resource = :resource")
    suspend fun delete(userId: String, resource: String)

    /** Sign-out and account deletion: nothing of the last user stays on the phone. */
    @Query("DELETE FROM cached_resource")
    suspend fun clearAll()

    /** When this user's cache was last refreshed from the API - the "data saved at" time. */
    @Query("SELECT MAX(updatedAtMillis) FROM cached_resource WHERE userId = :userId")
    fun observeNewest(userId: String): Flow<Long?>
}
