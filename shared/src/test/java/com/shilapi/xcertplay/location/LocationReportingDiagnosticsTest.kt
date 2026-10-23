package com.shilapi.xcertplay.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocationReportingDiagnosticsTest {
    @Before
    fun resetState() {
        LocationReportingDiagnostics.reset()
    }

    @Test
    fun anIdleSessionReportsNoSubscriptionOrFix() {
        assertEquals(
            "subscribed=no provider=idle lastFix=none lastSend=none",
            LocationReportingDiagnostics.report(nowMillis = 10_000),
        )
    }

    @Test
    fun anActiveSubscriptionReportsTheFixAndTheSentSentenceKinds() {
        LocationReportingDiagnostics.onRequested(setOf(0, 1, 2, 32769), nowMillis = 1_000)
        LocationReportingDiagnostics.onProviderStarted(true)
        LocationReportingDiagnostics.onFix("gps", fixAtMillis = 2_500, accuracyMeters = 4.5f)
        LocationReportingDiagnostics.onSent(
            "\$GPGGA,123519.00,4807.0380,N,01131.0000,E,1,08,1.0,545.4,M,0.0,M,,\r\n" +
                "\$GPRMC,123519.00,A,4807.0380,N,01131.0000,E,,,,,\r\n",
            nowMillis = 3_000,
        )

        val report = LocationReportingDiagnostics.report(nowMillis = 3_500)
        assertTrue(report, report.contains("subscribed=yes"))
        assertTrue(report, report.contains("components=[0, 1, 2, 32769]"))
        assertTrue(report, report.contains("requestAgeMs=2500ms"))
        assertTrue(report, report.contains("provider=started"))
        assertTrue(report, report.contains("lastFix=gps ageMs=1000ms accuracy=4.5 m"))
        assertTrue(report, report.contains("lastSend=GPGGA+GPRMC ageMs=500ms"))
        // The diagnostic report describes the stream, never the driver's coordinates.
        assertFalse(report, report.contains("4807.0380"))
        assertFalse(report, report.contains("01131.0000"))
    }

    @Test
    fun aSubscribedLinkWithoutAFixSaysSo() {
        LocationReportingDiagnostics.onRequested(setOf(0, 1), nowMillis = 5_000)

        val report = LocationReportingDiagnostics.report(nowMillis = 6_000)
        assertTrue(report, report.contains("subscribed=yes"))
        assertTrue(report, report.contains("provider=not started"))
        assertTrue(report, report.contains("lastFix=none"))
        assertTrue(report, report.contains("lastSend=none"))
    }

    @Test
    fun stopEndsTheSubscriptionButKeepsTheLastFixForContext() {
        LocationReportingDiagnostics.onRequested(setOf(0, 1), nowMillis = 1_000)
        LocationReportingDiagnostics.onProviderStarted(true)
        LocationReportingDiagnostics.onFix("gps", fixAtMillis = 2_000, accuracyMeters = null)
        LocationReportingDiagnostics.onSent("\$GPGGA,120000.00,4807.0380,N,01131.0000,E\r\n", nowMillis = 2_100)

        LocationReportingDiagnostics.onStopped()

        val report = LocationReportingDiagnostics.report(nowMillis = 3_000)
        assertTrue(report, report.contains("subscribed=no"))
        assertTrue(report, report.contains("provider=idle"))
        assertTrue(report, report.contains("lastFix=gps ageMs=1000ms"))
        assertTrue(report, report.contains("lastSend=GPGGA ageMs=900ms"))
    }
}
