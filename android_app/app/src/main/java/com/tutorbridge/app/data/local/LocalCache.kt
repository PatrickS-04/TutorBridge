package com.tutorbridge.app.data.local

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.lang.reflect.Type

/** A cached response and when it was fetched from the API. */
data class CachedValue<T>(val value: T, val updatedAtMillis: Long)

// Reference: Google. 2024. Gson (Version 2.11.0). [Computer program].
//   Available at: https://github.com/google/gson [Accessed 21 September 2026].
/**
 * Offline read cache: remembers the last good answer for each API read, so the
 * screens still have something to show with no connection.
 *
 * [userId] is looked up on every call rather than held, because the signed-in
 * user changes under a long-lived repository. With nobody signed in the cache
 * is simply off - it neither returns nor stores anything, which is what keeps a
 * late response arriving after sign-out from being saved against the next user.
 */
class LocalCache(
    private val dao: CachedResourceDao,
    private val userId: () -> String?,
    private val gson: Gson = Gson(),
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** Stores [value] as the latest answer for [resource]. Never throws: a failed save is only a missed optimisation. */
    suspend fun <T> put(resource: String, value: T) {
        val user = userId() ?: return
        try {
            dao.put(CachedResourceEntity(user, resource, gson.toJson(value), clock()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not cache $resource", e)
        }
    }

    /**
     * The last answer saved for [resource], or null if there is none. An entry
     * that no longer parses - saved by an older version of the app whose models
     * differed - is dropped and reported as a miss rather than crashing a screen.
     */
    suspend fun <T> get(resource: String, type: Type): CachedValue<T>? {
        val user = userId() ?: return null
        return try {
            val row = dao.get(user, resource) ?: return null
            try {
                val value: T = gson.fromJson(row.payload, type) ?: throw JsonParseException("empty payload")
                CachedValue(value, row.updatedAtMillis)
            } catch (e: JsonParseException) {
                Log.w(TAG, "Dropping unreadable cache entry $resource", e)
                dao.delete(user, resource)
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read cached $resource", e)
            null
        }
    }

    /** When anything was last saved for the signed-in user, as it changes. */
    fun newestUpdate(): Flow<Long?> = userId()?.let { dao.observeNewest(it) } ?: emptyFlow()

    /** Forget everything, for every user. */
    suspend fun clear() {
        try {
            dao.clearAll()
            Log.i(TAG, "Cache cleared")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear the cache", e)
        }
    }

    private companion object {
        const val TAG = "LocalCache"
    }
}
