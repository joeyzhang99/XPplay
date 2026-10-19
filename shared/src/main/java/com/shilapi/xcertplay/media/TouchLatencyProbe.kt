package com.shilapi.xcertplay.media

/**
 * Diagnostic link between the touch uplink and the next video frame, read by [VideoStats].
 *
 * A touch that never gets a frame (the screen was static, the session changed, the car was left)
 * must not be paired with a much later frame: subtracting a timestamp from another session produced
 * two-hour "touch2frame" values in a head unit report. Latency is only reported while the frame
 * follows the touch by a plausible time.
 */
internal object TouchLatencyProbe {
    private const val MAX_LATENCY_NS = 2_000_000_000L

    @Volatile private var pendingTouchNs = 0L
    @Volatile var maxSendNs = 0L

    fun onTouchSent(sentAtNs: Long, sendDurationNs: Long) {
        if (pendingTouchNs == 0L) pendingTouchNs = sentAtNs
        if (sendDurationNs > maxSendNs) maxSendNs = sendDurationNs
    }

    /** Returns touch-to-frame latency for the first frame after a touch, or -1 when there is none. */
    fun onFrame(nowNs: Long): Long {
        val touch = pendingTouchNs
        if (touch == 0L) return -1
        pendingTouchNs = 0L
        val latency = nowNs - touch
        return if (latency in 0..MAX_LATENCY_NS) latency else -1
    }
}
