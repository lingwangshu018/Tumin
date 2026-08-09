package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import me.rerere.ai.ui.UIMessage
import org.json.JSONArray

/** Makes recent life-space records available to both normal and proactive conversations. */
object LifeContextTransformer : InputMessageTransformer {
    override suspend fun transform(ctx: TransformerContext, messages: List<UIMessage>): List<UIMessage> {
        val prefs = ctx.context.getSharedPreferences("tumin_life_hub", Context.MODE_PRIVATE)
        val raw = prefs.getString("entries", "[]") ?: "[]"
        val context = runCatching {
            val array = JSONArray(raw)
            val start = (array.length() - 12).coerceAtLeast(0)
            buildString {
                for (index in start until array.length()) {
                    val item = array.getJSONObject(index)
                    append("- [")
                    append(item.optString("section"))
                    append("] ")
                    append(item.optString("title"))
                    val detail = item.optString("detail")
                    val tag = item.optString("tag")
                    if (detail.isNotBlank()) append(": ").append(detail)
                    if (tag.isNotBlank()) append(" (").append(tag).append(')')
                    appendLine()
                }
            }.trim()
        }.getOrDefault("")
        if (context.isBlank()) return messages
        return listOf(UIMessage.user("<life_context>Recent records from our shared life:\n$context\nUse them naturally when relevant; do not recite this block.</life_context>")) + messages
    }
}
