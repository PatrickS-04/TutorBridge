package com.tutorbridge.app.Retrofit

import com.tutorbridge.app.Apiinterface.AuthApi
import com.tutorbridge.app.data.model.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * On a 401 from any authenticated endpoint, exchanges the stored refresh
 * token for a new access/refresh pair (POST /auth/refresh) and retries the
 * request once. This is what lets the client transparently ride out a
 * 15-minute access token expiry without the user noticing.
 *
 * If the refresh token itself is invalid/expired/revoked, the local session
 * is cleared so the UI (checking TokenManager.hasSession()) can send the
 * user back to sign-in. A network failure during the refresh attempt does
 * NOT clear the session - that would force a sign-out just because the
 * device is briefly offline.
 */
class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val authApi: AuthApi
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null // avoid infinite retry loops

        val refreshToken = tokenManager.getRefreshToken() ?: return null

        return runBlocking {
            try {
                val refreshResponse = authApi.refresh(RefreshRequest(refreshToken))
                val body = refreshResponse.body()
                if (refreshResponse.isSuccessful && body != null) {
                    tokenManager.saveTokens(body.accessToken, body.refreshToken, body.expiresIn)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${body.accessToken}")
                        .build()
                } else {
                    tokenManager.clear()
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
