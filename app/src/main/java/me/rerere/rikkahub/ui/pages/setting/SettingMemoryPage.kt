Exit code: 0
Wall time: 0.4 seconds
Output:
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
    NATURAL("鑷劧锛堟帹鑽愶級"), SAVER("鐪?Token"), STRONG("寮鸿蹇?), CUSTOM("鑷畾涔?)
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
            title = { Text("娓呯┖杩戞湡鐢熸椿娴侊紵") },
            text = { Text("鍙細娓呴櫎姝よ鑹茬殑璺ㄧ獥鍙ｈ繎鏈熷唴瀹逛笌鍘嬬缉鎽樿锛屼笉浼氬垹闄ゆ牳蹇冭韩浠藉拰闀挎湡璁板繂銆?) },
            confirmButton = {
                TextButton(onClick = {
                    store.clearAssistant(assistant.id.toString())
                    recentRefresh++
                    confirmClear = false
                }) { Text("娓呯┖") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("鍙栨秷") } },
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("璁板繂") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        if (assistant == null) {
            Column(Modifier.padding(padding).padding(24.dp)) { Text("璇峰厛鍒涘缓涓€涓鑹层€?) }
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
                Text("璁板繂鎺у埗涓績", style = MaterialTheme.typography.headlineSmall)
                Text("AI 甯綘璁帮紝浣嗘渶缁堣В閲婃潈濮嬬粓鍦ㄤ綘鎵嬮噷銆?, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Select(
                    options = assistants,
                    selectedOption = assistant,
                    onOptionSelected = { selectedId = it.id.toString() },
                    optionToString = { it.name.ifBlank { "鏈懡鍚嶈鑹? } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                CardGroup(title = { Text("璁板繂绛栫暐") }) {
                    item(
                        headlineContent = { Text(strategy.label) },
                        supportingContent = { Text("鑷劧锛氫笁灞傚钩琛★紱鐪?Token锛氭洿鏃╁帇缂┿€佹洿灏戝彫鍥烇紱寮鸿蹇嗭細淇濈暀鍜屽彫鍥炴洿澶氥€?) },
                        trailingContent = {
                            Select(
                                options = MemoryStrategy.entries,
                                selectedOption = strategy,
                                onOptionSelected = { chosen -> updateAssistant { applyStrategy(it, chosen) } },
                                optionToString = { it.label },
                            )
                        },
                    )
                }
            }
            item {
                CardGroup(title = { Text("鏍稿績韬唤 路 甯搁┗") }) {
                    item(
                        headlineContent = { Text("瑙掕壊鏍稿績韬唤") },
                        supportingContent = { Text("姣忚疆甯搁┗锛屽彧鏈変綘鑳藉湪杩欓噷淇敼銆備繚瀛樺悗浼氫紭鍏堜簬鑷姩璁板繂銆?) },
                    )
                }
                OutlinedTextField(
                    value = identityDraft,
                    onValueChange = { identityDraft = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 5,
                    label = { Text("韬唤銆佸叧绯汇€侀噸瑕佺害瀹氫笌涓嶅彲杩濊儗鐨勫亸濂?) },
                )
                Button(
                    onClick = { updateAssistant { it.copy(systemPrompt = identityDraft.trim()) } },
                    enabled = identityDraft.trim() != assistant.systemPrompt,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("淇濆瓨鏍稿績韬唤") }
            }
            item {
                CardGroup(title = { Text("杩戞湡鐢熸椿娴?路 澧為噺娉ㄥ叆") }) {
                    item(
                        headlineContent = { Text("璺ㄧ獥鍙ｈ繎鏈熻蹇?) },
                        supportingContent = { Text("鏈帇缂╁熬宸翠細鐩存帴娉ㄥ叆锛屽洜姝ゅ垰鍙戠敓鐨勫唴瀹逛笉浼氱瓑寰呮憳瑕併€?) },
                        trailingContent = {
                            Switch(
                                checked = assistant.enableCrossWindowMemory,
                                onCheckedChange = { enabled -> updateAssistant { it.copy(enableCrossWindowMemory = enabled) } },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("鍚庡彴鑷姩鍘嬬缉") },
                        supportingContent = { Text("鍙帇鐢ㄦ埛涓庡姪鎵嬫鏂囷紝涓嶅彂閫佸伐鍏疯皟鐢ㄣ€佹帹鐞嗘垨鎶€鏈瓧娈点€?) },
                        trailingContent = {
                            Switch(
                                checked = assistant.enableCrossWindowMemoryCompression,
                                onCheckedChange = { enabled -> updateAssistant { it.copy(enableCrossWindowMemoryCompression = enabled) } },
                            )
                        },
                    )
                }
                if (recent.isEmpty()) {
                    Text("杩樻病鏈夎繎鏈熺敓娲绘祦銆傚紑濮嬭亰澶╁悗浼氭寜瑙掕壊鑷姩璁板綍銆?, modifier = Modifier.padding(12.dp))
                } else {
                    recent.take(8).forEach { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.text, maxLines = 4)
                                Text(
                                    "涓轰粈涔?TA 璁板緱锛氭潵婧愪簬鑱婂ぉ 路 ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.timestamp))} 路 杩戞湡鐢熸椿娴?,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { confirmClear = true }, enabled = recent.isNotEmpty()) { Text("娓呯┖姝よ鑹茬殑杩戞湡鐢熸椿娴?) }
            }
            item {
                CardGroup(title = { Text("闀挎湡璁板繂 路 鐩稿叧鍙洖") }) {
                    item(
                        onClick = { nav.navigate(Screen.AssistantMemory(assistant.id.toString())) },
                        headlineContent = { Text("绠＄悊闀挎湡璁板繂") },
                        supportingContent = { Text("鏂板銆佷慨鏀规垨鍒犻櫎璁板繂锛涗笁灞傛ā寮忎笅鍙彫鍥炰笌褰撳墠鑱婂ぉ鐩稿叧鐨勫唴瀹广€?) },
                    )
                    item(
                        headlineContent = { Text("鍚敤闀挎湡璁板繂") },
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
                CardGroup(title = { Text("楂樼骇璁剧疆") }) {
                    item(
                        headlineContent = { Text("涓夊眰璁板繂") },
                        supportingContent = { Text("鏍稿績甯搁┗ + 杩戞湡澧為噺 + 闀挎湡鐩稿叧鍙洖") },
                        trailingContent = {
                            Switch(
                                checked = assistant.enableThreeLayerMemory,
                                onCheckedChange = { enabled -> updateAssistant { it.copy(enableThreeLayerMemory = enabled) } },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("鍘嬬缉闃堝€?) },
                        supportingContent = { Text("${assistant.crossWindowMemoryCompressionThresholdChars} 瀛楃") },
                    )
                    item(
                        headlineContent = { Text("鏈帇缂╁熬宸?) },
                        supportingContent = { Text("淇濈暀 ${assistant.crossWindowMemoryTailEntries} 鏉?) },
                    )
                    item(
                        headlineContent = { Text("闀挎湡鍙洖") },
                        supportingContent = { Text("鏈€澶?${assistant.longTermMemoryRecallCount} 鏉?/ ${assistant.longTermMemoryMaxChars} 瀛楃") },
                    )
                    item(
                        headlineContent = { Text("Recent Chats Reference 鍥為€€") },
                        supportingContent = { Text("浠呭湪璺ㄧ獥鐢熸椿娴佸叧闂椂鎵嶄娇鐢紝閬垮厤姣忚疆閲嶅濉炴渶杩戣亰澶┿€?) },
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

