package me.rerere.rikkahub.ui.pages.couple

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.CoupleCommentEntity
import me.rerere.rikkahub.data.db.entity.CoupleDiaryEntity
import me.rerere.rikkahub.data.db.entity.CoupleDiaryFolderEntity
import me.rerere.rikkahub.data.db.entity.CouplePostEntity
import me.rerere.rikkahub.data.db.entity.CoupleRelationshipEntity
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import org.json.JSONArray
import org.koin.androidx.compose.koinViewModel

@Composable
fun CoupleSpacePage(vm: CoupleVM = koinViewModel()) {
    val relationship by vm.relationship.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val partner = settings.assistants.firstOrNull { it.id.toString() == relationship?.assistantId }
    var pendingPartnerId by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("情侣空间") }, navigationIcon = { BackButton() }) }) { padding ->
        if (relationship == null || partner == null) {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("选择你的恋人", style = MaterialTheme.typography.headlineSmall) }
                item { Text("绑定后，QQ空间、我们的日记和纪念日都会属于你们两个人。") }
                items(settings.assistants) { assistant ->
                    Card(onClick = { pendingPartnerId = assistant.id.toString() }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(assistant.name.ifBlank { "未命名助手" }, style = MaterialTheme.typography.titleMedium)
                            Text("设为恋人")
                        }
                    }
                }
            }
        } else {
            CoupleHome(relationship!!, partner.name.ifBlank { "恋人" }, Modifier.padding(padding))
        }
    }
    pendingPartnerId?.let { assistantId ->
        DateChooserDialog(
            title = "选择恋爱开始日期",
            onDismiss = { pendingPartnerId = null },
            onConfirm = { date ->
                vm.bind(assistantId, date)
                pendingPartnerId = null
            },
        )
    }
}

@Composable
private fun CoupleHome(relationship: CoupleRelationshipEntity, partnerName: String, modifier: Modifier = Modifier) {
    val nav = LocalNavController.current
    val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - relationship.startedAt) + 1
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("我和 $partnerName", style = MaterialTheme.typography.headlineSmall)
                    Text("相恋第 $days 天", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text("从 ${formatDate(relationship.startedAt)} 开始")
                }
            }
        }
        item { FeatureCard("⭐", "QQ空间", "逛逛你和 $partnerName 各自的动态、照片与留言") { nav.navigate(Screen.CoupleMoments) } }
        item { FeatureCard("📖", "我们的日记", "THE PRIVATE JOURNAL · 写下心事，也等一封 $partnerName 的回信") { nav.navigate(Screen.CoupleDiary) } }
        item { FeatureCard("🎂", "纪念日", "收藏每一个值得记住的日子") { nav.navigate(Screen.CoupleAnniversaries) } }
    }
}

@Composable
private fun FeatureCard(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
            Column { Text(title, style = MaterialTheme.typography.titleLarge); Text(subtitle) }
        }
    }
}

private enum class SpaceFilter { ALL, USER, AI }

@Composable
fun CoupleMomentsPage(vm: CoupleVM = koinViewModel()) {
    val posts by vm.posts.collectAsStateWithLifecycle()
    val comments by vm.comments.collectAsStateWithLifecycle()
    val relationship by vm.relationship.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val partner = settings.assistants.firstOrNull { it.id.toString() == relationship?.assistantId }
    val userName = settings.displaySetting.userNickname.ifBlank { "我" }
    val partnerName = partner?.name?.ifBlank { "TA" } ?: "TA"

    var filter by remember { mutableStateOf(SpaceFilter.ALL) }
    var showAdd by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var selectedImageUris by remember { mutableStateOf<List<String>>(emptyList()) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val accepted = uris.take((9 - selectedImageUris.size).coerceAtLeast(0))
        accepted.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        selectedImageUris = (selectedImageUris + accepted.map { it.toString() }).distinct().take(9)
    }

    LaunchedEffect(relationship?.id) {
        if (relationship != null) {
            delay(700)
            vm.maybeCreateAiPost()
        }
    }

    val visiblePosts = when (filter) {
        SpaceFilter.ALL -> posts
        SpaceFilter.USER -> posts.filter { it.author == "user" }
        SpaceFilter.AI -> posts.filter { it.author == "assistant" }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("我们的空间") }, navigationIcon = { BackButton() }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Text("＋") } },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("⭐ 我们的 QQ 空间", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("$userName × $partnerName", style = MaterialTheme.typography.titleMedium)
                        Text("文字、照片和评论都会留在这里。你们都会发动态，也会真的在评论区说话。", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { showAdd = true }) { Text("我发动态") }
                            OutlinedButton(onClick = { vm.maybeCreateAiPost(force = true) }) { Text("让 $partnerName 发一条") }
                        }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter == SpaceFilter.ALL, onClick = { filter = SpaceFilter.ALL }, label = { Text("全部") })
                    FilterChip(selected = filter == SpaceFilter.USER, onClick = { filter = SpaceFilter.USER }, label = { Text("$userName 的空间") })
                    FilterChip(selected = filter == SpaceFilter.AI, onClick = { filter = SpaceFilter.AI }, label = { Text("$partnerName 的空间") })
                }
            }
            if (visiblePosts.isEmpty()) {
                item { Text("这里还没有动态。", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(visiblePosts, key = { it.id }) { post ->
                MomentCard(
                    post = post,
                    postComments = comments.filter { it.postId == post.id },
                    userName = userName,
                    partnerName = partnerName,
                    onLike = { vm.toggleLike(post) },
                    onComment = { vm.addUserComment(post, it) },
                )
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("发表说说") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth(), label = { Text("这一刻想说什么？") }, minLines = 3)
                    if (selectedImageUris.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            selectedImageUris.take(3).forEach { uri ->
                                AsyncImage(model = uri, contentDescription = null, modifier = Modifier.size(68.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                            }
                            if (selectedImageUris.size > 3) {
                                Surface(modifier = Modifier.size(68.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                    Box(contentAlignment = Alignment.Center) { Text("+${selectedImageUris.size - 3}") }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(enabled = selectedImageUris.size < 9, onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                            Text(if (selectedImageUris.isEmpty()) "📷 添加照片" else "📷 再选照片")
                        }
                        if (selectedImageUris.isNotEmpty()) TextButton(onClick = { selectedImageUris = emptyList() }) { Text("清空") }
                    }
                    Text("最多 9 张照片 · 当前 ${selectedImageUris.size}/9", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(enabled = draft.isNotBlank() || selectedImageUris.isNotEmpty(), onClick = {
                    vm.addPost(draft.trim(), selectedImageUris)
                    draft = ""
                    selectedImageUris = emptyList()
                    showAdd = false
                }) { Text("发表") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun MomentCard(
    post: CouplePostEntity,
    postComments: List<CoupleCommentEntity>,
    userName: String,
    partnerName: String,
    onLike: () -> Unit,
    onComment: (String) -> Unit,
) {
    val authorName = if (post.author == "assistant") partnerName else userName
    val avatarText = authorName.take(1).ifBlank { if (post.author == "assistant") "A" else "我" }
    val images = remember(post.imageUri) { decodePostImages(post.imageUri) }
    var commentDraft by remember(post.id) { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { Text(avatarText, fontWeight = FontWeight.Bold) }
                }
                Column {
                    Text(authorName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(formatDateTime(post.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (post.content.isNotBlank()) Text(post.content, style = MaterialTheme.typography.bodyLarge)
            if (images.isNotEmpty()) PostImageGrid(images)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onLike) { Text(if (post.liked) "♡ 已喜欢" else "♡ 喜欢") }
                TextButton(onClick = { }) { Text("💬 ${postComments.size}") }
            }
            if (postComments.isNotEmpty()) {
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        postComments.forEach { comment ->
                            val commentName = if (comment.author == "assistant") partnerName else userName
                            Text(text = "$commentName：${comment.content}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = commentDraft, onValueChange = { commentDraft = it }, modifier = Modifier.weight(1f), placeholder = { Text(if (post.author == "assistant") "评论 $partnerName…" else "留言…") }, singleLine = true)
                TextButton(enabled = commentDraft.isNotBlank(), onClick = { onComment(commentDraft.trim()); commentDraft = "" }) { Text("发送") }
            }
        }
    }
}

@Composable
private fun PostImageGrid(images: List<String>) {
    val rows = images.take(9).chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { rowImages ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowImages.forEach { uri ->
                    AsyncImage(model = uri, contentDescription = "空间照片", modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                }
                repeat(3 - rowImages.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}

private fun decodePostImages(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        if (raw.trimStart().startsWith("[")) {
            val array = JSONArray(raw)
            buildList { for (index in 0 until array.length()) array.optString(index).takeIf { it.isNotBlank() }?.let(::add) }
        } else listOf(raw)
    }.getOrElse { listOf(raw) }
}

private data class JournalPaperPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val background: Color,
    val accent: Color,
    val text: Color,
    val ornament: String,
)

private val journalPaperPresets = listOf(
    JournalPaperPreset("ivory", "象牙信纸", "温柔旧纸", Color(0xFFFFF9EA), Color(0xFFB58B54), Color(0xFF46392D), "✦"),
    JournalPaperPreset("rose", "玫瑰粉笺", "柔粉花信", Color(0xFFFFEFF3), Color(0xFFC66B82), Color(0xFF55363E), "❀"),
    JournalPaperPreset("mist", "雾蓝信笺", "清晨薄雾", Color(0xFFEAF3F8), Color(0xFF678DA5), Color(0xFF314651), "☁"),
    JournalPaperPreset("lavender", "薰衣草笺", "安静晚风", Color(0xFFF3EDFA), Color(0xFF8B70AD), Color(0xFF493D58), "✧"),
    JournalPaperPreset("sage", "鼠尾草纸", "植物手札", Color(0xFFEEF3E8), Color(0xFF718364), Color(0xFF394234), "❧"),
    JournalPaperPreset("night", "月夜信纸", "深蓝月光", Color(0xFF20283A), Color(0xFFB8C8EF), Color(0xFFF2F4FA), "☾"),
)

private fun paperPreset(id: String?): JournalPaperPreset = journalPaperPresets.firstOrNull { it.id == id } ?: journalPaperPresets.first()

@Composable
fun CoupleDiaryPage(vm: CoupleVM = koinViewModel()) {
    val entries by vm.diaries.collectAsStateWithLifecycle()
    val persistedFolders by vm.diaryFolders.collectAsStateWithLifecycle()
    val relationship by vm.relationship.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val partner = settings.assistants.firstOrNull { it.id.toString() == relationship?.assistantId }
    val partnerName = partner?.name?.ifBlank { "TA" } ?: "TA"

    var query by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf("全部心事") }
    var showAdd by remember { mutableStateOf(false) }
    var showFolderManager by remember { mutableStateOf(false) }
    var selectedDiaryId by remember { mutableStateOf<String?>(null) }
    var editingDiaryId by remember { mutableStateOf<String?>(null) }
    var requestedReplyId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(entries, persistedFolders) {
        val existing = persistedFolders.map { it.name.lowercase() }.toSet()
        entries.mapNotNull { it.folder?.trim()?.takeIf { name -> name.isNotBlank() && name != "全部心事" } }
            .distinct()
            .filter { it.lowercase() !in existing }
            .forEach(vm::addDiaryFolder)
    }

    val folders = remember(entries, persistedFolders) {
        listOf("全部心事") + (persistedFolders.map { it.name } + entries.mapNotNull { it.folder })
            .filter { it.isNotBlank() && it != "全部心事" }
            .distinct()
    }
    if (selectedFolder !in folders) selectedFolder = "全部心事"

    val visibleEntries = entries.filter { entry ->
        val folderMatch = selectedFolder == "全部心事" || entry.folder == selectedFolder
        val queryMatch = query.isBlank() || entry.title.contains(query, true) || entry.content.contains(query, true)
        folderMatch && queryMatch
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("我们的日记") }, navigationIcon = { BackButton() }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Text("＋") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.padding(22.dp, 26.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("我们的日记", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("THE PRIVATE JOURNAL", style = MaterialTheme.typography.labelLarge)
                        Text("把已经发生的故事写下来。原文永远是你的，$partnerName 的回信会单独留在这一页。", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            item {
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp), placeholder = { Text("搜索心事……") }, singleLine = true)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(folders) { folder ->
                            FilterChip(selected = selectedFolder == folder, onClick = { selectedFolder = folder }, label = { Text(folder) })
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showFolderManager = true }) { Text("🗂 管理分类") }
                    }
                }
            }
            if (visibleEntries.isEmpty()) {
                item { Text(if (query.isBlank()) "这里还没有写下心事。" else "没有找到这篇心事。", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(visibleEntries, key = { it.id }) { entry ->
                JournalCard(entry = entry, partnerName = partnerName, onClick = { selectedDiaryId = entry.id })
            }
        }
    }

    if (showAdd) {
        JournalEditorDialog(
            titleText = "写一篇日记",
            initial = null,
            existingFolders = folders,
            onDismiss = { showAdd = false },
            onSave = { title, content, folder, paper ->
                if (folder != "全部心事" && folders.none { it.equals(folder, true) }) vm.addDiaryFolder(folder)
                vm.addDiary(title, content, folder, paper)
                showAdd = false
            },
        )
    }

    if (showFolderManager) {
        JournalFolderManagerDialog(
            folders = persistedFolders,
            onDismiss = { showFolderManager = false },
            onAdd = vm::addDiaryFolder,
            onRename = vm::renameDiaryFolder,
            onDelete = { folder ->
                if (selectedFolder == folder.name) selectedFolder = "全部心事"
                vm.deleteDiaryFolder(folder)
            },
        )
    }

    editingDiaryId?.let { diaryId ->
        entries.firstOrNull { it.id == diaryId }?.let { entry ->
            JournalEditorDialog(
                titleText = "编辑这篇日记",
                initial = entry,
                existingFolders = folders,
                onDismiss = { editingDiaryId = null },
                onSave = { title, content, folder, paper ->
                    if (folder != "全部心事" && folders.none { it.equals(folder, true) }) vm.addDiaryFolder(folder)
                    vm.updateDiary(entry, title, content, folder, paper)
                    editingDiaryId = null
                    selectedDiaryId = entry.id
                },
            )
        }
    }

    selectedDiaryId?.let { diaryId ->
        entries.firstOrNull { it.id == diaryId }?.let { current ->
            LaunchedEffect(current.reply) { if (!current.reply.isNullOrBlank()) requestedReplyId = null }
            JournalReaderDialog(
                entry = current,
                partnerName = partnerName,
                waitingForReply = requestedReplyId == current.id && current.reply.isNullOrBlank(),
                onDismiss = { selectedDiaryId = null },
                onEdit = {
                    selectedDiaryId = null
                    editingDiaryId = current.id
                },
                onRequestReply = {
                    requestedReplyId = current.id
                    vm.requestDiaryReply(current)
                },
            )
        }
    }
}

@Composable
private fun JournalCard(entry: CoupleDiaryEntity, partnerName: String, onClick: () -> Unit) {
    val paper = paperPreset(entry.paper)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp), colors = CardDefaults.cardColors(containerColor = paper.background)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(entry.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = paper.text)
                Text(formatDate(entry.entryDate), style = MaterialTheme.typography.bodySmall, color = paper.text.copy(alpha = 0.68f))
            }
            Text(entry.content.replace("\n", " ").take(120) + if (entry.content.length > 120) "…" else "", style = MaterialTheme.typography.bodyMedium, color = paper.text.copy(alpha = 0.78f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${paper.ornament} ${entry.folder ?: "全部心事"}", style = MaterialTheme.typography.labelMedium, color = paper.accent)
                if (!entry.reply.isNullOrBlank()) Text("✉ 已收到 $partnerName 的回信", style = MaterialTheme.typography.labelMedium, color = paper.accent)
                else Text("还没有回信", style = MaterialTheme.typography.labelMedium, color = paper.text.copy(alpha = 0.58f))
            }
        }
    }
}

@Composable
private fun JournalEditorDialog(
    titleText: String,
    initial: CoupleDiaryEntity?,
    existingFolders: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var content by remember(initial?.id) { mutableStateOf(initial?.content.orEmpty()) }
    var folder by remember(initial?.id) { mutableStateOf(initial?.folder ?: "全部心事") }
    var paper by remember(initial?.id) { mutableStateOf(initial?.paper ?: "ivory") }
    var customFolder by remember(initial?.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("标题") }, singleLine = true)
                OutlinedTextField(content, { content = it }, modifier = Modifier.fillMaxWidth(), label = { Text("正文") }, minLines = 7)
                Text("分类", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    existingFolders.forEach { name ->
                        FilterChip(selected = folder == name, onClick = { folder = name; customFolder = "" }, label = { Text(name) })
                    }
                }
                OutlinedTextField(value = customFolder, onValueChange = { customFolder = it; if (it.isNotBlank()) folder = it.trim() }, modifier = Modifier.fillMaxWidth(), label = { Text("新分类（可选）") }, singleLine = true)
                Text("日记纸", style = MaterialTheme.typography.labelLarge)
                PaperSelector(selected = paper, onSelect = { paper = it })
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && content.isNotBlank(), onClick = { onSave(title.trim(), content.trim(), folder.ifBlank { "全部心事" }, paper) }) {
                Text(if (initial == null) "保存这页" else "保存修改")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PaperSelector(selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(journalPaperPresets, key = { it.id }) { paper ->
            Surface(
                onClick = { onSelect(paper.id) },
                modifier = Modifier.width(112.dp).height(90.dp),
                shape = RoundedCornerShape(14.dp),
                color = paper.background,
                border = BorderStroke(if (selected == paper.id) 2.dp else 1.dp, if (selected == paper.id) paper.accent else paper.accent.copy(alpha = 0.35f)),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(paper.ornament, color = paper.accent, style = MaterialTheme.typography.titleMedium)
                    Column {
                        Text(paper.name, color = paper.text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(paper.subtitle, color = paper.text.copy(alpha = 0.62f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalFolderManagerDialog(
    folders: List<CoupleDiaryFolderEntity>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRename: (CoupleDiaryFolderEntity, String) -> Unit,
    onDelete: (CoupleDiaryFolderEntity) -> Unit,
) {
    var newFolder by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理日记分类") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("“全部心事”是默认分类，不能删除。删除其他分类时，里面的日记会自动回到“全部心事”。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newFolder, { newFolder = it }, modifier = Modifier.weight(1f), label = { Text("新分类") }, singleLine = true)
                    FilledTonalButton(enabled = newFolder.isNotBlank(), onClick = { onAdd(newFolder); newFolder = "" }) { Text("新建") }
                }
                HorizontalDivider()
                if (folders.isEmpty()) Text("还没有自定义分类。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                folders.forEach { folder ->
                    if (editingId == folder.id) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(editingName, { editingName = it }, modifier = Modifier.weight(1f), singleLine = true)
                            TextButton(enabled = editingName.isNotBlank(), onClick = { onRename(folder, editingName); editingId = null; editingName = "" }) { Text("保存") }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(folder.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { editingId = folder.id; editingName = folder.name }) { Text("改名") }
                            TextButton(onClick = { onDelete(folder) }) { Text("删除") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun JournalReaderDialog(
    entry: CoupleDiaryEntity,
    partnerName: String,
    waitingForReply: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRequestReply: () -> Unit,
) {
    val paper = paperPreset(entry.paper)
    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp), shape = RoundedCornerShape(24.dp), color = paper.background, border = BorderStroke(1.dp, paper.accent.copy(alpha = 0.28f))) {
            Column(Modifier.padding(22.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("THE PRIVATE JOURNAL", style = MaterialTheme.typography.labelMedium, color = paper.accent)
                    Text(paper.ornament, style = MaterialTheme.typography.titleLarge, color = paper.accent)
                }
                Text(entry.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = paper.text)
                Text("${formatDate(entry.entryDate)} · ${entry.folder ?: "全部心事"} · ${paper.name}", style = MaterialTheme.typography.bodySmall, color = paper.text.copy(alpha = 0.62f))
                HorizontalDivider(color = paper.accent.copy(alpha = 0.3f))
                Text(entry.content, style = MaterialTheme.typography.bodyLarge, color = paper.text)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = paper.accent.copy(alpha = 0.3f))
                Text("$partnerName 的回信", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = paper.text)
                when {
                    !entry.reply.isNullOrBlank() -> {
                        Text(entry.reply, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = paper.text)
                        entry.replyAt?.let { Text("回信于 ${formatDateTime(it)}", style = MaterialTheme.typography.bodySmall, color = paper.text.copy(alpha = 0.62f)) }
                    }
                    waitingForReply -> Text("信已经送出去了，正在等 $partnerName 写回来……", color = paper.accent)
                    else -> Text("这一页还没有回信。你可以把它递给 $partnerName。", color = paper.text.copy(alpha = 0.65f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEdit) { Text("编辑") }
                    TextButton(onClick = onDismiss) { Text("合上日记") }
                    FilledTonalButton(enabled = !waitingForReply, onClick = onRequestReply) {
                        Text(if (entry.reply.isNullOrBlank()) "请 $partnerName 回信" else "请 $partnerName 再写一封")
                    }
                }
            }
        }
    }
}

@Composable
fun CoupleAnniversariesPage(vm: CoupleVM = koinViewModel()) {
    val entries by vm.anniversaries.collectAsStateWithLifecycle()
    EntryListPage("纪念日", "添加纪念日", entries, { it.title }, { formatDate(it.eventDate) }, chooseDate = true) { title, _, date -> vm.addAnniversary(title, date) }
}

@Composable
private fun <T> EntryListPage(
    title: String,
    action: String,
    entries: List<T>,
    content: (T) -> String,
    date: (T) -> String,
    onLike: ((T) -> Unit)? = null,
    likeLabel: ((T) -> String)? = null,
    chooseDate: Boolean = false,
    onAdd: (String, String, Long) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDateChooser by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton() }) }, floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Text("＋") } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (entries.isEmpty()) item { Text("这里还没有内容，点击右下角开始记录。") }
            items(entries) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(content(entry), style = MaterialTheme.typography.bodyLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(date(entry), style = MaterialTheme.typography.bodySmall)
                            if (onLike != null) TextButton(onClick = { onLike(entry) }) { Text(likeLabel?.invoke(entry) ?: "喜欢") }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false },
        title = { Text(action) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(first, { first = it }, label = { Text("标题") })
                if (title == "日记") OutlinedTextField(second, { second = it }, label = { Text("正文") }, minLines = 4)
                if (chooseDate) TextButton(onClick = { showDateChooser = true }) { Text("日期：${formatDate(selectedDate)}") }
            }
        },
        confirmButton = { TextButton(enabled = first.isNotBlank(), onClick = { onAdd(first, second, selectedDate); first = ""; second = ""; showAdd = false }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } },
    )
    if (showDateChooser) DateChooserDialog(title = "选择纪念日", initialDate = selectedDate, onDismiss = { showDateChooser = false }, onConfirm = { selectedDate = it; showDateChooser = false })
}

@Composable
private fun DateChooserDialog(
    title: String,
    initialDate: Long = System.currentTimeMillis(),
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDate)
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = { onConfirm(state.selectedDateMillis ?: initialDate) }) { Text("确定") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(24.dp, 20.dp, 24.dp, 0.dp))
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

private fun formatDate(value: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(value))
private fun formatDateTime(value: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(value))
