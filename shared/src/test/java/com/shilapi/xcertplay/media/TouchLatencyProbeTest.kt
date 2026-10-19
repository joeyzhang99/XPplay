package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TouchLatencyProbeTest {
    @Before fun clearPendingTouch() {
        // A previous test may have left a touch that never got a frame.
        TouchLatencyProbe.onFrame(0L)
        TouchLatencyProbe.maxSendNs = 0L
    }

    @Test fun reportsTheTimeFromTheTouchToTheNextFrame() {
        TouchLatencyProbe.onTouchSent(sentAtNs = 1_000_000_000L, sendDurationNs = 3_000_000L)
        assertEquals(40_000_000L, TouchLatencyProbe.onFrame(1_040_000_000L))
        // Only the first frame after the touch counts.
        assertEquals(-1L, TouchLatencyProbe.onFrame(1_200_000_000L))
    }

    // A report showed touch2frame=9,107,270 ms: a touch from an earlier session was paired with a
    // frame hours later. A latency that large is not a measurement.
    @Test fun aTouchThatNeverGotAFrameIsNotPairedWithALaterOne() {
        TouchLatencyProbe.onTouchSent(sentAtNs = 1_000_000_000L, sendDurationNs = 2_000_000L)
        assertEquals(-1L, TouchLatencyProbe.onFrame(10_000_000_000L))
        assertEquals(0L, TouchLatencyProbe.maxSendNs - 2_000_000L)
    }

    @Test fun aNegativeLatencyIsNotReported() {
        TouchLatencyProbe.onTouchSent(sentAtNs = 5_000_000_000L, sendDurationNs = 0L)
        assertEquals(-1L, TouchLatencyProbe.onFrame(4_000_000_000L))
    }
}
