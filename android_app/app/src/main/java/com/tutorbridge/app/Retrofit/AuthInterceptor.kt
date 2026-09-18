package com.tutorbridge.app.Retrofit

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches "Authorization: Bearer <accessToken>" to every request made
 * through the authenticated Retrofit client. Requests to auth.ts endpoints
 * (register/login/sso/refresh) go through a separate, plain client and never
 * see this interceptor - they don't need a token to work.
 */
class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenManager.getAccessToken()
        val request = if (token != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
