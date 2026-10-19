package com.shilapi.xcertplay.airplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RtpClockProbeTest {
    @Test fun measuresTheClockOnceAfterTheWindow() {
        val probe = RtpClockProbe(expectedHz = 16_000, windowNs = 1_000_000_000L)
        assertNull(probe.observe(1_000, 0L))
        assertNull(probe.observe(1_000 + 8_000, 500_000_000L))
        assertEquals(16_000, probe.observe(1_000 + 16_000, 1_000_000_000L))
        assertNull(probe.observe(1_000 + 32_000, 2_000_000_000L))
    }

    @Test fun countsAcrossTheUnsignedTimestampWrap() {
        val probe = RtpClockProbe(expectedHz = 48_000, windowNs = 1_000_000_000L)
        probe.observe(-24_000, 0L)
        assertEquals(48_000, probe.observe(24_000, 1_000_000_000L))
    }

    // A report showed a 44.1 kHz stream "measured" at 36.8 kHz because the window spanned a pause:
    // the timestamps advanced by less than the wall clock. Such a window must not be reported.
    @Test fun aWindowThatSpannedAPauseIsDiscardedAndMeasuredAgain() {
        val probe = RtpClockProbe(expectedHz = 44_100, windowNs = 1_000_000_000L, maxRestarts = 2)
        assertNull(probe.observe(0, 0L))
        // Only 0.5 s of audio timestamps arrived in 1 s of wall time (music paused mid-window).
        assertNull(probe.observe(22_050, 1_000_000_000L))
        // A clean window afterwards reports the iPhone's real clock.
        assertNull(probe.observe(44_100, 2_000_000_000L))
        assertEquals(44_100, probe.observe(88_200, 3_000_000_000L))
    }

    @Test fun stopsAfterTheRestartBudget() {
        val probe = RtpClockProbe(expectedHz = 44_100, windowNs = 1_000_000_000L, maxRestarts = 1)
        assertNull(probe.observe(0, 0L))
        assertNull(probe.observe(22_050, 1_000_000_000L)) // first discard, window restarts
        assertNull(probe.observe(33_075, 2_000_000_000L)) // second discard gives up
        assertNull(probe.observe(44_100, 3_000_000_000L))
        assertNull(probe.observe(88_200, 4_000_000_000L))
        assertNull(probe.observe(132_300, 5_000_000_000L))
    }
}
