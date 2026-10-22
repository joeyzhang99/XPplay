package com.shilapi.xcertplay

import java.io.File

/**
 * Reads the retained session logs once and produces what a support report needs at a glance:
 * the time span every segment covers, how often the session reconnected, how healthy video and
 * audio were, and where the oldest retained line is. Segment headers also get their own span so
 * a reader can tell which part of a multi-hour drive each file holds.
 */
internal object DiagnosticLogSummary {
    data class Coverage(val firstTimestamp: String, val lastTimestamp: String, val lineCount: Int)

    data class Analysis(
        val coverage: Map<String, Coverage>,
        val lines: List<String>,
    )

    private val TIMESTAMP = Regex("^(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s")
    private val VIDEO_STATS = Regex("Video: video stats rx=")
    private val VIDEO_GAP = Regex("maxGap=(\\d+)ms")
    private val VIDEO_LATE = Regex("late=(\\d+)")
    private val AUDIO_STATS = Regex("audio stats audioType=(\\w+)")
    private val AUDIO_UNDERRUNS = Regex("underruns=\\+?(\\d+)")
    private val AUDIO_ENDED = Regex("ended=(true|false)")
    private val AUDIO_GAP = Regex("maxGapMs=(\\d+)")

    fun analyze(segments: List<Pair<String, File>>): Analysis {
        val coverage = LinkedHashMap<String, Coverage>()
        var reconnects = 0
        var videoWindows = 0
        var videoLateWindows = 0
        var videoRecoveries = 0
        var videoMaxGap = 0
        var mediaUnderruns = 0
        var promptUnderruns = 0
        var tailUnderruns = 0
        var rebufferPauses = 0
        var audioMaxGap = 0
        var firstTimestamp: String? = null
        var lastTimestamp: String? = null
        var totalLines = 0

        for ((name, file) in segments) {
            if (!file.isFile) continue
            var segmentFirst: String? = null
            var segmentLast: String? = null
            var segmentLines = 0
            file.useLines { lines ->
                for (line in lines) {
                    segmentLines++
                    totalLines++
                    val timestamp = TIMESTAMP.find(line)?.groupValues?.get(1)
                    // Log lines start with "HH:MM:SS.mmm  "; match event names without the stamp.
                    val body = if (timestamp != null) line.substring(timestamp.length).trimStart() else line
                    if (timestamp != null) {
                        if (segmentFirst == null) segmentFirst = timestamp
                        segmentLast = timestamp
                        if (firstTimestamp == null) firstTimestamp = timestamp
                        lastTimestamp = timestamp
                    }
                    if (body.contains("AirPlay session ended; reconnecting from scratch")) reconnects++
                    if (body.startsWith("Video recovery:")) videoRecoveries++
                    if (VIDEO_STATS.containsMatchIn(line)) {
                        videoWindows++
                        val gap = VIDEO_GAP.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        if (gap > videoMaxGap) videoMaxGap = gap
                        val late = VIDEO_LATE.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        if (late > 0) videoLateWindows++
                    }
                    if (body.startsWith("Audio: rebuffer paused")) rebufferPauses++
                    val audioType = AUDIO_STATS.find(line)?.groupValues?.get(1)
                    if (audioType != null) {
                        val underruns = AUDIO_UNDERRUNS.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        if (underruns > 0) {
                            if (AUDIO_ENDED.find(line)?.groupValues?.get(1) == "true") {
                                // The stream-end flush reports the hardware's tail count; that is the
                                // short prompt's last samples, not a link stall during playback.
                                tailUnderruns += underruns
                            } else if (audioType == "media") {
                                mediaUnderruns += underruns
                            } else {
                                promptUnderruns += underruns
                            }
                        }
                        val gap = AUDIO_GAP.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        if (gap > audioMaxGap) audioMaxGap = gap
                    }
                }
            }
            if (segmentFirst != null && segmentLast != null) {
                coverage[name] = Coverage(segmentFirst, segmentLast, segmentLines)
            }
        }

        val span = if (firstTimestamp != null && lastTimestamp != null) {
            "$firstTimestamp–$lastTimestamp"
        } else {
            "no timestamps"
        }
        return Analysis(
            coverage = coverage,
            lines = listOf(
                "Log coverage: $span across ${coverage.size} segment(s), $totalLines lines",
                "Session reconnects: $reconnects",
                "Video: $videoWindows windows, $videoLateWindows with late frames, " +
                    "$videoRecoveries keyframe recoveries, max frame gap ${videoMaxGap}ms",
                "Audio: media underruns $mediaUnderruns, prompt underruns $promptUnderruns " +
                    "(stream-end tail $tailUnderruns), rebuffer pauses $rebufferPauses, " +
                    "max packet gap ${audioMaxGap}ms",
            ),
        )
    }

    /** A segment header that also tells which part of the session the file holds. */
    fun header(name: String, coverage: Coverage?): String =
        if (coverage == null) "--- $name ---"
        else "--- $name (${coverage.firstTimestamp}–${coverage.lastTimestamp}, ${coverage.lineCount} lines) ---"
}
