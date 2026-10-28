package com.tutorbridge.app

import com.google.gson.Gson
import com.tutorbridge.app.Apiinterface.TutorBridgeApi
import com.tutorbridge.app.data.local.MessagePayload
import com.tutorbridge.app.data.local.NotesPayload
import com.tutorbridge.app.data.local.OutboxEntity
import com.tutorbridge.app.data.local.OutboxType
import com.tutorbridge.app.data.local.RequestPayload
import com.tutorbridge.app.data.local.SettingsPayload
import com.tutorbridge.app.data.model.CreateTuitionRequest
import com.tutorbridge.app.data.model.TutorNoteItemDto
import com.tutorbridge.app.data.model.TutorNotesDto
import com.tutorbridge.app.data.model.UpdateSettingsRequest
import com.tutorbridge.app.data.repository.TutorBridgeRepository
import com.tutorbridge.app.sync.RepositoryOutboxSender
import com.tutorbridge.app.sync.SendResult
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

// Reference: Android Developers. 2026. Build local unit tests. [Online].
//   Available at: https://developer.android.com/training/testing/local-tests [Accessed 21 September 2026].
/**
 * That a queued item reaches the right endpoint with the right body, and that
 * every answer the server can give is read the right way: keep trying, give up
 * on this item, or wait for a sign-in. Runs the real Retrofit stack against a
 * scripted network.
 */
class OutboxSenderTest {

    private data class Seen(val method: String, val path: String, val body: String)

    private var code = 200
    private var body = "{}"
    private var down = false
    private val seen = mutableListOf<Seen>()
    private val gson = Gson()

    private val sender: RepositoryOutboxSender by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                seen += Seen(request.method, request.url.encodedPath, request.body?.let { Buffer().also { b -> it.writeTo(b) }.readUtf8() }.orEmpty())
                if (down) throw IOException("no route to host")
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("test")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        val api = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TutorBridgeApi::class.java)
        RepositoryOutboxSender(TutorBridgeRepository(api))
    }

    private fun item(type: OutboxType, payload: Any) =
        OutboxEntity(id = 1, userId = "u1", type = type.name, payload = gson.toJson(payload), createdAtMillis = 0)

    private val message = item(OutboxType.SEND_MESSAGE, MessagePayload("t1", "hello", "c-1"))

    @Test
    fun `a message goes to its thread with its client id`() = runBlocking {
        body = """{"_id":"m1","senderId":"u1","body":"hello","sentAt":"2026-10-20T10:00:00.000Z","clientMessageId":"c-1"}"""
        val result = sender.send(message)

        assertEquals(SendResult.Sent, result)
        val request = seen.single()
        assertEquals("POST", request.method)
        assertEquals("/threads/t1/messages", request.path)
        assertTrue(request.body.contains("\"clientMessageId\":\"c-1\""))
        assertTrue(request.body.contains("\"body\":\"hello\""))
    }

    @Test
    fun `a request goes out with the client request id it was queued with`() = runBlocking {
        val payload = RequestPayload(
            CreateTuitionRequest(subjectId = "s1", areaId = "a1", description = "Limits", clientRequestId = "r-1"),
            subjectName = "Maths",
            areaName = "Upper Campus"
        )
        body = """{"id":"x"}"""
        assertEquals(SendResult.Sent, sender.send(item(OutboxType.CREATE_REQUEST, payload)))

        val request = seen.single()
        assertEquals("POST", request.method)
        assertEquals("/tuition-requests", request.path)
        assertTrue(request.body.contains("\"clientRequestId\":\"r-1\""))
        assertTrue("display-only names are not sent", !request.body.contains("Upper Campus"))
    }

    @Test
    fun `notes are saved to their session`() = runBlocking {
        val notes = TutorNotesDto(listOf(TutorNoteItemDto("Mark homework", true)), "Parent wants an update")
        body = """{"items":[],"text":""}"""
        assertEquals(SendResult.Sent, sender.send(item(OutboxType.SAVE_NOTES, NotesPayload("b1", notes))))

        val request = seen.single()
        assertEquals("PUT", request.method)
        assertEquals("/bookings/b1/notes", request.path)
        assertTrue(request.body.contains("Mark homework"))
    }

    @Test
    fun `settings are saved as they were changed`() = runBlocking {
        body = """{"theme":"DARK"}"""
        assertEquals(SendResult.Sent, sender.send(item(OutboxType.SAVE_SETTINGS, SettingsPayload(UpdateSettingsRequest(theme = "DARK")))))

        val request = seen.single()
        assertEquals("PUT", request.method)
        assertEquals("/users/me/settings", request.path)
        assertEquals("""{"theme":"DARK"}""", request.body)
    }

    @Test
    fun `no connection means try again later`() = runBlocking {
        down = true
        val result = sender.send(message)
        assertTrue(result is SendResult.Retry)
    }

    @Test
    fun `a struggling server means try again later, not give up`() = runBlocking {
        for (status in listOf(408, 429, 500, 502, 503)) {
            code = status
            body = """{"error":{"code":"X","message":"busy"}}"""
            assertTrue("$status should retry", sender.send(message) is SendResult.Retry)
        }
    }

    @Test
    fun `an expired sign-in waits for the user`() = runBlocking {
        code = 401
        body = """{"error":{"code":"INVALID_TOKEN","message":"expired"}}"""
        assertEquals(SendResult.AuthRequired, sender.send(message))
    }

    @Test
    fun `a refusal is final and carries the server's reason`() = runBlocking {
        for (status in listOf(400, 403, 404, 409)) {
            code = status
            body = """{"error":{"code":"NOPE","message":"Thread not found"}}"""
            val result = sender.send(message)
            assertTrue("$status should be rejected", result is SendResult.Rejected)
            assertEquals("Thread not found", (result as SendResult.Rejected).message)
        }
    }

    @Test
    fun `an item that cannot be read is rejected instead of retried forever`() = runBlocking {
        val broken = message.copy(payload = "{ not json")
        assertTrue(sender.send(broken) is SendResult.Rejected)
        assertTrue("and nothing was sent", seen.isEmpty())

        val unknown = message.copy(type = "FROM_THE_FUTURE")
        assertTrue(sender.send(unknown) is SendResult.Rejected)
    }
}
