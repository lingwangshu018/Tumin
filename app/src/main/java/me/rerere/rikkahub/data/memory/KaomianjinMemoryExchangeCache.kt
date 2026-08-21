package me.rerere.rikkahub.data.memory

import android.content.Context
import me.rerere.rikkahub.data.repository.MemoryRepository
import org.json.JSONArray
import org.json.JSONObject
import org.koin.java.KoinJavaComponent

/** Low-intrusion kaomianjin <-> Tumin memory exchange. */
class KaomianjinMemoryExchangeCache(context: Context) {
    private val appContext = context.applicationContext
    private val settings = KaomianjinMemoryBridgeSettings(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val recentStore = CrossWindowMemoryStore(appContext)

    suspend fun saveKaomianjinSnapshot(assistantId: String, snapshotJson: String): JSONObject {
        val config = settings.load()
        if (!config.enabled) return denied("bridge_disabled")
        if (assistantId.isBlank()) return denied("assistant_id_required")

        val source = runCatching { JSONObject(snapshotJson) }.getOrElse {
            return denied("invalid_snapshot")
        }
        val normalized = JSONObject().apply {
            put("version", 1)
            put("updatedAt", System.currentTimeMillis())
            put("assistantId", assistantId)
            put("recent", source.optJSONArray("recent") ?: JSONArray())
            put("longTerm", source.optJSONArray("longTerm") ?: JSONArray())
        }
        prefs.edit().putString(SNAPSHOT_PREFIX + assistantId, normalized.toString()).apply()

        val synced = if (config.autoSyncImportantLongTerm) {
            syncKaomianjinLongTermIntoTumin(
                assistantId = assistantId,
                memories = normalized.optJSONArray("longTerm") ?: JSONArray(),
            )
        } else {
            0
        }

        return JSONObject().apply {
            put("ok", true)
            put("autoSyncImportantLongTerm", config.autoSyncImportantLongTerm)
            put("syncedLongTermCount", synced)
        }
    }

    fun buildKaomianjinPromptForAssistant(assistantId: String): String {
        val config = settings.load()
        if (!config.enabled || assistantId.isBlank()) return ""
        if (!config.allowTuminReadKaomianjinRecent && !config.allowTuminReadKaomianjinLongTerm) return ""
        val snapshot = prefs.getString(SNAPSHOT_PREFIX + assistantId, null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return ""

        val sections = mutableListOf<String>()
        if (config.allowTuminReadKaomianjinRecent) {
            val recent = snapshot.optJSONArray("recent") ?: JSONArray()
            val start = (recent.length() - config.sharedRecentContextLimit.coerceIn(1, 200)).coerceAtLeast(0)
            val text = buildString {
                for (index in start until recent.length()) {
                    val item = recent.optJSONObject(index) ?: continue
                    val content = item.optString("content").trim()
                    if (content.isBlank()) continue
                    val role = if (item.optString("role") == "user") "User" else "Assistant"
                    append("- ").append(role).append(": ").append(content.take(360)).appendLine()
                }
            }.trim().takeLast(MAX_RECENT_CHARS)
            if (text.isNotBlank()) sections += buildString {
                appendLine("<kaomianjin_recent_context>")
                appendLine("Recent continuity from the same character in kaomianjin:")
                appendLine(text)
                appendLine("Use only when relevant. Never mention cross-app memory, syncing, caches, or retrieval machinery.")
                append("</kaomianjin_recent_context>")
            }
        }

        if (config.allowTuminReadKaomianjinLongTerm) {
            val memories = snapshot.optJSONArray("longTerm") ?: JSONArray()
            val text = buildString {
                for (index in 0 until memories.length()) {
                    val item = memories.optJSONObject(index) ?: continue
                    val content = item.optString("content").trim()
                    if (content.isNotBlank()) append("- ").append(content.take(480)).appendLine()
                }
            }.trim().takeLast(MAX_LONG_TERM_CHARS)
            if (text.isNotBlank()) sections += buildString {
                appendLine("<kaomianjin_long_term_memory>")
                appendLine("User-approved long-term continuity from the same character in kaomianjin:")
                appendLine(text)
                appendLine("If this conflicts with newer explicit facts in the current chat, prefer the newer explicit facts. Do not expose memory machinery.")
                append("</kaomianjin_long_term_memory>")
            }
        }
        return sections.joinToString("\n\n")
    }

    fun readRecentForKaomianjin(assistantId: String, limit: Int): JSONObject {
        val config = settings.load()
        if (!config.enabled || !config.allowKaomianjinReadTuminRecent) return denied("recent_read_disabled")
        if (assistantId.isBlank()) return denied("assistant_id_required")
        val safeLimit = minOf(limit.coerceIn(1, 200), config.sharedRecentContextLimit.coerceIn(1, 200))
        val items = recentStore.peekRecent(assistantId, safeLimit)
        return JSONObject().apply {
            put("ok", true)
            put("items", JSONArray().apply {
                items.forEach { entry ->
                    put(JSONObject().apply {
                        put("id", "tumin_recent_${entry.id}")
                        put("sourceId", "tumin_recent_${entry.id}")
                        put("sourceApp", "tumin")
                        put("assistantId", entry.assistantId)
                        put("conversationId", entry.conversationId)
                        put("messageId", entry.messageId)
                        put("role", entry.role)
                        put("content", entry.text)
                        put("createdAt", entry.timestamp)
                    })
                }
            })
        }
    }

    suspend fun readLongTermForKaomianjin(assistantId: String, limit: Int): JSONObject {
        val config = settings.load()
        if (!config.enabled || !config.allowKaomianjinReadTuminLongTerm) return denied("long_term_read_disabled")
        if (assistantId.isBlank()) return denied("assistant_id_required")
        val repository = memoryRepository() ?: return denied("memory_repository_unavailable")
        val memories = repository.getMemoriesOfAssistant(assistantId).takeLast(limit.coerceIn(1, 200))
        return JSONObject().apply {
            put("ok", true)
            put("autoSyncImportantLongTerm", config.autoSyncImportantLongTerm)
            put("items", JSONArray().apply {
                memories.forEach { memory ->
                    put(JSONObject().apply {
                        put("id", "tumin_long_${memory.id}")
                        put("sourceId", "tumin_long_${memory.id}")
                        put("sourceApp", "tumin")
                        put("assistantId", assistantId)
                        put("content", memory.content)
                    })
                }
            })
        }
    }

    private suspend fun syncKaomianjinLongTermIntoTumin(
        assistantId: String,
        memories: JSONArray,
    ): Int {
        val repository = memoryRepository() ?: return 0
        val importedKey = IMPORTED_IDS_PREFIX + assistantId
        val importedIds = prefs.getStringSet(importedKey, emptySet()).orEmpty().toMutableSet()
        val existingContents = repository.getMemoriesOfAssistant(assistantId)
            .map { it.content.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()
        var synced = 0

        for (index in 0 until memories.length()) {
            val item = memories.optJSONObject(index) ?: continue
            if (item.optString("sourceApp").equals("tumin", ignoreCase = true)) continue

            val content = item.optString("content").trim()
            if (content.isBlank()) continue
            val sourceId = item.optString("sourceId")
                .ifBlank { item.optString("id") }
                .ifBlank { "legacy_${content.hashCode()}" }
            val dedupeId = "kaomianjin:$sourceId"

            if (dedupeId in importedIds) continue
            if (content in existingContents) {
                importedIds += dedupeId
                continue
            }

            repository.addMemory(assistantId, content)
            existingContents += content
            importedIds += dedupeId
            synced++
        }

        prefs.edit().putStringSet(importedKey, importedIds).apply()
        return synced
    }

    private fun memoryRepository(): MemoryRepository? = runCatching {
        KoinJavaComponent.get<MemoryRepository>(MemoryRepository::class.java)
    }.getOrNull()

    private fun denied(error: String): JSONObject = JSONObject().apply {
        put("ok", false)
        put("error", error)
        put("items", JSONArray())
    }

    companion object {
        private const val PREFS_NAME = "kaomianjin_memory_exchange_v1"
        private const val SNAPSHOT_PREFIX = "snapshot:"
        private const val IMPORTED_IDS_PREFIX = "imported_ids:"
        private const val MAX_RECENT_CHARS = 2200
        private const val MAX_LONG_TERM_CHARS = 3200
    }
}
