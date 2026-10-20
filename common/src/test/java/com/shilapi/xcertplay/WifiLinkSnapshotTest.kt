package com.shilapi.xcertplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class WifiLinkSnapshotTest {
    /**
     * The diagnostic exporter drops any line that looks like a credential, and its pattern includes
     * "ssid". The first Wi-Fi link line used "ssidReadable=true" and never reached a report.
     */
    @Test fun theWifiLinkLineSurvivesTheDiagnosticExporter() {
        val line = WifiLinkSnapshot.describe(RuntimeEnvironment.getApplication()) ?: return
        assertFalse(line.contains("ssid", ignoreCase = true))
        assertNotNull(DiagnosticRedactor.redact("Wireless link $line"))
    }
}
