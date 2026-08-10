package me.rerere.rikkahub.ui.components.ui

import android.content.Context
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

@Composable
fun StickerUrlPicker(
    modifier: Modifier = Modifier,
    height: Int = 320,
    onStickerSelected: (String) -> Unit,
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

    fun persist(updated: List<StickerPack>) {
        packs = updated
        saveStickerPacks(context, updated)
        if (selectedPackId !in updated.map { it.id }) selectedPackId = updated.firstOrNull()?.id
    }

    Surface(
        modifier = modifier.height(height.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("我的表情包", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("从 URL 导入 · 点一下直接作为图片发送", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { managing = !managing }) { Text(if (managing) "完成" else "管理") }
                FilledTonalButton(onClick = { showImport = true }, shape = RoundedCornerShape(14.dp)) { Text("＋ 导入") }
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
                        Text("贴一个图片直链或表情包 JSON URL 就能导入。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(onClick = { showImport = true }) { Text("导入第一个表情包") }
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
                                        Text("${pack.stickers.size} 张 · ${pack.sourceUrl}", maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(
                                        enabled = !loading,
                                        onClick = {
                                            loading = true
                                            error = null
                                            scope.launch {
                                                runCatching { fetchStickerPack(client, pack.sourceUrl, pack.name) }
                                                    .onSuccess { refreshed ->
                                                        persist(packs.map { if (it.id == pack.id) refreshed.copy(id = pack.id) else it })
                                                    }
                                                    .onFailure { error = it.message ?: "刷新失败" }
                                                loading = false
                                            }
                                        },
                                    ) { Text("刷新") }
                                    TextButton(onClick = { persist(packs.filterNot { it.id == pack.id }) }) { Text("删除") }
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(72.dp),
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        gridItems(selected.stickers, key = { it.url }) { sticker ->
                            Surface(
                                onClick = { onStickerSelected(sticker.url) },
                                modifier = Modifier.aspectRatio(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                AsyncImage(
                                    model = sticker.url,
                                    contentDescription = sticker.name,
                                    modifier = Modifier.fillMaxSize().padding(4.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (showImport) {
        ImportStickerPackDialog(
            loading = loading,
            error = error,
            onDismiss = { if (!loading) { showImport = false; error = null } },
            onImport = { name, url ->
                loading = true
                error = null
                scope.launch {
                    runCatching { fetchStickerPack(client, url, name) }
                        .onSuccess { pack ->
                            persist(packs + pack)
                            selectedPackId = pack.id
                            loading = false
                            showImport = false
                        }
                        .onFailure {
                            error = it.message ?: "导入失败，请检查 URL"
                            loading = false
                        }
                }
            },
        )
    }
}

@Composable
private fun ImportStickerPackDialog(
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onImport: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("导入表情包 URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("支持：单张图片直链、JSON 表情包、纯文本 URL 列表。JSON 可以是字符串数组，也可以是带 name/url 的对象数组。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("表情包名字（可选）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("https://…") },
                    minLines = 2,
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            FilledTonalButton(
                enabled = (url.trim().startsWith("http://") || url.trim().startsWith("https://")) && !loading,
                onClick = { onImport(name.trim(), url.trim()) },
            ) { Text("导入") }
        },
        dismissButton = { TextButton(enabled = !loading, onClick = onDismiss) { Text("取消") } },
    )
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

    val urls = text.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("http://") || it.startsWith("https://") }
        .distinct()
        .mapIndexed { index, url -> StickerItem("表情 ${index + 1}", url) }
        .toList()
    return ParsedStickerSource(stickers = urls)
}

private fun parseStickerArray(array: JSONArray): List<StickerItem> = buildList {
    for (index in 0 until array.length()) {
        when (val value = array.opt(index)) {
            is String -> if (value.startsWith("http")) add(StickerItem("表情 ${index + 1}", value))
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
                    if (url.isNotBlank()) add(StickerItem(sticker.optString("name"), url))
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
