package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DiagnosticExportStorePruneTest {
    @Test
    fun publicReportsKeepTheNewestEightAndLeaveForeignFilesAlone() {
        val directory = Files.createTempDirectory("xp play reports").toFile()
        val now = System.currentTimeMillis()
        val reports = (1..10).map { index ->
            File(directory, "XPplay-20261022-10000$index-000.txt").apply {
                writeText("report $index")
                setLastModified(now - (11 - index) * 60_000L)
            }
        }
        val foreign = File(directory, "notes.txt").apply { writeText("keep") }
        val current = reports.last()

        DiagnosticExportStore.pruneReports(directory, 8, current)

        val remaining = directory.listFiles()!!.map { it.name }.toSet()
        assertEquals(8, remaining.count { it.startsWith("XPplay-") && it.endsWith(".txt") })
        // Newest seven others plus the current one survive; the two oldest reports go.
        assertTrue(remaining.contains(current.name))
        assertTrue(!remaining.contains(reports[0].name))
        assertTrue(!remaining.contains(reports[1].name))
        assertTrue(remaining.contains(reports[2].name))
        assertTrue(remaining.contains(foreign.name))
    }
}
