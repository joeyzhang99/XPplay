package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.AudioCodecKind

/**
 * Format limits for the microphone uplink.
 *
 * Ported from xcertplay 1.4.2 (`fix(audio): potential issue in microphone uplink`). The old rule
 * rejected every format above mono/16 kHz; the recorder now opens at the negotiated rate and only
 * the AAC-ELD encoder is mono-only, because LPCM carries the negotiated channel count as-is.
 *
 * Keeping this in one place lets the engine refuse an unusable format up front instead of failing
 * in the middle of a capture, and lets tests cover the rule without a device.
 */
internal object MicrophoneFormat {
    /** Why this format cannot be captured, or null when it can. */
    fun rejection(codec: AudioCodecKind, channels: Int): String? =
        if (codec == AudioCodecKind.AAC_ELD && channels != 1) {
            "microphone AAC-ELD requires mono, requested channels=$channels"
        } else {
            null
        }
}
