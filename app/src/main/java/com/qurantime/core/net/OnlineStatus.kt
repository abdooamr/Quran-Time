package com.qurantime.core.net

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether the device has a working network connection. */
interface OnlineStatus {
    val online: StateFlow<Boolean>
}

/** Follows the default network: online once it has validated internet access. */
class ConnectivityOnlineStatus(cm: ConnectivityManager) : OnlineStatus {
    private val state = MutableStateFlow(isUp(cm.getNetworkCapabilities(cm.activeNetwork)))
    override val online: StateFlow<Boolean> = state

    init {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state.value = isUp(caps)
            }

            override fun onLost(network: Network) {
                state.value = false
            }
        })
    }

    private fun isUp(caps: NetworkCapabilities?): Boolean =
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
