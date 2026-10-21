package com.tutorbridge.app.Retrofit

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.auth
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

// Reference: Square, Inc. 2023. OkHttp (Version 4.12.0). [Computer program].
//   Available at: https://lysine.dev/okhttp/ [Accessed 21 September 2026].

/** What asking for the signed-in user's Firebase ID token came to. */
sealed class TokenResult {
    data class Token(val value: String) : TokenResult()

    /** Nobody is signed in: public endpoints go through without a token. */
    object NoUser : TokenResult()

    /** Someone is signed in but the token could not be fetched because the network is down. */
    object NetworkFailure : TokenResult()

    /** Someone is signed in and the token could not be fetched for another reason (account removed, session revoked). */
    object Failed : TokenResult()
}

/**
 * Attaches the signed-in user's Firebase ID token as the bearer, which is
 * what backend_api's `authenticate` middleware verifies.
 *
 * There is no refresh endpoint or stored refresh token to manage: the
 * Firebase SDK caches the ID token and mints a fresh one when it is close to
 * expiry, which is what getIdToken(false) returns here. Requests made while
 * signed out go through unauthenticated and the backend answers 401.
 *
 * Offline, the SDK cannot mint a fresh token once the cached one has expired.
 * Sending the request anyway, without one, would turn "no connection" into a
 * 401 - which the app reads as "sign in again" and the sync engine as "stop
 * until the user signs in". So when the token is missing for lack of a network,
 * the request fails the way any request would on a dead connection (an
 * IOException), and the cache and the outbox treat it as exactly that.
 */
class AuthInterceptor(private val tokens: () -> TokenResult = ::firebaseToken) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return when (val result = tokens()) {
            TokenResult.NoUser -> chain.proceed(request)
            is TokenResult.Token -> chain.proceed(request.newBuilder().header("Authorization", "Bearer ${result.value}").build())
            TokenResult.NetworkFailure -> throw IOException("Could not refresh your sign-in without a connection")
            // Not a connectivity problem: let the server say what it thinks (a 401), which
            // is the signal that the user really does have to sign in again.
            TokenResult.Failed -> chain.proceed(request)
        }
    }
}

private const val TAG = "AuthInterceptor"

/** The real token source. Interceptors run off the main thread, so blocking on the task is fine. */
internal fun firebaseToken(): TokenResult {
    val user = Firebase.auth.currentUser ?: return TokenResult.NoUser
    return try {
        Tasks.await(user.getIdToken(false))?.token?.let { TokenResult.Token(it) } ?: TokenResult.Failed
    } catch (exception: Exception) {
        Log.w(TAG, "Could not get an ID token", exception)
        if (generateSequence<Throwable>(exception) { it.cause }.any { it is FirebaseNetworkException || it is IOException }) {
            TokenResult.NetworkFailure
        } else {
            TokenResult.Failed
        }
    }
}
