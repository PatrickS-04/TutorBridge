package com.tutorbridge.app.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Reference: Android Developers. 2026. Save data in a local database using Room. [Online].
//   Available at: https://developer.android.com/training/data-storage/room [Accessed 20 October 2026].

/**
 * The phone's own copy of what offline mode needs: cached API responses now,
 * and the queue of things the user did offline (the outbox) once that lands.
 *
 * Tables are added here as the offline work grows. The cache can always be
 * rebuilt from the API, but the outbox holds the user's own unsent work, so
 * schema changes must be real migrations - never `fallbackToDestructiveMigration`.
 */
@Database(
    entities = [CachedResourceEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cachedResources(): CachedResourceDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val FILE_NAME = "tutorbridge.db"

        fun build(context: Context): AppDatabase {
            Log.i(TAG, "Opening local database $FILE_NAME")
            return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME).build()
        }
    }
}
