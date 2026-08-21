package me.rerere.rikkahub.data.memory

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.MemoryRepository
import org.json.JSONArray
import org.json.JSONObject
import org.koin.java.KoinJavaComponent
import java.io.File

/** Low-intrusion Float <-> Tumin exchange using Tumin's existing per-plugin KV store. */
class FloatMemoryExchangeCache(context: Context) {
    private val appContext = context.applicationContext
    private val settings = FloatMemoryBridgeSettings(appContext)

    fun buildFloatPromptForAssistant(assistantId: String): String {
        val config = settings.load()
        if (!config.enabled || assistantId.isBlank()) return ""
        if (!config.allowTuminReadFloatRecent && !config.allowTuminReadFloatLongTerm) return ""
        val snapshot = findNewestJson(SNAPSHOT_KEY_FLOAT_PREFIX + assistantId) ?: return ""
        val sections = mutableListOf<String>()

        if (config.allowTuminReadFloatRecent) {
            val recent = snapshot.optJSONArray("recent") ?: JSONArray()
            val start = (recent.length() - config.sharedRecentContextLimit.coerceIn(1, 200)).coerceAtLeast(0)
            val text = buildString {
                for (index in start until recent.length()) {
                    val item = recent.optJSONObject(index) ?: continue
                    val content = item.optString("content").trim()
                    if (content.isNotBlank()) append("- ").append(content.take(360)).appendLine()
                }
            }.trim().takeLast(MAX_RECENT_CHARS)
            if (text.isNotBlank()) sections += buildString {
                appendLine("<float_recent_context>")
                appendLine("Recent continuity from the same character in Float:")
                appendLine(text)
                appendLine("Use only when relevant. Never mention Float, cross-app memory, syncing, caches, or retrieval machinery.")
                append("</float_recent_context>")
            }
        }

        if (config.allowTuminReadFloatLongTerm) {
            val memories = snapshot.optJSONArray("longTerm") ?: JSONArray()
            val text = buildString {
                for (index in 0 until memories.length()) {
                    val item = memories.optJSONObject(index) ?: continue
                    val content = item.optString("content").trim()
                    if (content.isNotBlank()) append("- ").append(content.take(480)).appendLine()
                }
            }.trim().takeLast(MAX_LONG_TERM_CHARS)
            if (text.isNotBlank()) sections += buildString {
                appendLine("<float_long_term_memory>")
                appendLine("User-approved long-term continuity from the same character in Float:")
                appendLine(text)
                appendLine("If this conflicts with newer explicit facts in the current chat, prefer the newer explicit facts. Do not expose memory machinery.")
                append("</float_long_term_memory>")
            }
        }
        return sections.joinToString("\n\n")
    }

    fun publishAssistantCatalog() {
        publishAssistantCatalog(floatPluginPrefs())
    }

    fun publishTuminSnapshot(assistant: Assistant) {
        val config = settings.load()
        if (!config.enabled || (!config.allowFloatReadTuminRecent && !config.allowFloatReadTuminLongTerm)) return
        val targets = floatPluginPrefs()
        if (targets.isEmpty()) return

        val assistantId = assistant.id.toString()
        val memoryRepository = runCatching { KoinJavaComponent.get(MemoryRepository::class.java) }.getOrNull() ?: return
        val bridge = TuminFloatMemoryBridge(appContext, memoryRepository)
        publishAssistantCatalog(targets)

        val recent = if (config.allowFloatReadTuminRecent) runCatching {
            JSONObject(bridge.readRecentForFloat(assistantId, config.sharedRecentContextLimit)).optJSONArray("items") ?: JSONArray()
        }.getOrDefault(JSONArray()) else JSONArray()

        targets.forEach { prefs ->
            val previous = prefs.getString(SNAPSHOT_KEY_TUMIN_PREFIX + assistantId, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            writeTuminSnapshot(prefs, assistantId, recent, previous?.optJSONArray("longTerm") ?: JSONArray())
        }

        if (!config.allowFloatReadTuminLongTerm) return
        IO_SCOPE.launch {
            val longTerm = runCatching {
                JSONObject(bridge.readLongTermForFloat(assistantId, MAX_SHARED_LONG_TERM)).optJSONArray("items") ?: JSONArray()
            }.getOrDefault(JSONArray())
            floatPluginPrefs().forEach { writeTuminSnapshot(it, assistantId, recent, longTerm) }
        }
    }

    private fun publishAssistantCatalog(targets: List<android.content.SharedPreferences>) {
        if (targets.isEmpty()) return
        val assistants = runCatching { KoinJavaComponent.get(SettingsStore::class.java).settingsFlow.value.assistants }.getOrDefault(emptyList())
        if (assistants.isEmpty()) return
        val payload = JSONObject().apply {
            put("version", 1)
            put("updatedAt", System.currentTimeMillis())
            put("assistants", JSONArray().apply {
                assistants.forEach { assistant -> put(JSONObject().apply { put("id", assistant.id.toString()); put("name", assistant.name) }) }
            })
        }.toString()
        targets.forEach { it.edit().putString(ASSISTANTS_KEY, payload).apply() }
    }

    private fun writeTuminSnapshot(prefs: android.content.SharedPreferences, assistantId: String, recent: JSONArray, longTerm: JSONArray) {
        val payload = JSONObject().apply {
            put("version", 1); put("updatedAt", System.currentTimeMillis()); put("assistantId", assistantId)
            put("recent", recent); put("longTerm", longTerm)
        }
        prefs.edit().putString(SNAPSHOT_KEY_TUMIN_PREFIX + assistantId, payload.toString()).apply()
    }

    private fun findNewestJson(key: String): JSONObject? {
        var newest: JSONObject? = null
        var newestTimestamp = Long.MIN_VALUE
        floatPluginPrefs().forEach { prefs ->
            val parsed = prefs.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@forEach
            val timestamp = when (val raw = parsed.opt("updatedAt")) {
                is Number -> raw.toLong()
                is String -> runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrDefault(0L)
                else -> 0L
            }
            if (timestamp >= newestTimestamp) { newestTimestamp = timestamp; newest = parsed }
        }
        return newest
    }

    private fun floatPluginPrefs(): List<android.content.SharedPreferences> {
        val dir = File(appContext.applicationInfo.dataDir, "shared_prefs")
        val files = dir.listFiles { file -> file.isFile && file.name.startsWith("plugin_data_") && file.name.endsWith(".xml") } ?: return emptyList()
        return files.mapNotNull { file ->
            val prefs = appContext.getSharedPreferences(file.name.removeSuffix(".xml"), Context.MODE_PRIVATE)
            val manifest = prefs.getString(MANIFEST_KEY, null) ?: return@mapNotNull null
            val valid = runCatching {
                val json = JSONObject(manifest)
                json.optString("type") == MANIFEST_TYPE && json.optInt("version", 0) == 1
            }.getOrDefault(false)
            prefs.takeIf { valid }
        }
    }

    private companion object {
        val IO_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        const val MANIFEST_KEY = "__float_memory_bridge_manifest_v1"
        const val MANIFEST_TYPE = "float-memory-bridge"
        const val SNAPSHOT_KEY_FLOAT_PREFIX = "__float_memory_bridge_snapshot_v1:"
        const val SNAPSHOT_KEY_TUMIN_PREFIX = "__tumin_memory_bridge_snapshot_v1:"
        const val ASSISTANTS_KEY = "__tumin_memory_bridge_assistants_v1"
        const val MAX_RECENT_CHARS = 2200
        const val MAX_LONG_TERM_CHARS = 3200
        const val MAX_SHARED_LONG_TERM = 200
    }
}
