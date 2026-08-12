/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.tts.provider.providers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.common.http.SseEvent
import me.rerere.common.http.sseFlow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "MiniMaxTTSProvider"

@Serializable
private data class MiniMaxResponseData(
    val audio: String,
    val status: Int,
    val ced: String
)

@Serializable
private data class MiniMaxResponse(
    val data: MiniMaxResponseData
)

class MiniMaxTTSProvider : TTSProvider<TTSProviderSetting.MiniMax> {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.MiniMax,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = buildMiniMaxRequestBody(providerSetting, request.text, request.emotionHint)

        Log.i(TAG, "generateSpeech: $requestBody")

        val httpRequest = Request.Builder()
            .url("${providerSetting.baseUrl}/t2a_v2")
            .addHeader("Authorization", "Bearer ${providerSetting.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(json.encodeToString(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        var hasEmittedAudio = false

        httpClient.sseFlow(httpRequest).collect {
            when (it) {
                is SseEvent.Open -> Log.i(TAG, "SSE connection opened")
                is SseEvent.Event -> {
                    try {
                        val data = json.decodeFromString<MiniMaxResponse>(it.data)

                        // Convert hex string to bytes
                        val audioBytes = hexStringToBytes(data.data.audio)

                        emit(
                            AudioChunk(
                                data = audioBytes,
                                format = AudioFormat.MP3, // MiniMax returns MP3 format
                                sampleRate = 32000, // Default sample rate from MiniMax
                                isLast = false, // Will be set to true on last chunk
                                metadata = mapOf(
                                    "provider" to "minimax",
                                    "model" to providerSetting.model,
                                    "voice" to providerSetting.voiceId,
                                    "status" to data.data.status.toString(),
                                    "ced" to data.data.ced
                                )
                            )
                        )
                        hasEmittedAudio = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process audio chunk", e)
                    }
                }

                is SseEvent.Closed -> {
                    Log.i(TAG, "SSE connection closed")
                    // Emit final chunk if we haven't already
                    if (hasEmittedAudio) {
                        emit(
                            AudioChunk(
                                data = byteArrayOf(), // Empty data for last chunk
                                format = AudioFormat.MP3,
                                sampleRate = 32000,
                                isLast = true,
                                metadata = mapOf("provider" to "minimax")
                            )
                        )
                    }
                }

                is SseEvent.Failure -> {
                    Log.e(TAG, "SSE connection failed", it.throwable)
                    throw it.throwable ?: Exception("MiniMax TTS streaming failed")
                }
            }
        }
    }
}

internal fun buildMiniMaxRequestBody(
    providerSetting: TTSProviderSetting.MiniMax,
    text: String,
    emotionHint: String? = null,
) = buildJsonObject {
    put("model", providerSetting.model)
    put("text", text)
    put("stream", true)
    put("output_format", "hex")
    put("stream_options", buildJsonObject {
        put("exclude_aggregated_audio", true)
    })
    put("voice_setting", buildJsonObject {
        put("voice_id", providerSetting.voiceId)
        put("speed", providerSetting.speed)
        val effectiveEmotion = if (providerSetting.useCharacterStateEmotion) {
            mapMiniMaxEmotion(emotionHint)
        } else {
            providerSetting.emotion.trim().takeIf { it.isNotEmpty() }
        }
        effectiveEmotion?.let {
            put("emotion", it)
        }
    })
}

internal fun mapMiniMaxEmotion(value: String?): String? {
    val emotion = value?.trim()?.lowercase().orEmpty()
    if (emotion.isBlank()) return null
    return when {
        listOf("happy", "joy", "开心", "高兴", "愉快", "甜蜜", "安心").any(emotion::contains) -> "happy"
        listOf("sad", "伤心", "难过", "失落", "委屈").any(emotion::contains) -> "sad"
        listOf("angry", "生气", "愤怒", "恼火", "吃醋", "嫉妒").any(emotion::contains) -> "angry"
        listOf("fear", "害怕", "恐惧", "不安", "紧张", "焦虑").any(emotion::contains) -> "fearful"
        listOf("disgust", "厌恶", "嫌弃", "反感").any(emotion::contains) -> "disgusted"
        listOf("surprise", "惊讶", "惊喜", "意外").any(emotion::contains) -> "surprised"
        listOf("calm", "平静", "冷静", "放松", "温柔").any(emotion::contains) -> "calm"
        else -> null
    }
}

private fun hexStringToBytes(hexString: String): ByteArray {
    val cleanHex = hexString.replace("\\s+".toRegex(), "")
    val length = cleanHex.length

    // Check for even number of characters
    if (length % 2 != 0) {
        throw IllegalArgumentException("Hex string must have even number of characters")
    }

    val bytes = ByteArray(length / 2)
    for (i in 0 until length step 2) {
        val hexByte = cleanHex.substring(i, i + 2)
        bytes[i / 2] = hexByte.toInt(16).toByte()
    }
    return bytes
}
