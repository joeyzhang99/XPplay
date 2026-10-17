package com.shilapi.xcertplay.airplay

/** 向手机声明的音频质量上限，实际格式由手机协商选择。 */
data class AudioQualitySettings(
    val opusEnabled: Boolean = true,
    val music: AudioQualityLimit = AudioQualityLimit(48_000, 2),
    val assistant: AudioQualityLimit = AudioQualityLimit(48_000, 1),
    val navigation: AudioQualityLimit = AudioQualityLimit(48_000, 2),
    val phone: AudioQualityLimit = AudioQualityLimit(24_000, 1),
    val microphone: AudioQualityLimit = AudioQualityLimit(16_000, 1),
)

data class AudioQualityLimit(val sampleRate: Int, val channels: Int)
