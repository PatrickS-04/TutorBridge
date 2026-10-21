package com.tutorbridge.app

import com.tutorbridge.app.Apiinterface.TutorBridgeApi
import com.tutorbridge.app.data.local.CachedResourceDao
import com.tutorbridge.app.data.local.CachedResourceEntity
import com.tutorbridge.app.data.local.LocalCache
import com.tutorbridge.app.data.repository.ApiException
import com.tutorbridge.app.data.repository.TutorBridgeRepository
import com.tutorbridge.app.ui.components.savedAtLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset

// Reference: Android Developers. 2026. Build local unit tests. [Online].
//   Available at: https://developer.android.com/training/testing/local-tests [Accessed 21 September 2026].
/**
 * The offline read cache end to end: the real Retrofit and Gson stack against a
 * network that can be switched up, down or into an error, and a Room DAO
 * replaced by an in-memory one. No Android, no socket.
 */
class OfflineCacheTest {

    private class FakeDao : CachedResourceDao {
        val rows = mutableMapOf<Pair<String, String>, CachedResourceEntity>()
        override suspend fun get(userId: String, resource: String) = rows[userId to resource]
        override suspend fun put(entry: CachedResourceEntity) { rows[entry.userId to entry.resource] = entry }
        override suspend fun delete(userId: String, resource: String) { rows.remove(userId to resource) }
        override suspend fun clearAll() = rows.clear()
        override fun observeNewest(userId: String): Flow<Long?> =
            MutableStateFlow(rows.values.filter { it.userId == userId }.maxOfOrNull { it.updatedAtMillis })
    }

    private enum class Network { UP, DOWN, SERVER_ERROR }

    private val bookingsJson = """
        {"data":[{"_id":"b1","subjectName":"Maths","startTime":"2026-10-21T08:00:00.000Z",
        "endTime":"2026-10-21T09:00:00.000Z","status":"CONFIRMED"}],"page":1,"pageSize":20,"totalPages":1}
    """.trimIndent()

    private val messagesJson = """
        {"data":[{"_id":"m1","senderId":"u1","body":"hi","sentAt":"2026-10-20T10:00:00.000Z"}],
        "page":1,"pageSize":20,"totalPages":1}
    """.trimIndent()

    private var network = Network.UP
    private var signedInAs: String? = "u1"
    private val dao = FakeDao()
    private val cache = LocalCache(dao, userId = { signedInAs }, clock = { 5_000L })

    private fun reply(code: Int, body: String, chain: Interceptor.Chain): Response =
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    private val repository: TutorBridgeRepository by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                when (network) {
                    Network.DOWN -> throw IOException("no route to host")
                    Network.SERVER_ERROR -> reply(500, """{"error":{"code":"BOOM","message":"the server broke"}}""", chain)
                    Network.UP -> reply(
                        200,
                        if (chain.request().url.encodedPath.contains("messages")) messagesJson else bookingsJson,
                        chain
                    )
                }
            })
            .build()
        val api = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TutorBridgeApi::class.java)
        TutorBridgeRepository(api, cache)
    }

    @Test
    fun `a good answer is returned and saved`() = runBlocking {
        val result = repository.bookings()
        assertEquals("Maths", result.getOrThrow().single().subjectName)
        assertEquals(1, dao.rows.size)
        assertEquals(5_000L, dao.rows.values.single().updatedAtMillis)
    }

    @Test
    fun `with the connection down the last saved answer is served`() = runBlocking {
        val online = repository.bookings().getOrThrow()
        network = Network.DOWN
        val offline = repository.bookings()
        assertTrue("falls back to the cache rather than failing", offline.isSuccess)
        assertEquals(online, offline.getOrThrow())
    }

    @Test
    fun `with the connection down and nothing saved the failure is passed on`() = runBlocking {
        network = Network.DOWN
        val result = repository.bookings()
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `a server error is not papered over with old data`() = runBlocking {
        repository.bookings().getOrThrow()
        network = Network.SERVER_ERROR
        val error = repository.bookings().exceptionOrNull()
        assertTrue(error is ApiException)
        assertEquals(500, (error as ApiException).code)
        assertEquals("the server broke", error.message)
    }

    @Test
    fun `nothing is saved or served while nobody is signed in`() = runBlocking {
        signedInAs = null
        repository.bookings().getOrThrow()
        assertTrue(dao.rows.isEmpty())

        network = Network.DOWN
        assertTrue(repository.bookings().isFailure)
    }

    @Test
    fun `one user never sees another user's saved data`() = runBlocking {
        repository.bookings().getOrThrow()          // saved for u1
        signedInAs = "u2"
        network = Network.DOWN
        assertTrue(repository.bookings().isFailure)

        signedInAs = "u1"
        assertTrue(repository.bookings().isSuccess)  // and u1 still has theirs
    }

    @Test
    fun `clearing wipes every user's data`() = runBlocking {
        repository.bookings().getOrThrow()
        cache.clear()
        network = Network.DOWN
        assertTrue(repository.bookings().isFailure)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `an unreadable saved entry is dropped instead of crashing`() = runBlocking {
        dao.put(CachedResourceEntity("u1", "bookings", "{ this is not json", 1L))
        network = Network.DOWN
        assertTrue(repository.bookings().isFailure)
        assertNull("the bad row is removed so it cannot fail again", dao.rows["u1" to "bookings"])
    }

    @Test
    fun `only the newest page of a conversation is kept`() = runBlocking {
        val firstPage = repository.messages("t1", "u1", page = 1).getOrThrow()
        assertEquals("hi", firstPage.first.single().body)
        assertEquals(1, dao.rows.size)

        network = Network.DOWN
        assertTrue("page 1 is served offline", repository.messages("t1", "u1", page = 1).isSuccess)
        assertTrue("older pages are not", repository.messages("t1", "u1", page = 2).isFailure)
    }

    @Test
    fun `the saved-at label shows the day only when it is not today`() {
        val zone = ZoneOffset.UTC
        val today = LocalDate.of(2026, 10, 20)
        val thisMorning = java.time.LocalDateTime.of(2026, 10, 20, 14, 5).toInstant(zone).toEpochMilli()
        val lastWeek = java.time.LocalDateTime.of(2026, 10, 13, 9, 30).toInstant(zone).toEpochMilli()

        assertEquals("14:05", savedAtLabel(thisMorning, zone, today))
        assertTrue(savedAtLabel(lastWeek, zone, today).endsWith("09:30"))
        assertTrue(savedAtLabel(lastWeek, zone, today).contains("13"))
    }
}
