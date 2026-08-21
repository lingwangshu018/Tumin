/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.pages.webview

import android.content.Context
import android.webkit.JavascriptInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.memory.KaomianjinMemoryExchangeCache
import me.rerere.rikkahub.data.memory.WorldMemoryBridgeStore
import org.json.JSONObject
import org.koin.java.KoinJavaComponent

/** Android host side of World Bridge Protocol v1. */
class WorldPortalBridge(context: Context) {
    private val store = WorldMemoryBridgeStore(context)
    private val kaomianjin = KaomianjinMemoryExchangeCache(context)

    @JavascriptInterface
    fun getCapabilities(): String = JSONObject().apply {
        put("ok", true)
        put("protocol", "tumin-world-bridge")
        put("version", 1)
        put("host", "tumin")
        put("memoryRead", true)
        put("memoryWrite", true)
        put("recentContextRead", true)
        put("snapshotPublish", true)
        put("identityBinding", true)
    }.toString()

    @JavascriptInterface
    fun connect(
        worldId: String,
        worldName: String,
        localCharacterId: String,
        characterName: String,
    ): String = bridgeResult {
        if (worldId.trim().equals(KAOMIANJIN_WORLD_ID, ignoreCase = true)) {
            connectKaomianjin(characterName)
        } else {
            store.connect(worldId, worldName, localCharacterId, characterName)
        }
    }

    @JavascriptInterface
    fun readRecentContext(globalCharacterId: String, limit: Int): String = bridgeResult {
        kaomianjin.readRecentForKaomianjin(globalCharacterId.trim(), limit)
    }

    @JavascriptInterface
    fun readMemories(globalCharacterId: String, limit: Int): String = bridgeResult {
        val assistantId = globalCharacterId.trim()
        if (isTuminAssistant(assistantId)) {
            runBlocking(Dispatchers.IO) {
                kaomianjin.readLongTermForKaomianjin(assistantId, limit)
            }
        } else {
            store.readMemories(globalCharacterId, limit)
        }
    }

    @JavascriptInterface
    fun publishSnapshot(
        globalCharacterId: String,
        worldId: String,
        localCharacterId: String,
        snapshotJson: String,
    ): String = bridgeResult {
        if (!worldId.trim().equals(KAOMIANJIN_WORLD_ID, ignoreCase = true)) {
            return@bridgeResult JSONObject().apply {
                put("ok", false)
                put("error", "snapshot publishing is not enabled for this world")
            }
        }
        kaomianjin.saveKaomianjinSnapshot(globalCharacterId.trim(), snapshotJson)
    }

    @JavascriptInterface
    fun writeMemory(
        globalCharacterId: String,
        worldId: String,
        localCharacterId: String,
        type: String,
        content: String,
        importance: Int,
    ): String = bridgeResult {
        store.writeMemory(
            globalCharacterId = globalCharacterId,
            worldId = worldId,
            localCharacterId = localCharacterId,
            type = type,
            content = content,
            importance = importance,
        )
    }

    private fun connectKaomianjin(characterName: String): JSONObject {
        val settingsStore = runCatching {
            KoinJavaComponent.get<SettingsStore>(SettingsStore::class.java)
        }.getOrNull() ?: return JSONObject().apply {
            put("ok", false)
            put("error", "Tumin assistant catalog is unavailable")
        }

        val assistants = settingsStore.settingsFlow.value.assistants
        val normalizedName = characterName.trim()
        val exactMatches = if (normalizedName.isBlank()) emptyList() else assistants.filter {
            it.name.trim().equals(normalizedName, ignoreCase = true)
        }
        val assistant = when {
            exactMatches.size == 1 -> exactMatches.first()
            assistants.size == 1 -> assistants.first()
            exactMatches.size > 1 -> null
            else -> null
        }

        return if (assistant != null) {
            JSONObject().apply {
                put("ok", true)
                put("protocol", "tumin-world-bridge")
                put("version", 1)
                put("globalCharacterId", assistant.id.toString())
                put("assistantId", assistant.id.toString())
                put("assistantName", assistant.name)
            }
        } else {
            JSONObject().apply {
                put("ok", false)
                put("error", "No unique Tumin assistant matches kaomianjin character: $normalizedName")
                put("needsBinding", true)
            }
        }
    }

    private fun isTuminAssistant(assistantId: String): Boolean {
        if (assistantId.isBlank()) return false
        val settingsStore = runCatching {
            KoinJavaComponent.get<SettingsStore>(SettingsStore::class.java)
        }.getOrNull() ?: return false
        return settingsStore.settingsFlow.value.assistants.any { it.id.toString() == assistantId }
    }

    private inline fun bridgeResult(block: () -> JSONObject): String = runCatching(block)
        .getOrElse { error ->
            JSONObject().apply {
                put("ok", false)
                put("error", error.message ?: "World Bridge request failed")
            }
        }
        .toString()

    private companion object {
        const val KAOMIANJIN_WORLD_ID = "kaomianjin"
    }
}
