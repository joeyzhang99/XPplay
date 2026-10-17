package com.shilapi.xcertplay.airplay

/**
 * 生成 /info 的 audioFormats 声明。
 *
 * 这里声明的是"本接收端能处理的格式上限"，手机仍会按 audioType 和传输方式再筛一遍，
 * 所以不需要在这里复刻手机的每路掩码。用户设置只作为上限，绝不承诺最终协商结果。
 */
object CarPlayAudioFormats {
    private class Format(val bit: Int, val codec: AudioCodecKind, val rate: Int, val channels: Int)

    /** bit 序号即 APAudioFormatIndex，与 iOS 固件 AirPlaySupport 的表一致。 */
    private val FORMATS: List<Format> = buildList {
        for ((index, rate) in listOf(8_000, 16_000, 24_000, 32_000, 44_100).withIndex()) {
            add(Format(2 + index * 2, AudioCodecKind.LPCM, rate, 1))
            add(Format(3 + index * 2, AudioCodecKind.LPCM, rate, 2))
        }
        // bit 12/13 是 44.1 kHz 24bit，本管线不产出，因此 48 kHz 从 bit 14 开始。
        add(Format(14, AudioCodecKind.LPCM, 48_000, 1))
        add(Format(15, AudioCodecKind.LPCM, 48_000, 2))

        add(Format(22, AudioCodecKind.AAC_LC, 44_100, 2))
        add(Format(23, AudioCodecKind.AAC_LC, 48_000, 2))

        add(Format(28, AudioCodecKind.OPUS, 16_000, 1))
        add(Format(29, AudioCodecKind.OPUS, 24_000, 1))
        add(Format(30, AudioCodecKind.OPUS, 48_000, 1))

        add(Format(24, AudioCodecKind.AAC_ELD, 44_100, 2))
        add(Format(25, AudioCodecKind.AAC_ELD, 48_000, 2))
        add(Format(26, AudioCodecKind.AAC_ELD, 16_000, 1))
        add(Format(27, AudioCodecKind.AAC_ELD, 24_000, 1))
        add(Format(31, AudioCodecKind.AAC_ELD, 44_100, 1))
        add(Format(32, AudioCodecKind.AAC_ELD, 48_000, 1))
        add(Format(43, AudioCodecKind.AAC_ELD, 32_000, 1))
    }

    fun build(
        quality: AudioQualitySettings,
        microphone: Boolean,
        wireless: Boolean,
        supportedOutput: Set<Long>? = null,
        /** DiPlay: whether Opus may be offered for the microphone uplink. */
        microphoneOpus: Boolean = true,
        /** DiPlay: iOS 27's buffered music stream, which opened only with AAC-LC. */
        mainBuffered: Boolean = false,
        /** DiPlay: drop the 8 and 16 kHz PCM speech formats for wide-compatibility head units. */
        speechWithout8And16Khz: Boolean = false,
    ): List<Map<String, Any?>> {
        val opus = quality.opusEnabled
        val allowed = supportedOutput?.fold(0L) { bits, bit -> bits or bit }

        fun mask(
            codec: AudioCodecKind,
            routes: List<AudioQualityProfiles.Route>,
            limit: AudioQualityLimit,
            narrow: Boolean = true,
        ): Long {
            val channels = routes.flatMap { AudioQualityProfiles.channels(it, wireless, opus) }.toSet()
            val bits = FORMATS.filter { format ->
                format.codec == codec &&
                    format.channels in channels &&
                    format.channels <= limit.channels &&
                    format.rate <= limit.sampleRate &&
                    routes.any { format.rate in AudioQualityProfiles.rates(it, wireless, opus) }
            }.fold(0L) { acc, format -> acc or (1L shl format.bit) }
            if (allowed == null || !narrow) return bits
            // 探测只能收窄、不能清空：整路都被判为不可用时，声明 0 等于让这一路必然没声音，
            // 而按原样声明至少还有机会（探测本身也可能误判）。
            return (bits and allowed).takeIf { it != 0L } ?: bits
        }

        val voiceCodec = AudioQualityProfiles.codec(AudioQualityProfiles.Route.ASSISTANT, wireless, opus)
        val musicCodec = AudioQualityProfiles.codec(AudioQualityProfiles.Route.MUSIC, wireless, opus)
        val assistant = AudioQualityProfiles.Route.ASSISTANT
        val navigation = AudioQualityProfiles.Route.NAVIGATION
        val phone = AudioQualityProfiles.Route.PHONE

        val speechMask = if (speechWithout8And16Khz) 0x3cL.inv() else -1L
        val assistantBits = mask(voiceCodec, listOf(assistant), quality.assistant) and speechMask
        val navigationBits = mask(voiceCodec, listOf(navigation), quality.navigation) and speechMask
        val phoneBits = mask(voiceCodec, listOf(phone), quality.phone) and speechMask
        // 媒体路同时给 PCM(100) 和 AAC(102)，由手机按传输方式选择。
        val musicPcm = mask(AudioCodecKind.LPCM, listOf(AudioQualityProfiles.Route.MUSIC), quality.music)
        val musicAac = mask(AudioCodecKind.AAC_LC, listOf(AudioQualityProfiles.Route.MUSIC), quality.music)
        // supportedOutput 是下行解码能力，与麦克风上行无关；上行能力已在设置页按采集/编码实测收窄。
        val microphoneBits = if (!microphone) null else mask(
            if (opus && microphoneOpus) voiceCodec
            else AudioQualityProfiles.codec(AudioQualityProfiles.Route.MICROPHONE, wireless, false),
            listOf(AudioQualityProfiles.Route.MICROPHONE),
            AudioQualityLimit(
                quality.microphone.sampleRate.coerceAtMost(AudioQualityProfiles.MICROPHONE_MAX_RATE),
                AudioQualityProfiles.MICROPHONE_CHANNELS,
            ),
            narrow = false,
        )

        fun entry(type: Int, audioType: String, output: Long, input: Long? = null): Map<String, Any?> =
            linkedMapOf<String, Any?>("type" to type, "audioType" to audioType, "audioOutputFormats" to output)
                .apply { if (input != null && input != 0L) put("audioInputFormats", input) }

        return buildList {
            add(entry(100, "compatibility", assistantBits, microphoneBits))
            add(entry(101, "compatibility", navigationBits))
            add(entry(100, "default", assistantBits, microphoneBits))
            add(entry(100, "alert", navigationBits))
            add(entry(100, "media", musicPcm))
            add(entry(100, "telephony", phoneBits, microphoneBits))
            add(entry(100, "speechRecognition", assistantBits, microphoneBits))
            add(entry(101, "default", navigationBits))
            add(entry(102, "media", musicAac))
            if (mainBuffered) add(entry(BufferedAudioStream.STREAM_TYPE, "media", musicAac))
        }
    }
}
