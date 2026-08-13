package me.rerere.rikkahub.ui.pages.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Mic01
import me.rerere.hugeicons.stroke.MicOff01
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.repository.CompanionStateRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.VoiceCallService
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalDisplaySettings
import me.rerere.rikkahub.ui.context.LocalSettings
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

/** Story video call with custom full-screen background, text input and persistent archives. */
@Composable
fun VideoCallPage(conversationId: Uuid, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val displaySetting = LocalDisplaySettings.current
    val visualStore = remember { VideoCallVisualSettingsStore(context) }
    val visualSettings = remember { visualStore.read() }
    val archiveStore = remember { VideoCallArchiveStore(context) }
    val companionRepository = koinInject<CompanionStateRepository>()
    val chatService = koinInject<ChatService>()
    var joined by remember { mutableStateOf(false) }

    if (!joined) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF171016))
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(HugeIcons.Camera01, null, tint = Color(0xFFFFB7C5), modifier = Modifier.size(72.dp))
                Text("视频电话", color = Color.White, fontSize = 28.sp, modifier = Modifier.padding(top = 20.dp))
                Text(
                    if (visualSettings.selfViewMode == VideoCallSelfViewMode.FRONT_CAMERA)
                        "通话期间会持续使用麦克风；你已选择前置摄像头，开始后会请求摄像头权限。"
                    else
                        "通话期间会持续使用麦克风；右上角默认显示你的头像，不会自动打开摄像头。",
                    color = Color.White.copy(.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
                Button(onClick = { joined = true }) { Text("开始通话") }
                TextButton(onClick = onBack) { Text("取消") }
            }
        }
        return
    }

    val audioPermission = rememberPermissionState(PermissionRecordAudio)
    val cameraPermission = rememberPermissionState(PermissionCamera)
    var service by remember { mutableStateOf<VoiceCallService?>(null) }
    var cameraEnabled by remember { mutableStateOf(false) }
    var cameraRequested by remember { mutableStateOf(false) }
    var speakerEnabled by remember { mutableStateOf(true) }
    var typedInput by remember { mutableStateOf("") }
    var callBaselineCount by remember(conversationId) { mutableStateOf<Int?>(null) }

    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as? VoiceCallService.LocalBinder)?.getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
    }

    DisposableEffect(conversationId) {
        if (
            audioPermission.allRequiredPermissionsGranted &&
            VoiceCallService.activeConversationId.value != conversationId.toString()
        ) {
            VoiceCallService.start(context, conversationId.toString())
        }
        context.bindService(Intent(context, VoiceCallService::class.java), connection, Context.BIND_AUTO_CREATE)
        onDispose { runCatching { context.unbindService(connection) } }
    }

    LaunchedEffect(Unit) {
        archiveStore.recoverInterruptedSessions()
        if (!audioPermission.allRequiredPermissionsGranted) {
            audioPermission.requestPermissions()
        }
        if (visualSettings.selfViewMode == VideoCallSelfViewMode.FRONT_CAMERA) {
            cameraRequested = true
            if (!cameraPermission.allRequiredPermissionsGranted) {
                cameraPermission.requestPermissions()
            }
        }
    }

    LaunchedEffect(cameraPermission.allRequiredPermissionsGranted, cameraRequested) {
        if (cameraRequested && cameraPermission.allRequiredPermissionsGranted) {
            cameraEnabled = true
        }
    }

    LaunchedEffect(audioPermission.allRequiredPermissionsGranted) {
        if (audioPermission.allRequiredPermissionsGranted && VoiceCallService.activeConversationId.value == null) {
            VoiceCallService.start(context, conversationId.toString())
        }
    }

    val uiState by (service?.uiState ?: MutableStateFlow(VoiceCallUiState()))
        .collectAsStateWithLifecycle(initialValue = VoiceCallUiState())
    val conversationFlow = service?.conversation?.map { it as me.rerere.rikkahub.data.model.Conversation? } ?: flowOf(null)
    val conversation by conversationFlow.collectAsStateWithLifecycle(initialValue = null)
    val assistant = conversation?.assistantId?.let { settings.getAssistantById(it) }
    val companionFlow = conversation?.assistantId?.let { companionRepository.observe(it) }
    val companion by (companionFlow ?: MutableStateFlow(me.rerere.rikkahub.data.model.CompanionState()))
        .collectAsStateWithLifecycle(initialValue = me.rerere.rikkahub.data.model.CompanionState())

    val displayName = assistant?.name.orEmpty().ifBlank {
        conversation?.title.orEmpty().ifBlank { "TA" }
    }

    LaunchedEffect(conversation?.id) {
        val current = conversation ?: return@LaunchedEffect
        if (callBaselineCount == null) {
            callBaselineCount = current.currentMessages.size
        }
    }

    LaunchedEffect(service, conversation?.assistantId, displayName) {
        val activeService = service ?: return@LaunchedEffect
        val current = conversation ?: return@LaunchedEffect
        VideoCallArchiveRuntime.attach(
            store = archiveStore,
            conversationId = conversationId,
            assistantId = current.assistantId,
            assistantName = displayName,
            conversationFlow = activeService.conversation,
        )
    }

    val callMessages = callBaselineCount?.let { baseline ->
        conversation?.currentMessages?.drop(baseline).orEmpty()
    }.orEmpty()
    val latestCallAssistantText = callMessages
        .lastOrNull { it.role == MessageRole.ASSISTANT }
        ?.toText()
        .orEmpty()
    val storySource = latestCallAssistantText.ifBlank { uiState.assistantText }
    val story = remember(storySource, companion.character.activity) {
        storyPresentation(storySource, companion.character.activity)
    }

    fun sendTypedMessage() {
        val text = typedInput.trim()
        if (text.isBlank()) return
        if (uiState.status == VoiceCallStatus.Speaking) {
            service?.interruptSpeaking()
        }
        runCatching {
            chatService.sendMessage(
                conversationId,
                listOf(UIMessagePart.Text(text)),
            )
        }
        typedInput = ""
    }

    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(Color(0xFF0D0B0D))) {
        VideoCallBackground(
            customPath = visualSettings.backgroundPath,
            assistantAvatar = assistant?.avatar ?: Avatar.Dummy,
            displayName = displayName,
            opacity = visualSettings.backgroundOpacity,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = .20f),
                        .35f to Color.Transparent,
                        1f to Color.Black.copy(alpha = .72f),
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 52.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f).padding(end = 126.dp)) {
                    Text(displayName, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOf(companion.character.emotion, companion.character.location)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                            .ifBlank { "视频通话中" },
                        color = Color.White.copy(.72f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(120.dp))

            if (story.action.isNotBlank()) {
                Text(
                    story.action,
                    color = Color.White.copy(.66f),
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
            }

            if (story.dialogue.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    color = Color.Black.copy(alpha = .48f),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Text(
                        story.dialogue,
                        color = Color.White,
                        fontSize = 17.sp,
                        lineHeight = 26.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    )
                }
            }

            if (uiState.userTranscript.isNotBlank() && uiState.status != VoiceCallStatus.Speaking) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(.82f)
                        .padding(top = 14.dp),
                    color = Color.Black.copy(alpha = .34f),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        uiState.userTranscript,
                        color = Color.White.copy(.82f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            OutlinedTextField(
                value = typedInput,
                onValueChange = { typedInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("对话…", color = Color.White.copy(.55f)) },
                trailingIcon = {
                    TextButton(
                        onClick = { sendTypedMessage() },
                        enabled = typedInput.isNotBlank(),
                    ) { Text("发送") }
                },
                maxLines = 3,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CallButton(
                    if (uiState.isMuted) HugeIcons.MicOff01 else HugeIcons.Mic01,
                    if (uiState.isMuted) "取消静音" else "静音",
                ) { service?.toggleMute() }

                CallButton(HugeIcons.Camera01, if (cameraEnabled) "头像" else "前置摄像头") {
                    if (cameraEnabled) {
                        cameraEnabled = false
                    } else if (!cameraPermission.allRequiredPermissionsGranted) {
                        cameraRequested = true
                        cameraPermission.requestPermissions()
                    } else {
                        cameraEnabled = true
                    }
                }

                CallButton(HugeIcons.VolumeHigh, if (speakerEnabled) "扬声器" else "听筒") {
                    speakerEnabled = !speakerEnabled
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audio.isSpeakerphoneOn = speakerEnabled
                }

                CallButton(HugeIcons.Cancel01, "挂断", Color(0xFFE5484D)) {
                    service?.endCall()
                    VoiceCallService.stop(context)
                    onBack()
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 92.dp, end = 18.dp)
                .size(width = 104.dp, height = 142.dp)
                .border(1.dp, Color.White.copy(alpha = .65f), RoundedCornerShape(18.dp)),
            color = Color.Black.copy(alpha = .25f),
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 8.dp,
        ) {
            if (cameraEnabled && cameraPermission.allRequiredPermissionsGranted) {
                FrontCameraPreview(modifier = Modifier.fillMaxSize())
            } else {
                UserAvatarPreview(
                    customPath = visualSettings.userAvatarPath,
                    avatar = displaySetting.userAvatar,
                    userName = displaySetting.userNickname.ifBlank { "You" },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun VideoCallBackground(
    customPath: String,
    assistantAvatar: Avatar,
    displayName: String,
    opacity: Float,
    modifier: Modifier = Modifier,
) {
    val customFile = customPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
    Box(modifier.background(Color(0xFF171016)), contentAlignment = Alignment.Center) {
        when {
            customFile != null -> AsyncImage(
                model = customFile,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().visualAlpha(opacity),
                contentScale = ContentScale.Crop,
            )

            assistantAvatar is Avatar.Image -> AsyncImage(
                model = assistantAvatar.url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().visualAlpha(opacity),
                contentScale = ContentScale.Crop,
            )

            assistantAvatar is Avatar.Emoji -> Text(
                assistantAvatar.content,
                fontSize = 112.sp,
                modifier = Modifier.visualAlpha(opacity),
            )

            else -> AutoAIIcon(
                name = displayName,
                modifier = Modifier.size(240.dp).visualAlpha(opacity),
            )
        }
    }
}

@Composable
private fun UserAvatarPreview(
    customPath: String,
    avatar: Avatar,
    userName: String,
    modifier: Modifier = Modifier,
) {
    val customFile = customPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF252126)), contentAlignment = Alignment.Center) {
        when {
            customFile != null -> AsyncImage(
                model = customFile,
                contentDescription = "用户头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            avatar is Avatar.Image -> AsyncImage(
                model = avatar.url,
                contentDescription = "用户头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            avatar is Avatar.Emoji -> Text(avatar.content, fontSize = 46.sp)
            else -> AutoAIIcon(name = userName, modifier = Modifier.size(72.dp))
        }
    }
}

private fun Modifier.visualAlpha(alpha: Float): Modifier = drawWithContent {
    drawContent()
    drawRect(Color.Black.copy(alpha = (1f - alpha.coerceIn(0.2f, 1f)) * .7f))
}

@Composable
private fun FrontCameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val textureView = remember { TextureView(context) }

    AndroidView(
        factory = { textureView },
        modifier = modifier,
    )

    DisposableEffect(textureView) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraThread = HandlerThread("story-video-camera").apply { start() }
        val cameraHandler = Handler(cameraThread.looper)
        var cameraDevice: CameraDevice? = null
        var captureSession: CameraCaptureSession? = null
        var previewSurface: Surface? = null

        fun closeCamera() {
            runCatching { captureSession?.close() }
            runCatching { cameraDevice?.close() }
            runCatching { previewSurface?.release() }
            captureSession = null
            cameraDevice = null
            previewSurface = null
        }

        fun openCamera() {
            if (!textureView.isAvailable) return
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: return
            try {
                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        val texture = textureView.surfaceTexture ?: return
                        previewSurface = Surface(texture)
                        val surface = previewSurface ?: return
                        camera.createCaptureSession(
                            listOf(surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    captureSession = session
                                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                        addTarget(surface)
                                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                                    }.build()
                                    runCatching { session.setRepeatingRequest(request, null, cameraHandler) }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) = Unit
                            },
                            cameraHandler,
                        )
                    }

                    override fun onDisconnected(camera: CameraDevice) = closeCamera()
                    override fun onError(camera: CameraDevice, error: Int) = closeCamera()
                }, cameraHandler)
            } catch (_: SecurityException) {
                closeCamera()
            } catch (_: Exception) {
                closeCamera()
            }
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) = openCamera()
            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                closeCamera()
                return true
            }
            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) = Unit
        }
        if (textureView.isAvailable) openCamera()

        onDispose {
            textureView.surfaceTextureListener = null
            closeCamera()
            cameraThread.quitSafely()
        }
    }
}

private data class StoryPresentation(val action: String, val dialogue: String)

private fun storyPresentation(text: String, fallbackAction: String): StoryPresentation {
    val actionRegex = Regex("\\*([^*]{2,80})\\*|\\(([^()]{2,80})\\)")
    val match = actionRegex.find(text)
    val action = match?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.trim()
        ?: fallbackAction.ifBlank { "TA 正在镜头那边陪着你" }
    return StoryPresentation(action, text.replace(actionRegex, "").trim())
}

@Composable
private fun CallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color = Color.Black.copy(.42f),
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color),
            modifier = Modifier.size(58.dp),
        ) {
            Icon(icon, label, tint = Color.White)
        }
        Text(label, color = Color.White.copy(.82f), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
