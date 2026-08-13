/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.VOICE_CALL_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.hooks.CustomAsrState
import me.rerere.rikkahub.ui.hooks.CustomTtsState
import me.rerere.rikkahub.ui.hooks.createCustomAsrState
import me.rerere.rikkahub.ui.hooks.createCustomTtsState
import me.rerere.rikkahub.ui.pages.voice.VoiceCallStatus
import me.rerere.rikkahub.ui.pages.voice.VoiceCallSurface
import me.rerere.rikkahub.ui.pages.voice.VoiceCallUiState
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

private const val TAG = "VoiceCallService"

/**
 * 语音 / 剧情视频通话后台服务。
 *
 * ASR、AI 生成与 TTS 都跟随 Service 生命周期，页面只负责 bind + UI。返回聊天页或切到
 * 后台时通话继续；只有主动挂断才真正结束。
 */
class VoiceCallService : Service(), KoinComponent {
    private val chatService: ChatService by inject()
    private val httpClient: OkHttpClient by inject()
    private val settingsStore: SettingsStore by inject()

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "VoiceCallService coroutine exception", e)
        }
    )

    private lateinit var conversationId: Uuid
    private lateinit var asr: CustomAsrState
    private lateinit var tts: CustomTtsState

    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private var previousAudioMode: Int? = null
    private var previousSpeakerphoneOn: Boolean? = null
    private var audioRoutePrepared = false
    private var callSurface: VoiceCallSurface = VoiceCallSurface.Voice

    private val _uiState = MutableStateFlow(VoiceCallUiState())
    val uiState: StateFlow<VoiceCallUiState> = _uiState.asStateFlow()

    val conversation: StateFlow<Conversation>
        get() = chatService.getConversationFlow(conversationId)

    // 任务协程
    private var vadJob: Job? = null
    private var speakingMonitorJob: Job? = null
    private var conversationMonitorJob: Job? = null
    private var asrMonitorJob: Job? = null
    private var interruptDetectJob: Job? = null
    private var lastSpokenText: String = ""

    // 跟踪 AI 消息的增量, 用于流式 TTS
    private var lastAssistantText: String = ""
    private var hasSentCurrentMessage = false

    // 流式 TTS: 记录已发送给 TTS 的文本长度
    private var ttsSentLength: Int = 0

    // 静音状态 (独立于 _uiState.isMuted, 检测循环里直接读这个字段更快)
    private var isMuted: Boolean = false

    companion object {
        private val _activeConversationId = MutableStateFlow<String?>(null)
        val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

        private val _activeCallSurface = MutableStateFlow<VoiceCallSurface?>(null)
        val activeCallSurface: StateFlow<VoiceCallSurface?> = _activeCallSurface.asStateFlow()

        fun isRunning(): Boolean = _activeConversationId.value != null

        /** 启动后台通话。语音页保持默认 Voice；视频页显式传 Video。 */
        fun start(
            context: Context,
            conversationId: String,
            surface: VoiceCallSurface = VoiceCallSurface.Voice,
        ) {
            val intent = Intent(context, VoiceCallService::class.java).apply {
                putExtra(EXTRA_CONVERSATION_ID, conversationId)
                putExtra(EXTRA_CALL_SURFACE, surface.name)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "启动 VoiceCallService 失败, conversationId=$conversationId, surface=$surface", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, VoiceCallService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "停止 VoiceCallService 失败", e)
            }
        }

        const val EXTRA_CONVERSATION_ID = "conversationId"
        const val EXTRA_CALL_SURFACE = "callSurface"
        const val ACTION_HANG_UP = "me.rerere.rikkahub.VOICE_CALL_HANG_UP"
        const val NOTIFICATION_ID = 40001
    }

    inner class LocalBinder : Binder() {
        fun getService(): VoiceCallService = this@VoiceCallService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HANG_UP) {
            endCall()
            stopSelf()
            return START_NOT_STICKY
        }

        val convIdStr = intent?.getStringExtra(EXTRA_CONVERSATION_ID)
        if (convIdStr == null) {
            Log.e(TAG, "onStartCommand 缺少 conversationId 参数, 无法启动通话")
            stopSelf()
            return START_NOT_STICKY
        }
        val requestedSurface = intent.getStringExtra(EXTRA_CALL_SURFACE)
            ?.let { value -> runCatching { VoiceCallSurface.valueOf(value) }.getOrNull() }
            ?: VoiceCallSurface.Voice

        // 已经在跑同一个对话：不要重复初始化。保留原通话 surface，避免页面误把正在进行的
        // 语音通话偷偷切成视频（反之亦然）。
        if (_activeConversationId.value == convIdStr) {
            return START_NOT_STICKY
        }

        if (_activeConversationId.value != null && _activeConversationId.value != convIdStr) {
            Log.w(TAG, "已有通话 ${_activeConversationId.value} 在进行, 忽略新的 start 请求 $convIdStr")
            return START_NOT_STICKY
        }

        try {
            conversationId = Uuid.parse(convIdStr)
        } catch (e: Exception) {
            Log.e(TAG, "conversationId 解析失败: $convIdStr", e)
            stopSelf()
            return START_NOT_STICKY
        }

        callSurface = requestedSurface
        _activeConversationId.value = convIdStr
        _activeCallSurface.value = requestedSurface

        // startForegroundService 后 5 秒内必须同步进入前台，ASR/TTS 初始化放到后续协程。
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(_uiState.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } catch (e: Exception) {
            Log.e(TAG, "startForeground 失败, conversationId=$conversationId", e)
            _activeConversationId.value = null
            _activeCallSurface.value = null
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            try {
                asr = createCustomAsrState(applicationContext, httpClient, settingsStore)
                tts = createCustomTtsState(applicationContext, settingsStore)

                startCall()

                launch {
                    uiState.collect { state ->
                        try {
                            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            manager.notify(NOTIFICATION_ID, buildNotification(state))
                        } catch (e: Exception) {
                            Log.e(TAG, "刷新通话通知失败", e)
                        }
                    }
                }

                launch {
                    asr.state.collect { asrState ->
                        updateAmplitudes(asrState.amplitudes)
                        if (asrState.status == me.rerere.asr.ASRStatus.Error) {
                            val msg = asrState.errorMessage ?: "语音识别发生未知错误"
                            Log.e(TAG, "ASR 底层报错, conversationId=$conversationId, msg=$msg")
                            _uiState.update {
                                it.copy(
                                    status = VoiceCallStatus.Error,
                                    errorMessage = "语音识别错误: $msg"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "初始化通话失败, conversationId=$conversationId", e)
                _uiState.update {
                    it.copy(
                        status = VoiceCallStatus.Error,
                        errorMessage = "初始化失败: ${e.message}"
                    )
                }
            }
        }

        return START_NOT_STICKY
    }

    /** 准备通话音频路由，并记住进入通话前的系统状态以便挂断后恢复。 */
    private fun prepareAudioRoute() {
        if (!audioRoutePrepared) {
            previousAudioMode = audioManager.mode
            previousSpeakerphoneOn = audioManager.isSpeakerphoneOn
            audioRoutePrepared = true
        }
        runCatching { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION }
            .onFailure { Log.w(TAG, "设置 MODE_IN_COMMUNICATION 失败", it) }
        setSpeakerEnabled(callSurface == VoiceCallSurface.Video)
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = enabled
            _uiState.update { it.copy(isSpeakerEnabled = enabled) }
        }.onFailure {
            Log.e(TAG, "切换扬声器失败, enabled=$enabled", it)
            _uiState.update { state -> state.copy(errorMessage = "音频输出切换失败: ${it.message}") }
        }
    }

    fun toggleSpeaker() {
        setSpeakerEnabled(!_uiState.value.isSpeakerEnabled)
    }

    private fun restoreAudioRoute() {
        if (!audioRoutePrepared) return
        runCatching {
            previousSpeakerphoneOn?.let { audioManager.isSpeakerphoneOn = it }
            previousAudioMode?.let { audioManager.mode = it }
        }.onFailure { Log.w(TAG, "恢复通话前音频路由失败", it) }
        previousSpeakerphoneOn = null
        previousAudioMode = null
        audioRoutePrepared = false
    }

    /**
     * 开始通话。ASR 在整场通话期间持续录音（静音时暂停）。
     */
    fun startCall() {
        if (_uiState.value.status != VoiceCallStatus.Idle) return
        lastAssistantText = ""
        lastSpokenText = ""
        hasSentCurrentMessage = false
        ttsSentLength = 0
        isMuted = false
        prepareAudioRoute()

        _uiState.update {
            it.copy(
                status = VoiceCallStatus.Listening,
                userTranscript = "",
                errorMessage = null,
                isMuted = false
            )
        }

        try {
            asr.start { transcript ->
                _uiState.update { it.copy(userTranscript = transcript) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动 ASR 失败, conversationId=$conversationId", e)
            _uiState.update {
                it.copy(
                    status = VoiceCallStatus.Error,
                    errorMessage = "麦克风启动失败: ${e.message}"
                )
            }
            return
        }

        startVadDetection()
        startAsrMonitor()
        startConversationMonitor()
    }

    private fun startListening() {
        tts.stop()
        ttsSentLength = 0
        lastAssistantText = ""
        hasSentCurrentMessage = false

        _uiState.update {
            it.copy(
                status = VoiceCallStatus.Listening,
                userTranscript = "",
                errorMessage = null
            )
        }

        interruptDetectJob?.cancel()

        if (!isMuted) {
            runCatching {
                asr.start { transcript ->
                    _uiState.update { it.copy(userTranscript = transcript) }
                }
            }.onFailure { Log.e(TAG, it.toString(), it) }
        }

        startVadDetection()
    }

    private fun startVadDetection() {
        vadJob?.cancel()
        vadJob = serviceScope.launch {
            var lastTranscript = ""
            var silenceStartTime: Long = 0L
            var lastAmplitudeTime: Long = System.currentTimeMillis()
            val silenceThresholdMs = 800L
            val minTranscriptLength = 2
            val amplitudeTimeoutMs = 2000L

            while (true) {
                delay(100)
                if (_uiState.value.status != VoiceCallStatus.Listening) break
                if (isMuted) continue
                if (!_uiState.value.autoSendEnabled) continue

                val currentTranscript = _uiState.value.userTranscript
                val amplitudes = _uiState.value.amplitudes
                val recentAmplitude = amplitudes.takeLast(3).average().toFloat()

                if (recentAmplitude > 0.05f) {
                    lastAmplitudeTime = System.currentTimeMillis()
                }

                if (currentTranscript != lastTranscript) {
                    lastTranscript = currentTranscript
                    silenceStartTime = 0L
                } else if (currentTranscript.length >= minTranscriptLength) {
                    if (silenceStartTime == 0L) {
                        silenceStartTime = System.currentTimeMillis()
                    }
                    val silentFor = System.currentTimeMillis() - silenceStartTime
                    val amplitudeSilentFor = System.currentTimeMillis() - lastAmplitudeTime

                    if (silentFor >= silenceThresholdMs || amplitudeSilentFor >= amplitudeTimeoutMs) {
                        Log.d(
                            TAG,
                            "VAD triggered auto-send: $currentTranscript (silentFor=$silentFor, ampSilent=$amplitudeSilentFor)"
                        )
                        sendCurrentMessage()
                        break
                    }
                }
            }
        }
    }

    private fun sendCurrentMessage() {
        sendCallMessage(_uiState.value.userTranscript.trim())
    }

    /** 视频电话文字输入与语音输入共用 Processing → AI → TTS → Listening 状态机。 */
    fun sendTextMessage(text: String) {
        val normalized = text.trim()
        if (normalized.isBlank()) return
        if (_uiState.value.status == VoiceCallStatus.Processing) return
        sendCallMessage(normalized)
    }

    private fun sendCallMessage(text: String) {
        val transcript = text.trim()
        vadJob?.cancel()

        if (transcript.isBlank()) {
            startListening()
            return
        }

        if (_uiState.value.status == VoiceCallStatus.Speaking) {
            interruptDetectJob?.cancel()
            tts.stop()
        }

        _uiState.update {
            it.copy(
                status = VoiceCallStatus.Processing,
                assistantText = "",
                userTranscript = "",
                errorMessage = null,
            )
        }
        ttsSentLength = 0
        lastAssistantText = ""

        try {
            chatService.sendMessage(
                conversationId,
                listOf(UIMessagePart.Text(transcript))
            )
        } catch (e: Exception) {
            Log.e(TAG, "发送消息失败, conversationId=$conversationId, transcript=$transcript", e)
            _uiState.update {
                it.copy(
                    status = VoiceCallStatus.Error,
                    errorMessage = "发送失败: ${e.message}"
                )
            }
        }
    }

    private fun startConversationMonitor() {
        conversationMonitorJob?.cancel()
        conversationMonitorJob = serviceScope.launch {
            conversation.collect { conv ->
                if (_uiState.value.status != VoiceCallStatus.Processing &&
                    _uiState.value.status != VoiceCallStatus.Speaking
                ) return@collect

                val lastMessage = conv.currentMessages.lastOrNull()
                if (lastMessage?.role != MessageRole.ASSISTANT) return@collect

                val currentText = lastMessage.toText()
                _uiState.update { it.copy(assistantText = currentText) }

                if (currentText.length > ttsSentLength) {
                    val newText = currentText.substring(ttsSentLength)
                    val sentences = extractCompleteSentences(newText)
                    for (sentence in sentences) {
                        if (sentence.isNotBlank()) {
                            tts.enqueueText(sentence)
                            Log.d(TAG, "Streaming TTS: $sentence")
                        }
                    }
                    ttsSentLength = currentText.length - getPendingRemainder(newText).length
                }

                if (_uiState.value.status == VoiceCallStatus.Processing && currentText.isNotBlank()) {
                    _uiState.update { it.copy(status = VoiceCallStatus.Speaking) }
                    startInterruptDetection()
                }

                lastAssistantText = currentText
            }
        }

        speakingMonitorJob?.cancel()
        speakingMonitorJob = serviceScope.launch {
            chatService.generationDoneFlow.collect { convId ->
                if (convId != conversationId) return@collect
                onGenerationDone()
            }
        }
    }

    private suspend fun onGenerationDone() {
        val finalText = _uiState.value.assistantText
        if (finalText.length > ttsSentLength) {
            val remaining = finalText.substring(ttsSentLength)
            if (remaining.isNotBlank()) {
                tts.enqueueText(remaining)
                ttsSentLength = finalText.length
            }
        }

        _uiState.update { it.copy(status = VoiceCallStatus.Speaking) }
        startInterruptDetection()
        waitForTtsToFinish()

        if (_uiState.value.status == VoiceCallStatus.Speaking) {
            startListening()
        }
    }

    private suspend fun waitForTtsToFinish() {
        var waitStart = System.currentTimeMillis()
        while (!tts.isSpeaking.value && System.currentTimeMillis() - waitStart < 5000) {
            delay(100)
        }
        val idleTimeoutMs = 5_000L
        val hardDeadlineMs = 300_000L
        val startTime = System.currentTimeMillis()
        var lastActiveTime = System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            val status = tts.playbackState.value.status
            val active = tts.isSpeaking.value ||
                status == me.rerere.tts.model.PlaybackStatus.Playing ||
                status == me.rerere.tts.model.PlaybackStatus.Buffering
            if (active) {
                lastActiveTime = now
            }
            if (!active && now - lastActiveTime >= idleTimeoutMs) {
                break
            }
            if (now - startTime > hardDeadlineMs) {
                Log.w(TAG, "TTS 播放超过 5 分钟未结束, 强制停止以防卡死")
                tts.stop()
                break
            }
            delay(300)
        }
        delay(300)
    }

    private fun extractCompleteSentences(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (char in text) {
            current.append(char)
            if (char == '。' || char == '？' || char == '！' || char == '.' ||
                char == '?' || char == '!' || char == '\n'
            ) {
                val sentence = current.toString().trim()
                if (sentence.isNotEmpty()) {
                    result.add(sentence)
                }
                current.clear()
            }
        }
        return result
    }

    private fun getPendingRemainder(text: String): String {
        val lastSentenceEnd = text.lastIndexOfAny(charArrayOf('。', '？', '！', '.', '?', '!', '\n'))
        return if (lastSentenceEnd >= 0 && lastSentenceEnd < text.length - 1) {
            text.substring(lastSentenceEnd + 1)
        } else if (lastSentenceEnd < 0) {
            text
        } else {
            ""
        }
    }

    private fun startInterruptDetection() {
        interruptDetectJob?.cancel()
        interruptDetectJob = serviceScope.launch {
            val baselineTranscript = _uiState.value.userTranscript
            while (true) {
                delay(150)
                if (_uiState.value.status != VoiceCallStatus.Speaking) break
                if (isMuted) continue

                val currentTranscript = _uiState.value.userTranscript
                val amplitudes = _uiState.value.amplitudes
                val recentAmplitude = amplitudes.takeLast(3).average().toFloat()

                val hasNewTranscript = currentTranscript.length > baselineTranscript.length + 1
                val hasLoudVoice = recentAmplitude > 0.15f

                if (hasNewTranscript || hasLoudVoice) {
                    Log.d(TAG, "检测到用户打断: transcript=$currentTranscript, amplitude=$recentAmplitude")
                    interruptSpeaking()
                    break
                }
            }
        }
    }

    fun interruptSpeaking() {
        if (_uiState.value.status != VoiceCallStatus.Speaking) return
        speakingMonitorJob?.cancel()
        interruptDetectJob?.cancel()
        startListening()
        startConversationMonitor()
    }

    private fun startAsrMonitor() {
        asrMonitorJob?.cancel()
        asrMonitorJob = serviceScope.launch {
            var wasRecording = false
            asr.state.collect { asrState ->
                val isRecording = asrState.isRecording

                if (wasRecording && !isRecording && !isMuted && _uiState.value.status == VoiceCallStatus.Listening) {
                    val transcript = asrState.transcript.trim()
                    if (transcript.isNotEmpty() && _uiState.value.autoSendEnabled) {
                        Log.d(TAG, "ASR monitor: Auto-send after ASR completed: $transcript")
                        sendCurrentMessage()
                    } else if (!isMuted && _uiState.value.status == VoiceCallStatus.Listening) {
                        runCatching {
                            asr.start { t -> _uiState.update { it.copy(userTranscript = t) } }
                        }.onFailure { Log.e(TAG, it.toString(), it) }
                    }
                }

                wasRecording = isRecording
            }
        }
    }

    fun toggleMute() {
        isMuted = !isMuted
        _uiState.update { it.copy(isMuted = isMuted) }

        try {
            if (isMuted) {
                asr.stop()
            } else {
                asr.start { transcript ->
                    _uiState.update { it.copy(userTranscript = transcript) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "切换静音状态失败, isMuted=$isMuted", e)
            _uiState.update { it.copy(errorMessage = "麦克风切换失败: ${e.message}") }
        }
    }

    fun toggleAutoSend() {
        _uiState.update { it.copy(autoSendEnabled = !it.autoSendEnabled) }
    }

    fun endCall() {
        vadJob?.cancel()
        speakingMonitorJob?.cancel()
        conversationMonitorJob?.cancel()
        asrMonitorJob?.cancel()
        interruptDetectJob?.cancel()
        if (::asr.isInitialized) asr.stop()
        if (::tts.isInitialized) tts.stop()
        restoreAudioRoute()
        _uiState.update {
            it.copy(
                status = VoiceCallStatus.Idle,
                isSpeakerEnabled = false,
            )
        }
        _activeConversationId.value = null
        _activeCallSurface.value = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(TAG, "stopForeground 失败", e)
        }
    }

    fun updateAmplitudes(amplitudes: List<Float>) {
        _uiState.update { it.copy(amplitudes = amplitudes) }
    }

    private fun buildNotification(state: VoiceCallUiState): android.app.Notification {
        val contentText = when (state.status) {
            VoiceCallStatus.Listening -> "正在聆听..."
            VoiceCallStatus.Processing -> "正在思考..."
            VoiceCallStatus.Speaking -> "正在说话..."
            VoiceCallStatus.Error -> state.errorMessage ?: "通话出错"
            VoiceCallStatus.Idle -> "通话中"
        }
        val isVideo = callSurface == VoiceCallSurface.Video
        val returnExtra = if (isVideo) "openVideoCallConversationId" else "openVoiceCallConversationId"

        val contentIntent = PendingIntent.getActivity(
            this,
            conversationId.hashCode(),
            Intent(this, RouteActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(returnExtra, conversationId.toString())
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val hangUpIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, VoiceCallService::class.java).apply { action = ACTION_HANG_UP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, VOICE_CALL_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(if (isVideo) "视频通话" else "语音通话")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.small_icon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, "挂断", hangUpIntent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    override fun onDestroy() {
        try {
            endCall()
            if (::asr.isInitialized) asr.cleanup()
            if (::tts.isInitialized) tts.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy 清理失败", e)
        } finally {
            serviceScope.cancel()
            super.onDestroy()
        }
    }
}
