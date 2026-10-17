package com.shilapi.xcertplay.airplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQualityProfilesTest {
    /**
     * 能力探测按 [AudioQualityProfiles.formatsFor] 逐项真实验证，范围必须覆盖
     * /info 里可能声明的每一种组合，否则会出现"声明了却从没验证过"的格式。
     */
    @Test
    fun everyDeclarableCombinationIsCoveredByTheProbePlan() {
        for (route in AudioQualityProfiles.Route.entries) {
            // 麦克风是上行方向，由采集与编码探测单独负责。
            if (route == AudioQualityProfiles.Route.MICROPHONE) continue
            for (wireless in listOf(false, true)) for (opus in listOf(true, false)) {
                val codec = AudioQualityProfiles.codec(route, wireless, opus)
                val plan = AudioQualityProfiles.formatsFor(codec)
                for (channels in AudioQualityProfiles.channels(route, wireless, opus)) {
                    for (rate in AudioQualityProfiles.rates(route, wireless, opus)) {
                        assertTrue(
                            "$route wireless=$wireless opus=$opus 缺少 $codec $rate/$channels",
                            rate to channels in plan,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun musicOnlyEverAsksForAacLcOrPcmStereo() {
        assertEquals(setOf(44_100 to 2, 48_000 to 2), AudioQualityProfiles.formatsFor(AudioCodecKind.AAC_LC))
        assertEquals(setOf(44_100 to 2, 48_000 to 2), AudioQualityProfiles.formatsFor(AudioCodecKind.LPCM)
            .filter { it.second == 2 && it.first >= 44_100 }.toSet())
    }

    @Test
    fun opusIsAlwaysMonoAndEldCoversEveryBitTheDeclarationUses() {
        assertTrue(AudioQualityProfiles.formatsFor(AudioCodecKind.OPUS).all { it.second == 1 })
        val eld = AudioQualityProfiles.formatsFor(AudioCodecKind.AAC_ELD)
        // 声明里用到的 ELD 位：44.1/48 stereo，16/24/32/44.1/48 mono。
        assertEquals(setOf(44_100 to 2, 48_000 to 2), eld.filter { it.second == 2 }.toSet())
        assertEquals(setOf(16_000 to 1, 24_000 to 1, 32_000 to 1, 44_100 to 1, 48_000 to 1), eld.filter { it.second == 1 }.toSet())
    }
}
