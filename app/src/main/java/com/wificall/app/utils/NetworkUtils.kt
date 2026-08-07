package com.wificall.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * NetworkUtils.kt
 * Utility object and Flow-based observer for monitoring internet connectivity.
 * Uses the modern ConnectivityManager.NetworkCallback API (API 26+) instead of
 * the deprecated BroadcastReceiver + CONNECTIVITY_ACTION approach.
 */
object NetworkUtils {

    /**
     * Returns true if the device currently has an active internet-capable
     * network connection (Wi-Fi, cellular, or Ethernet).
     *
     * @param context Any valid Context (Application context preferred to avoid leaks).
     */
    fun isInternetAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * A cold [Flow] that emits [Boolean] whenever network connectivity changes:
     * - `true`  → internet is now available
     * - `false` → internet was lost
     *
     * Collects inside a coroutine scope (e.g. viewModelScope) and automatically
     * unregisters the callback when the scope is cancelled.
     *
     * Usage:
     * ```kotlin
     * networkUtils.observeNetworkState(context)
     *     .onEach { isConnected -> updateUi(isConnected) }
     *     .launchIn(viewModelScope)
     * ```
     */
    fun observeNetworkState(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Network became available – verify it actually has internet
                val caps = cm.getNetworkCapabilities(network)
                val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                trySend(hasInternet)
            }

            override fun onLost(network: Network) {
                // Network connection dropped
                trySend(false)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                // Capabilities changed – re-evaluate internet availability
                val validated = networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                )
                trySend(validated)
            }
        }

        // Build a request that matches any network capable of internet access
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, callback)

        // Emit the current state immediately so observers don't wait for the next change
        trySend(isInternetAvailable(context))

        // Clean up: unregister when the collecting coroutine is cancelled
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }
}
