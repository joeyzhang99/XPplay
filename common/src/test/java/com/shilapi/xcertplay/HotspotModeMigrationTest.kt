package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class HotspotModeMigrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)

    // V1.4.8 exposes it again as an experimental direct link; a saved selection is kept on Android 8+.
    @Test fun localOnlySelectionIsPreservedOnAndroid8AndNewer() {
        AirPlayPersistence.saveManualHotspotSsid(context, "Test car")
        AirPlayPersistence.saveManualHotspotPassphrase(context, "test-password")
        AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.LOCAL_ONLY_HOTSPOT)
        assertEquals(WirelessHotspotMode.LOCAL_ONLY_HOTSPOT, AirPlayPersistence.loadWirelessHotspotMode(context))
        assertEquals("LOCAL_ONLY_HOTSPOT", prefs.getString("wireless_hotspot_mode", null))
        assertEquals("Test car", AirPlayPersistence.loadManualHotspotSsid(context))
        assertEquals("test-password", AirPlayPersistence.loadManualHotspotPassphrase(context))
    }

    // Android 7 has no local-only hotspot API; the saved car-hotspot details carry over.
    @Test @Config(sdk = [25]) fun localOnlySelectionMigratesOnAndroid7() {
        prefs.edit().putString("wireless_hotspot_mode", "LOCAL_ONLY_HOTSPOT").apply()
        AirPlayPersistence.saveManualHotspotSsid(context, "Test car")
        AirPlayPersistence.saveManualHotspotPassphrase(context, "test-password")
        assertEquals(WirelessHotspotMode.MANUAL, AirPlayPersistence.loadWirelessHotspotMode(context))
        assertEquals("MANUAL", prefs.getString("wireless_hotspot_mode", null))
        assertEquals("Test car", AirPlayPersistence.loadManualHotspotSsid(context))
        assertEquals("test-password", AirPlayPersistence.loadManualHotspotPassphrase(context))
    }

    @Test fun freshInstallUsesBuiltInHotspot() {
        prefs.edit().clear().apply()
        assertEquals(WirelessHotspotMode.MANUAL, AirPlayPersistence.loadWirelessHotspotMode(context))
    }

    @Test fun existingWifiDirectSelectionIsPreserved() {
        AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.WIFI_P2P)
        assertEquals(WirelessHotspotMode.WIFI_P2P, AirPlayPersistence.loadWirelessHotspotMode(context))
    }

    @Test @Config(sdk = [28]) fun olderAndroidKeepsWifiDirectSelected() {
        prefs.edit().putString("wireless_hotspot_mode", "WIFI_P2P").apply()
        assertEquals(WirelessHotspotMode.WIFI_P2P, AirPlayPersistence.loadWirelessHotspotMode(context))
    }
}
