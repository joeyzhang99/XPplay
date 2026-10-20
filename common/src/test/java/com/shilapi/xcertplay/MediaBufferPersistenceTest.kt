package com.shilapi.xcertplay

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class MediaBufferPersistenceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)

    @Before fun clearPreferences() {
        prefs.edit().clear().apply()
    }

    @Test fun freshInstallUsesEachTransportsDefault() {
        // Wireless starts at 1000 ms so 0.5-1.1 s link stalls do not starve music; wired keeps 300 ms.
        assertEquals(1000, AirPlayPersistence.loadMediaBufferMillis(context, wireless = true))
        assertEquals(300, AirPlayPersistence.loadMediaBufferMillis(context, wireless = false))
    }

    @Test fun wiredAndWirelessAreSavedSeparately() {
        AirPlayPersistence.saveMediaBufferMillis(context, wireless = false, millis = 50)
        AirPlayPersistence.saveMediaBufferMillis(context, wireless = true, millis = 1000)
        assertEquals(50, AirPlayPersistence.loadMediaBufferMillis(context, wireless = false))
        assertEquals(1000, AirPlayPersistence.loadMediaBufferMillis(context, wireless = true))
    }

    @Test fun sharedDelayFromEarlierVersionsCarriesOverToWired() {
        prefs.edit().putInt("media_buffer_ms", 50).apply()
        assertEquals(50, AirPlayPersistence.loadMediaBufferMillis(context, wireless = false))
        // 50 ms is not a wireless preset, so wireless falls back to its own default.
        assertEquals(1000, AirPlayPersistence.loadMediaBufferMillis(context, wireless = true))
    }

    @Test fun changingWirelessDoesNotMoveAnInheritedWiredDelay() {
        prefs.edit().putInt("media_buffer_ms", 500).apply()
        AirPlayPersistence.saveMediaBufferMillis(context, wireless = true, millis = 1000)
        assertEquals(500, AirPlayPersistence.loadMediaBufferMillis(context, wireless = false))
        assertEquals(1000, AirPlayPersistence.loadMediaBufferMillis(context, wireless = true))
    }

    @Test fun wirelessRejectsWiredOnlyPresets() {
        AirPlayPersistence.saveMediaBufferMillis(context, wireless = true, millis = 100)
        assertEquals(1000, AirPlayPersistence.loadMediaBufferMillis(context, wireless = true))
    }

    // The G3's H.264 decoder needs ~57 ms/frame and freezes; HEVC takes ~29 ms, so it is the default.
    @Test fun hevcIsOnByDefaultAndCanBeTurnedOff() {
        assertEquals(true, AirPlayPersistence.loadHevcEnabled(context))
        AirPlayPersistence.saveHevcEnabled(context, false)
        assertEquals(false, AirPlayPersistence.loadHevcEnabled(context))
    }

    @Test fun highPerformanceWifiIsOnByDefaultAndCanBeTurnedOff() {
        assertEquals(true, AirPlayPersistence.loadWifiHighPerformance(context))
        AirPlayPersistence.saveWifiHighPerformance(context, false)
        assertEquals(false, AirPlayPersistence.loadWifiHighPerformance(context))
    }

    @Test fun anExplicitWirelessChoiceSurvives() {
        AirPlayPersistence.saveMediaBufferMillis(context, wireless = true, millis = 300)
        assertEquals(300, AirPlayPersistence.loadMediaBufferMillis(context, wireless = true))
    }
}
