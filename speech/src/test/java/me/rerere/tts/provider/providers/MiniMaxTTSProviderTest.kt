package me.rerere.tts.provider.providers

import kotlinx.serialization.json.jsonObject
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MiniMaxTTSProviderTest {
    @Test
    fun autoEmotionOmitsEmotionField() {
        val body = buildMiniMaxRequestBody(
            TTSProviderSetting.MiniMax(emotion = ""),
            "今天见到你很开心",
        )

        assertFalse("emotion" in body["voice_setting"]!!.jsonObject)
    }

    @Test
    fun explicitEmotionIsPreserved() {
        val body = buildMiniMaxRequestBody(
            TTSProviderSetting.MiniMax(emotion = "happy"),
            "你好",
        )

        assertEquals(
            "\"happy\"",
            body["voice_setting"]!!.jsonObject["emotion"].toString(),
        )
    }
}
