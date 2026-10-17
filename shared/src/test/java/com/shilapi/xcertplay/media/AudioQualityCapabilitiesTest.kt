package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.AudioCodecKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQualityCapabilitiesTest {
    private fun result(
        supported: Map<Pair<AudioCodecKind, Int>, List<Int>> = emptyMap(),
        eldMicrophoneRates: List<Int> = emptyList(),
        eldFrames: Set<Int> = emptySet(),
        failure: String? = null,
    ) = AudioQualityCapabilities.Result(supported, eldMicrophoneRates, emptyList(), eldFrames, emptySet(), failure)

    @Test
    fun opusIsGatedByItsDecoderNotByThePhoneEncoderRate() {
        // Opus 播放管线固定 48 kHz mono 解码，因此只要它可用，协议里 16/24/48 kHz 都可选。
        val caps = result(mapOf((AudioCodecKind.OPUS to 1) to listOf(48_000)))
        assertEquals(listOf(16_000, 24_000, 48_000), caps.rates(AudioCodecKind.OPUS, 1))
        assertTrue(caps.allowedFormatBits!!.containsAll(listOf(1L shl 28, 1L shl 29, 1L shl 30)))
        assertTrue(caps.rates(AudioCodecKind.OPUS, 2).isEmpty())
    }

    @Test
    fun missingOpusDecoderDoesNotAdvertiseOpus() {
        val caps = result()
        assertTrue(caps.rates(AudioCodecKind.OPUS, 1).isEmpty())
        assertFalse(caps.allowedFormatBits!!.contains(1L shl 28))
    }

    @Test
    fun compatibleModeNeedsBothDirectionsAndTheDefaultFrameLength() {
        val decoders = mapOf(
            (AudioCodecKind.AAC_ELD to 1) to listOf(16_000, 44_100, 48_000),
            (AudioCodecKind.AAC_ELD to 2) to listOf(44_100, 48_000),
        )
        assertTrue(result(decoders, listOf(16_000), setOf(480)).compatibleModeAvailable)
        // 只有 512 帧长时协议默认的 480 无法工作，不能算可用。
        assertFalse(result(decoders, listOf(16_000), setOf(512)).compatibleModeAvailable)
        // 上行编码器不可用。
        assertFalse(result(decoders, emptyList(), setOf(480)).compatibleModeAvailable)
        // 只有 24 kHz 解码，缺少语音所需的 44.1/48。
        val narrow = mapOf((AudioCodecKind.AAC_ELD to 1) to listOf(24_000))
        assertFalse(result(narrow, listOf(16_000), setOf(480)).compatibleModeAvailable)
    }

    @Test
    fun probeFailureMustNotBeReportedAsUnsupported() {
        val caps = result(failure = "AudioService unavailable")
        // 失败时没有依据收窄声明，返回 null 让调用方保持协议默认，避免整条音频被砍掉。
        assertNull(caps.allowedFormatBits)
        assertFalse(caps.compatibleModeAvailable)
    }

    @Test
    fun formatBitsCoverMultiBitFormatsWithoutIntegerTruncation() {
        val caps = result(
            mapOf(
                (AudioCodecKind.AAC_ELD to 1) to listOf(16_000, 32_000, 44_100, 48_000),
                (AudioCodecKind.LPCM to 2) to listOf(44_100, 48_000),
            ),
        )
        val bits = caps.allowedFormatBits!!
        assertTrue(bits.contains(1L shl 32)) // ELD 48 kHz mono 落在 32 位以上
        assertTrue(bits.contains(1L shl 43)) // ELD 32 kHz mono
        assertTrue(bits.contains(1L shl 15)) // PCM 48 kHz stereo
        assertFalse(bits.contains(1L shl 14)) // PCM 48 kHz mono 未声明
    }
}
