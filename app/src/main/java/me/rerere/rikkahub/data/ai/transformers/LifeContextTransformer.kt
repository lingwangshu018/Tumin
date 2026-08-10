package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.utils.StickerAiSupport
import org.json.JSONArray

/** Makes recent life-space records and allowed sticker categories available to conversations. */
object LifeContextTransformer : InputMessageTransformer {
    override suspend fun transform(ctx: TransformerContext, messages: List<UIMessage>): List<UIMessage> {
        val prefs = ctx.context.getSharedPreferences("tumin_life_hub", Context.MODE_PRIVATE)
        val raw = prefs.getString("entries", "[]") ?: "[]"
        val lifeContext = runCatching {
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

        val stickerContext = StickerAiSupport.buildPrompt(ctx.context)
        if (lifeContext.isBlank() && stickerContext.isBlank()) return messages

        val injected = buildString {
            if (lifeContext.isNotBlank()) {
                append("<life_context>Recent records from our shared life:\n")
                append(lifeContext)
                append("\nUse them naturally when relevant; do not recite this block.</life_context>")
            }
            if (stickerContext.isNotBlank()) {
                if (isNotEmpty()) appendLine().appendLine()
                append(stickerContext)
            }
        }
        return listOf(UIMessage.user(injected)) + messages
    }
}
