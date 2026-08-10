package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.context.LocalCurrentAssistant
import me.rerere.rikkahub.utils.StickerAiSupport

@Composable
fun StickerAiPicker(
    modifier: Modifier = Modifier,
    height: Int = 320,
    onStickerSelected: (name: String, url: String) -> Unit,
) {
    val context = LocalContext.current
    val assistant = LocalCurrentAssistant.current
    val assistantId = assistant.id.toString()
    var showAiSettings by remember { mutableStateOf(false) }
    var summaries by remember(assistantId) { mutableStateOf(emptyList<StickerAiSupport.PackSummary>()) }

    Column(modifier.height(height.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.fillMaxWidth(0.62f)) {
                    Text("TA 的表情权限", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text("${assistant.name} 只会看到你勾选的分类", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(
                    onClick = {
                        summaries = StickerAiSupport.getPackSummaries(context, assistantId)
                        showAiSettings = true
                    },
                    shape = RoundedCornerShape(14.dp),
                ) { Text("AI 可用分类") }
            }
        }

        StickerUrlPicker(
            modifier = Modifier.fillMaxWidth(),
            height = (height - 52).coerceAtLeast(180),
            onStickerSelected = onStickerSelected,
        )
    }

    if (showAiSettings) {
        AlertDialog(
            onDismissRequest = { showAiSettings = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("${assistant.name} 可以用哪些表情包？") },
            text = {
                if (summaries.isEmpty()) {
                    Text("还没有表情分类。先从 URL 或相册导入一个表情包吧。")
                } else {
                    LazyColumn(
                        modifier = Modifier.height((summaries.size * 66).coerceAtMost(360).dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(summaries, key = { it.id }) { pack ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(Modifier.fillMaxWidth(0.78f)) {
                                        Text(pack.name, fontWeight = FontWeight.SemiBold)
                                        Text("${pack.stickerCount} 张表情", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = pack.aiEnabled,
                                        onCheckedChange = { enabled ->
                                            StickerAiSupport.setPackAiEnabled(
                                                context = context,
                                                assistantId = assistantId,
                                                packId = pack.id,
                                                enabled = enabled,
                                            )
                                            summaries = summaries.map {
                                                if (it.id == pack.id) it.copy(aiEnabled = enabled) else it
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAiSettings = false }) { Text("完成") } },
        )
    }
}
