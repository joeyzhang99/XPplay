package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioStreamCodec
import com.shilapi.xcertplay.airplay.MicrophoneConfig
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AacEldFormatTest {
    @Test
    fun eldConfigRetainsRateChannelsAndBothPacketSizes() {
        for (rate in listOf(16_000, 24_000, 32_000, 44_100, 48_000)) {
            for (channels in 1..2) {
                for (samples in listOf(480, 512)) {
                    assertEquals(AacEldFormat.Description(rate, channels, samples),
                        AacEldFormat.parse(AacEldFormat.audioSpecificConfig(rate, channels, samples)))
                }
            }
        }
    }

    @Test
    fun rejectsLcTruncatedAndSbrConfigurations() {
        assertNull(AacEldFormat.parse(byteArrayOf(0x12, 0x10)))
        assertNull(AacEldFormat.parse(byteArrayOf(0xf8.toByte())))
        val sbr = AacEldFormat.audioSpecificConfig(16_000, 1).also {
            it[2] = (it[2].toInt() or 1).toByte()
        }
        assertNull(AacEldFormat.parse(sbr))
    }

    @Test
    fun extendedEldBitsDoNotTruncateOrMatchCombinedFormats() {
        val mono48 = AudioStreamCodec.fromFormatBits(1L shl 32, 100)
        assertEquals(AudioCodecKind.AAC_ELD, mono48.codec)
        assertEquals(48_000, mono48.sampleRate)
        assertEquals(1, mono48.channels)
        val mono32 = AudioStreamCodec.fromFormatBits(1L shl 43, 100)
        assertEquals(AudioCodecKind.AAC_ELD, mono32.codec)
        assertEquals(32_000, mono32.sampleRate)
        assertEquals(AudioCodecKind.LPCM,
            AudioStreamCodec.fromFormatBits((1L shl 32) or (1L shl 26), 100).codec)
    }

    @Test
    fun exactPacketSamplesAvoidMillisecondRoundingForEld() {
        val config = MicrophoneConfig("default", 16_000, 1, 100, 32,
            InetAddress.getLoopbackAddress(), 1234, ByteArray(32),
            AudioCodecKind.AAC_ELD, 48_000, 512)
        assertEquals(512, config.samplesPerPacket)
        assertEquals(512, config.rtpSamplesPerPacket)
        assertEquals(1024, config.frameBytes)
        val opus = config.copy(codec = AudioCodecKind.OPUS, frameMillis = 20, framesPerPacket = null)
        assertEquals(960, opus.rtpSamplesPerPacket)
    }
}
