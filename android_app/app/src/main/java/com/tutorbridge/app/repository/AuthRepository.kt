package com.tutorbridge.app.data.repository

import com.google.gson.Gson
import com.tutorbridge.app.Apiinterface.AuthApi
import com.tutorbridge.app.data.model.*
import retrofit2.Response

sealed class AuthResult {
    data class Success(val profile: ProfileResponse) : AuthResult()
    data class Failure(val message: String) : AuthResult()
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
                AuthResult.Failure(parseError(response))
            }
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
