package com.shilapi.xcertplay.media

/** MPEG-4 AudioSpecificConfig：AAC-ELD，无 SBR、无错误恢复扩展。 */
object AacEldFormat {
    const val DEFAULT_FRAMES_PER_PACKET = 480

    data class Description(val sampleRate: Int, val channels: Int, val framesPerPacket: Int)

    private val rates = listOf(96_000, 88_200, 64_000, 48_000, 44_100, 32_000,
        24_000, 22_050, 16_000, 12_000, 11_025, 8_000, 7_350)

    fun audioSpecificConfig(sampleRate: Int, channels: Int, framesPerPacket: Int = DEFAULT_FRAMES_PER_PACKET): ByteArray {
        require(sampleRate in rates && channels in 1..2)
        require(framesPerPacket == 480 || framesPerPacket == 512)
        val bits = "11111" + "000111" + rates.indexOf(sampleRate).toString(2).padStart(4, '0') +
            channels.toString(2).padStart(4, '0') + (if (framesPerPacket == 480) "1" else "0") +
            "000" + "0" + "0000"
        return bits.padEnd((bits.length + 7) / 8 * 8, '0').chunked(8)
            .map { it.toInt(2).toByte() }.toByteArray()
    }

    /** 只接受能够直接放进 CarPlay RTP 的基础 ELD，避免误发 LC 或 SBR 码流。 */
    fun parse(bytes: ByteArray): Description? = runCatching {
        var cursor = 0
        fun read(count: Int): Int {
            require(cursor + count <= bytes.size * 8)
            var value = 0
            repeat(count) {
                value = (value shl 1) or ((bytes[cursor / 8].toInt() ushr (7 - cursor % 8)) and 1)
                cursor++
            }
            return value
        }
        var objectType = read(5)
        if (objectType == 31) objectType = 32 + read(6)
        require(objectType == 39)
        val frequency = read(4)
        val sampleRate = if (frequency == 15) read(24) else rates.getOrNull(frequency) ?: error("Invalid frequency")
        val channels = read(4)
        require(channels in 1..2)
        val samples = if (read(1) == 1) 480 else 512
        require(read(3) == 0 && read(1) == 0 && read(4) == 0)
        Description(sampleRate, channels, samples)
    }.getOrNull()
}
