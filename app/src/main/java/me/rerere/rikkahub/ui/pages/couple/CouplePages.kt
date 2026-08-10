package me.rerere.rikkahub.ui.pages.couple

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.CoupleCommentEntity
import me.rerere.rikkahub.data.db.entity.CouplePostEntity
import me.rerere.rikkahub.data.db.entity.CoupleRelationshipEntity
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
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
                item { Text("绑定后，QQ空间、日记和纪念日都会属于你们两个人。") }
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
        item { FeatureCard("⭐", "QQ空间", "逛逛你和 $partnerName 各自的动态与留言") { nav.navigate(Screen.CoupleMoments) } }
        item { FeatureCard("📖", "日记", "写下个人与共同回忆") { nav.navigate(Screen.CoupleDiary) } }
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

    val partner = settings.assistants.firstOrNull { it.id.toString() == relationship?.assistantId }
    val userName = settings.displaySetting.userNickname.ifBlank { "我" }
    val partnerName = partner?.name?.ifBlank { "TA" } ?: "TA"

    var filter by remember { mutableStateOf(SpaceFilter.ALL) }
    var showAdd by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

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
        topBar = {
            TopAppBar(
                title = { Text("我们的空间") },
                navigationIcon = { BackButton() },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Text("＋") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        Modifier.padding(20.dp, 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("⭐ 我们的 QQ 空间", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("$userName × $partnerName", style = MaterialTheme.typography.titleMedium)
                        Text("这里不是单向记录：你们都会发动态，也会真的在评论区说话。", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { showAdd = true }) { Text("我发动态") }
                            OutlinedButton(onClick = { vm.maybeCreateAiPost(force = true) }) { Text("让 $partnerName 发一条") }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = filter == SpaceFilter.ALL,
                        onClick = { filter = SpaceFilter.ALL },
                        label = { Text("全部") },
                    )
                    FilterChip(
                        selected = filter == SpaceFilter.USER,
                        onClick = { filter = SpaceFilter.USER },
                        label = { Text("$userName 的空间") },
                    )
                    FilterChip(
                        selected = filter == SpaceFilter.AI,
                        onClick = { filter = SpaceFilter.AI },
                        label = { Text("$partnerName 的空间") },
                    )
                }
            }

            if (visiblePosts.isEmpty()) {
                item {
                    Text(
                        "这里还没有动态。",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("这一刻想说什么？") },
                    minLines = 4,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = draft.isNotBlank(),
                    onClick = {
                        vm.addPost(draft.trim())
                        draft = ""
                        showAdd = false
                    },
                ) { Text("发表") }
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
    var commentDraft by remember(post.id) { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Text(avatarText, fontWeight = FontWeight.Bold)
                    }
                }
                Column {
                    Text(authorName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(formatDateTime(post.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(post.content, style = MaterialTheme.typography.bodyLarge)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onLike) { Text(if (post.liked) "♡ 已喜欢" else "♡ 喜欢") }
                TextButton(onClick = { }) { Text("💬 ${postComments.size}") }
            }

            if (postComments.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        postComments.forEach { comment ->
                            val commentName = if (comment.author == "assistant") partnerName else userName
                            Text(
                                text = "$commentName：${comment.content}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = commentDraft,
                    onValueChange = { commentDraft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (post.author == "assistant") "评论 $partnerName…" else "留言…") },
                    singleLine = true,
                )
                TextButton(
                    enabled = commentDraft.isNotBlank(),
                    onClick = {
                        onComment(commentDraft.trim())
                        commentDraft = ""
                    },
                ) { Text("发送") }
            }
        }
    }
}

@Composable
fun CoupleDiaryPage(vm: CoupleVM = koinViewModel()) {
    val entries by vm.diaries.collectAsStateWithLifecycle()
    EntryListPage("日记", "写日记", entries, { it.title + "\n" + it.content }, { formatDate(it.entryDate) }) { title, content, _ -> vm.addDiary(title, content) }
}

@Composable
fun CoupleAnniversariesPage(vm: CoupleVM = koinViewModel()) {
    val entries by vm.anniversaries.collectAsStateWithLifecycle()
    EntryListPage(
        "纪念日",
        "添加纪念日",
        entries,
        { it.title },
        { formatDate(it.eventDate) },
        chooseDate = true,
    ) { title, _, date -> vm.addAnniversary(title, date) }
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
    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton() }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Text("＋") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (entries.isEmpty()) item { Text("这里还没有内容，点击右下角开始记录。") }
            items(entries) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(content(entry), style = MaterialTheme.typography.bodyLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(date(entry), style = MaterialTheme.typography.bodySmall)
                            if (onLike != null) TextButton(onClick = { onLike(entry) }) {
                                Text(likeLabel?.invoke(entry) ?: "喜欢")
                            }
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
                if (chooseDate) TextButton(onClick = { showDateChooser = true }) {
                    Text("日期：${formatDate(selectedDate)}")
                }
            }
        },
        confirmButton = { TextButton(enabled = first.isNotBlank(), onClick = { onAdd(first, second, selectedDate); first = ""; second = ""; showAdd = false }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } },
    )
    if (showDateChooser) DateChooserDialog(
        title = "选择纪念日",
        initialDate = selectedDate,
        onDismiss = { showDateChooser = false },
        onConfirm = { selectedDate = it; showDateChooser = false },
    )
}

@Composable
private fun DateChooserDialog(
    title: String,
    initialDate: Long = System.currentTimeMillis(),
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDate)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.selectedDateMillis ?: initialDate) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(24.dp, 20.dp, 24.dp, 0.dp))
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

private fun formatDate(value: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(value))
private fun formatDateTime(value: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(value))
