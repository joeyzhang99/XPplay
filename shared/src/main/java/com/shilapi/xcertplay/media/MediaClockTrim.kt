package com.shilapi.xcertplay.media

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Keeps the head unit's audio clock in step with the iPhone's during music (#610).
 *
 * The iPhone sends music at its own 48 kHz and the head unit plays it at its own. A Humax BHCN-T3103C played
 * 0.76 % fast, so the 300 ms start buffer ran dry about every 70 s and music paused while it refilled. After
 * each write the renderer reports how many frames are queued; a slow average of that level drives a PI
 * controller that, once a second, trims the track's playback rate so the level stays at the start threshold.
 * The trim is bounded by [MAX_TRIM] (26 cents at most) and settles at the head unit's own drift, which is a
 * hardware constant, so the next music stream starts from the trim this one learned.
 *
 * A head unit's report showed the controller pinned at +1.5 % for 75 % of a music session: bursty arrivals
 * swing the queued level by tens of percent, and the proportional term alone was enough to hit the bound.
 * Rate changes are therefore limited to [MAX_STEP_PER_UPDATE] per second and a level inside the
 * deadband holds the current rate, so ordinary jitter cannot make the pitch jump between the bounds.
 */
internal class MediaClockTrim(
    private val sampleRate: Int,
    private val targetFrames: Long,
    private var integral: Double = learnedTrim,
) {
    private var level: Double? = null
    private var lastSampleNs = 0L
    private var lastUpdateNs: Long? = null
    private var compressedBacklog = false

    /** The playback rate last asked of the track, in Hz. */
    var rate = sampleRate
        private set

    /** Include compressed packets waiting for decode: those frames are already queued audio. */
    fun sample(queuedFrames: Long, nowNs: Long, compressedFrames: Long = 0L) {
        if (targetFrames <= 0L) return
        // Decoder backpressure is not evidence that the head unit's clock runs at a different
        // rate; do not teach the *next* music stream an artificial clock correction.
        compressedBacklog = compressedFrames > targetFrames
        val buffered = queuedFrames.coerceAtLeast(0L) +
            compressedFrames.coerceIn(0L, sampleRate.toLong() * 16L)
        val error = (buffered - targetFrames).toDouble() / targetFrames
        val previous = level
        // After a pause, a rebuffer or the first write the average restarts from what is queued now.
        level = if (previous == null || nowNs - lastSampleNs > STALE_NS) error
        else previous + (error - previous) * (1.0 - exp(-(nowNs - lastSampleNs).toDouble() / AVERAGE_NS))
        lastSampleNs = nowNs
    }

    /** The playback rate to set now, or null to keep the current one. */
    fun rateDue(nowNs: Long): Int? {
        val error = level ?: return null
        // Without new audio the level says nothing about the clocks: keep the trim as it is.
        if (nowNs - lastSampleNs > STALE_NS) return null
        val previousUpdate = lastUpdateNs
        if (previousUpdate != null && nowNs - previousUpdate < UPDATE_NS) return null
        // A pause between updates is not drift: integrate at most one normal step over it.
        val seconds = if (previousUpdate == null) 0.0 else ((nowNs - previousUpdate) / 1e9).coerceAtMost(MAX_STEP_SECONDS)
        lastUpdateNs = nowNs
        // Inside the deadband the level is where it should be: keep the learned drift and give the
        // proportional term back, instead of chasing jitter towards the bound. Above the target a
        // deep queue is harmless, so the band is wider there; below it the queue is the only protection
        // against a radio gap, so a dip is corrected much sooner.
        val withinDeadband = error in -DEADBAND_LOW..DEADBAND_HIGH
        if (!withinDeadband && !compressedBacklog) {
            integral = (integral + KI * error * seconds).coerceIn(-MAX_TRIM, MAX_TRIM)
            learnedTrim = integral
        }
        val proportional = if (withinDeadband) 0.0 else KP * error
        val goal = (sampleRate * (1.0 + (proportional + integral).coerceIn(-MAX_TRIM, MAX_TRIM))).roundToInt()
        // The first update after (re)starting music may jump to the learned drift; later ones ramp so
        // a level swing cannot shift the pitch audibly within one second.
        val maxStep = (sampleRate * MAX_STEP_PER_UPDATE).roundToInt().coerceAtLeast(1)
        val next = if (previousUpdate == null) goal else goal.coerceIn(rate - maxStep, rate + maxStep)
        if (next == rate) return null
        rate = next
        return next
    }

    internal companion object {
        /** At most 1.5 % slower or faster: 26 cents, above the 0.76 % seen on #610. */
        const val MAX_TRIM = 0.015

        /** Level error (fraction of the target) that is treated as jitter, not as clock drift. */
        const val DEADBAND_HIGH = 0.2
        const val DEADBAND_LOW = 0.05

        /** At most 0.3 % of the sample rate per update, so a correction ramps instead of jumping. */
        const val MAX_STEP_PER_UPDATE = 0.003
        const val KP = 0.03
        /** Per second, per unit of level error. */
        const val KI = 0.001
        const val AVERAGE_NS = 5_000_000_000.0
        const val UPDATE_NS = 1_000_000_000L
        const val STALE_NS = 250_000_000L
        const val MAX_STEP_SECONDS = 2.0

        /** The drift the last music stream settled on. Each process plays to one head unit. */
        @Volatile var learnedTrim = 0.0
    }
}
