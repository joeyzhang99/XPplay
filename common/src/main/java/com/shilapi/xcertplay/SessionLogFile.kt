package com.shilapi.xcertplay

import java.io.Closeable
import java.io.File

/** Bounded, private diagnostics. Each write is redacted before touching storage. */
internal class SessionLogFile(val file: File) : Closeable {
    private val lock = Any()
    private var closed = false
    fun reset(header: String) = synchronized(lock) {
        if (!closed) {
            file.parentFile?.mkdirs()
            rotate()
            file.writeText("")
            append(header)
        }
    }
    fun append(line: String) = synchronized(lock) {
        if (closed) return@synchronized
        val safe = DiagnosticRedactor.redact(line) ?: return@synchronized
        runCatching {
            if (file.length() > MAX_BYTES) {
                rotate()
                file.writeText("")
            }
            file.appendText(safe + "\n")
        }
        Unit
    }
    private fun rotate() {
        if (!file.exists() || file.length() == 0L) return
        for (index in ARCHIVE_NAMES.lastIndex downTo 1) {
            val source = File(file.parentFile, ARCHIVE_NAMES[index - 1])
            val destination = File(file.parentFile, ARCHIVE_NAMES[index])
            if (source.exists()) source.copyTo(destination, overwrite = true)
        }
        file.copyTo(File(file.parentFile, ARCHIVE_NAMES.first()), overwrite = true)
    }
    override fun close() = synchronized(lock) { closed = true }
    companion object {
        /**
         * 1 MiB, not 512 KiB: today's logs filled 512 KiB in about 25 minutes, so eight files covered
         * only about three hours. The driver's multi-hour trips lost the earliest part of the session.
         * Eight files at 1 MiB still bound the private log directory at about 8 MiB while covering
         * roughly six to seven hours on this head unit.
         */
        const val MAX_BYTES = 1024 * 1024L
        private val ARCHIVE_NAMES = listOf("previous.log") + (2..7).map { "previous-$it.log" }
        val REPORT_NAMES = ARCHIVE_NAMES.reversed() + "diplay.log"
    }
}
