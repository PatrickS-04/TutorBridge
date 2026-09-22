package com.tutorbridge.app.Apiinterface

import com.tutorbridge.app.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// Reference: Square, Inc. 2024. Retrofit (Version 2.11.0). [Computer program].
//   Available at: https://lysine.dev/retrofit/ [Accessed 21 September 2026].
/**
 * Mirrors backend_api/src/routes/auth.ts and the public
 * GET /reference/institutions endpoint.
 *
 * Sign-in, sign-up, SSO and token refresh all happen in the Firebase SDK, so
 * the only auth call left here is the profile bootstrap.
 */
interface AuthApi {
    /** Create-or-return this Firebase user's TutorBridge profile. Idempotent. */
    @POST("auth/bootstrap")
    suspend fun bootstrap(@Body body: BootstrapRequest): Response<ProfileResponse>

    /** Replaces an admin-issued temporary password. 204, no body. */
    @POST("auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): Response<Unit>

    /** Public - the sign-up screen needs this before anybody is signed in. */
    @GET("reference/institutions")
    suspend fun listInstitutions(): Response<List<InstitutionSummary>>
}
