package me.rerere.rikkahub.ui.components.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.koin.compose.koinInject
import java.io.File
import java.util.UUID

private const val STICKER_PREFS = "tumin_sticker_library"
private const val STICKER_PACKS_KEY = "packs"

private data class StickerItem(
    val name: String,
    val url: String,
)

private data class StickerPack(
    val id: String,
    val name: String,
    val sourceUrl: String,
    val stickers: List<StickerItem>,
)

private data class PendingGallerySticker(
    val url: String,
    val name: String,
)

@Composable
fun StickerUrlPicker(
    modifier: Modifier = Modifier,
    height: Int = 320,
    onStickerSelected: (name: String, url: String) -> Unit,
) {
    val context = LocalContext.current
    val client: OkHttpClient = koinInject()
    val scope = rememberCoroutineScope()
    var packs by remember { mutableStateOf(loadStickerPacks(context)) }
    var selectedPackId by remember { mutableStateOf(packs.firstOrNull()?.id) }
    var showImport by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var galleryDrafts by remember { mutableStateOf<List<PendingGallerySticker>>(emptyList()) }

    fun persist(updated: List<StickerPack>) {
        packs = updated
        saveStickerPacks(context, updated)
        if (selectedPackId !in updated.map { it.id }) selectedPackId = updated.firstOrNull()?.id
    }

    fun addGalleryStickers(stickers: List<StickerItem>) {
        if (stickers.isEmpty()) return
        val currentId = selectedPackId
        if (currentId != null && packs.any { it.id == currentId }) {
            persist(
                packs.map { pack ->
                    if (pack.id == currentId) {
                        pack.copy(stickers = (pack.stickers + stickers).distinctBy { it.url })
                    } else pack
                }
            )
        } else {
            val pack = StickerPack(
                id = UUID.randomUUID().toString(),
                name = "相册表情",
                sourceUrl = "",
                stickers = stickers.distinctBy { it.url },
            )
            persist(packs + pack)
            selectedPackId = pack.id
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            loading = true
            error = null
            scope.launch {
                runCatching { copyGalleryStickers(context, uris) }
                    .onSuccess { copied ->
                        galleryDrafts = copied.mapIndexed { index, url ->
                            PendingGallerySticker(url = url, name = "表情 ${index + 1}")
                        }
                    }
                    .onFailure { error = it.message ?: "相册图片导入失败" }
                loading = false
            }
        }
    }

    Surface(
        modifier = modifier.height(height.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("我的表情包", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("URL 或相册导入 · 给每张表情起名字", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { managing = !managing }) { Text(if (managing) "完成" else "管理") }
                TextButton(
                    enabled = !loading,
                    onClick = { galleryLauncher.launch("image/*") },
                ) { Text("🖼 相册") }
                FilledTonalButton(onClick = { showImport = true }, shape = RoundedCornerShape(14.dp)) { Text("＋ URL") }
            }

            if (packs.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("🧸", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("这里还没有自己的表情包", fontWeight = FontWeight.SemiBold)
                        Text(
                            "可以从相册选图并逐张命名，也可以粘贴“哭哭: https://…gif”。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { galleryLauncher.launch("image/*") }) { Text("从相册导入") }
                            OutlinedButton(onClick = { showImport = true }) { Text("从 URL 导入") }
                        }
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listItems(packs, key = { it.id }) { pack ->
                        FilterChip(
                            selected = selectedPackId == pack.id,
                            onClick = { selectedPackId = pack.id },
                            label = { Text("${pack.name}  ${pack.stickers.size}") },
                        )
                    }
                }

                val selected = packs.firstOrNull { it.id == selectedPackId } ?: packs.first()
                if (managing) {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listItems(packs, key = { it.id }) { pack ->
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(pack.name, fontWeight = FontWeight.SemiBold)
                                        val sourceLabel = pack.sourceUrl.ifBlank {
                                            if (pack.stickers.any { it.url.startsWith("file:") }) "本地 / 手动导入" else "手动粘贴清单"
                                        }
                                        Text(
                                            "${pack.stickers.size} 张 · $sourceLabel",
                                            maxLines = 1,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (pack.sourceUrl.isNotBlank()) {
                                        TextButton(
                                            enabled = !loading,
                                            onClick = {
                                                loading = true
                                                error = null
                                                scope.launch {
                                                    runCatching { fetchStickerPack(client, pack.sourceUrl, pack.name) }
                                                        .onSuccess { refreshed ->
                                                            val localItems = pack.stickers.filter { it.url.startsWith("file:") }
                                                            persist(
                                                                packs.map {
                                                                    if (it.id == pack.id) refreshed.copy(
                                                                        id = pack.id,
                                                                        stickers = (refreshed.stickers + localItems).distinctBy { item -> item.url },
                                                                    ) else it
                                                                }
                                                            )
                                                        }
                                                        .onFailure { error = it.message ?: "刷新失败" }
                                                    loading = false
                                                }
                                            },
                                        ) { Text("刷新") }
                                    }
                                    TextButton(onClick = { persist(packs.filterNot { it.id == pack.id }) }) { Text("删除") }
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(82.dp),
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        gridItems(selected.stickers, key = { it.url }) { sticker ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    onClick = { onStickerSelected(sticker.name, sticker.url) },
                                    modifier = Modifier.size(72.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                ) {
                                    AsyncImage(
                                        model = sticker.url,
                                        contentDescription = sticker.name,
                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    sticker.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (showImport) {
        ImportStickerPackDialog(
            loading = loading,
            error = error,
            onDismiss = { if (!loading) { showImport = false; error = null } },
            onImport = { name, source ->
                loading = true
                error = null
                scope.launch {
                    runCatching { importStickerPack(client, source, name) }
                        .onSuccess { pack ->
                            persist(packs + pack)
                            selectedPackId = pack.id
                            loading = false
                            showImport = false
                        }
                        .onFailure {
                            error = it.message ?: "导入失败，请检查格式"
                            loading = false
                        }
                }
            },
        )
    }

    if (galleryDrafts.isNotEmpty()) {
        NameGalleryStickersDialog(
            drafts = galleryDrafts,
            onDraftsChange = { galleryDrafts = it },
            onDismiss = { galleryDrafts = emptyList() },
            onSave = {
                addGalleryStickers(
                    galleryDrafts.mapIndexed { index, draft ->
                        StickerItem(
                            name = draft.name.trim().ifBlank { "表情 ${index + 1}" },
                            url = draft.url,
                        )
                    }
                )
                galleryDrafts = emptyList()
            },
        )
    }
}

@Composable
private fun NameGalleryStickersDialog(
    drafts: List<PendingGallerySticker>,
    onDraftsChange: (List<PendingGallerySticker>) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("给表情起个名字") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "名字会和图片一起保存，例如“哭哭”“抱抱”“委屈”。AI 收到表情时也能知道它代表什么。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listItems(drafts.indices.toList(), key = { drafts[it].url }) { index ->
                        val draft = drafts[index]
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AsyncImage(
                                    model = draft.url,
                                    contentDescription = null,
                                    modifier = Modifier.size(62.dp),
                                    contentScale = ContentScale.Fit,
                                )
                                OutlinedTextField(
                                    value = draft.name,
                                    onValueChange = { newName ->
                                        onDraftsChange(
                                            drafts.mapIndexed { i, item ->
                                                if (i == index) item.copy(name = newName) else item
                                            }
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("这张表情叫什么？") },
                                    singleLine = true,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { FilledTonalButton(onClick = onSave) { Text("收进表情包") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ImportStickerPackDialog(
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onImport: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("从 URL 导入表情包") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "推荐格式：每行“名字: 图片URL”，例如：\n哭哭: https://img.example.com/cry.gif\n抱抱: https://img.example.com/hug.webp\n\n也支持远程清单 URL、JSON、纯 URL 列表和单张图片直链。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("表情包名字（可选）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("粘贴 URL 或“名字: URL”清单") },
                    minLines = 5,
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            FilledTonalButton(
                enabled = source.isNotBlank() && !loading,
                onClick = { onImport(name.trim(), source.trim()) },
            ) { Text("导入") }
        },
        dismissButton = { TextButton(enabled = !loading, onClick = onDismiss) { Text("取消") } },
    )
}

private suspend fun copyGalleryStickers(context: Context, uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
    val targetDir = File(context.filesDir, "stickers").apply { mkdirs() }
    uris.mapNotNull { uri ->
        runCatching {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val displayName = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            val extensionFromName = displayName?.substringAfterLast('.', "")?.takeIf { it.length in 2..5 }
            val extension = extensionFromName
                ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                ?: "img"
            val file = File(targetDir, "sticker_${UUID.randomUUID()}.$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: error("无法读取图片")
            Uri.fromFile(file).toString()
        }.getOrNull()
    }
}

private suspend fun importStickerPack(
    client: OkHttpClient,
    source: String,
    requestedName: String,
): StickerPack {
    val trimmed = source.trim()
    val isSingleRemoteSource = !trimmed.contains('\n') &&
        (trimmed.startsWith("http://") || trimmed.startsWith("https://"))
    return if (isSingleRemoteSource) {
        fetchStickerPack(client, trimmed, requestedName)
    } else {
        val parsed = parseStickerSource(trimmed)
        val stickers = parsed.stickers.distinctBy { it.url }
        if (stickers.isEmpty()) error("没有找到“名字: 图片URL”或有效图片链接")
        StickerPack(
            id = UUID.randomUUID().toString(),
            name = requestedName.ifBlank { parsed.name.ifBlank { "我的表情包" } },
            sourceUrl = "",
            stickers = stickers,
        )
    }
}

private suspend fun fetchStickerPack(
    client: OkHttpClient,
    sourceUrl: String,
    requestedName: String,
): StickerPack = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(sourceUrl).get().build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("下载失败：HTTP ${response.code}")

        val contentType = response.body.contentType()?.toString().orEmpty()
        if (contentType.startsWith("image/", ignoreCase = true)) {
            return@withContext StickerPack(
                id = UUID.randomUUID().toString(),
                name = requestedName.ifBlank { "我的表情" },
                sourceUrl = sourceUrl,
                stickers = listOf(StickerItem(requestedName.ifBlank { "表情" }, sourceUrl)),
            )
        }

        val body = response.body.string()
        val parsed = parseStickerSource(body)
        val stickers = parsed.stickers.distinctBy { it.url }
        if (stickers.isEmpty()) error("没有在这个 URL 里找到图片链接")
        return@withContext StickerPack(
            id = UUID.randomUUID().toString(),
            name = requestedName.ifBlank { parsed.name.ifBlank { "我的表情包" } },
            sourceUrl = sourceUrl,
            stickers = stickers,
        )
    }
}

private data class ParsedStickerSource(val name: String = "", val stickers: List<StickerItem>)

private fun parseStickerSource(raw: String): ParsedStickerSource {
    val text = raw.trim()
    if (text.startsWith("[")) {
        return ParsedStickerSource(stickers = parseStickerArray(JSONArray(text)).distinctBy { it.url })
    }
    if (text.startsWith("{")) {
        val obj = JSONObject(text)
        val name = obj.optString("name").ifBlank { obj.optString("title") }
        val array = when {
            obj.has("stickers") -> obj.optJSONArray("stickers")
            obj.has("items") -> obj.optJSONArray("items")
            obj.has("images") -> obj.optJSONArray("images")
            else -> null
        }
        if (array != null) return ParsedStickerSource(name, parseStickerArray(array).distinctBy { it.url })
        val singleUrl = obj.optString("url").ifBlank { obj.optString("image") }
        if (singleUrl.startsWith("http")) return ParsedStickerSource(name, listOf(StickerItem(name.ifBlank { "表情" }, singleUrl)))
    }

    val stickers = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { line -> parseNamedStickerLine(line) }
        .distinctBy { it.url }
        .toList()
    return ParsedStickerSource(stickers = stickers)
}

private fun parseNamedStickerLine(line: String): StickerItem? {
    val httpIndex = listOf(line.indexOf("https://"), line.indexOf("http://"))
        .filter { it >= 0 }
        .minOrNull() ?: return null
    val url = line.substring(httpIndex).trim().removeSurrounding("<", ">")
    if (!(url.startsWith("http://") || url.startsWith("https://"))) return null

    val rawName = line.substring(0, httpIndex)
        .trim()
        .trimEnd(':', '：', '-', '—', '=', '>', '|')
        .trim()
    val name = rawName.ifBlank { "表情" }
    return StickerItem(name = name, url = url)
}

private fun parseStickerArray(array: JSONArray): List<StickerItem> = buildList {
    for (index in 0 until array.length()) {
        when (val value = array.opt(index)) {
            is String -> {
                parseNamedStickerLine(value)?.let(::add)
                    ?: if (value.startsWith("http")) add(StickerItem("表情 ${index + 1}", value)) else Unit
            }
            is JSONObject -> {
                val url = value.optString("url")
                    .ifBlank { value.optString("src") }
                    .ifBlank { value.optString("image") }
                    .ifBlank { value.optString("imageUrl") }
                if (url.startsWith("http")) {
                    add(StickerItem(value.optString("name").ifBlank { value.optString("title") }.ifBlank { "表情 ${index + 1}" }, url))
                }
            }
        }
    }
}.distinctBy { it.url }

private fun loadStickerPacks(context: Context): List<StickerPack> = runCatching {
    val raw = context.getSharedPreferences(STICKER_PREFS, Context.MODE_PRIVATE).getString(STICKER_PACKS_KEY, "[]") ?: "[]"
    val packs = JSONArray(raw)
    buildList {
        for (i in 0 until packs.length()) {
            val obj = packs.getJSONObject(i)
            val stickersArray = obj.optJSONArray("stickers") ?: JSONArray()
            val stickers = buildList {
                for (j in 0 until stickersArray.length()) {
                    val sticker = stickersArray.getJSONObject(j)
                    val url = sticker.optString("url")
                    if (url.isNotBlank()) add(StickerItem(sticker.optString("name").ifBlank { "表情" }, url))
                }
            }.distinctBy { it.url }
            add(StickerPack(obj.getString("id"), obj.optString("name", "我的表情包"), obj.optString("sourceUrl"), stickers))
        }
    }
}.getOrDefault(emptyList())

private fun saveStickerPacks(context: Context, packs: List<StickerPack>) {
    val array = JSONArray()
    packs.forEach { pack ->
        array.put(JSONObject().apply {
            put("id", pack.id)
            put("name", pack.name)
            put("sourceUrl", pack.sourceUrl)
            put("stickers", JSONArray().apply {
                pack.stickers.distinctBy { it.url }.forEach { sticker ->
                    put(JSONObject().apply {
                        put("name", sticker.name)
                        put("url", sticker.url)
                    })
                }
            })
        })
    }
    context.getSharedPreferences(STICKER_PREFS, Context.MODE_PRIVATE).edit().putString(STICKER_PACKS_KEY, array.toString()).apply()
}
