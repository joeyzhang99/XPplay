package com.shilapi.xcertplay.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import com.shilapi.xcertplay.airplay.AudioCodecKind
import java.nio.ByteBuffer

/**
 * 音频解码器的格式构造与实例选择，能力探测和实际播放共用。
 *
 * 两边必须走同一条路径：探测用 A 解码器、播放用 B 解码器，或者两边 MediaFormat
 * 差一个字段，都会出现"探测说能放、播放时配不起来"。
 */
object AudioDecoders {
    private const val MAX_INPUT_SIZE = 64 * 1024

    fun mime(codec: AudioCodecKind): String? = when (codec) {
        AudioCodecKind.AAC_LC, AudioCodecKind.AAC_ELD -> MediaFormat.MIMETYPE_AUDIO_AAC
        AudioCodecKind.OPUS -> MediaFormat.MIMETYPE_AUDIO_OPUS
        AudioCodecKind.LPCM -> null
    }

    fun format(codec: AudioCodecKind, sampleRate: Int, channels: Int, framesPerPacket: Int? = null): MediaFormat {
        val mime = requireNotNull(mime(codec)) { "$codec 不需要解码器" }
        return MediaFormat.createAudioFormat(mime, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)
            when (codec) {
                AudioCodecKind.AAC_LC -> {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_IS_ADTS, 1)
                    setByteBuffer("csd-0", ByteBuffer.wrap(AacLcFormat.audioSpecificConfig(sampleRate, channels)))
                }
                AudioCodecKind.AAC_ELD -> {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectELD)
                    setInteger(MediaFormat.KEY_IS_ADTS, 0)
                    val frames = framesPerPacket ?: AacEldFormat.DEFAULT_FRAMES_PER_PACKET
                    setByteBuffer("csd-0", ByteBuffer.wrap(AacEldFormat.audioSpecificConfig(sampleRate, channels, frames)))
                }
                AudioCodecKind.OPUS -> {
                    setByteBuffer("csd-0", ByteBuffer.wrap(OpusFormat.head(channels, sampleRate)))
                    setByteBuffer("csd-1", ByteBuffer.wrap(OpusFormat.codecDelay()))
                    setByteBuffer("csd-2", ByteBuffer.wrap(OpusFormat.seekPreRoll()))
                }
                AudioCodecKind.LPCM -> Unit
            }
        }
    }

    /**
     * 该编码的候选解码器名，按系统列表顺序。ELD 只保留声明了 ELD profile 的，
     * 其余全部保留：声明不可靠，是否真能用交给 [open] 实测。
     */
    fun candidates(codec: AudioCodecKind): List<String> {
        val mime = mime(codec) ?: return emptyList()
        val infos = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos }
            .getOrDefault(emptyArray())
        return infos.filter { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                (codec != AudioCodecKind.AAC_ELD || runCatching {
                    info.getCapabilitiesForType(mime).profileLevels
                        .any { it.profile == MediaCodecInfo.CodecProfileLevel.AACObjectELD }
                }.getOrDefault(false))
        }.map { it.name }
    }

    /** 依次尝试候选，返回第一个真正 configure + start 成功的解码器；都失败则抛出最后一个错误。 */
    fun open(format: MediaFormat, candidates: List<String>): Pair<MediaCodec, String> {
        var lastError: Exception? = null
        for (name in candidates) {
            var codec: MediaCodec? = null
            try {
                codec = MediaCodec.createByCodecName(name)
                codec.configure(format, null, null, 0)
                codec.start()
                return codec to name
            } catch (error: Exception) {
                lastError = error
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
            }
        }
        throw IllegalStateException("没有能配置 ${format.getString(MediaFormat.KEY_MIME)} 的解码器", lastError)
    }

    /** 与 [open] 同一路径的探测版本，用完立即释放，避免反复探测耗尽解码器实例。 */
    fun canOpen(format: MediaFormat, candidates: List<String>): Boolean = runCatching {
        val (codec, _) = open(format, candidates)
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }.isSuccess
}
