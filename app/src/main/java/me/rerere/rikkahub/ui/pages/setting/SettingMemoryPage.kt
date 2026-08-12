package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.memory.CrossWindowMemoryStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date

private enum class MemoryStrategy(val label: String) {
    NATURAL("自然（推荐）"), SAVER("省 Token"), STRONG("强记忆"), CUSTOM("自定义")
}

@Composable
fun SettingMemoryPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val context = LocalContext.current
    val store = remember { CrossWindowMemoryStore(context) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val assistants = settings.assistants
    val assistant = assistants.firstOrNull { it.id.toString() == selectedId } ?: assistants.firstOrNull()
    var identityDraft by remember(assistant?.id, assistant?.systemPrompt) {
        mutableStateOf(assistant?.systemPrompt.orEmpty())
    }
    var recentRefresh by remember { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun updateAssistant(transform: (Assistant) -> Assistant) {
        val current = assistant ?: return
        vm.updateSettings(settings.copy(assistants = assistants.map { if (it.id == current.id) transform(it) else it }))
    }

    if (confirmClear && assistant != null) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空近期生活流？") },
            text = { Text("只会清除此角色的跨窗口近期内容与压缩摘要，不会删除核心身份和长期记忆。") },
            confirmButton = {
                TextButton(onClick = {
                    store.clearAssistant(assistant.id.toString())
                    recentRefresh++
                    confirmClear = false
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("记忆") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        if (assistant == null) {
            Column(Modifier.padding(padding).padding(24.dp)) { Text("请先创建一个角色。") }
            return@Scaffold
        }
        val strategy = detectStrategy(assistant)
        val recent = remember(assistant.id, recentRefresh) { store.peekRecent(assistant.id.toString(), 30).reversed() }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("记忆控制中心", style = MaterialTheme.typography.headlineSmall)
                Text("AI 帮你记，但最终解释权始终在你手里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Select(
                    options = assistants,
                    selectedOption = assistant,
                    onOptionSelected = { selectedId = it.id.toString() },
                    optionToString = { it.name.ifBlank { "未命名角色" } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                CardGroup(title = { Text("记忆策略") }) {
                    item(
                        headlineContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("选择记忆策略")
                                Text(
                                    "自然：三层平衡；省 Token：更早压缩、更少召回；强记忆：保留和召回更多。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Select(
                                    options = MemoryStrategy.entries,
                                    selectedOption = strategy,
                                    onOptionSelected = { chosen -> updateAssistant { applyStrategy(it, chosen) } },
                                    optionToString = { it.label },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                    )
                }
            }
            item {
                CardGroup(title = { Text("核心身份 · 常驻") }) {
                    item(
                        headlineContent = { Text("角色核心身份") },
                        supportingContent = { Text("每轮常驻，只有你能在这里修改。保存后会优先于自动记忆。") },
                    )
                }
                OutlinedTextField(
                    value = identityDraft,
                    onValueChange = { identityDraft = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 5,
                    label = { Text("身份、关系、重要约定与不可违背的偏好") },
                )
                Button(
                    onClick = { updateAssistant { it.copy(systemPrompt = identityDraft.trim()) } },
                    enabled = identityDraft.trim() != assistant.systemPrompt,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("保存核心身份") }
            }
            item {
                CardGroup(title = { Text("近期生活流 · 增量注入") }) {
                    item(
                        headlineContent = { Text("跨窗口近期记忆") },
                        supportingContent = { Text("未压缩尾巴会直接注入，因此刚发生的内容不会等待摘要。") },
                        trailingContent = {
                            Switch(
                                checked = assistant.enableCrossWindowMemory,
                                onCheckedChange = { enabled -> updateAssistant { it.copy(enableCrossWindowMemory = enabled) } },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("后台自动压缩") },
                        supportingContent = { Text("只压用户与助手正文，不发送工具调用、推理或技术字段。") },
                        trailingContent = {
                            Switch(
                                checked = assistant.enableCrossWindowMemoryCompression,
                                onCheckedChange = { enabled -> updateAssistant { it.copy(enableCrossWindowMemoryCompression = enabled) } },
                            )
                        },
                    )
                }
                if (recent.isEmpty()) {
                    Text("还没有近期生活流。开始聊天后会按角色自动记录。", modifier = Modifier.padding(12.dp))
                } else {
                    recent.take(8).forEach { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.text, maxLines = 4)
                                Text(
                                    "为什么 TA 记得：来源于聊天 · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.timestamp))} · 近期生活流",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { confirmClear = true }, enabled = recent.isNotEmpty()) { Text("清空此角色的近期生活流") }
            }
            item {
                CardGroup(title = { Text("长期记忆 · 相关召回") }) {
                    item(
                        onClick = { nav.navigate(Screen.AssistantMemory(assistant.id.toString())) },
                        headlineContent = { Text("管理长期记忆") },
                        supportingContent = { Text("新增、修改或删除记忆；三层模式下只召回与当前聊天相关的内容。") },
                    )
                    item(
                        headlineContent = { Text("启用长期记忆") },
                        trailingContent = {
                            Switch(
                                checked = assistant.enableMemory,
                                onCheckedChange = { enabled -> updateAssistant { it.copy(enableMemory = enabled) } },
                            )
                        },
                    )
                }
            }
            item {
                CardGroup(title = { Text("高级设置") }) {
                    item(
                        headlineContent = { Text("三层记忆") },
                        supportingContent = { Text("核心常驻 + 近期增量 + 长期相关召回") },
                        trailingContent = {
                            Switch(
                                checked = assistant.enableThreeLayerMemory,
                                onCheckedChange = { enabled -> updateAssistant { it.copy(enableThreeLayerMemory = enabled) } },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("压缩阈值") },
                        supportingContent = { Text("${assistant.crossWindowMemoryCompressionThresholdChars} 字符") },
                    )
                    item(
                        headlineContent = { Text("未压缩尾巴") },
                        supportingContent = { Text("保留 ${assistant.crossWindowMemoryTailEntries} 条") },
                    )
                    item(
                        headlineContent = { Text("长期召回") },
                        supportingContent = { Text("最多 ${assistant.longTermMemoryRecallCount} 条 / ${assistant.longTermMemoryMaxChars} 字符") },
                    )
                    item(
                        headlineContent = { Text("Recent Chats Reference 回退") },
                        supportingContent = { Text("仅在跨窗生活流关闭时才使用，避免每轮重复塞最近聊天。") },
                        trailingContent = {
                            Switch(
                                checked = assistant.useRecentChatsAsFallback,
                                onCheckedChange = { enabled -> updateAssistant { it.copy(useRecentChatsAsFallback = enabled) } },
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun detectStrategy(a: Assistant): MemoryStrategy = when {
    a.crossWindowMemoryCompressionThresholdChars == 8000 && a.crossWindowMemoryTailEntries == 10 && a.longTermMemoryRecallCount == 4 -> MemoryStrategy.SAVER
    a.crossWindowMemoryCompressionThresholdChars == 20000 && a.crossWindowMemoryTailEntries == 24 && a.longTermMemoryRecallCount == 10 -> MemoryStrategy.STRONG
    a.crossWindowMemoryCompressionThresholdChars == 12000 && a.crossWindowMemoryTailEntries == 16 && a.longTermMemoryRecallCount == 6 -> MemoryStrategy.NATURAL
    else -> MemoryStrategy.CUSTOM
}

private fun applyStrategy(a: Assistant, strategy: MemoryStrategy): Assistant = when (strategy) {
    MemoryStrategy.NATURAL -> a.copy(enableThreeLayerMemory = true, enableCrossWindowMemory = true, enableCrossWindowMemoryCompression = true, crossWindowMemoryCompressionThresholdChars = 12000, crossWindowMemoryTailEntries = 16, longTermMemoryRecallCount = 6, longTermMemoryMaxChars = 3000)
    MemoryStrategy.SAVER -> a.copy(enableThreeLayerMemory = true, enableCrossWindowMemory = true, enableCrossWindowMemoryCompression = true, crossWindowMemoryCompressionThresholdChars = 8000, crossWindowMemoryTailEntries = 10, longTermMemoryRecallCount = 4, longTermMemoryMaxChars = 1800)
    MemoryStrategy.STRONG -> a.copy(enableThreeLayerMemory = true, enableCrossWindowMemory = true, enableCrossWindowMemoryCompression = true, crossWindowMemoryCompressionThresholdChars = 20000, crossWindowMemoryTailEntries = 24, longTermMemoryRecallCount = 10, longTermMemoryMaxChars = 5000)
    MemoryStrategy.CUSTOM -> a
}
