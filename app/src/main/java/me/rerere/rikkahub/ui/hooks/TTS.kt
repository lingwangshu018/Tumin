/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.hooks

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getSelectedTTSProvider
import me.rerere.rikkahub.data.repository.CompanionStateRepository
import me.rerere.rikkahub.utils.stripMarkdown
import me.rerere.tts.controller.TtsController
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.compose.koinInject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "TTS"

/**
 * Composable function to remember and manage custom TTS state.
 * Uses user-configured TTS providers instead of system TTS.
 */
@Composable
fun rememberCustomTtsState(): CustomTtsState {
    val context = LocalContext.current
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()

    val ttsState = remember {
        CustomTtsStateImpl(
            context = context.applicationContext,
            settingsStore = settingsStore
        )
    }

    DisposableEffect(settings.selectedTTSProviderId, settings.ttsProviders) {
        ttsState.updateProvider(settings.getSelectedTTSProvider())
        onDispose { }
    }

    DisposableEffect(ttsState) {
        onDispose {
            ttsState.cleanup()
        }
    }

    return ttsState
}

/**
 * 非 Compose 版本的工厂函数，供 Service 等非 UI 场景创建独立的 TTS 实例。
 *
 * suspend 函数，会真正挂起等待 provider 设置完成后才返回实例，
 * 消除之前 launch{} "发射后不管" 导致的 controller 为 null 竞态。
 */
suspend fun createCustomTtsState(
    context: Context,
    settingsStore: SettingsStore,
): CustomTtsState {
    val ttsState = CustomTtsStateImpl(context.applicationContext, settingsStore)
    try {
        val settings = settingsStore.settingsFlow.first()
        ttsState.updateProvider(settings.getSelectedTTSProvider())
    } catch (e: Exception) {
        Log.e("CreateCustomTtsState", "初始化 TTS provider 失败", e)
    }
    return ttsState
}

interface CustomTtsState {
    val isAvailable: StateFlow<Boolean>
    val isSpeaking: StateFlow<Boolean>
    val error: StateFlow<String?>
    val currentChunk: StateFlow<Int>
    val totalChunks: StateFlow<Int>
    val playbackState: StateFlow<PlaybackState>

    fun speak(text: String, flushCalled: Boolean = true)
    fun stop()
    fun pause()
    fun resume()
    fun skipNext()
    fun fastForward(ms: Long = 5_000)
    fun setSpeed(speed: Float)
    fun setEmotionHint(emotion: String?)
    fun cleanup()

    /**
     * 流式朗读: 追加一段文本到 TTS 队列, 不清空当前播放.
     * 用于语音通话中"边生成边朗读"的场景.
     *
     * @param text 要追加朗读的文本片段
     */
    fun enqueueText(text: String)
}

internal class CustomTtsStateImpl(
    private val context: Context,
    private val settingsStore: SettingsStore
) : CustomTtsState, KoinComponent {

    private val ttsManager by inject<TTSManager>()
    private val companionStateRepository = CompanionStateRepository(context)

    private val controller by lazy {
        TtsController(context, ttsManager)
    }

    override val isAvailable: StateFlow<Boolean> get() = controller.isAvailable
    override val isSpeaking: StateFlow<Boolean> get() = controller.isSpeaking
    override val error: StateFlow<String?> get() = controller.error
    override val currentChunk: StateFlow<Int> get() = controller.currentChunk
    override val totalChunks: StateFlow<Int> get() = controller.totalChunks
    override val playbackState: StateFlow<PlaybackState> get() = controller.playbackState

    fun updateProvider(provider: TTSProviderSetting?) {
        controller.setProvider(provider)
        refreshCharacterEmotionHint()
    }

    /**
     * MiniMax 的 Character State Emotion 模式只在真正朗读前读取当前角色状态。
     * 这样普通朗读、语音电话和视频电话复用同一个 TTS state 时，都会拿到同一份
     * CompanionStateRepository 中的实时 emotion，而不会把旧情绪缓存整场通话。
     *
     * Auto（emotion 为空）和手动 Emotion 不使用这个 hint；它们仍由 provider 自己决定。
     */
    private fun refreshCharacterEmotionHint() {
        val settings = settingsStore.settingsFlow.value
        val provider = settings.getSelectedTTSProvider()
        val emotionHint = if (provider is TTSProviderSetting.MiniMax && provider.useCharacterStateEmotion) {
            val assistant = settings.getCurrentAssistant()
            companionStateRepository.observe(assistant.id).value.character.emotion
                .trim()
                .takeIf { it.isNotBlank() }
        } else {
            null
        }
        controller.setEmotionHint(emotionHint)
    }

    override fun speak(text: String, flushCalled: Boolean) {
        val processed = text.stripMarkdown()
        refreshCharacterEmotionHint()
        controller.speak(processed, flushCalled)
    }

    override fun stop() {
        controller.stop()
    }

    override fun pause() {
        controller.pause()
        Log.d("CustomTtsState", "TTS paused")
    }

    override fun resume() {
        controller.resume()
        Log.d("CustomTtsState", "TTS resumed")
    }

    override fun skipNext() {
        controller.skipNext()
    }

    override fun fastForward(ms: Long) {
        controller.fastForward(ms)
    }

    override fun setSpeed(speed: Float) {
        controller.setSpeed(speed)
    }

    override fun setEmotionHint(emotion: String?) {
        controller.setEmotionHint(emotion)
    }

    override fun enqueueText(text: String) {
        if (text.isBlank()) return
        val processed = text.stripMarkdown()
        if (processed.isBlank()) return
        refreshCharacterEmotionHint()
        controller.speak(processed, flush = false)
    }

    override fun cleanup() {
        controller.dispose()
    }
}