package org.schabi.newpipe.qpipepipe.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** What Android knows about the phone's internet connection. */
object ValidatedInternet {
    /**
     * Whether the phone's default network reaches the internet, as Android last checked it:
     * false with no network, and with a network that has no internet (a Wi-Fi without a line, a
     * captive portal, a private DNS server that can't be reached). When Android can't tell, true.
     */
    @JvmStatic
    fun isAvailable(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
