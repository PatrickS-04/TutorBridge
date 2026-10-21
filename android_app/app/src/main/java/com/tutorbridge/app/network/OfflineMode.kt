package com.tutorbridge.app.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * The "Force offline mode" switch in Settings. It lives outside the ViewModel
 * because the OkHttp client that has to obey it is a singleton with no access
 * to one. Forcing offline is how the app is shown working without a
 * connection, on a phone that has one.
 */
object OfflineMode {
    @Volatile
    var forced: Boolean = false

    const val BLOCKED_MESSAGE = "Offline mode is on"
}

/**
 * Whether the app should behave as connected: the phone has a working network
 * and the user has not forced offline mode.
 */
fun effectivelyOnline(networkAvailable: Boolean, forcedOffline: Boolean): Boolean =
    networkAvailable && !forcedOffline

// Reference: Square, Inc. 2023. OkHttp (Version 4.12.0). [Computer program].
//   Available at: https://lysine.dev/okhttp/ [Accessed 21 September 2026].
/**
 * Refuses every request while offline mode is forced, the way a dead network
 * would: with an IOException, which the repository already turns into a failed
 * Result. Without this the switch would only change a banner. It sits first in
 * the chain so nothing - not even the Firebase token lookup - runs for a call
 * that is not going anywhere.
 */
class OfflineGateInterceptor(private val isForced: () -> Boolean = { OfflineMode.forced }) : Interceptor {
    private companion object {
        const val TAG = "Network"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (isForced()) {
            Log.d(TAG, "Blocked ${chain.request().method} ${chain.request().url.encodedPath}: offline mode is forced")
            throw IOException(OfflineMode.BLOCKED_MESSAGE)
        }
        return chain.proceed(chain.request())
    }
}
