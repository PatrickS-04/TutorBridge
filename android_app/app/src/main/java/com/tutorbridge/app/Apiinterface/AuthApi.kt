package com.tutorbridge.app.Apiinterface

import com.tutorbridge.app.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Mirrors backend_api/src/routes/auth.ts and the public
 * GET /reference/institutions endpoint. None of these calls need a Bearer
 * token, so this interface is built on RetrofitInstance's plain client.
 */
interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<RegisterResponse>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @POST("auth/sso/google")
    suspend fun ssoGoogle(@Body body: SsoRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<RefreshResponse>

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest): Response<Unit>

    @GET("reference/institutions")
    suspend fun listInstitutions(): Response<List<InstitutionSummary>>
}
