package com.shilapi.xcertplay.location

import java.util.Locale

/**
 * Runtime state of iAP2 LocationInformation for the exported diagnostic report.
 *
 * The report records no coordinates: provider name, fix age, accuracy and the sentence types keep
 * a support conversation useful without copying the driver's position into a shared file.
 */
object LocationReportingDiagnostics {
    private data class State(
        val subscribed: Boolean = false,
        val requestAtMillis: Long = 0L,
        val components: Set<Int> = emptySet(),
        val providerStarted: Boolean = false,
        val fixProvider: String? = null,
        val fixAtMillis: Long = 0L,
        val fixAccuracyMeters: Float? = null,
        val sendAtMillis: Long = 0L,
        val sentKinds: String? = null,
    )

    private val lock = Any()
    private var state = State()

    /** The iPhone subscribed with 0xFFFA; this starts a new subscription generation. */
    fun onRequested(components: Set<Int>, nowMillis: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            state = State(subscribed = true, requestAtMillis = nowMillis, components = components)
        }
    }

    fun onProviderStarted(started: Boolean) {
        synchronized(lock) { state = state.copy(providerStarted = started) }
    }

    /** Records the fix that is actually about to be encoded, not every LocationListener callback. */
    fun onFix(provider: String, fixAtMillis: Long, accuracyMeters: Float?) {
        synchronized(lock) {
            state = state.copy(
                fixProvider = provider.ifBlank { "unknown" },
                fixAtMillis = fixAtMillis,
                fixAccuracyMeters = accuracyMeters,
            )
        }
    }

    fun onSent(nmea: String, nowMillis: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            state = state.copy(sendAtMillis = nowMillis, sentKinds = sentenceKinds(nmea))
        }
    }

    /** The iPhone sent 0xFFFC: the subscription and provider are gone, the last fix stays for context. */
    fun onStopped() {
        synchronized(lock) {
            state = state.copy(
                subscribed = false,
                requestAtMillis = 0L,
                components = emptySet(),
                providerStarted = false,
            )
        }
    }

    fun reset() {
        synchronized(lock) { state = State() }
    }

    fun report(nowMillis: Long = System.currentTimeMillis()): String {
        val current = synchronized(lock) { state }
        return buildList {
            add("subscribed=${if (current.subscribed) "yes" else "no"}")
            if (current.subscribed) {
                add("components=${current.components.sorted()}")
                add("requestAgeMs=${ageMillis(nowMillis, current.requestAtMillis)}")
            }
            add(
                "provider=" + when {
                    current.providerStarted -> "started"
                    current.subscribed -> "not started"
                    else -> "idle"
                },
            )
            add(
                if (current.fixProvider == null) {
                    "lastFix=none"
                } else {
                    buildString {
                        append("lastFix=").append(current.fixProvider)
                        append(" ageMs=").append(ageMillis(nowMillis, current.fixAtMillis))
                        accuracyMeters(current.fixAccuracyMeters)?.let { append(" accuracy=").append(it).append(" m") }
                    }
                },
            )
            add(
                if (current.sentKinds == null) {
                    "lastSend=none"
                } else {
                    "lastSend=${current.sentKinds} ageMs=${ageMillis(nowMillis, current.sendAtMillis)}"
                },
            )
        }.joinToString(" ")
    }

    private fun ageMillis(nowMillis: Long, thenMillis: Long): String =
        if (thenMillis <= 0L) "none" else "${(nowMillis - thenMillis).coerceAtLeast(0L)}ms"

    private fun sentenceKinds(nmea: String): String = buildList {
        if (nmea.contains("\$GPGGA")) add("GPGGA")
        if (nmea.contains("\$GPRMC")) add("GPRMC")
    }.joinToString("+").ifEmpty { "NMEA" }

    private fun accuracyMeters(value: Float?): String? =
        value?.takeIf { it.isFinite() && it > 0f }?.let { String.format(Locale.US, "%.1f", it) }
}
