package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DiagnosticLogSummaryTest {
    private fun segment(lines: String): File =
        Files.createTempFile("diplay-summary", ".log").toFile().apply { writeText(lines) }

    @Test
    fun coverageAndHeadersUseTheRealTimeSpanOfEachSegment() {
        val first = segment(
            """
            10:44:12.225  Host started; MFI target=local; transport=wireless
            10:44:39.429  wireless startup generation=1 listener=1 session established atNs=1
            10:46:00.244  Video: video stats rx=9.2fps shown=9.2fps maxGap=1908ms kbps=952 recoveries=0 late=0 decode p50=29ms p90=32ms
            """.trimIndent() + "\n",
        )
        val second = segment(
            """
            13:45:25.227  audio stats audioType=media channel=MEDIA codec=AAC_LC underruns=+2 queueMinMs=0 ended=false
            13:46:17.355  Video: video stats rx=12.8fps shown=12.8fps maxGap=1097ms kbps=200 recoveries=0 late=1 decode p50=29ms p90=33ms
            """.trimIndent() + "\n",
        )
        val analysis = DiagnosticLogSummary.analyze(listOf("previous-6.log" to first, "diplay.log" to second))

        assertEquals(DiagnosticLogSummary.Coverage("10:44:12.225", "10:46:00.244", 3), analysis.coverage["previous-6.log"])
        assertEquals(DiagnosticLogSummary.Coverage("13:45:25.227", "13:46:17.355", 2), analysis.coverage["diplay.log"])
        assertEquals(
            "--- previous-6.log (10:44:12.225–10:46:00.244, 3 lines) ---",
            DiagnosticLogSummary.header("previous-6.log", analysis.coverage["previous-6.log"]),
        )
        assertTrue(analysis.lines.any { it.startsWith("Log coverage: 10:44:12.225–13:46:17.355 across 2 segment(s)") })
    }

    @Test
    fun summaryCountsReconnectsVideoAndAudioWithoutTreatingPromptTailsAsStalls() {
        val log = segment(
            """
            10:00:00.000  AirPlay session ended; reconnecting from scratch
            10:00:01.000  Audio: rebuffer paused audioType=media rebufferCount=1 retainedMs=0
            10:00:02.000  Video recovery: requested keyframe sent=true
            10:00:03.000  Video: video stats rx=20fps shown=20fps maxGap=300ms kbps=900 recoveries=0 late=0 decode p50=28ms p90=35ms
            10:00:04.000  Video: video stats rx=5fps shown=5fps maxGap=1900ms kbps=120 recoveries=0 late=1 decode p50=28ms p90=35ms
            10:00:05.000  audio stats audioType=media channel=MEDIA underruns=+2 queueMinMs=0 maxGapMs=400 ended=false
            10:00:06.000  audio stats audioType=speechrecognition channel=ASSISTANT underruns=+5 queueMinMs=0 maxGapMs=300 ended=false
            10:00:07.000  audio stats audioType=default channel=NAVIGATION underruns=+7 queueMinMs=0 maxGapMs=300 ended=true
            10:00:08.000  audio stats audioType=media channel=MEDIA underruns=+0 queueMinMs=800 maxGapMs=1400 ended=false
            """.trimIndent() + "\n",
        )
        val lines = DiagnosticLogSummary.analyze(listOf("diplay.log" to log)).lines.joinToString("\n")

        assertTrue(lines, lines.contains("Session reconnects: 1"))
        assertTrue(lines, lines.contains("Video: 2 windows, 1 with late frames, 1 keyframe recoveries, max frame gap 1900ms"))
        assertTrue(lines, lines.contains("Audio: media underruns 2, prompt underruns 5 (stream-end tail 7), rebuffer pauses 1, max packet gap 1400ms"))
    }

    @Test
    fun missingSegmentsDoNotBreakTheSummary() {
        val analysis = DiagnosticLogSummary.analyze(listOf("previous.log" to File("/does/not/exist")))
        assertTrue(analysis.coverage.isEmpty())
        assertTrue(analysis.lines.first().startsWith("Log coverage: no timestamps"))
        assertEquals("--- previous.log ---", DiagnosticLogSummary.header("previous.log", null))
    }
}
