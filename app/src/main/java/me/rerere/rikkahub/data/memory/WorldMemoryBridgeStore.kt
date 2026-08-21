/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Experimental storage for World Bridge Protocol v1.
 *
 * This store is intentionally independent from Tumin's native assistant memory database.
 * It acts as a small host-owned cross-world save area while the protocol is being proven.
 */
class WorldMemoryBridgeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun connect(
        worldId: String,
        worldName: String,
        localCharacterId: String,
        characterName: String,
    ): JSONObject {
        val normalizedWorldId = normalizeId(worldId)
        val normalizedCharacterId = normalizeId(localCharacterId)
        require(normalizedWorldId.isNotBlank()) { "worldId is required" }
        require(normalizedCharacterId.isNotBlank()) { "localCharacterId is required" }

        val bindings = readObject(KEY_BINDINGS)
        val bindingKey = "$normalizedWorldId::$normalizedCharacterId"
        val existing = bindings.optJSONObject(bindingKey)
        val globalCharacterId = existing?.optString("globalCharacterId")?.takeIf { it.isNotBlank() }
            ?: "gc_${UUID.randomUUID()}"

        bindings.put(
            bindingKey,
            JSONObject().apply {
                put("globalCharacterId", globalCharacterId)
                put("worldId", normalizedWorldId)
                put("worldName", worldName.trim().take(120))
                put("localCharacterId", normalizedCharacterId)
                put("characterName", characterName.trim().take(120))
                put("updatedAt", System.currentTimeMillis())
            },
        )
        prefs.edit().putString(KEY_BINDINGS, bindings.toString()).apply()

        return JSONObject().apply {
            put("ok", true)
            put("protocol", "tumin-world-bridge")
            put("version", 1)
            put("globalCharacterId", globalCharacterId)
        }
    }

    @Synchronized
    fun readMemories(globalCharacterId: String, limit: Int): JSONObject {
        val safeId = normalizeId(globalCharacterId)
        val safeLimit = limit.coerceIn(1, 100)
        val all = readArray(KEY_MEMORIES)
        val matched = mutableListOf<JSONObject>()

        for (index in 0 until all.length()) {
            val item = all.optJSONObject(index) ?: continue
            if (item.optString("globalCharacterId") == safeId) matched += item
        }

        val result = JSONArray()
        matched.takeLast(safeLimit).forEach(result::put)
        return JSONObject().apply {
            put("ok", true)
            put("globalCharacterId", safeId)
            put("items", result)
        }
    }

    @Synchronized
    fun writeMemory(
        globalCharacterId: String,
        worldId: String,
        localCharacterId: String,
        type: String,
        content: String,
        importance: Int,
    ): JSONObject {
        val safeGlobalId = normalizeId(globalCharacterId)
        val safeWorldId = normalizeId(worldId)
        val safeLocalId = normalizeId(localCharacterId)
        val safeContent = content.trim().take(MAX_CONTENT_LENGTH)
        require(safeGlobalId.isNotBlank()) { "globalCharacterId is required" }
        require(safeWorldId.isNotBlank()) { "worldId is required" }
        require(safeLocalId.isNotBlank()) { "localCharacterId is required" }
        require(safeContent.isNotBlank()) { "content is required" }

        val item = JSONObject().apply {
            put("id", "wm_${UUID.randomUUID()}")
            put("globalCharacterId", safeGlobalId)
            put("sourceWorldId", safeWorldId)
            put("sourceLocalCharacterId", safeLocalId)
            put("type", type.trim().ifBlank { "memory" }.take(60))
            put("content", safeContent)
            put("importance", importance.coerceIn(0, 100))
            put("createdAt", System.currentTimeMillis())
        }

        val all = readArray(KEY_MEMORIES)
        all.put(item)
        val trimmed = JSONArray()
        val start = (all.length() - MAX_MEMORY_ENTRIES).coerceAtLeast(0)
        for (index in start until all.length()) {
            all.opt(index)?.let(trimmed::put)
        }
        prefs.edit().putString(KEY_MEMORIES, trimmed.toString()).apply()

        return JSONObject().apply {
            put("ok", true)
            put("item", item)
        }
    }

    private fun readObject(key: String): JSONObject = runCatching {
        JSONObject(prefs.getString(key, null).orEmpty().ifBlank { "{}" })
    }.getOrElse { JSONObject() }

    private fun readArray(key: String): JSONArray = runCatching {
        JSONArray(prefs.getString(key, null).orEmpty().ifBlank { "[]" })
    }.getOrElse { JSONArray() }

    private fun normalizeId(value: String): String = value.trim().take(160)

    companion object {
        private const val PREFS_NAME = "world_memory_bridge_v1"
        private const val KEY_BINDINGS = "bindings"
        private const val KEY_MEMORIES = "memories"
        private const val MAX_MEMORY_ENTRIES = 500
        private const val MAX_CONTENT_LENGTH = 4000
    }
}
