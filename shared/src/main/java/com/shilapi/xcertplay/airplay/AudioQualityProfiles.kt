package com.shilapi.xcertplay.airplay

/**
 * 每一路音频在 CarPlay 协议层允许使用的编码、采样率和声道。
 *
 * 取值来自 iOS 固件里按 audioType 生效的格式掩码；界面选项和 /info 声明共用这张表，
 * 避免两处各写一份导致"能选却发不出去"或"能发却选不了"。
 */
object AudioQualityProfiles {
    enum class Route { MUSIC, ASSISTANT, NAVIGATION, PHONE, MICROPHONE }

    /** 有线 CarPlay 的低延迟流全部使用 LPCM；无线使用 Opus，关闭 Opus 时退回 AAC-ELD。 */
    fun codec(route: Route, wireless: Boolean, opus: Boolean): AudioCodecKind = when {
        !wireless -> AudioCodecKind.LPCM
        route == Route.MUSIC -> AudioCodecKind.AAC_LC
        opus -> AudioCodecKind.OPUS
        else -> AudioCodecKind.AAC_ELD
    }

    fun channels(route: Route, wireless: Boolean, opus: Boolean): List<Int> = when (route) {
        Route.MUSIC -> listOf(2)
        Route.MICROPHONE -> listOf(1)
        // 提示音/仪表路允许立体声；语音类与电话按协议只有 mono。
        Route.NAVIGATION -> if (wireless && opus) listOf(1) else listOf(1, 2)
        Route.ASSISTANT, Route.PHONE -> listOf(1)
    }

    fun rates(route: Route, wireless: Boolean, opus: Boolean): List<Int> = when (route) {
        Route.MUSIC -> listOf(44_100, 48_000)
        // 语音助手下限 24 kHz：Siri 走 speechRecognition，协议只接受 24 kHz。
        Route.ASSISTANT -> when {
            !wireless -> listOf(24_000, 32_000, 44_100, 48_000)
            opus -> listOf(24_000, 48_000)
            else -> listOf(24_000, 44_100, 48_000)
        }
        Route.NAVIGATION -> if (wireless && opus) listOf(48_000) else listOf(44_100, 48_000)
        Route.PHONE -> when {
            !wireless -> listOf(8_000, 16_000, 24_000, 32_000)
            opus -> listOf(16_000, 24_000, 48_000)
            else -> listOf(16_000, 24_000, 32_000)
        }
        Route.MICROPHONE -> if (wireless) listOf(16_000) else listOf(8_000, 16_000)
    }

    /** 麦克风上行的协议上限：mono，且不高于 16 kHz。 */
    const val MICROPHONE_MAX_RATE = 16_000
    const val MICROPHONE_CHANNELS = 1

    /**
     * 该路在任意传输方式下都合法的取值，用于校验从 SharedPreferences 读回的历史设置。
     * 换设备或换传输方式后，旧值可能落在合法范围外，必须在读取时就收敛，
     * 否则会推出一个空的格式声明，导致这一路彻底没有声音。
     */
    fun persistentChannels(route: Route): List<Int> = transports().flatMap { (wireless, opus) ->
        channels(route, wireless, opus)
    }.distinct().sorted()

    fun persistentRates(route: Route): List<Int> = transports().flatMap { (wireless, opus) ->
        rates(route, wireless, opus)
    }.distinct().sorted()

    /**
     * 该编码在协议里可能被要求处理的「采样率 × 声道」组合。
     *
     * 能力探测按这张表逐项真实验证，范围与声明严格一致：既不会漏掉能选的组合，
     * 也不会为协议根本不会出现的组合浪费解码器启停。
     */
    fun formatsFor(codec: AudioCodecKind): Set<Pair<Int, Int>> = buildSet {
        for (route in Route.entries) {
            // 麦克风是上行方向，由采集与编码探测单独负责。
            if (route == Route.MICROPHONE) continue
            for ((wireless, opus) in transports()) {
                if (codec(route, wireless, opus) != codec) continue
                for (channels in channels(route, wireless, opus)) {
                    for (rate in rates(route, wireless, opus)) add(rate to channels)
                }
            }
        }
    }

    private fun transports(): List<Pair<Boolean, Boolean>> =
        listOf(false to true, true to true, true to false)
}
