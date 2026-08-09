package me.rerere.rikkahub.ui.pages.life

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.json.JSONArray
import org.json.JSONObject

private enum class LifeSection(val title: String, val emoji: String, val hint: String) {
    STATUS("身体状态", "🌸", "记录心情、精力和身体感受"),
    MEMO("备忘录", "📝", "保存我的、AI 的和共同计划"),
    MUSIC("音乐记忆", "🎵", "收藏一起听过的歌和当时的心情"),
    READING("共读书架", "📖", "记录书籍、进度、书签和共同想法"),
}

private data class LifeEntry(
    val id: Long = System.currentTimeMillis(),
    val section: LifeSection,
    val title: String,
    val detail: String,
    val tag: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeHubPage() {
    val context = LocalContext.current
    var section by remember { mutableStateOf(LifeSection.STATUS) }
    var entries by remember { mutableStateOf(loadEntries(context)) }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("生活空间") }, navigationIcon = { BackButton() }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Text("＋") } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = section.ordinal) {
                LifeSection.entries.forEach { item ->
                    Tab(
                        selected = section == item,
                        onClick = { section = item },
                        text = { Text(item.emoji + " " + item.title) },
                    )
                }
            }
            val filtered = entries.filter { it.section == section }.sortedByDescending { it.createdAt }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(section.emoji + " " + section.title, style = MaterialTheme.typography.headlineSmall)
                            Text(section.hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (filtered.isEmpty()) item {
                    Text("这里还没有记录，点击右下角开始。", modifier = Modifier.padding(8.dp))
                }
                items(filtered, key = { it.id }) { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(entry.title, style = MaterialTheme.typography.titleLarge)
                                if (entry.tag.isNotBlank()) Text(entry.tag, color = MaterialTheme.colorScheme.primary)
                            }
                            if (entry.detail.isNotBlank()) Text(entry.detail)
                            TextButton(onClick = {
                                entries = entries.filterNot { it.id == entry.id }
                                saveEntries(context, entries)
                            }) { Text("删除") }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) AddLifeEntryDialog(
        section = section,
        onDismiss = { showAdd = false },
        onSave = { title, detail, tag ->
            entries = entries + LifeEntry(section = section, title = title, detail = detail, tag = tag)
            saveEntries(context, entries)
            showAdd = false
        },
    )
}

@Composable
private fun AddLifeEntryDialog(
    section: LifeSection,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var title by remember(section) { mutableStateOf("") }
    var detail by remember(section) { mutableStateOf("") }
    var tag by remember(section) { mutableStateOf("") }
    val labels = when (section) {
        LifeSection.STATUS -> Triple("今天感觉怎么样", "身体感受或想让 AI 知道的事", "心情 / 精力")
        LifeSection.MEMO -> Triple("备忘标题", "计划或想法", "我的 / AI / 我们的")
        LifeSection.MUSIC -> Triple("歌曲名", "歌手、故事或一起听歌的回忆", "想念 / 开心 / 安慰")
        LifeSection.READING -> Triple("书名", "阅读进度、批注或共同观点", "在读 / 想读 / 读完")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加${section.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(labels.first) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(detail, { detail = it }, label = { Text(labels.second) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tag, { tag = it }, label = { Text(labels.third) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onSave(title.trim(), detail.trim(), tag.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun loadEntries(context: Context): List<LifeEntry> = runCatching {
    val raw = context.getSharedPreferences("tumin_life_hub", Context.MODE_PRIVATE).getString("entries", "[]") ?: "[]"
    val array = JSONArray(raw)
    buildList {
        repeat(array.length()) { index ->
            val item = array.getJSONObject(index)
            add(
                LifeEntry(
                    id = item.getLong("id"),
                    section = LifeSection.valueOf(item.getString("section")),
                    title = item.getString("title"),
                    detail = item.optString("detail"),
                    tag = item.optString("tag"),
                    createdAt = item.optLong("createdAt", item.getLong("id")),
                ),
            )
        }
    }
}.getOrDefault(emptyList())

private fun saveEntries(context: Context, entries: List<LifeEntry>) {
    val array = JSONArray()
    entries.forEach { entry ->
        array.put(JSONObject().apply {
            put("id", entry.id)
            put("section", entry.section.name)
            put("title", entry.title)
            put("detail", entry.detail)
            put("tag", entry.tag)
            put("createdAt", entry.createdAt)
        })
    }
    context.getSharedPreferences("tumin_life_hub", Context.MODE_PRIVATE).edit().putString("entries", array.toString()).apply()
}
