package me.rerere.rikkahub.data.ai.transformers

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedMemoryBudgetTest {
    @Test
    fun leavesPromptUntouchedWhenNoManagedMemoryExists() {
        val source = "You are a character.\n\n## Code Block Rules\nKeep code tidy."
        val result = UnifiedMemoryBudget.budgetSystemPrompt(source, maxChars = 600)
        assertTrue(result.text == source)
        assertTrue(result.memoryChars == 0)
    }

    @Test
    fun keepsRecentCrossWindowTailAheadOfOlderMemory() {
        val source = buildString {
            appendLine("Character core")
            appendLine("**Memories**")
            appendLine("These are memories stored via the memory_tool.")
            appendLine("OLD-" + "x".repeat(900))
            appendLine("**Recent Chats**")
            appendLine("CHAT-" + "y".repeat(500))
            appendLine("## Shared recent life context")
            appendLine("The following are recent events you experienced with the user in other chat windows.")
            appendLine("- Earlier shared context: SUMMARY-" + "s".repeat(350))
            appendLine("- User: MOST-RECENT-USER-DETAIL")
            appendLine("- You: MOST-RECENT-ASSISTANT-DETAIL")
            appendLine("## Code Block Rules")
            appendLine("Keep code tidy.")
        }

        val result = UnifiedMemoryBudget.budgetSystemPrompt(source, maxChars = 700)

        assertTrue("MOST-RECENT-USER-DETAIL" in result.text)
        assertTrue("MOST-RECENT-ASSISTANT-DETAIL" in result.text)
        assertTrue("cross_window_recent_tail" in result.keptSections)
        assertTrue(result.memoryChars <= 700)
    }

    @Test
    fun removesOldMemoryMarkersAndReplacesThemWithOneQuietContext() {
        val source = buildString {
            appendLine("Character core")
            appendLine("**Memories**")
            appendLine("[{\"id\":1,\"content\":\"likes rabbits\"}]")
            appendLine("**Recent Chats**")
            appendLine("[{\"title\":\"Yesterday\"}]")
            appendLine("## Shared recent life context")
            appendLine("continuity instruction")
            appendLine("- Earlier shared context: promised to read together")
            appendLine("- User: remember chapter three")
            appendLine("## Code Block Rules")
            appendLine("Keep code tidy.")
        }

        val result = UnifiedMemoryBudget.budgetSystemPrompt(source, maxChars = 1200)

        assertTrue("<memory_context>" in result.text)
        assertTrue("</memory_context>" in result.text)
        assertTrue("## Code Block Rules" in result.text)
        assertFalse("**Memories**" in result.text)
        assertFalse("**Recent Chats**" in result.text)
        assertFalse("## Shared recent life context" in result.text)
        assertTrue("remember chapter three" in result.text)
    }

    @Test
    fun hardBudgetDropsLowPriorityRecentChatsBeforeRecentTail() {
        val source = buildString {
            appendLine("**Memories**")
            appendLine("LONG-" + "a".repeat(700))
            appendLine("**Recent Chats**")
            appendLine("FALLBACK-" + "b".repeat(700))
            appendLine("## Shared recent life context")
            appendLine("instruction")
            appendLine("- User: LIVE-TAIL")
            appendLine("- You: LIVE-REPLY")
            appendLine("## Code Block Rules")
        }

        val result = UnifiedMemoryBudget.budgetSystemPrompt(source, maxChars = 420)

        assertTrue("LIVE-TAIL" in result.text)
        assertTrue("cross_window_recent_tail" in result.keptSections)
        assertTrue("recent_chats_fallback" in result.droppedSections)
        assertTrue(result.memoryChars <= 420)
    }
}
