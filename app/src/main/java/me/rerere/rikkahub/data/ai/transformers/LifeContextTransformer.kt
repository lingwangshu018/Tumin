package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.utils.StickerAiSupport
import org.json.JSONArray

/** Makes recent life-space, shared-reading, and allowed sticker context available to conversations. */
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

        val readingContext = buildReadingContext(ctx.context)
        val stickerContext = StickerAiSupport.buildPrompt(
            context = ctx.context,
            assistantId = ctx.assistant.id.toString(),
        )
        if (lifeContext.isBlank() && readingContext.isBlank() && stickerContext.isBlank()) return messages

        val injected = buildString {
            if (lifeContext.isNotBlank()) {
                append("<life_context>Recent records from our shared life:\n")
                append(lifeContext)
                append("\nUse them naturally when relevant; do not recite this block.</life_context>")
            }
            if (readingContext.isNotBlank()) {
                if (isNotEmpty()) appendLine().appendLine()
                append(readingContext)
            }
            if (stickerContext.isNotBlank()) {
                if (isNotEmpty()) appendLine().appendLine()
                append(stickerContext)
            }
        }
        return listOf(UIMessage.user(injected)) + messages
    }

    private fun buildReadingContext(context: Context): String {
        val prefs = context.getSharedPreferences("tumin_reading_space", Context.MODE_PRIVATE)
        val booksRaw = prefs.getString("books", "[]") ?: "[]"
        val notesRaw = prefs.getString("notes", "[]") ?: "[]"
        val memoriesRaw = prefs.getString("memories", "[]") ?: "[]"
        return runCatching {
            val books = JSONArray(booksRaw)
            if (books.length() == 0) return@runCatching ""

            var currentIndex = 0
            var newest = Long.MIN_VALUE
            for (i in 0 until books.length()) {
                val item = books.getJSONObject(i)
                val time = item.optLong("lastReadAt", 0L)
                if (time > newest) {
                    newest = time
                    currentIndex = i
                }
            }
            val book = books.getJSONObject(currentIndex)
            val bookId = book.optString("id")
            val notes = JSONArray(notesRaw)
            val memories = JSONArray(memoriesRaw)

            buildString {
                appendLine("<shared_reading>")
                appendLine("我们正在共读：${book.optString("title", "未命名书籍")}")
                appendLine("当前进度：第 ${book.optInt("chapterIndex", 0) + 1} 章附近，第 ${book.optInt("paragraphIndex", 0) + 1} 段附近。")

                val noteItems = buildList {
                    for (i in 0 until notes.length()) {
                        val item = notes.getJSONObject(i)
                        if (item.optString("bookId") == bookId) add(item)
                    }
                }.sortedByDescending { it.optLong("createdAt") }.take(5)
                if (noteItems.isNotEmpty()) {
                    appendLine("最近用户留给你的共读批注：")
                    noteItems.reversed().forEach { item ->
                        append("- 原文“").append(item.optString("quote").take(220)).append("”")
                        val text = item.optString("text")
                        if (text.isNotBlank()) append("；用户说：").append(text)
                        appendLine()
                    }
                }

                val memoryItems = buildList {
                    for (i in 0 until memories.length()) {
                        val item = memories.getJSONObject(i)
                        if (item.optString("bookId") == bookId) add(item)
                    }
                }.sortedByDescending { it.optLong("createdAt") }.take(6)
                if (memoryItems.isNotEmpty()) {
                    appendLine("我们的共读记忆：")
                    memoryItems.reversed().forEach { item ->
                        appendLine("- [${item.optString("type")}] ${item.optString("text")}")
                    }
                }
                appendLine("当用户聊到这本书或这些批注时，请像正在和用户一起读，而不是像书评助手。不要假装读过没有提供给你的章节内容。")
                append("</shared_reading>")
            }.trim()
        }.getOrDefault("")
    }
}
