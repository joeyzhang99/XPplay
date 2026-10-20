package com.shilapi.xcertplay.network

/**
 * Picks the channel advertised to the iPhone when the platform did not report the AP's live one.
 *
 * Android 9's LocalOnlyHotspot reservation reports `apChannel = 0` (automatic) and most Qualcomm
 * firmwares do not answer the Wireless Extensions query for a soft AP interface. A 2.4 GHz-only head
 * unit (XPeng G3) still has to advertise a usable channel: iOS scans for the CarPlay network, but the
 * channel in the accessory Wi-Fi configuration decides how quickly it joins. A measured value always
 * wins; a neighbouring station channel is a good guess on single-radio devices; the two fallbacks are
 * the most common defaults of their band.
 */
internal object LocalOnlyHotspotChannelPolicy {
    const val FALLBACK_2_GHZ_CHANNEL = 6
    const val FALLBACK_5_GHZ_CHANNEL = 36

    fun resolve(
        bandLabel: String,
        liveFrequencyMHz: Int?,
        configuredChannel: Int,
        requestedChannel: Int?,
        scannedChannel: Int?,
        stationChannel: Int?,
    ): Int {
        liveFrequencyMHz?.let(::wifiFrequencyMhzToChannel)?.let { return it }
        if (configuredChannel > 0) return configuredChannel
        requestedChannel?.let { return it }
        scannedChannel?.let { if (it > 0) return it }
        if (bandLabel == "2.4 GHz") stationChannel?.takeIf { it in 1..14 }?.let { return it }
        return if (bandLabel == "2.4 GHz") FALLBACK_2_GHZ_CHANNEL else FALLBACK_5_GHZ_CHANNEL
    }
}
