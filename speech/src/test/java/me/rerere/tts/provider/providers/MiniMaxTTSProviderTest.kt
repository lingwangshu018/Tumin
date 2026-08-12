package me.rerere.tts.provider.providers

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MiniMaxTTSProviderTest {

    @Test
    fun `character state emotion overrides configured emotion when enabled`() {
        val body = buildMiniMaxRequestBody(
            TTSProviderSetting.MiniMax(emotion = "calm", useCharacterStateEmotion = true),
            "hello",
            "鏈夌偣濮斿眻",
        )

        assertEquals("sad", body["voice_setting"]?.jsonObject?.get("emotion")?.jsonPrimitive?.content)
    }

    @Test
    fun `unknown character emotion falls back to auto omission`() {
        val body = buildMiniMaxRequestBody(
            TTSProviderSetting.MiniMax(emotion = "angry", useCharacterStateEmotion = true),
            "hello",
            "澶嶆潅寰楄涓嶆竻",
        )

        assertNull(body["voice_setting"]?.jsonObject?.get("emotion"))
    }
    @Test
    fun autoEmotionOmitsEmotionField() {
        val body = buildMiniMaxRequestBody(
            TTSProviderSetting.MiniMax(emotion = ""),
            "浠婂ぉ瑙佸埌浣犲緢寮€蹇?,
        )

        assertFalse("emotion" in body["voice_setting"]!!.jsonObject)
    }

    @Test
    fun explicitEmotionIsPreserved() {
        val body = buildMiniMaxRequestBody(
            TTSProviderSetting.MiniMax(emotion = "happy"),
            "浣犲ソ",
        )

        assertEquals(
            "\"happy\"",
            body["voice_setting"]!!.jsonObject["emotion"].toString(),
        )
    }
}
