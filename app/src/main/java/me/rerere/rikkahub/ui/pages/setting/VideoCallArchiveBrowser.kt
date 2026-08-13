package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.pages.voice.VideoCallArchiveEntry
import me.rerere.rikkahub.ui.pages.voice.VideoCallArchiveStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val archiveTimeFormatter = DateTimeFormatter.ofPattern("MM月dd日 HH:mm")

@Composable
fun VideoCallArchiveBrowserContent(
    store: VideoCallArchiveStore,
    onBack: () -> Unit,
) {
    var archives by remember { mutableStateOf(store.list()) }
    var selected by remember { mutableStateOf<VideoCallArchiveEntry?>(null) }

    val detail = selected
    if (detail != null) {
        VideoCallArchiveDetail(
            archive = detail,
            onBack = { selected = null },
            onDelete = {
                store.deleteSession(detail.id)
                archives = store.list()
                selected = null
            },
        )
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                "视频通话记录",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            "每场视频电话都会保存当时的可见聊天正文。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))

        if (archives.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    "还没有视频通话记录。第一次挂断视频电话后会出现在这里。",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(archives, key = { it.id }) { archive ->
                    Card(
                        onClick = { selected = archive },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                "与 ${archive.assistantName.ifBlank { "TA" }} 的视频通话",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                formatArchiveTime(archive.startedAtEpochMillis),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${formatArchiveDuration(archive)} · ${archive.messages.size} 条记录" +
                                    if (archive.endedNormally) "" else " · 异常结束",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
}

@Composable
private fun VideoCallArchiveDetail(
    archive: VideoCallArchiveEntry,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Column(Modifier.weight(1f)) {
                Text(
                    archive.assistantName.ifBlank { "视频通话" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${formatArchiveTime(archive.startedAtEpochMillis)} · ${formatArchiveDuration(archive)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDelete) { Text("删除") }
        }

        if (!archive.endedNormally) {
            Text(
                "这场通话没有正常挂断，已保留退出前最后一次成功写入的内容。",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(archive.messages) { message ->
                val isUser = message.role == "user"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(0.86f),
                        shape = RoundedCornerShape(18.dp),
                        color = if (isUser)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                            Text(
                                if (isUser) "你" else archive.assistantName.ifBlank { "TA" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(message.text, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            if (archive.messages.isEmpty()) {
                item {
                    Text(
                        "这场通话没有产生可保存的文字记录。",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

private fun formatArchiveTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(archiveTimeFormatter)

private fun formatArchiveDuration(archive: VideoCallArchiveEntry): String {
    val end = archive.endedAtEpochMillis ?: System.currentTimeMillis()
    val seconds = max(0L, (end - archive.startedAtEpochMillis) / 1000L)
    val minutes = seconds / 60
    val restSeconds = seconds % 60
    return if (minutes > 0) "${minutes}分${restSeconds}秒" else "${restSeconds}秒"
}
