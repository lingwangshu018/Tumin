package me.rerere.rikkahub.ui.pages.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.IBinder
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Mic01
import me.rerere.hugeicons.stroke.MicOff01
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.rikkahub.data.repository.CompanionStateRepository
import me.rerere.rikkahub.service.VoiceCallService
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

/** First-stage story video call: shared call context, character scene, action and subtitles. */
@Composable
fun VideoCallPage(conversationId: Uuid, onBack: () -> Unit) {
    val context = LocalContext.current
    val companionRepository = koinInject<CompanionStateRepository>()
    var joined by remember { mutableStateOf(false) }
    if (!joined) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF171016)).padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(HugeIcons.Camera01, null, tint = Color(0xFFFFB7C5), modifier = Modifier.size(72.dp))
                Text("Story Video Call", color = Color.White, fontSize = 28.sp, modifier = Modifier.padding(top = 20.dp))
                Text(
                    "Microphone stays active during the call and may continue in the background until you hang up. The front camera is off by default.",
                    color = Color.White.copy(.7f), textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 20.dp)
                )
                Button(onClick = { joined = true }) { Text("Start call") }
                TextButton(onClick = onBack) { Text("Cancel") }
            }
        }
        return
    }
    val audioPermission = rememberPermissionState(PermissionRecordAudio)
    val cameraPermission = rememberPermissionState(PermissionCamera)
    var service by remember { mutableStateOf<VoiceCallService?>(null) }
    var cameraEnabled by remember { mutableStateOf(false) }
    var speakerEnabled by remember { mutableStateOf(true) }

    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as? VoiceCallService.LocalBinder)?.getService()
            }
            override fun onServiceDisconnected(name: ComponentName?) { service = null }
        }
    }
    DisposableEffect(conversationId) {
        if (audioPermission.allRequiredPermissionsGranted &&
            VoiceCallService.activeConversationId.value != conversationId.toString()
        ) VoiceCallService.start(context, conversationId.toString())
        context.bindService(Intent(context, VoiceCallService::class.java), connection, Context.BIND_AUTO_CREATE)
        onDispose { runCatching { context.unbindService(connection) } }
    }
    LaunchedEffect(Unit) {
        if (!audioPermission.allRequiredPermissionsGranted) audioPermission.requestPermissions()
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
    val companionFlow = conversation?.assistantId?.let { companionRepository.observe(it) }
    val companion by (companionFlow ?: MutableStateFlow(me.rerere.rikkahub.data.model.CompanionState()))
        .collectAsStateWithLifecycle(initialValue = me.rerere.rikkahub.data.model.CompanionState())
    val story = remember(uiState.assistantText, companion.character.activity) {
        storyPresentation(uiState.assistantText, companion.character.activity)
    }

    BackHandler { onBack() }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF171016), Color(0xFF382229), Color(0xFF120D10)))
        )
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Story Video Call", color = Color.White.copy(.75f), modifier = Modifier.padding(top = 24.dp))
            Spacer(Modifier.height(28.dp))
            val displayName = conversation?.title.orEmpty().ifBlank { "TA" }
            AutoAIIcon(name = displayName, modifier = Modifier.size(132.dp))
            Text(displayName, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text(
                listOf(companion.character.emotion, companion.character.location).filter { it.isNotBlank() }.joinToString(" / "),
                color = Color(0xFFFFB7C5), modifier = Modifier.padding(top = 6.dp)
            )
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                color = Color.White.copy(alpha = .09f), shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(story.action, color = Color.White.copy(.62f), textAlign = TextAlign.Center)
                    if (story.dialogue.isNotBlank()) {
                        Text(story.dialogue, color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
                    }
                    if (uiState.userTranscript.isNotBlank() && uiState.status != VoiceCallStatus.Speaking) {
                        Text("You: ${uiState.userTranscript}", color = Color.White.copy(.72f), modifier = Modifier.padding(top = 14.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (cameraEnabled) {
                Surface(color = Color.Black.copy(.45f), shape = RoundedCornerShape(18.dp), modifier = Modifier.align(Alignment.End)) {
                    Text("Front camera enabled", color = Color.White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 28.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                CallButton(if (uiState.isMuted) HugeIcons.MicOff01 else HugeIcons.Mic01, "Mute") { service?.toggleMute() }
                CallButton(HugeIcons.Camera01, if (cameraEnabled) "Camera off" else "Front camera") {
                    if (!cameraPermission.allRequiredPermissionsGranted) cameraPermission.requestPermissions()
                    else cameraEnabled = !cameraEnabled
                }
                CallButton(HugeIcons.VolumeHigh, "Speaker") {
                    speakerEnabled = !speakerEnabled
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audio.isSpeakerphoneOn = speakerEnabled
                }
                CallButton(HugeIcons.Cancel01, "Hang up", Color(0xFFE5484D)) {
                    service?.endCall(); VoiceCallService.stop(context); onBack()
                }
            }
        }
    }
}

private data class StoryPresentation(val action: String, val dialogue: String)

private fun storyPresentation(text: String, fallbackAction: String): StoryPresentation {
    val actionRegex = Regex("\\*([^*]{2,80})\\*|\\(([^()]{2,80})\\)")
    val action = actionRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
        ?: fallbackAction.ifBlank { "TA is here with you" }
    return StoryPresentation(action, text.replace(actionRegex, "").trim())
}

@Composable
private fun CallButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color = Color.White.copy(.14f), onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick = onClick, colors = IconButtonDefaults.filledIconButtonColors(containerColor = color), modifier = Modifier.size(54.dp)) {
            Icon(icon, label, tint = Color.White)
        }
        Text(label, color = Color.White.copy(.72f), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
