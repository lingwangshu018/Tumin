package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import me.rerere.rikkahub.ui.pages.voice.VideoCallSelfViewMode
import me.rerere.rikkahub.ui.pages.voice.VideoCallVisualSettings
import me.rerere.rikkahub.ui.pages.voice.VideoCallVisualSettingsStore

@Composable
fun VideoCallSettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { VideoCallVisualSettingsStore(context) }
    var settings by remember { mutableStateOf(store.read()) }

    fun update(next: VideoCallVisualSettings) {
        settings = next
        store.write(next)
    }

    fun copyPickedImage(uri: android.net.Uri, prefix: String): String? = runCatching {
        val dir = store.assetsDir()
        dir.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.img")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        file.absolutePath
    }.getOrNull()

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        copyPickedImage(uri, "user_avatar")?.let { path ->
            update(settings.copy(userAvatarPath = path))
        }
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        copyPickedImage(uri, "background")?.let { path ->
            update(settings.copy(backgroundPath = path))
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("视频设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "设置只影响视频电话画面，不会修改聊天页头像或聊天背景。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                SettingSection(title = "用户头像") {
                    ImageSettingRow(
                        path = settings.userAvatarPath,
                        emptyHint = "默认使用聊天里的用户头像",
                        onChoose = { avatarPicker.launch(arrayOf("image/*")) },
                        onClear = {
                            settings.userAvatarPath.takeIf { it.isNotBlank() }?.let { File(it).delete() }
                            update(settings.copy(userAvatarPath = ""))
                        },
                    )
                }
            }

            item {
                SettingSection(title = "通话背景") {
                    ImageSettingRow(
                        path = settings.backgroundPath,
                        emptyHint = "默认使用当前 Character 的聊天头像",
                        onChoose = { backgroundPicker.launch(arrayOf("image/*")) },
                        onClear = {
                            settings.backgroundPath.takeIf { it.isNotBlank() }?.let { File(it).delete() }
                            update(settings.copy(backgroundPath = ""))
                        },
                        widePreview = true,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("背景透明度", fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = settings.backgroundOpacity,
                            onValueChange = { update(settings.copy(backgroundOpacity = it)) },
                            valueRange = 0.2f..1f,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("${(settings.backgroundOpacity * 100).toInt()}%")
                    }
                    Text(
                        "透明度越低，背景越柔和，文字和剧情内容会更突出。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingSection(title = "通话设置") {
                    SelfViewChoice(
                        title = "用户头像",
                        subtitle = "右上角显示你选择的头像，不启用摄像头",
                        selected = settings.selfViewMode == VideoCallSelfViewMode.USER_AVATAR,
                        onClick = { update(settings.copy(selfViewMode = VideoCallSelfViewMode.USER_AVATAR)) },
                    )
                    HorizontalDivider()
                    SelfViewChoice(
                        title = "前置摄像头",
                        subtitle = "进入视频电话后使用手机前置摄像头",
                        selected = settings.selfViewMode == VideoCallSelfViewMode.FRONT_CAMERA,
                        onClick = { update(settings.copy(selfViewMode = VideoCallSelfViewMode.FRONT_CAMERA)) },
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable Column.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ImageSettingRow(
    path: String,
    emptyHint: String,
    onChoose: () -> Unit,
    onClear: () -> Unit,
    widePreview: Boolean = false,
) {
    val file = path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(
            modifier = if (widePreview) Modifier.size(width = 118.dp, height = 78.dp) else Modifier.size(78.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(if (widePreview) 78.dp else 78.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(if (widePreview) 78.dp else 78.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("默认", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(if (file != null) file.name else emptyHint, style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = onChoose) { Text(if (file == null) "选择图片" else "更换") }
                if (file != null) TextButton(onClick = onClear) { Text("恢复默认") }
            }
        }
    }
}

@Composable
private fun SelfViewChoice(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
