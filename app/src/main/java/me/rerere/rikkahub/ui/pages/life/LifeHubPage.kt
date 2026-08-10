package me.rerere.rikkahub.ui.pages.life

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

private enum class LifeSection(val title: String, val emoji: String, val hint: String) {
    HOME("今日", "🏡", "今天的状态、安排与共同生活"),
    STATUS("身体状态", "🌸", "记录心情、精力和身体感受"),
    MEMO("备忘录", "📝", "把想法、待办和两个人的小计划好好收起来"),
    CALENDAR("日历提醒", "📅", "把计划和纪念日加入系统日历"),
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
    val memoCategory: String = "生活",
    val pinned: Boolean = false,
    val completed: Boolean = false,
    val reminderAt: Long? = null,
)

private data class MemoCategory(
    val id: String,
    val label: String,
    val emoji: String,
    val background: Color,
    val accent: Color,
)

private val memoCategories = listOf(
    MemoCategory("life", "生活", "☁", Color(0xFFFFF5E8), Color(0xFFAA7A42)),
    MemoCategory("todo", "待办", "✓", Color(0xFFEAF4EE), Color(0xFF5D806B)),
    MemoCategory("idea", "灵感", "✦", Color(0xFFF1EDFA), Color(0xFF7966A1)),
    MemoCategory("together", "我们的", "♡", Color(0xFFFFEDF3), Color(0xFFB85E7A)),
    MemoCategory("ai", "TA 的", "✉", Color(0xFFEAF3FA), Color(0xFF5A7F9A)),
)

private fun memoCategory(id: String): MemoCategory =
    memoCategories.firstOrNull { it.id == id } ?: memoCategories.first()

private enum class MemoFilter { ALL, PINNED, TODO, DONE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeHubPage() {
    val context = LocalContext.current
    var section by remember { mutableStateOf(LifeSection.HOME) }
    var entries by remember { mutableStateOf(loadEntries(context)) }
    var showAdd by remember { mutableStateOf(false) }
    var editingMemoId by remember { mutableStateOf<Long?>(null) }

    val saveAll: (List<LifeEntry>) -> Unit = { updated ->
        entries = updated
        saveEntries(context, updated)
    }

    val bookImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "导入的小说"
            val preview = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(800) }
            }.getOrNull().orEmpty()
            saveAll(entries + LifeEntry(section = LifeSection.READING, title = name, detail = preview, tag = "刚刚导入"))
            section = LifeSection.READING
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("生活空间") }, navigationIcon = { BackButton() }) },
        floatingActionButton = {
            if (section != LifeSection.HOME) FloatingActionButton(onClick = { showAdd = true }) { Text("＋") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = section.ordinal, edgePadding = 8.dp) {
                LifeSection.entries.forEach { item ->
                    Tab(
                        selected = section == item,
                        onClick = { section = item },
                        text = { Text(item.emoji + " " + item.title) },
                    )
                }
            }

            if (section == LifeSection.MEMO) {
                MemoBoard(
                    entries = entries.filter { it.section == LifeSection.MEMO },
                    onAdd = { showAdd = true },
                    onEdit = { editingMemoId = it.id },
                    onPin = { entry -> saveAll(entries.map { if (it.id == entry.id) it.copy(pinned = !it.pinned) else it }) },
                    onToggleDone = { entry -> saveAll(entries.map { if (it.id == entry.id) it.copy(completed = !it.completed) else it }) },
                    onDelete = { entry -> saveAll(entries.filterNot { it.id == entry.id }) },
                    onAddCalendar = { openMemoCalendar(context, it) },
                )
            } else {
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
                    if (section == LifeSection.READING) item {
                        TextButton(onClick = { bookImporter.launch(arrayOf("text/plain", "text/*")) }) {
                            Text("＋ 导入 TXT 小说")
                        }
                    }
                    if (section == LifeSection.HOME) {
                        items(LifeSection.entries.filterNot { it == LifeSection.HOME }) { destination ->
                            val latest = entries.filter { it.section == destination }.maxByOrNull { it.createdAt }
                            Card(onClick = { section = destination }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(destination.emoji + " " + destination.title, style = MaterialTheme.typography.titleLarge)
                                    Text(latest?.title ?: destination.hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else if (filtered.isEmpty()) item {
                        Text("这里还没有记录，点击右下角开始。", modifier = Modifier.padding(8.dp))
                    }
                    if (section != LifeSection.HOME) items(filtered, key = { it.id }) { entry ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(entry.title, style = MaterialTheme.typography.titleLarge)
                                    if (entry.tag.isNotBlank()) Text(entry.tag, color = MaterialTheme.colorScheme.primary)
                                }
                                if (entry.detail.isNotBlank()) Text(entry.detail)
                                if (entry.section == LifeSection.CALENDAR) TextButton(onClick = { openCalendar(context, entry) }) {
                                    Text("添加到系统日历")
                                }
                                if (entry.section == LifeSection.MUSIC) TextButton(onClick = { openMusic(context, entry.title) }) {
                                    Text("一起听这首歌")
                                }
                                TextButton(onClick = { saveAll(entries.filterNot { it.id == entry.id }) }) { Text("删除") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        if (section == LifeSection.MEMO) {
            MemoEditorDialog(
                initial = null,
                onDismiss = { showAdd = false },
                onSave = { title, detail, category, pinned, reminderAt ->
                    saveAll(
                        entries + LifeEntry(
                            section = LifeSection.MEMO,
                            title = title,
                            detail = detail,
                            tag = "",
                            memoCategory = category,
                            pinned = pinned,
                            reminderAt = reminderAt,
                        )
                    )
                    showAdd = false
                },
            )
        } else {
            AddLifeEntryDialog(
                section = section,
                onDismiss = { showAdd = false },
                onSave = { title, detail, tag ->
                    saveAll(entries + LifeEntry(section = section, title = title, detail = detail, tag = tag))
                    showAdd = false
                },
            )
        }
    }

    editingMemoId?.let { id ->
        entries.firstOrNull { it.id == id }?.let { entry ->
            MemoEditorDialog(
                initial = entry,
                onDismiss = { editingMemoId = null },
                onSave = { title, detail, category, pinned, reminderAt ->
                    saveAll(entries.map {
                        if (it.id == entry.id) it.copy(
                            title = title,
                            detail = detail,
                            memoCategory = category,
                            pinned = pinned,
                            reminderAt = reminderAt,
                        ) else it
                    })
                    editingMemoId = null
                },
            )
        }
    }
}

@Composable
private fun MemoBoard(
    entries: List<LifeEntry>,
    onAdd: () -> Unit,
    onEdit: (LifeEntry) -> Unit,
    onPin: (LifeEntry) -> Unit,
    onToggleDone: (LifeEntry) -> Unit,
    onDelete: (LifeEntry) -> Unit,
    onAddCalendar: (LifeEntry) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(MemoFilter.ALL) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }

    val filtered = entries
        .asSequence()
        .filter {
            query.isBlank() || it.title.contains(query, true) || it.detail.contains(query, true)
        }
        .filter {
            when (filter) {
                MemoFilter.ALL -> true
                MemoFilter.PINNED -> it.pinned
                MemoFilter.TODO -> !it.completed
                MemoFilter.DONE -> it.completed
            }
        }
        .filter { categoryFilter == null || it.memoCategory == categoryFilter }
        .sortedWith(compareByDescending<LifeEntry> { it.pinned }.thenBy { it.completed }.thenByDescending { it.createdAt })
        .toList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp, 16.dp, 14.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF4C536B),
                shadowElevation = 4.dp,
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("✦  LIFE MEMO", color = Color(0xFFDDE4FF), style = MaterialTheme.typography.labelLarge)
                    Text("把脑袋里的小事放在这里", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${entries.count { !it.completed }} 件待处理 · ${entries.count { it.pinned }} 张置顶便签 · ${entries.count { it.completed }} 件已完成", color = Color.White.copy(alpha = 0.72f))
                    FilledTonalButton(onClick = onAdd) { Text("＋ 写一张新便签") }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索标题或内容……") },
                singleLine = true,
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = filter == MemoFilter.ALL, onClick = { filter = MemoFilter.ALL }, label = { Text("全部 ${entries.size}") }) }
                item { FilterChip(selected = filter == MemoFilter.PINNED, onClick = { filter = MemoFilter.PINNED }, label = { Text("📌 置顶") }) }
                item { FilterChip(selected = filter == MemoFilter.TODO, onClick = { filter = MemoFilter.TODO }, label = { Text("待办") }) }
                item { FilterChip(selected = filter == MemoFilter.DONE, onClick = { filter = MemoFilter.DONE }, label = { Text("已完成") }) }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { AssistChip(onClick = { categoryFilter = null }, label = { Text("所有分类") }) }
                items(memoCategories) { category ->
                    FilterChip(
                        selected = categoryFilter == category.id,
                        onClick = { categoryFilter = if (categoryFilter == category.id) null else category.id },
                        label = { Text("${category.emoji} ${category.label}") },
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                Text(
                    if (entries.isEmpty()) "还没有便签。想到什么就先写下来，不用怕忘掉。" else "没有找到符合条件的便签。",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(filtered, key = { it.id }) { entry ->
            MemoCard(
                entry = entry,
                onEdit = { onEdit(entry) },
                onPin = { onPin(entry) },
                onToggleDone = { onToggleDone(entry) },
                onDelete = { onDelete(entry) },
                onAddCalendar = { onAddCalendar(entry) },
            )
        }
    }
}

@Composable
private fun MemoCard(
    entry: LifeEntry,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
    onAddCalendar: () -> Unit,
) {
    val category = memoCategory(entry.memoCategory)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = category.background),
        border = if (entry.pinned) BorderStroke(1.5.dp, category.accent.copy(alpha = 0.55f)) else null,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(category.emoji, color = category.accent)
                        Text(category.label, style = MaterialTheme.typography.labelLarge, color = category.accent)
                        if (entry.pinned) Text("📌", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (entry.completed) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (entry.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
                TextButton(onClick = onPin, colors = ButtonDefaults.textButtonColors(contentColor = category.accent)) {
                    Text(if (entry.pinned) "取消置顶" else "置顶")
                }
            }
            if (entry.detail.isNotBlank()) {
                Text(entry.detail, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (entry.completed) 0.55f else 0.82f))
            }
            entry.reminderAt?.let {
                Surface(shape = RoundedCornerShape(10.dp), color = category.accent.copy(alpha = 0.10f)) {
                    Text("⏰ ${formatMemoDate(it)}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = category.accent, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggleDone, colors = ButtonDefaults.textButtonColors(contentColor = category.accent)) {
                    Text(if (entry.completed) "↩ 恢复待办" else "✓ 标记完成")
                }
                Row {
                    if (entry.reminderAt != null) TextButton(onClick = onAddCalendar) { Text("加到日历") }
                    TextButton(onClick = onEdit) { Text("编辑") }
                    TextButton(onClick = onDelete) { Text("删除") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoEditorDialog(
    initial: LifeEntry?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Boolean, Long?) -> Unit,
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var detail by remember(initial?.id) { mutableStateOf(initial?.detail.orEmpty()) }
    var category by remember(initial?.id) { mutableStateOf(initial?.memoCategory ?: "life") }
    var pinned by remember(initial?.id) { mutableStateOf(initial?.pinned ?: false) }
    var reminderAt by remember(initial?.id) { mutableStateOf(initial?.reminderAt) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "写一张便签" else "编辑便签") },
        text = {
            Column(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(detail, { detail = it }, label = { Text("内容 / 想法 / 小计划") }, minLines = 5, modifier = Modifier.fillMaxWidth())
                Text("放进哪个抽屉？", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(memoCategories) { item ->
                        FilterChip(
                            selected = category == item.id,
                            onClick = { category = item.id },
                            label = { Text("${item.emoji} ${item.label}") },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("置顶这张便签")
                        Text("重要的事情会一直排在最前面", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = pinned, onCheckedChange = { pinned = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Text(reminderAt?.let { "⏰ ${formatMemoDate(it)}" } ?: "＋ 设置提醒日期")
                    }
                    if (reminderAt != null) TextButton(onClick = { reminderAt = null }) { Text("清除") }
                }
                Text("提醒日期会保存在便签里，也可以一键添加到系统日历。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onSave(title.trim(), detail.trim(), category, pinned, reminderAt) }) {
                Text(if (initial == null) "收好便签" else "保存修改")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = reminderAt ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    reminderAt = state.selectedDateMillis
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
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
        LifeSection.HOME -> Triple("", "", "")
        LifeSection.STATUS -> Triple("今天感觉怎么样", "身体感受或想让 AI 知道的事", "心情 / 精力")
        LifeSection.MEMO -> Triple("备忘标题", "计划或想法", "分类")
        LifeSection.CALENDAR -> Triple("安排或提醒标题", "时间、地点和需要 AI 提醒的事情", "今天 / 本周 / 纪念日")
        LifeSection.MUSIC -> Triple("歌曲名", "歌手、故事或一起听歌的回忆", "想念 / 开心 / 安慰")
        LifeSection.READING -> Triple("书名或章节", "阅读进度、原文、你的批注以及想问 AI 的问题", "普通书签 / 情绪书签 / 猜想书签 / 记忆书签")
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

private fun openCalendar(context: Context, entry: LifeEntry) {
    val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).apply {
        putExtra(CalendarContract.Events.TITLE, entry.title)
        putExtra(CalendarContract.Events.DESCRIPTION, entry.detail)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis() + 60 * 60 * 1000)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, System.currentTimeMillis() + 2 * 60 * 60 * 1000)
    }
    context.startActivity(intent)
}

private fun openMemoCalendar(context: Context, entry: LifeEntry) {
    val start = entry.reminderAt ?: return
    val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).apply {
        putExtra(CalendarContract.Events.TITLE, entry.title)
        putExtra(CalendarContract.Events.DESCRIPTION, entry.detail)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 60 * 60 * 1000)
    }
    context.startActivity(intent)
}

private fun openMusic(context: Context, title: String) {
    val query = Uri.encode(title)
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("orpheus://search?keyword=$query"))
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.163.com/#/search/m/?s=$query"))
    runCatching { context.startActivity(appIntent) }.getOrElse { context.startActivity(webIntent) }
}

private fun formatMemoDate(value: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(value))

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
                    memoCategory = item.optString("memoCategory").takeIf { it.isNotBlank() }
                        ?: when (item.optString("tag")) {
                            "AI", "TA 的" -> "ai"
                            "我们的" -> "together"
                            else -> "life"
                        },
                    pinned = item.optBoolean("pinned", false),
                    completed = item.optBoolean("completed", false),
                    reminderAt = item.optLong("reminderAt", 0L).takeIf { it > 0L },
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
            put("memoCategory", entry.memoCategory)
            put("pinned", entry.pinned)
            put("completed", entry.completed)
            entry.reminderAt?.let { put("reminderAt", it) }
        })
    }
    context.getSharedPreferences("tumin_life_hub", Context.MODE_PRIVATE).edit().putString("entries", array.toString()).apply()
}
