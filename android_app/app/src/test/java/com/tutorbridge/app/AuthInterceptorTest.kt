package com.tutorbridge.app

import com.tutorbridge.app.Retrofit.AuthInterceptor
import com.tutorbridge.app.Retrofit.TokenResult
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

// Reference: Android Developers. 2026. Build local unit tests. [Online].
//   Available at: https://developer.android.com/training/testing/local-tests [Accessed 21 September 2026].
/**
 * What the sign-in token does to a request - in particular that a token missing
 * because the network is down looks like a dead connection, not a signed-out user.
 */
class AuthInterceptorTest {

    private var reachedServer = false
    private var authHeader: String? = null

    private var serverCode = 200
    private var rejections = 0

    private fun client(token: TokenResult): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(onRejected = { rejections++ }) { token })
        .addInterceptor(Interceptor { chain ->
            reachedServer = true
            authHeader = chain.request().header("Authorization")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(serverCode)
                .message("OK")
                .body("".toResponseBody(null))
                .build()
        })
        .build()

    private val request = Request.Builder().url("http://localhost/users/me").build()

    @Test
    fun `a signed-in request carries the bearer token`() {
        client(TokenResult.Token("abc123")).newCall(request).execute()
        assertEquals("Bearer abc123", authHeader)
    }

    @Test
    fun `a 401 for a signed-in user reports the session as over`() {
        serverCode = 401
        client(TokenResult.Token("abc123")).newCall(request).execute()
        assertEquals(1, rejections)
    }

    @Test
    fun `a 401 for nobody signed in, or any other answer, reports nothing`() {
        serverCode = 401
        client(TokenResult.NoUser).newCall(request).execute()
        serverCode = 403
        client(TokenResult.Token("abc123")).newCall(request).execute()
        assertEquals(0, rejections)
    }

    @Test
    fun `a signed-out request goes through without one`() {
        client(TokenResult.NoUser).newCall(request).execute()
        assertTrue(reachedServer)
        assertNull(authHeader)
    }

    @Test
    fun `a token lost to the network fails like a dead connection and is not sent without one`() {
        try {
            client(TokenResult.NetworkFailure).newCall(request).execute()
            fail("the request should not have gone out")
        } catch (expected: IOException) {
            // the cache and the outbox both read an IOException as "no connection"
        }
        assertTrue("nothing reached the server", !reachedServer)
    }

    @Test
    fun `a token that cannot be had for another reason is left for the server to judge`() {
        client(TokenResult.Failed).newCall(request).execute()
        assertTrue("sent, so the server's 401 tells the user to sign in again", reachedServer)
        assertNull(authHeader)
    }
}
