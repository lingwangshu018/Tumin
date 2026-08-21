/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.pages.webview

import android.content.Context
import android.webkit.JavascriptInterface
import me.rerere.rikkahub.data.memory.WorldMemoryBridgeStore
import org.json.JSONObject

/** Android host side of World Bridge Protocol v1. */
class WorldPortalBridge(context: Context) {
    private val store = WorldMemoryBridgeStore(context)

    @JavascriptInterface
    fun getCapabilities(): String = JSONObject().apply {
        put("ok", true)
        put("protocol", "tumin-world-bridge")
        put("version", 1)
        put("host", "tumin")
        put("memoryRead", true)
        put("memoryWrite", true)
        put("identityBinding", true)
    }.toString()

    @JavascriptInterface
    fun connect(
        worldId: String,
        worldName: String,
        localCharacterId: String,
        characterName: String,
    ): String = bridgeResult {
        store.connect(worldId, worldName, localCharacterId, characterName)
    }

    @JavascriptInterface
    fun readMemories(globalCharacterId: String, limit: Int): String = bridgeResult {
        store.readMemories(globalCharacterId, limit)
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

    private inline fun bridgeResult(block: () -> JSONObject): String = runCatching(block)
        .getOrElse { error ->
            JSONObject().apply {
                put("ok", false)
                put("error", error.message ?: "World Bridge request failed")
            }
        }
        .toString()
}
