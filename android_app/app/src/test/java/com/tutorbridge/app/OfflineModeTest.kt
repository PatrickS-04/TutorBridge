package com.tutorbridge.app

import com.tutorbridge.app.network.OfflineGateInterceptor
import com.tutorbridge.app.network.OfflineMode
import com.tutorbridge.app.network.effectivelyOnline
import com.tutorbridge.app.viewmodel.TutorBridgeUiState
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

// Reference: Android Developers. 2026. Build local unit tests. [Online].
//   Available at: https://developer.android.com/training/testing/local-tests [Accessed 21 September 2026].
/** What "offline" means, and that forcing it really stops requests rather than only changing a banner. */
class OfflineModeTest {

    @Test
    fun `the app is online only with a network and offline mode not forced`() {
        assertTrue(effectivelyOnline(networkAvailable = true, forcedOffline = false))
        assertFalse(effectivelyOnline(networkAvailable = false, forcedOffline = false))
        assertFalse(effectivelyOnline(networkAvailable = true, forcedOffline = true))
        assertFalse(effectivelyOnline(networkAvailable = false, forcedOffline = true))
    }

    @Test
    fun `the screens read the same answer from the state`() {
        assertTrue(TutorBridgeUiState().isOnline)
        assertFalse(TutorBridgeUiState(networkAvailable = false).isOnline)
        assertFalse(TutorBridgeUiState(isOffline = true).isOnline)
    }

    /** A client whose "network" is a canned 200, so no socket is ever opened. */
    private fun client(forced: () -> Boolean, reached: () -> Unit): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(OfflineGateInterceptor(forced))
            .addInterceptor(Interceptor { chain ->
                reached()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody(null))
                    .build()
            })
            .build()

    private val request = Request.Builder().url("http://localhost/users/me").build()

    @Test
    fun `a request goes through while offline mode is off`() {
        var reached = false
        val response = client(forced = { false }, reached = { reached = true }).newCall(request).execute()
        assertEquals(200, response.code)
        assertTrue(reached)
    }

    @Test
    fun `a request is refused like a dead network while offline mode is forced`() {
        var reached = false
        try {
            client(forced = { true }, reached = { reached = true }).newCall(request).execute()
            fail("the request should not have been sent")
        } catch (expected: IOException) {
            assertEquals(OfflineMode.BLOCKED_MESSAGE, expected.message)
        }
        assertFalse("nothing should run after the gate", reached)
    }

    @Test
    fun `switching offline mode back off lets requests through again`() {
        var forced = true
        val client = client(forced = { forced }, reached = {})
        try {
            client.newCall(request).execute()
            fail("forced offline should have refused this")
        } catch (expected: IOException) {
            // refused, as above
        }
        forced = false
        assertEquals(200, client.newCall(request).execute().code)
    }
}
