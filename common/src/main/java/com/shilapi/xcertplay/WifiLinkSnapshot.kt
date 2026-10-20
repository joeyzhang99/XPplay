package com.shilapi.xcertplay

import android.content.Context
import android.net.wifi.WifiManager

/**
 * One line about the head unit's station link, for reports that must tell a stalling link from a busy
 * decoder. A 2026-10-20 report showed 0.5–1.2 s video/audio arrival gaps while decode stayed at
 * 55–70 ms; without the signal level and link speed the next report cannot say whether the radio was
 * weak, the rate was low, or the access point paused the station.
 */
internal object WifiLinkSnapshot {
    fun describe(context: Context): String? {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        val info = runCatching { wifi.connectionInfo }.getOrNull() ?: return null
        val ssid = info.ssid?.removeSurrounding("\"")
            ?.takeUnless { it == WifiManager.UNKNOWN_SSID || it.isEmpty() }
        // calculateSignalLevel(4) needs API 30; the 5-level form has existed since API 1.
        @Suppress("DEPRECATION")
        val level = runCatching { WifiManager.calculateSignalLevel(info.rssi, 5) }.getOrDefault(-1)
        return "rssi=${info.rssi}dBm level=$level speed=${info.linkSpeed}Mbps " +
            "freq=${info.frequency}MHz nameReadable=${ssid != null}"
    }
}
