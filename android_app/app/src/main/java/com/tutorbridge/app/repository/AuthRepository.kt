package com.tutorbridge.app.data.repository

import com.tutorbridge.app.Apiinterface.AuthApi
import com.tutorbridge.app.data.model.*

sealed class AuthResult {
    data class Success(val profile: ProfileResponse) : AuthResult()
    /** [message] is the server's own; null means it could not be reached. */
    data class Failure(val message: String?) : AuthResult()
}

/**
 * The backend half of signing in. Firebase authenticates the user; this maps
 * that Firebase identity onto their TutorBridge profile (role, institution,
 * verification status) and reads the public institution list.
 */
class AuthRepository(private val authApi: AuthApi) {

    /**
     * Call straight after any successful Firebase sign-in. Idempotent: it
     * creates the profile on first sign-in, links an admin-provisioned one by
     * email, and otherwise just returns the existing profile.
     */
    suspend fun bootstrapProfile(institutionId: String?, fullName: String?): AuthResult {
        return try {
            val response = authApi.bootstrap(BootstrapRequest(institutionId, fullName))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                AuthResult.Success(body)
            } else {
                AuthResult.Failure(parseApiError(response))
            }
        } catch (e: Exception) {
            AuthResult.Failure(null)
        }
    }

    /** The server sets it in Firebase and clears mustChangePassword in one step. */
    suspend fun changePassword(newPassword: String): Result<Unit> = try {
        val response = authApi.changePassword(ChangePasswordRequest(newPassword))
        if (response.isSuccessful) Result.success(Unit)
        else Result.failure(IllegalStateException(parseApiError(response)))
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    /** Only there to wake the server; whether it answers changes nothing here. */
    suspend fun wakeServer() {
        runCatching { authApi.health() }
    }

    suspend fun listInstitutions(): List<InstitutionSummary> {
        return try {
            val response = authApi.listInstitutions()
            if (response.isSuccessful) response.body().orEmpty() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
