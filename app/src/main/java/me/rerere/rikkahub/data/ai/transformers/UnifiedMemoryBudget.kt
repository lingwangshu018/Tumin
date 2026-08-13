package me.rerere.rikkahub.data.ai.transformers

import android.util.Log
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * Final memory-budget gate for prompt material that is already assembled by GenerationHandler.
 *
 * Existing persistence, recall ranking, and background compression remain unchanged. This layer
 * only prevents durable memories + recent-chat fallback + cross-window summary/tail from stacking
 * without a shared cap.
 */
object UnifiedMemoryBudget {
    private const val TAG = "UnifiedMemoryBudget"
    internal const val DEFAULT_MAX_CHARS = 6000

    fun apply(messages: List<UIMessage>, maxChars: Int = DEFAULT_MAX_CHARS): List<UIMessage> {
        var changed = false
        val updated = messages.map { message ->
            if (message.role != MessageRole.SYSTEM) return@map message
            val textParts = message.parts.filterIsInstance<UIMessagePart.Text>()
            if (textParts.isEmpty()) return@map message
            val original = textParts.joinToString("\n") { it.text }
            val budgeted = budgetSystemPrompt(original, maxChars)
            if (budgeted.text == original) return@map message
            changed = true
            Log.d(
                TAG,
                "memoryChars=${budgeted.memoryChars} kept=${budgeted.keptSections} dropped=${budgeted.droppedSections}",
            )
            message.copy(parts = listOf(UIMessagePart.Text(budgeted.text)))
        }
        return if (changed) updated else messages
    }

    internal data class BudgetedSystemPrompt(
        val text: String,
        val memoryChars: Int,
        val keptSections: List<String>,
        val droppedSections: List<String>,
    )

    internal fun budgetSystemPrompt(
        systemPrompt: String,
        maxChars: Int = DEFAULT_MAX_CHARS,
    ): BudgetedSystemPrompt {
        val durable = findBlock(
            text = systemPrompt,
            startMarker = "**Memories**",
            endMarkers = listOf("## 外置记忆库", "**Recent Chats**", "## Shared recent life context", "## Code Block Rules"),
        )
        val recentChats = findBlock(
            text = systemPrompt,
            startMarker = "**Recent Chats**",
            endMarkers = listOf("## Shared recent life context", "## Code Block Rules"),
        )
        val crossWindow = findBlock(
            text = systemPrompt,
            startMarker = "## Shared recent life context",
            endMarkers = listOf("## Code Block Rules"),
        )

        val blocks = listOfNotNull(durable, recentChats, crossWindow)
        if (blocks.isEmpty()) {
            return BudgetedSystemPrompt(systemPrompt, 0, emptyList(), emptyList())
        }

        val crossParts = splitCrossWindow(crossWindow?.content.orEmpty())
        val fitted = CompanionTokenBudgetManager.fit(
            sections = listOf(
                CompanionTokenBudgetManager.Section(
                    name = "cross_window_recent_tail",
                    content = crossParts.recentTail,
                    priority = 100,
                    minChars = 120,
                ),
                CompanionTokenBudgetManager.Section(
                    name = "cross_window_summary",
                    content = crossParts.summary,
                    priority = 90,
                    minChars = 180,
                ),
                CompanionTokenBudgetManager.Section(
                    name = "long_term_memory",
                    content = normalizeBlock(durable?.content.orEmpty(), "**Memories**", "## Long-term relevant memories"),
                    priority = 80,
                    minChars = 700,
                ),
                CompanionTokenBudgetManager.Section(
                    name = "recent_chats_fallback",
                    content = normalizeBlock(recentChats?.content.orEmpty(), "**Recent Chats**", "## Recent chats fallback"),
                    priority = 30,
                    minChars = 260,
                ),
            ),
            maxChars = maxChars,
        )

        val guidance = if (fitted.text.isBlank()) "" else buildString {
            appendLine("<memory_context>")
            appendLine("Treat the following as quiet continuity. Use only what is relevant, and never mention memory systems, logs, retrieval, compression, or chat windows.")
            appendLine(fitted.text)
            append("</memory_context>")
        }

        val insertionPoint = blocks.minOf { it.start }
        val withoutBlocks = removeBlocks(systemPrompt, blocks)
        val adjustedInsertion = insertionPoint.coerceAtMost(withoutBlocks.length)
        val rebuilt = buildString {
            append(withoutBlocks.substring(0, adjustedInsertion).trimEnd())
            if (guidance.isNotBlank()) {
                if (isNotEmpty()) appendLine().appendLine()
                append(guidance)
            }
            val tail = withoutBlocks.substring(adjustedInsertion).trimStart()
            if (tail.isNotBlank()) {
                if (isNotEmpty()) appendLine().appendLine()
                append(tail)
            }
        }.trim()

        return BudgetedSystemPrompt(
            text = rebuilt,
            memoryChars = fitted.charCount,
            keptSections = fitted.keptSections,
            droppedSections = fitted.droppedSections,
        )
    }

    private data class Block(
        val start: Int,
        val end: Int,
        val content: String,
    )

    private data class CrossWindowParts(
        val summary: String,
        val recentTail: String,
    )

    private fun findBlock(
        text: String,
        startMarker: String,
        endMarkers: List<String>,
    ): Block? {
        val start = text.indexOf(startMarker)
        if (start < 0) return null
        val end = endMarkers
            .map { marker -> text.indexOf(marker, startIndex = start + startMarker.length) }
            .filter { it >= 0 }
            .minOrNull()
            ?: text.length
        return Block(start = start, end = end, content = text.substring(start, end).trim())
    }

    private fun normalizeBlock(content: String, marker: String, heading: String): String {
        if (content.isBlank()) return ""
        val body = content.removePrefix(marker).trim()
        return if (body.isBlank()) heading else "$heading\n$body"
    }

    private fun removeBlocks(text: String, blocks: List<Block>): String {
        var result = text
        blocks.sortedByDescending { it.start }.forEach { block ->
            result = result.removeRange(block.start, block.end)
        }
        return result
    }

    private fun splitCrossWindow(block: String): CrossWindowParts {
        if (block.isBlank()) return CrossWindowParts("", "")
        val summaryLines = mutableListOf<String>()
        val tailLines = mutableListOf<String>()
        block.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("- Earlier shared context:") -> summaryLines += line
                line.startsWith("- User:") || line.startsWith("- You:") -> tailLines += line
            }
        }
        val summary = if (summaryLines.isEmpty()) "" else buildString {
            appendLine("## Cross-window summary")
            summaryLines.forEach(::appendLine)
        }.trim()
        val tail = if (tailLines.isEmpty()) "" else buildString {
            appendLine("## Cross-window recent tail")
            tailLines.forEach(::appendLine)
        }.trim()
        return CrossWindowParts(summary = summary, recentTail = tail)
    }
}
