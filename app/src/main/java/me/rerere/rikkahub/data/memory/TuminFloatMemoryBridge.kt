/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.memory

import android.content.Context
import me.rerere.rikkahub.data.repository.MemoryRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Isolated adapter for Float <-> Tumin memory interoperability.
 *
 * This class deliberately does not change Tumin's native memory storage or retrieval rules.
 * It only exposes normalized, read-only snapshots for a transport layer such as PluginWebView.
 */
class TuminFloatMemoryBridge(
    context: Context,
    private val memoryRepository: MemoryRepository,
) {
    private val recentStore = CrossWindowMemoryStore(context.applicationContext)
    private val settings = FloatMemoryBridgeSettings(context.applicationContext)

    fun readRecentForFloat(
        assistantId: String,
        limit: Int = DEFAULT_RECENT_LIMIT,
    ): String {
        val config = settings.load()
        if (!config.enabled || !config.allowFloatReadTuminRecent) {
            return deniedResult("recent", assistantId, "Float recent-memory reading is disabled")
        }
        if (assistantId.isBlank()) return missingAssistantResult("recent")
        val safeLimit = minOf(limit.coerceIn(1, MAX_SHARED_LIMIT), config.sharedRecentContextLimit.coerceIn(1, MAX_SHARED_LIMIT))
        val items = recentStore.peekRecent(assistantId, safeLimit)

        return JSONObject().apply {
            put("success", true)
            put("kind", "recent")
            put("assistantId", assistantId)
            put("items", JSONArray().apply {
                items.forEach { entry ->
                    put(JSONObject().apply {
                        put("id", "tumin_recent_${entry.id}")
                        put("origin", "tumin")
                        put("kind", "recent")
                        put("assistantId", entry.assistantId)
                        put("conversationId", entry.conversationId)
                        put("messageId", entry.messageId)
                        put("role", entry.role)
                        put("content", entry.text)
                        put("timestamp", entry.timestamp)
                    })
                }
            })
        }.toString()
    }

    suspend fun readLongTermForFloat(
        assistantId: String,
        limit: Int = MAX_SHARED_LIMIT,
    ): String {
        val config = settings.load()
        if (!config.enabled || !config.allowFloatReadTuminLongTerm) {
            return deniedResult("long_term", assistantId, "Float long-term-memory reading is disabled")
        }
        if (assistantId.isBlank()) return missingAssistantResult("long_term")
        val safeLimit = limit.coerceIn(1, MAX_SHARED_LIMIT)
        val memories = memoryRepository.getMemoriesOfAssistant(assistantId).takeLast(safeLimit)

        return JSONObject().apply {
            put("success", true)
            put("kind", "long_term")
            put("assistantId", assistantId)
            put("items", JSONArray().apply {
                memories.forEach { memory ->
                    put(JSONObject().apply {
                        put("id", "tumin_long_${memory.id}")
                        put("origin", "tumin")
                        put("kind", "long_term")
                        put("assistantId", assistantId)
                        put("content", memory.content)
                    })
                }
            })
        }.toString()
    }

    private fun missingAssistantResult(kind: String): String = JSONObject().apply {
        put("success", false)
        put("kind", kind)
        put("items", JSONArray())
        put("error", "assistantId is required")
    }.toString()

    private fun deniedResult(kind: String, assistantId: String, error: String): String = JSONObject().apply {
        put("success", false)
        put("kind", kind)
        if (assistantId.isNotBlank()) put("assistantId", assistantId)
        put("items", JSONArray())
        put("error", error)
    }.toString()

    private companion object {
        const val DEFAULT_RECENT_LIMIT = 20
        const val MAX_SHARED_LIMIT = 200
    }
}
