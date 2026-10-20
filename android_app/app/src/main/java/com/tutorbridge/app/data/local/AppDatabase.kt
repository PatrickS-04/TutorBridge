package com.tutorbridge.app.data.local

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Reference: Android Developers. 2026. Save data in a local database using Room. [Online].
//   Available at: https://developer.android.com/training/data-storage/room [Accessed 20 October 2026].

/**
 * When a cached list was last refreshed from the API, keyed by what the list
 * is ("bookings", "requests", ...). Screens show it as "Last updated", which is
 * the honest label for data that may be older than the server's.
 */
@Entity(tableName = "cache_meta")
data class CacheMetaEntity(
    @PrimaryKey val key: String,
    val updatedAtMillis: Long
)

@Dao
interface CacheMetaDao {
    @Query("SELECT * FROM cache_meta WHERE `key` = :key")
    fun observe(key: String): Flow<CacheMetaEntity?>

    @Upsert
    suspend fun put(entry: CacheMetaEntity)

    @Query("DELETE FROM cache_meta")
    suspend fun clear()
}

/**
 * The phone's own copy of what offline mode needs: cached API data now, and the
 * queue of things the user did offline (the outbox) once that lands.
 *
 * Tables are added here as the offline work grows. The cache can always be
 * rebuilt from the API, but the outbox holds the user's own unsent work, so
 * schema changes must be real migrations - never `fallbackToDestructiveMigration`.
 */
@Database(
    entities = [CacheMetaEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cacheMeta(): CacheMetaDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val FILE_NAME = "tutorbridge.db"

        fun build(context: Context): AppDatabase {
            Log.i(TAG, "Opening local database $FILE_NAME")
            return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME).build()
        }
    }
}
