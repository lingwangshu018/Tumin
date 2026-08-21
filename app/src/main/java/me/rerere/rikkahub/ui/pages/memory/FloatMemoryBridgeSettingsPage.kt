package me.rerere.rikkahub.ui.pages.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.rikkahub.data.memory.FloatMemoryBridgeConfig
import me.rerere.rikkahub.data.memory.FloatMemoryBridgeSettings

private val recentContextPresets = listOf(5, 10, 20, 50)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatMemoryBridgeSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { FloatMemoryBridgeSettings(context) }
    var config by remember { mutableStateOf(store.load()) }
    var customLimitDraft by remember(config.sharedRecentContextLimit) {
        mutableStateOf(config.sharedRecentContextLimit.toString())
    }

    fun update(transform: (FloatMemoryBridgeConfig) -> FloatMemoryBridgeConfig) {
        val next = transform(config)
        config = next
        store.save(next)
    }

    fun setRecentLimit(value: Int) {
        val safe = value.coerceIn(1, 200)
        customLimitDraft = safe.toString()
        update { old -> old.copy(sharedRecentContextLimit = safe) }
    }

    val customSelected = config.sharedRecentContextLimit !in recentContextPresets

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("float 记忆互通") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(HugeIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                SectionTitle("记忆互通")
                ToggleRow("float 记忆互通", "允许兔眠与 float 按下面的权限共享记忆", config.enabled) {
                    update { old -> old.copy(enabled = it) }
                }
            }
            item {
                SectionTitle("短期上下文")
                ToggleRow("允许兔眠读取 float 近期记忆", "兔眠聊天时可临时借用 float 的近期上下文", config.allowTuminReadFloatRecent) {
                    update { old -> old.copy(allowTuminReadFloatRecent = it) }
                }
                ToggleRow("允许 float 读取兔眠近期记忆", "只提供上下文，不自动写入对方短期记忆库", config.allowFloatReadTuminRecent) {
                    update { old -> old.copy(allowFloatReadTuminRecent = it) }
                }
            }
            item {
                SectionTitle("共享最近上下文")
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("控制跨应用最多共享多少条近期上下文", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentContextPresets.forEach { preset ->
                            FilterChip(
                                selected = config.sharedRecentContextLimit == preset,
                                onClick = { setRecentLimit(preset) },
                                label = { Text("$preset 条") },
                            )
                        }
                    }
                    FilterChip(
                        selected = customSelected,
                        onClick = {
                            if (!customSelected) setRecentLimit(30)
                        },
                        label = { Text("自定义") },
                    )
                    if (customSelected) {
                        OutlinedTextField(
                            value = customLimitDraft,
                            onValueChange = { raw ->
                                val digits = raw.filter(Char::isDigit).take(3)
                                customLimitDraft = digits
                                digits.toIntOrNull()?.let(::setRecentLimit)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("共享条数") },
                            supportingText = { Text("1–200 条") },
                        )
                    }
                }
            }
            item {
                SectionTitle("长期记忆")
                ToggleRow("允许兔眠读取 float 长期记忆", "允许兔眠按需读取 float 已保存的长期记忆", config.allowTuminReadFloatLongTerm) {
                    update { old -> old.copy(allowTuminReadFloatLongTerm = it) }
                }
                ToggleRow("允许 float 读取兔眠长期记忆", "允许 float 按需读取兔眠的长期记忆", config.allowFloatReadTuminLongTerm) {
                    update { old -> old.copy(allowFloatReadTuminLongTerm = it) }
                }
                ToggleRow("自动同步重要长期记忆", "重要长期记忆自动同步；默认关闭", config.autoSyncImportantLongTerm) {
                    update { old -> old.copy(autoSyncImportantLongTerm = it) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}
