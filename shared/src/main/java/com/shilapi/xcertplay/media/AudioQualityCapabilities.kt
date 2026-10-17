package com.shilapi.xcertplay.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.util.Log
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioQualityProfiles

/**
 * 本机音频能力探测：判断这台设备能否解码并播放 CarPlay 会声明的格式。
 *
 * 三点必须说清楚，避免把探测结果当成硬件规格：
 * 1. 探测通过只表示"本机能用完全相同的参数配置解码器/播放器"。系统混音器可能重采样，
 *    因此不等于硬件以原生采样率直出。设备声明的原生采样率记录在
 *    [Result.nativeOutputRates]，仅用于日志。
 * 2. 编码格式（AAC-LC/AAC-ELD/Opus）一律真实 configure + start 验证，不采信格式声明。
 * 3. 结果按音频设备指纹缓存。设备不变时重复调用只做一次轻量查询，不会重复创建
 *    AudioTrack 或 MediaCodec，所以进入设置页不会因为探测而卡顿。
 */
object AudioQualityCapabilities {
    private const val TAG = "xcertplay-usb"

    /** CarPlay 会用到的采样率。协议上限是 48 kHz。 */
    private val RATES = listOf(8_000, 16_000, 24_000, 32_000, 44_100, 48_000)

    /** 麦克风采集只用 mono，且协议上限 16 kHz。 */
    private val MICROPHONE_RATES = listOf(8_000, 16_000)

    /**
     * 延迟创建：对象初始化不能触碰 Android 音频 API，否则单元测试连构造 [Result] 都做不到。
     */
    private fun mediaAttributes(): AudioAttributes =
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()

    data class Result(
        private val supported: Map<Pair<AudioCodecKind, Int>, List<Int>>,
        /** 本机能用 AAC-ELD 编码 16 kHz mono 上行麦克风的采样率。 */
        val eldMicrophoneRates: List<Int>,
        /** 本机能以 mono 采集的采样率；只查询最小缓冲区，不真正打开采集设备。 */
        val microphoneCaptureRates: List<Int>,
        /** 已通过实际解码器配置验证的 ELD 帧长。 */
        val eldFramesPerPacket: Set<Int>,
        /** 输出设备声明的原生采样率，仅用于日志；空集合表示设备未声明。 */
        val nativeOutputRates: Set<Int>,
        /** 非空表示探测本身失败（例如音频服务异常），与"本机不支持"是两回事。 */
        val failure: String? = null,
    ) {
        /**
         * Opus 的采样率必须按 48 kHz 解码时钟判断，而不是按手机选择的编码采样率。
         * 现有播放管线固定以 48 kHz mono 配置 Opus 解码器，因此只要它能工作，
         * 协议里的 16/24/48 kHz 编码选项都可用。
         */
        fun rates(codec: AudioCodecKind, channels: Int): List<Int> = when (codec) {
            AudioCodecKind.OPUS ->
                if (channels == 1 && 48_000 in supported[AudioCodecKind.OPUS to 1].orEmpty()) {
                    listOf(16_000, 24_000, 48_000)
                } else {
                    emptyList()
                }
            else -> supported[codec to channels].orEmpty()
        }

        /** 关闭 Opus 后改用 AAC-ELD 是否真的能工作：下行要能解码，上行要能编码。 */
        val compatibleModeAvailable: Boolean
            get() {
                if (failure != null) return false
                // 480 是协议默认帧长；512 只在 SETUP 明确要求时出现。
                if (AacEldFormat.DEFAULT_FRAMES_PER_PACKET !in eldFramesPerPacket) return false
                if (16_000 !in eldMicrophoneRates) return false
                val mono = rates(AudioCodecKind.AAC_ELD, 1)
                return 16_000 in mono && (44_100 in mono || 48_000 in mono)
            }

        /**
         * 精确的可用格式集合，用于避免向手机声明本机其实放不了的格式。
         * 探测失败时返回 null，表示"没有依据可以收窄声明"，调用方应保持协议默认声明，
         * 而不是把全部格式都砍掉导致完全没有声音。
         */
        val allowedFormatBits: Set<Long>? by lazy {
            if (failure != null) return@lazy null
            buildSet {
                for ((index, rate) in RATES.withIndex()) for (channels in 1..2) {
                    // bit 12/13 是 44.1 kHz 24bit，不属于本管线；48 kHz 从 bit 14 开始。
                    val bit = if (index == 5) 13 + channels else 1 + index * 2 + channels
                    if (rate in rates(AudioCodecKind.LPCM, channels)) add(1L shl bit)
                }
                for ((bit, rate, channels) in listOf(
                    Triple(22, 44_100, 2), Triple(23, 48_000, 2),
                    Triple(24, 44_100, 2), Triple(25, 48_000, 2),
                    Triple(26, 16_000, 1), Triple(27, 24_000, 1),
                    Triple(28, 16_000, 1), Triple(29, 24_000, 1), Triple(30, 48_000, 1),
                    Triple(31, 44_100, 1), Triple(32, 48_000, 1), Triple(43, 32_000, 1),
                )) {
                    val codec = when (bit) {
                        22, 23 -> AudioCodecKind.AAC_LC
                        28, 29, 30 -> AudioCodecKind.OPUS
                        else -> AudioCodecKind.AAC_ELD
                    }
                    if (rate in rates(codec, channels)) add(1L shl bit)
                }
            }
        }
    }

    private data class Cached(val fingerprint: String, val result: Result)

    /** 只保存纯数据，不持有 Context，避免泄漏 Activity。 */
    @Volatile private var cached: Cached? = null

    /** 保证同一时刻只有一次真实探测；等待者复用它的结果。 */
    private val probeLock = Any()

    /** 已缓存的能力，未探测时返回 null。可在主线程安全调用。 */
    fun cached(): Result? = cached?.result

    fun clearCache() {
        cached = null
    }

    /**
     * 返回本机能力。设备未变化时直接命中缓存，只有变化时才真正重新探测。
     * 需要在 IO 线程调用：首次探测会创建 AudioTrack 和 MediaCodec。
     */
    fun probe(context: Context, force: Boolean = false): Result {
        val audio = context.applicationContext.getSystemService(AudioManager::class.java)
        val outputs = outputDevices(audio)
        val fingerprint = fingerprint(outputs)
        if (!force) cached?.let { if (it.fingerprint == fingerprint) return it.result }
        synchronized(probeLock) {
            if (!force) cached?.let { if (it.fingerprint == fingerprint) return it.result }
            val startedAt = System.nanoTime()
            val result = runCatching { detect(outputs) }.getOrElse { error ->
                Log.w(TAG, "audio quality probe failed", error)
                Result(emptyMap(), emptyList(), emptyList(), emptySet(), emptySet(),
                    error.message ?: error.javaClass.simpleName)
            }
            Log.i(
                TAG,
                "audio quality probe in ${(System.nanoTime() - startedAt) / 1_000_000}ms " +
                    "supported=${result.ratesSummary()} " +
                    "eldMic=${result.eldMicrophoneRates} eldFrames=${result.eldFramesPerPacket} " +
                    "nativeRates=${result.nativeOutputRates} failure=${result.failure}",
            )
            // 探测失败不写缓存：下次进入设置页应重试，而不是永久禁用兼容模式。
            if (result.failure == null) cached = Cached(fingerprint, result)
            return result
        }
    }

    private fun outputDevices(audio: AudioManager?): List<AudioDeviceInfo> =
        runCatching { audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList().orEmpty() }
            .getOrDefault(emptyList())

    /** 设备指纹；输出设备不变时不需要重新探测。 */
    private fun fingerprint(outputs: List<AudioDeviceInfo>): String = outputs.joinToString("|") { device ->
        "${device.id}:${device.type}:" +
            "${device.sampleRates.joinToString(",")}:" +
            device.channelCounts.joinToString(",")
    }

    private fun detect(outputs: List<AudioDeviceInfo>): Result {
        // 候选列表只取一次；格式构造与解码器选择和播放端共用 [AudioDecoders]，
        // 所以这里验证通过的组合，播放时会以完全相同的参数、同样的顺序打开。
        val candidates = listOf(AudioCodecKind.AAC_LC, AudioCodecKind.AAC_ELD, AudioCodecKind.OPUS)
            .associateWith { AudioDecoders.candidates(it) }
        fun decodes(codec: AudioCodecKind, rate: Int, channels: Int, frames: Int? = null): Boolean {
            val names = candidates[codec].orEmpty()
            if (names.isEmpty()) return false
            val format = runCatching { AudioDecoders.format(codec, rate, channels, frames) }.getOrNull() ?: return false
            return AudioDecoders.canOpen(format, names)
        }

        val supported = mutableMapOf<Pair<AudioCodecKind, Int>, List<Int>>()
        val nativeRates = nativeOutputRates(outputs)

        // LPCM 走 AudioTrack 初始化，设备已声明原生支持该采样率时直接判定通过，不再开一次输出。
        for (channels in 1..2) {
            supported[AudioCodecKind.LPCM to channels] = RATES.filter { canOpenTrack(it, channels, nativeRates) }
        }

        // 编码格式一律真实 configure + start：解码器声明的 profile 和采样率与实际能收的码流
        // 经常不一致，只查声明会把放不了的格式报成可用，也会把能放的报成不可用。
        // 验证范围来自 [AudioQualityProfiles]，与向手机声明的组合严格一致。
        verifiedRates(AudioCodecKind.AAC_LC) { rate, channels -> decodes(AudioCodecKind.AAC_LC, rate, channels) }
            .forEach { (channels, rates) -> supported[AudioCodecKind.AAC_LC to channels] = rates }

        // Opus 播放管线固定 48 kHz mono，16/24 kHz 只是手机侧的编码选项，解码时钟不变。
        val opusReady = decodes(AudioCodecKind.OPUS, OpusFormat.OUTPUT_RATE, OpusFormat.OUTPUT_CHANNELS)
        supported[AudioCodecKind.OPUS to 1] = if (opusReady) listOf(OpusFormat.OUTPUT_RATE) else emptyList()
        supported[AudioCodecKind.OPUS to 2] = emptyList()

        val verifiedFrames = mutableSetOf<Int>()
        verifiedRates(AudioCodecKind.AAC_ELD) { rate, channels ->
            ELD_FRAME_LENGTHS.any { frame ->
                decodes(AudioCodecKind.AAC_ELD, rate, channels, frame).also { ok -> if (ok) verifiedFrames += frame }
            }
        }.forEach { (channels, rates) -> supported[AudioCodecKind.AAC_ELD to channels] = rates }

        return Result(
            supported = supported,
            eldMicrophoneRates = listOf(16_000).filter { AacEldEncoder.supports(it) },
            microphoneCaptureRates = microphoneCaptureRates(),
            eldFramesPerPacket = verifiedFrames,
            nativeOutputRates = nativeRates,
        )
    }

    /** 按协议表逐项真实验证，返回「声道数 -> 实测可用采样率」。 */
    private fun verifiedRates(
        codec: AudioCodecKind,
        test: (rate: Int, channels: Int) -> Boolean,
    ): Map<Int, List<Int>> = AudioQualityProfiles.formatsFor(codec)
        .groupBy({ it.second }, { it.first })
        .mapValues { (channels, rates) ->
            rates.distinct().sorted().filter { rate -> test(rate, channels) }
        }

    private val ELD_FRAME_LENGTHS = listOf(AacEldFormat.DEFAULT_FRAMES_PER_PACKET, 512)

    private fun Result.ratesSummary(): Map<AudioCodecKind, Map<Int, List<Int>>> =
        AudioCodecKind.entries.associateWith { codec -> (1..2).associateWith { rates(codec, it) } }

    // ---------------------------------------------------------------- 播放能力

    /** 设备声明的原生采样率。空数组按 Android 语义表示"任意"，因此这里不做判定。 */
    private fun nativeOutputRates(outputs: List<AudioDeviceInfo>): Set<Int> =
        outputs.flatMapTo(mutableSetOf()) { it.sampleRates.toList() }

    /**
     * 播放管线用的是 16bit PCM AudioTrack，这里按同样方式验证能否初始化。
     * 只表示本机能播，不表示硬件以该采样率原生输出。
     */
    private fun canOpenTrack(rate: Int, channels: Int, nativeRates: Set<Int>): Boolean {
        if (rate in nativeRates) return true
        return runCatching {
            val mask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            val minimum = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT)
            if (minimum <= 0) return@runCatching false
            val track = AudioTrack.Builder()
                .setAudioAttributes(mediaAttributes())
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(mask)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(minimum * 2)
                .build()
            try {
                track.state == AudioTrack.STATE_INITIALIZED
            } finally {
                track.release()
            }
        }.getOrDefault(false)
    }

    // ---------------------------------------------------------------- 采集能力

    /**
     * 只查询最小缓冲区大小，不真正打开 AudioRecord：
     * 打开采集设备需要录音权限，且容易因为占用而失败，不适合在设置页探测。
     */
    private fun microphoneCaptureRates(): List<Int> = MICROPHONE_RATES.filter { rate ->
        runCatching {
            AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ) > 0
        }.getOrDefault(false)
    }
}
