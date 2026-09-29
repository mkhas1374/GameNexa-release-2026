package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object VpnDetector {
    fun isVpnActiveFlow(context: Context): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        // Check initial state
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        val initialVpnState = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        trySend(initialVpnState)

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val isVpn = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                trySend(isVpn)
            }

            override fun onLost(network: Network) {
                // If a network is lost, we check the fallback active network
                val fallbackNetwork = connectivityManager.activeNetwork
                val fallbackCaps = connectivityManager.getNetworkCapabilities(fallbackNetwork)
                val isVpn = fallbackCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                trySend(isVpn)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }.distinctUntilChanged()
}
