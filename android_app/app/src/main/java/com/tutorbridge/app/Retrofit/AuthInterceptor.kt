package com.tutorbridge.app.Retrofit

import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import okhttp3.Interceptor
import okhttp3.Response

// Reference: Square, Inc. 2023. OkHttp (Version 4.12.0). [Computer program].
//   Available at: https://lysine.dev/okhttp/ [Accessed 21 September 2026].
/**
 * Attaches the signed-in user's Firebase ID token as the bearer, which is
 * what backend_api's `authenticate` middleware verifies.
 *
 * There is no refresh endpoint or stored refresh token to manage: the
 * Firebase SDK caches the ID token and mints a fresh one when it is close to
 * expiry, which is what getIdToken(false) returns here. Requests made while
 * signed out go through unauthenticated and the backend answers 401.
 */
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val user = Firebase.auth.currentUser ?: return chain.proceed(chain.request())

        // Interceptors run off the main thread, so blocking here is fine.
        val token = try {
            Tasks.await(user.getIdToken(false))?.token
        } catch (exception: Exception) {
            null
        }

        val request = if (token != null) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
