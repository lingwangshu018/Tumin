package me.rerere.rikkahub.utils

import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.json.JSONArray

object StickerAiSupport {
    private const val PREFS = "tumin_sticker_library"
    private const val PACKS_KEY = "packs"
    private const val AI_ENABLED_PACKS_PREFIX = "ai_enabled_pack_ids_"

    data class Sticker(
        val packId: String,
        val packName: String,
        val name: String,
        val url: String,
    )

    data class PackSummary(
        val id: String,
        val name: String,
        val stickerCount: Int,
        val aiEnabled: Boolean,
    )

    fun getPackSummaries(context: Context, assistantId: String): List<PackSummary> {
        val enabled = getEnabledPackIds(context, assistantId)
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PACKS_KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val pack = array.getJSONObject(i)
                    val id = pack.optString("id")
                    if (id.isBlank()) continue
                    add(
                        PackSummary(
                            id = id,
                            name = pack.optString("name", "表情包"),
                            stickerCount = pack.optJSONArray("stickers")?.length() ?: 0,
                            aiEnabled = id in enabled,
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun setPackAiEnabled(
        context: Context,
        assistantId: String,
        packId: String,
        enabled: Boolean,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = getEnabledPackIds(context, assistantId).toMutableSet()
        if (enabled) ids += packId else ids -= packId
        prefs.edit().putStringSet(enabledKey(assistantId), ids).apply()
    }

    fun buildPrompt(context: Context, assistantId: String): String {
        val stickers = loadEnabledStickers(context, assistantId)
        if (stickers.isEmpty()) return ""
        val grouped = stickers.groupBy { it.packName }
        return buildString {
            appendLine("<sticker_library>")
            appendLine("你可以在聊天中偶尔发送一个表情包。只有下面列出的分类允许你使用：")
            grouped.forEach { (pack, items) ->
                append("- ").append(pack).append(": ")
                appendLine(items.map { it.name }.distinct().take(40).joinToString("、"))
            }
            appendLine()
            appendLine("需要发送表情时，在回复中单独写一个内部标记：[[STICKER:分类/表情名]]。")
            appendLine("例如：[[STICKER:兔兔/哭哭]]。App 会把标记替换成真正图片，用户不会看到标记。")
            appendLine("规则：只可使用上面列出的分类和表情名；不需要表情时不要输出标记；不要每条消息都发表情；通常一条回复最多一个；不要输出图片 URL。")
            appendLine("</sticker_library>")
        }.trim()
    }

    fun replaceDirectives(
        context: Context,
        assistantId: String,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val stickers = loadEnabledStickers(context, assistantId)
        if (stickers.isEmpty()) return messages
        val byKey = stickers.associateBy { normalizeKey(it.packName, it.name) }
        val regex = Regex("\\[\\[STICKER\\s*:\\s*([^/\\]\\n]+?)\\s*/\\s*([^\\]\\n]+?)\\s*]]")

        return messages.map { message ->
            if (message.role != MessageRole.ASSISTANT) return@map message
            val newParts = buildList {
                message.parts.forEach { part ->
                    if (part !is UIMessagePart.Text) {
                        add(part)
                        return@forEach
                    }
                    var cursor = 0
                    regex.findAll(part.text).forEach { match ->
                        val before = part.text.substring(cursor, match.range.first)
                        if (before.isNotEmpty()) add(part.copy(text = before))
                        val packName = match.groupValues[1].trim()
                        val stickerName = match.groupValues[2].trim()
                        val sticker = byKey[normalizeKey(packName, stickerName)]
                        if (sticker != null) {
                            add(
                                UIMessagePart.Image(
                                    url = sticker.url,
                                    metadata = buildJsonObject {
                                        put("tumin_sticker_name", sticker.name)
                                        put("tumin_sticker_pack", sticker.packName)
                                        put("tumin_sticker_ai", true)
                                    },
                                )
                            )
                        } else {
                            add(part.copy(text = match.value))
                        }
                        cursor = match.range.last + 1
                    }
                    val tail = part.text.substring(cursor)
                    if (tail.isNotEmpty()) add(part.copy(text = tail))
                }
            }
            message.copy(parts = newParts)
        }
    }

    private fun loadEnabledStickers(context: Context, assistantId: String): List<Sticker> {
        val enabled = getEnabledPackIds(context, assistantId)
        if (enabled.isEmpty()) return emptyList()
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PACKS_KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val pack = array.getJSONObject(i)
                    val packId = pack.optString("id")
                    if (packId !in enabled) continue
                    val packName = pack.optString("name", "表情包")
                    val items = pack.optJSONArray("stickers") ?: JSONArray()
                    for (j in 0 until items.length()) {
                        val item = items.getJSONObject(j)
                        val name = item.optString("name").trim()
                        val url = item.optString("url").trim()
                        if (name.isNotBlank() && url.isNotBlank()) {
                            add(Sticker(packId, packName, name, url))
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun getEnabledPackIds(context: Context, assistantId: String): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(enabledKey(assistantId), emptySet())
            ?.toSet()
            ?: emptySet()

    private fun enabledKey(assistantId: String) = AI_ENABLED_PACKS_PREFIX + assistantId

    private fun normalizeKey(pack: String, name: String): String =
        pack.trim().lowercase() + "\u0000" + name.trim().lowercase()
}
