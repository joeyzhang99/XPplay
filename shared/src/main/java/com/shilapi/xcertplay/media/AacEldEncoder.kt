package com.shilapi.xcertplay.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import java.io.Closeable

/** 编码器须实际输出与协商帧长一致的 ELD；仅声明 profile 支持并不足够。 */
class AacEldEncoder private constructor(
    private val codec: MediaCodec,
    private val sampleRate: Int,
    private val framesPerPacket: Int,
) : Closeable {
    private val info = MediaCodec.BufferInfo()
    private var samplesSubmitted = 0L
    private var verified = false
    private var closed = false

    @Synchronized
    fun encode(pcm: ByteArray): List<ByteArray> {
        check(!closed)
        require(pcm.size == framesPerPacket * 2)
        val index = codec.dequeueInputBuffer(10_000)
        // 编码器偶尔一时腾不出输入缓冲：丢这一帧，别让异常把整条麦克风上行线程带走。
        if (index < 0) return drain()
        val input = checkNotNull(codec.getInputBuffer(index))
        input.clear()
        input.put(pcm)
        codec.queueInputBuffer(index, 0, pcm.size, samplesSubmitted * 1_000_000L / sampleRate, 0)
        samplesSubmitted += framesPerPacket
        return drain()
    }

    private fun drain(): List<ByteArray> {
        val packets = ArrayList<ByteArray>()
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val csd = checkNotNull(codec.outputFormat.getByteBuffer("csd-0")).duplicate()
                    verify(ByteArray(csd.remaining()).also(csd::get))
                }
                outputIndex >= 0 -> {
                    try {
                        val buffer = checkNotNull(codec.getOutputBuffer(outputIndex)).duplicate()
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val bytes = ByteArray(info.size).also(buffer::get)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            verify(bytes)
                        } else if (bytes.isNotEmpty()) {
                            check(verified) { "AAC-ELD encoder did not provide a valid AudioSpecificConfig" }
                            packets += bytes
                        }
                    } finally {
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
                else -> return packets
            }
        }
    }

    private fun verify(bytes: ByteArray) {
        val description = AacEldFormat.parse(bytes)
        check(description == AacEldFormat.Description(sampleRate, 1, framesPerPacket)) {
            "AAC-ELD encoder produced an incompatible configuration: $description"
        }
        verified = true
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        try { codec.stop() } catch (_: Exception) { }
        codec.release()
    }

    companion object {
        fun create(sampleRate: Int, framesPerPacket: Int = AacEldFormat.DEFAULT_FRAMES_PER_PACKET,
                   bitrate: Int = 48_000): AacEldEncoder {
            var lastError: Exception? = null
            for (entry in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
                if (!entry.isEncoder || MediaFormat.MIMETYPE_AUDIO_AAC !in entry.supportedTypes) continue
                var codec: MediaCodec? = null
                try {
                    val capabilities = entry.getCapabilitiesForType(MediaFormat.MIMETYPE_AUDIO_AAC)
                    if (capabilities.profileLevels.none { it.profile == MediaCodecInfo.CodecProfileLevel.AACObjectELD }) continue
                    val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectELD)
                        setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, framesPerPacket * 2)
                    }
                    codec = MediaCodec.createByCodecName(entry.name)
                    codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    codec.start()
                    return AacEldEncoder(codec, sampleRate, framesPerPacket)
                } catch (error: Exception) {
                    lastError = error
                    try { codec?.stop() } catch (_: Exception) { }
                    try { codec?.release() } catch (_: Exception) { }
                }
            }
            throw IllegalStateException("No compatible AAC-ELD encoder", lastError)
        }

        fun supports(sampleRate: Int, framesPerPacket: Int = AacEldFormat.DEFAULT_FRAMES_PER_PACKET): Boolean =
            runCatching {
                create(sampleRate, framesPerPacket).use { encoder ->
                    var produced = false
                    // 探测使用独立实例，避免运行时发送预热残留的静音包。
                    repeat(8) {
                        if (encoder.encode(ByteArray(framesPerPacket * 2)).isNotEmpty()) produced = true
                    }
                    check(produced) { "AAC-ELD encoder produced no packets" }
                }
            }.isSuccess
    }
}
