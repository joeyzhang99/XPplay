package com.shilapi.xcertplay.media

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * CarPlay Opus 解码所需的 CSD。
 *
 * 能力探测和实际播放共用这一份构造，避免"探测用一套、运行用另一套"导致
 * 探测通过但运行时配置失败。
 */
object OpusFormat {
    /** CarPlay 协商的 Opus 输出始终是 48 kHz 单声道。 */
    const val OUTPUT_RATE = 48_000
    const val OUTPUT_CHANNELS = 1

    private const val CODEC_DELAY_NANOS = 6_500_000L
    private const val SEEK_PRE_ROLL_NANOS = 80_000_000L

    fun head(channels: Int = OUTPUT_CHANNELS, sampleRate: Int = OUTPUT_RATE): ByteArray {
        val head = ByteArray(19)
        "OpusHead".toByteArray(Charsets.US_ASCII).copyInto(head, 0)
        head[8] = 1
        head[9] = channels.toByte()
        head[10] = 0x38
        head[11] = 0x01
        head[12] = sampleRate.toByte()
        head[13] = (sampleRate ushr 8).toByte()
        head[14] = (sampleRate ushr 16).toByte()
        head[15] = (sampleRate ushr 24).toByte()
        return head
    }

    fun codecDelay(): ByteArray = longLittleEndian(CODEC_DELAY_NANOS)

    fun seekPreRoll(): ByteArray = longLittleEndian(SEEK_PRE_ROLL_NANOS)

    private fun longLittleEndian(value: Long): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
}
