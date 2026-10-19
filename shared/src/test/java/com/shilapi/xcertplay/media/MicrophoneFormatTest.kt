package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioStreamCodec
import com.shilapi.xcertplay.airplay.MicrophoneConfig
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The microphone format rule ported from xcertplay 1.4.2: every negotiated rate is capturable and
 * only the AAC-ELD encoder is mono-only.
 */
class MicrophoneFormatTest {
    @Test
    fun everyMonophonicEldFormatTheAppAdvertisesCanBeCaptured() {
        // ELD mono bits: 16 kHz, 24 kHz, 44.1 kHz, 48 kHz, 32 kHz.
        val monoBits = listOf(1L shl 26, 1L shl 27, 1L shl 31, 1L shl 32, 1L shl 43)
        for (bits in monoBits) {
            val format = AudioStreamCodec.fromFormatBits(bits, payloadType = 100, audioType = "speechrecognition")
            assertEquals("bits=$bits", AudioCodecKind.AAC_ELD, format.codec)
            assertEquals("bits=$bits", 1, format.channels)
            assertNull("AAC-ELD ${format.sampleRate} Hz", MicrophoneFormat.rejection(format.codec, format.channels))
        }
    }

    @Test
    fun stereoEldIsRefusedUpFrontInsteadOfFailingDuringCapture() {
        for (bits in listOf(1L shl 24, 1L shl 25)) {
            val format = AudioStreamCodec.fromFormatBits(bits, payloadType = 100, audioType = "default")
            assertEquals(2, format.channels)
            assertNotNull("bits=$bits", MicrophoneFormat.rejection(format.codec, format.channels))
        }
    }

    @Test
    fun lpcmAndOpusKeepTheNegotiatedChannelCount() {
        assertNull(MicrophoneFormat.rejection(AudioCodecKind.LPCM, channels = 2))
        assertNull(MicrophoneFormat.rejection(AudioCodecKind.OPUS, channels = 1))
    }

    @Test
    fun opusTimestampFollowsTheNegotiatedClockWhileCaptureStaysAt48k() {
        val config = MicrophoneConfig(
            "speechrecognition", 48_000, 1, 100, 20,
            InetAddress.getLoopbackAddress(), 1234, ByteArray(32), AudioCodecKind.OPUS,
            opusClockRate = 48_000,
        )
        assertEquals(960, config.samplesPerPacket)
        assertEquals(960, config.rtpSamplesPerPacket)
        assertEquals("24 kHz clock", 480, config.copy(opusClockRate = 24_000).rtpSamplesPerPacket)
        assertEquals("16 kHz clock", 320, config.copy(opusClockRate = 16_000).rtpSamplesPerPacket)
    }
}
