package com.shilapi.xcertplay.airplay

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Measures the RTP clock the iPhone uses on one audio stream from its own downlink packets, so a
 * report shows which clock a microphone uplink on that stream must count in.
 *
 * A stream that pauses (music stopped, a link gap, buffered audio running dry) advances its
 * timestamps slower than the wall clock; a window that spans such a pause would report a clock far
 * below the real one. A measurement outside [tolerance] of [expectedHz] is therefore discarded and
 * the window restarts. The probe gives up after [maxRestarts] discards so a stream that never sends
 * a clean window does not keep re-arming for the whole session.
 */
internal class RtpClockProbe(
    private val expectedHz: Int,
    private val windowNs: Long = 2_000_000_000L,
    private val tolerance: Double = 0.10,
    private val maxRestarts: Int = 4,
) {
    private var firstSample = 0L
    private var firstNs = 0L
    private var started = false
    private var done = false
    private var restarts = 0

    /** Returns the measured clock in Hz once, after [windowNs] of packets; null otherwise. */
    @Synchronized
    fun observe(sample: Int, nowNs: Long): Int? {
        if (done) return null
        val unsigned = sample.toLong() and 0xffff_ffffL
        if (!started) {
            startWindow(unsigned, nowNs)
            return null
        }
        val elapsedNs = nowNs - firstNs
        if (elapsedNs < windowNs) return null
        val ticks = (unsigned - firstSample) and 0xffff_ffffL
        val hz = (ticks * 1_000_000_000.0 / elapsedNs).roundToInt()
        if (expectedHz <= 0 || abs(hz - expectedHz) <= expectedHz * tolerance) {
            done = true
            return hz
        }
        // The window spanned a pause or a link gap: its elapsed time is not the stream's time.
        if (restarts >= maxRestarts) {
            done = true
            return null
        }
        restarts++
        startWindow(unsigned, nowNs)
        return null
    }

    private fun startWindow(sample: Long, nowNs: Long) {
        started = true
        firstSample = sample
        firstNs = nowNs
    }
}
