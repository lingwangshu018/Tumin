package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import android.util.Log
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.ui.pages.life.MusicPlaybackSession
import me.rerere.rikkahub.utils.StickerAiSupport
import org.json.JSONArray
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Makes companion state and recent life context available through one bounded injection. */
object LifeContextTransformer : InputMessageTransformer {
    private const val CONTEXT_BUDGET_CHARS = 5600

    override suspend fun transform(ctx: TransformerContext, messages: List<UIMessage>): List<UIMessage> {
        val prefs = ctx.context.getSharedPreferences("tumin_life_hub", Context.MODE_PRIVATE)
        val raw = prefs.getString("entries", "[]") ?: "[]"
        val lifeContext = runCatching {
            val array = JSONArray(raw)
            val start = (array.length() - 12).coerceAtLeast(0)
            buildString {
                for (index in start until array.length()) {
                    val item = array.getJSONObject(index)
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

        val companionContext = CompanionContextBuilder.build(ctx.context, ctx.assistant)
        val healthContext = buildHealthContext(ctx.context)
        val musicContext = buildMusicContext()
        val readingContext = buildReadingContext(ctx.context)
        val stickerContext = StickerAiSupport.buildPrompt(
            context = ctx.context,
            assistantId = ctx.assistant.id.toString(),
        )
        val wrappedLifeContext = if (lifeContext.isBlank()) "" else buildString {
            append("<life_context>Recent records from our shared life:\n")
            append(lifeContext)
            append("\nUse them naturally when relevant; do not recite this block.</life_context>")
        }

        val budgeted = CompanionTokenBudgetManager.fit(
            sections = listOf(
                CompanionTokenBudgetManager.Section(
                    name = "companion_state",
                    content = companionContext,
                    priority = 100,
                    minChars = 650,
                ),
                CompanionTokenBudgetManager.Section(
                    name = "health_cycle",
                    content = healthContext,
                    priority = 80,
                    minChars = 650,
                ),
                CompanionTokenBudgetManager.Section(
                    name = "shared_music",
                    content = musicContext,
                    priority = 70,
                    minChars = 350,
                ),
                CompanionTokenBudgetManager.Section(
                    name = "shared_reading",
                    content = readingContext,
                    priority = 65,
                    minChars = 650,
                ),
                CompanionTokenBudgetManager.Section(
                    name = "life_context",
                    content = wrappedLifeContext,
                    priority = 55,
                    minChars = 450,
                ),
                CompanionTokenBudgetManager.Section(
                    name = "sticker_context",
                    content = stickerContext,
                    priority = 40,
                    minChars = 220,
                ),
            ),
            maxChars = CONTEXT_BUDGET_CHARS,
        )
        if (budgeted.text.isBlank()) return messages

        Log.d(
            "CompanionContext",
            "assistant=${ctx.assistant.id} chars=${budgeted.charCount} kept=${budgeted.keptSections} dropped=${budgeted.droppedSections}",
        )
        return listOf(UIMessage.user(budgeted.text)) + messages
    }

    private fun buildMusicContext(): String {
        MusicPlaybackSession.syncPosition()
        val playback = MusicPlaybackSession.state.value
        if (!playback.active) return ""
        val lyricIndex = playback.lyricIndex()
        val currentLyric = playback.lyrics.getOrNull(lyricIndex)?.text.orEmpty()
        val previousLyric = playback.lyrics.getOrNull(lyricIndex - 1)?.text.orEmpty()
        val nextLyric = playback.lyrics.getOrNull(lyricIndex + 1)?.text.orEmpty()
        return buildString {
            appendLine("<shared_music>")
            appendLine(if (playback.togetherMode) "用户正在和你一起听歌。" else "用户当前正在听歌。")
            appendLine("歌曲：${playback.title} · ${playback.artist}")
            appendLine("播放状态：${if (playback.isPlaying) "播放中" else "暂停"}；进度约 ${formatPosition(playback.positionMs)}。")
            if (currentLyric.isNotBlank()) {
                appendLine("当前歌词：$currentLyric")
                if (previousLyric.isNotBlank()) appendLine("上一句：$previousLyric")
                if (nextLyric.isNotBlank()) appendLine("下一句：$nextLyric")
            }
            appendLine("如果用户说“这一句”“这里”“这段”之类的话，可以结合当前歌词自然回应。不要假装知道未提供的歌词或歌曲背景。")
            append("</shared_music>")
        }.trim()
    }

    private fun formatPosition(ms: Long): String {
        val safe = ms.coerceAtLeast(0L) / 1000L
        return "%02d:%02d".format(safe / 60L, safe % 60L)
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

            val defaultCycle = prefs.getInt("cycle_length", 30)
            val defaultPeriod = prefs.getInt("period_length", 7)
            val intervals = starts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
                .filter { it in 15..60 }
                .takeLast(6)
            val learnedCycle = if (intervals.size >= 2) {
                intervals.average().roundToInt().coerceIn(20, 45)
            } else null
            val cycle = learnedCycle ?: defaultCycle

            val completedLengths = buildList {
                for (i in 0 until periods.length()) {
                    val item = periods.getJSONObject(i)
                    val start = item.optString("start").takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: continue
                    val end = item.optString("end").takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: continue
                    val length = ChronoUnit.DAYS.between(start, end).toInt() + 1
                    if (length in 1..12) add(length)
                }
            }.takeLast(6)
            val learnedPeriod = if (completedLengths.size >= 2) {
                completedLengths.average().roundToInt().coerceIn(2, 10)
            } else null
            val periodLength = learnedPeriod ?: defaultPeriod

            val lastStart = starts.lastOrNull()
            val today = LocalDate.now()
            val predicted = lastStart?.plusDays(cycle.toLong())
            val daysUntil = predicted?.let { ChronoUnit.DAYS.between(today, it).toInt() }

            var currentPeriodDay: Int? = null
            var currentPeriodIsPredictedEnd = false
            for (i in 0 until periods.length()) {
                val item = periods.getJSONObject(i)
                val start = item.optString("start").takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: continue
                val recordedEnd = item.optString("end").takeIf { it.isNotBlank() }?.let(LocalDate::parse)
                val displayEnd = recordedEnd ?: start.plusDays((periodLength - 1).coerceAtLeast(0).toLong())
                if (!today.isBefore(start) && !today.isAfter(displayEnd)) {
                    currentPeriodDay = ChronoUnit.DAYS.between(start, today).toInt() + 1
                    currentPeriodIsPredictedEnd = recordedEnd == null
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
                    currentPeriodDay != null && currentPeriodIsPredictedEnd -> appendLine("用户记录了本轮经期开始，目前是第 ${currentPeriodDay} 天；结束日尚未手动确认，暂按约 $periodLength 天经期长度预测。")
                    currentPeriodDay != null -> appendLine("用户当前记录为经期第 ${currentPeriodDay} 天。")
                    daysUntil != null && daysUntil >= 0 -> {
                        if (learnedCycle != null) appendLine("根据用户最近的实际周期记录估算，距离下一次经期约 $daysUntil 天。")
                        else appendLine("历史记录还少，当前先按约 $cycle 天周期估算，距离下一次经期约 $daysUntil 天。")
                    }
                    predicted != null -> appendLine("当前预测的经期开始日期已经经过，实际情况可能与预测不同。")
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
                appendLine("请把这些信息当作用户主动允许你知道的生活状态，自然关心即可；所有日期都是记录或估算，不要把周期预测说成医学结论，也不要反复复述敏感数据。")
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
