/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory

/**
 * Coordinates the three memory layers without introducing another persistence format:
 * core identity stays in the system prompt, cross-window life events remain incremental,
 * and durable memories are selected for relevance to the current visible user text.
 */
internal object ThreeLayerMemoryPolicy {
    fun selectLongTermMemories(
        memories: List<AssistantMemory>,
        query: String,
        limit: Int,
        maxChars: Int,
    ): List<AssistantMemory> {
        if (query.isBlank() || memories.isEmpty() || limit <= 0 || maxChars <= 0) return emptyList()
        val queryTerms = terms(query)
        if (queryTerms.isEmpty()) return emptyList()

        val ranked = memories.mapNotNull { memory ->
            val content = memory.content.trim()
            if (content.isBlank()) return@mapNotNull null
            val contentTerms = terms(content)
            val overlap = queryTerms.count { it in contentTerms }
            val phraseBonus = if (content.contains(query.trim(), ignoreCase = true)) 4 else 0
            val score = overlap + phraseBonus
            if (score > 0) memory to score else null
        }.sortedWith(
            compareByDescending<Pair<AssistantMemory, Int>> { it.second }
                .thenByDescending { it.first.id }
        )

        val selected = mutableListOf<AssistantMemory>()
        var chars = 0
        for ((memory, _) in ranked) {
            if (selected.size >= limit) break
            val nextChars = memory.content.length
            if (selected.isNotEmpty() && chars + nextChars > maxChars) break
            if (selected.isEmpty() && nextChars > maxChars) {
                selected += memory.copy(content = memory.content.take(maxChars))
                break
            }
            selected += memory
            chars += nextChars
        }
        return selected
    }

    fun shouldInjectRecentChats(assistant: Assistant): Boolean {
        if (!assistant.enableRecentChatsReference) return false
        if (!assistant.enableThreeLayerMemory) return true
        return assistant.useRecentChatsAsFallback && !assistant.enableCrossWindowMemory
    }

    private fun terms(text: String): Set<String> {
        val normalized = text.lowercase()
        val latinTerms = Regex("[\\p{L}\\p{N}_]{2,}")
            .findAll(normalized)
            .map { it.value }
            .filterNot { token -> token.all { it in '\u4e00'..'\u9fff' } }
        val cjk = normalized.filter { it in '\u4e00'..'\u9fff' }
        val cjkTerms = if (cjk.length >= 2) cjk.windowed(2).asSequence() else emptySequence()
        return (latinTerms + cjkTerms).toSet()
    }
}
