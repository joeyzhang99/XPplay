package com.shilapi.xcertplay.media

/** MPEG-4 AudioSpecificConfig：AAC-LC，播放端配置与能力探测共用同一份字节。 */
object AacLcFormat {
    private const val OBJECT_TYPE_LC = 2

    fun audioSpecificConfig(sampleRate: Int, channels: Int): ByteArray {
        val frequencyIndex = MediaCodecSupport.aacFrequencyIndex(sampleRate)
        val value = (OBJECT_TYPE_LC shl 11) or (frequencyIndex shl 7) or (channels.coerceIn(1, 7) shl 3)
        return byteArrayOf((value ushr 8).toByte(), value.toByte())
    }
}
