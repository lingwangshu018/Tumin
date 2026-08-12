/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.tts.model

import kotlinx.serialization.Serializable

@Serializable
data class TTSRequest(
    val text: String,
    /** Optional companion-state emotion. Providers may ignore this hint. */
    val emotionHint: String? = null,
)

@Serializable
enum class AudioFormat {
    MP3,
    WAV,
    OGG,
    AAC,
    OPUS,
    PCM
}
