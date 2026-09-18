package com.tutorbridge.app.data.repository

import com.google.gson.Gson
import com.tutorbridge.app.Apiinterface.AuthApi
import com.tutorbridge.app.Retrofit.TokenManager
import com.tutorbridge.app.data.model.*
import retrofit2.Response

sealed class AuthResult {
    data class Success(val user: UserSummary) : AuthResult()
    data class Failure(val message: String) : AuthResult()
}

class AuthRepository(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager
) {

    /** Registers a new password account, then immediately signs it in to obtain a session. */
    suspend fun register(
        email: String,
        password: String,
        fullName: String,
        institutionId: String
    ): AuthResult {
        return try {
            val response = authApi.register(RegisterRequest(email, password, fullName, institutionId))
            if (response.isSuccessful) {
                login(email, password) // /auth/register doesn't return tokens itself
            } else {
                AuthResult.Failure(parseError(response))
            }
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Could not reach the server")
        }
    }

    suspend fun login(email: String, password: String): AuthResult {
        return try {
            handleAuthResponse(authApi.login(LoginRequest(email, password)))
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Could not reach the server")
        }
    }

    /**
     * @param institutionId only required the first time this Google account
     * signs in (the backend auto-creates the account then). Ignored by the
     * server for returning users.
     */
    suspend fun loginWithGoogle(idToken: String, institutionId: String?, fullName: String?): AuthResult {
        return try {
            handleAuthResponse(authApi.ssoGoogle(SsoRequest(idToken, institutionId, fullName)))
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Could not reach the server")
        }
    }

    suspend fun listInstitutions(): List<InstitutionSummary> {
        return try {
            val response = authApi.listInstitutions()
            if (response.isSuccessful) response.body().orEmpty() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun logout() {
        val refreshToken = tokenManager.getRefreshToken()
        tokenManager.clear()
        if (refreshToken != null) {
            try {
                authApi.logout(LogoutRequest(refreshToken))
            } catch (_: Exception) {
                // Local session is already cleared; server-side revoke is best-effort.
            }
        }
    }

    fun hasSession(): Boolean = tokenManager.hasSession()

    private fun handleAuthResponse(response: Response<AuthResponse>): AuthResult {
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            tokenManager.saveTokens(body.accessToken, body.refreshToken, body.expiresIn)
            AuthResult.Success(body.user)
        } else {
            AuthResult.Failure(parseError(response))
        }
    }

    private fun <T> parseError(response: Response<T>): String {
        val raw = response.errorBody()?.string()
        return try {
            Gson().fromJson(raw, ApiErrorBody::class.java)?.error?.message
                ?: "Something went wrong (${response.code()})"
        } catch (e: Exception) {
            "Something went wrong (${response.code()})"
        }
    }
}
