package com.tutorbridge.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

// Reference: Android Developers. 2026. Monitor connectivity status and connection metering. [Online].
//   Available at: https://developer.android.com/develop/connectivity/network-ops/reading-network-state [Accessed 20 October 2026].
/**
 * Tells the app whether the phone can reach the internet, and when that changes.
 *
 * "Can reach" means the network is validated - Android has checked that
 * traffic really gets out - not merely that wifi is switched on. A hotel or
 * campus wifi that wants a login page is connected but not online, and for
 * offline mode that counts as offline.
 */
class NetworkMonitor(context: Context) {

    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    /** A one-off answer, for deciding whether to try a request now. */
    fun isOnlineNow(): Boolean = usable(connectivity.getNetworkCapabilities(connectivity.activeNetwork))

    /** The current answer first, then every change. */
    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(usable(capabilities))
            }

            // The default network went away; if another takes over, its capabilities arrive next.
            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        trySend(isOnlineNow())
        awaitClose {
            Log.d(TAG, "Network callback unregistered")
            connectivity.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    private fun usable(capabilities: NetworkCapabilities?): Boolean =
        capabilities != null &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private companion object {
        const val TAG = "NetworkMonitor"
    }
}
