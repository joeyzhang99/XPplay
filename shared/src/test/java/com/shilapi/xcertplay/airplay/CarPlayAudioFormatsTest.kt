package com.shilapi.xcertplay.airplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPlayAudioFormatsTest {
    private fun events102(entries: List<Map<String, Any?>>) =
        entries.single { it["type"] == 102 }["audioOutputFormats"]

    private fun build(
        quality: AudioQualitySettings = AudioQualitySettings(),
        wireless: Boolean = false,
        supported: Set<Long>? = null,
    ) = CarPlayAudioFormats.build(quality, microphone = true, wireless = wireless, supportedOutput = supported)

    /** 同一 audioType 会同时出现在 100 和 101 上，必须按 type + audioType 精确定位。 */
    private fun output(entries: List<Map<String, Any?>>, audioType: String, type: Int = 100) =
        entries.single { it["audioType"] == audioType && it["type"] == type }["audioOutputFormats"] as Long

    private fun inputs(entries: List<Map<String, Any?>>) =
        entries.mapNotNull { it["audioInputFormats"] as? Long }.distinct()

    @Test
    fun wiredVoiceUsesPcmAndMusicKeepsBothStreams() {
        val entries = build()
        assertEquals(0x4540L, output(entries, "default")) // PCM 24/32/44.1/48 kHz mono
        assertEquals(0xcc00L, output(entries, "alert")) // PCM 44.1/48 kHz mono+stereo
        assertEquals(0x8800L, output(entries, "media")) // PCM 44.1/48 kHz stereo
        assertTrue(entries.any { it["type"] == 102 })
    }

    @Test
    fun wirelessVoiceUsesOpusMonoOnlyAndMusicUsesAac() {
        val entries = build(wireless = true)
        assertEquals((1L shl 29) or (1L shl 30), output(entries, "default"))
        assertEquals(1L shl 30, output(entries, "alert"))
        assertEquals((1L shl 22) or (1L shl 23), events102(entries))
        assertEquals(listOf(1L shl 28), inputs(entries))
    }

    @Test
    fun eldReplacesOpusWithHighBitsIntact() {
        val entries = build(AudioQualitySettings(opusEnabled = false), wireless = true)
        val bits = output(entries, "default")
        assertTrue(bits and (1L shl 32) != 0L) // ELD 48 kHz mono
        assertEquals(0L, bits and 0x70000000L) // 不再声明 Opus
        assertEquals(listOf(1L shl 26), inputs(entries)) // 麦克风 ELD 16 kHz mono
    }

    @Test
    fun microphoneNeverExceedsMono16kEvenIfConfiguredHigher() {
        val entries = build(AudioQualitySettings(microphone = AudioQualityLimit(48_000, 2)))
        assertEquals(listOf(0x14L), inputs(entries)) // PCM 8/16 kHz mono
    }

    @Test
    fun userLimitOnlyNarrowsEachRoute() {
        val entries = build(
            AudioQualitySettings(
                music = AudioQualityLimit(48_000, 2),
                assistant = AudioQualityLimit(24_000, 1),
                navigation = AudioQualityLimit(44_100, 1),
                phone = AudioQualityLimit(8_000, 1),
            ),
        )
        assertEquals(0x40L, output(entries, "default")) // 仅 24 kHz mono
        assertEquals(0x400L, output(entries, "alert")) // 仅 44.1 kHz mono
        assertEquals(0x4L, output(entries, "telephony")) // 仅 8 kHz mono
        assertEquals(0x8800L, output(entries, "media")) // 44.1/48 kHz stereo
    }

    @Test
    fun probedCapabilitiesRemoveFormatsTheDeviceCannotPlay() {
        // 本机只有 PCM 44.1 kHz mono/stereo：有交集的路只留交集；
        // default 与实测毫无交集，按原样声明而不是清空。
        val entries = build(supported = setOf(1L shl 10, 1L shl 11))
        assertEquals(1L shl 11, output(entries, "media"))
        assertEquals((1L shl 10) or (1L shl 11), output(entries, "alert"))
        assertEquals(1L shl 10, output(entries, "default"))
    }

    @Test
    fun probeFailureLeavesProtocolDeclarationUntouched() {
        val full = build()
        val unfiltered = build(supported = null)
        assertEquals(full.map { it["audioOutputFormats"] }, unfiltered.map { it["audioOutputFormats"] })
    }

    @Test
    fun everyEntryCarriesTheThreeStreamTypesTheEngineHandles() {
        val types = build().map { it["type"] as Int }.toSet()
        assertEquals(setOf(100, 101, 102), types)
    }

    @Test
    fun musicIsNeverDeclaredMonoAndMicrophoneNeverStereo() {
        val entries = build(AudioQualitySettings(music = AudioQualityLimit(48_000, 1)))
        assertEquals(0L, output(entries, "media")) // 音乐固定 stereo，配置成 mono 不会降级声明
        entries.mapNotNull { it["audioInputFormats"] as? Long }.forEach {
            assertFalse(it and (1L shl 3) != 0L)
        }
    }

    @Test
    fun probeNeverEmptiesARouteItCouldNotVerify() {
        // 只有音乐能用：Opus 一个都没验证通过，语音类也不能因此声明 0。
        val entries = build(wireless = true, supported = setOf(1L shl 22, 1L shl 23))
        assertEquals((1L shl 29) or (1L shl 30), output(entries, "default"))
        assertEquals(1L shl 30, output(entries, "alert"))
    }

    @Test
    fun microphoneIsNotNarrowedByPlaybackCapabilities() {
        // 下行能力里没有任何 PCM 8k/16k mono，有线麦克风声明照旧。
        val entries = build(supported = setOf(1L shl 14, 1L shl 15))
        assertEquals(listOf(0x14L), inputs(entries))
    }
}
