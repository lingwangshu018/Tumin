package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.utils.StickerAiSupport
import org.json.JSONArray
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Makes recent life-space, health-cycle, shared-reading, and allowed sticker context available to conversations. */
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
                    // STATUS 已升级为独立周期模块，旧记录仍保留，但不重复注入。
                    if (item.optString("section") == "STATUS") continue
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

        val healthContext = buildHealthContext(ctx.context)
        val readingContext = buildReadingContext(ctx.context)
        val stickerContext = StickerAiSupport.buildPrompt(
            context = ctx.context,
            assistantId = ctx.assistant.id.toString(),
        )
        if (lifeContext.isBlank() && healthContext.isBlank() && readingContext.isBlank() && stickerContext.isBlank()) return messages

        val injected = buildString {
            if (lifeContext.isNotBlank()) {
                append("<life_context>Recent records from our shared life:\n")
                append(lifeContext)
                append("\nUse them naturally when relevant; do not recite this block.</life_context>")
            }
            if (healthContext.isNotBlank()) {
                if (isNotEmpty()) appendLine().appendLine()
                append(healthContext)
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

    private fun buildHealthContext(context: Context): String {
        val prefs = context.getSharedPreferences("tumin_health_cycle", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("ai_allowed", true)) return ""
        val periodsRaw = prefs.getString("periods", "[]") ?: "[]"
        val logsRaw = prefs.getString("daily_logs", "[]") ?: "[]"
        return runCatching {
            val periods = JSONArray(periodsRaw)
            val logs = JSONArray(logsRaw)
            if (periods.length() == 0 && logs.length() == 0) return@runCatching ""

            val starts = buildList {
                for (i in 0 until periods.length()) {
                    periods.getJSONObject(i).optString("start").takeIf { it.isNotBlank() }?.let { add(LocalDate.parse(it)) }
                }
            }.sorted()
            val defaultCycle = prefs.getInt("cycle_length", 28)
            val intervals = starts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }.filter { it in 15..60 }.takeLast(6)
            val cycle = intervals.takeIf { it.isNotEmpty() }?.average()?.toInt()?.coerceIn(20, 45) ?: defaultCycle
            val lastStart = starts.lastOrNull()
            val today = LocalDate.now()
            val predicted = lastStart?.plusDays(cycle.toLong())
            val daysUntil = predicted?.let { ChronoUnit.DAYS.between(today, it).toInt() }

            var currentPeriodDay: Int? = null
            for (i in 0 until periods.length()) {
                val item = periods.getJSONObject(i)
                val start = item.optString("start").takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: continue
                val end = item.optString("end").takeIf { it.isNotBlank() }?.let(LocalDate::parse)
                if (!today.isBefore(start) && (end == null || !today.isAfter(end))) {
                    currentPeriodDay = ChronoUnit.DAYS.between(start, today).toInt() + 1
                }
            }

            val recentLogs = buildList {
                for (i in 0 until logs.length()) {
                    val item = logs.getJSONObject(i)
                    val date = item.optString("date").takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: continue
                    if (ChronoUnit.DAYS.between(date, today) in 0..7) add(item)
                }
            }.sortedByDescending { it.optString("date") }.take(3)

            buildString {
                appendLine("<body_cycle_context>")
                when {
                    currentPeriodDay != null -> appendLine("用户当前记录为经期第 ${currentPeriodDay} 天。")
                    daysUntil != null && daysUntil >= 0 -> appendLine("按用户自己的历史记录简单估算，距离下一次经期约 $daysUntil 天。")
                    predicted != null -> appendLine("按历史记录估算的经期日期已经经过，实际情况可能与预测不同。")
                }
                if (recentLogs.isNotEmpty()) {
                    appendLine("最近身体记录：")
                    recentLogs.reversed().forEach { item ->
                        val parts = buildList {
                            item.optString("flow").takeIf { it.isNotBlank() }?.let { add("经量:$it") }
                            val symptomArray = item.optJSONArray("symptoms") ?: JSONArray()
                            val symptoms = buildList { for (j in 0 until symptomArray.length()) add(symptomArray.optString(j)) }.filter { it.isNotBlank() }
                            if (symptoms.isNotEmpty()) add("身体:${symptoms.joinToString("、")}")
                            item.optString("mood").takeIf { it.isNotBlank() }?.let { add("心情:$it") }
                            item.optString("energy").takeIf { it.isNotBlank() }?.let { add("精力:$it") }
                            item.optString("note").takeIf { it.isNotBlank() }?.let { add("备注:${it.take(160)}") }
                        }
                        if (parts.isNotEmpty()) appendLine("- ${item.optString("date")}: ${parts.joinToString("；")}")
                    }
                }
                appendLine("请把这些信息当作用户主动允许你知道的生活状态，自然关心即可；不要把周期预测说成医学结论，也不要反复复述敏感数据。")
                append("</body_cycle_context>")
            }.trim()
        }.getOrDefault("")
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
