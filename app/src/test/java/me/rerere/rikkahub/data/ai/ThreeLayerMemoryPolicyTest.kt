package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreeLayerMemoryPolicyTest {
    @Test
    fun `recalls only memories relevant to current user text`() {
        val memories = listOf(
            AssistantMemory(1, "用户喜欢雨天一起喝热可可"),
            AssistantMemory(2, "用户下周要参加数学考试"),
            AssistantMemory(3, "The user's cat is named Momo"),
        )

        val result = ThreeLayerMemoryPolicy.selectLongTermMemories(
            memories = memories,
            query = "数学考试要复习什么？",
            limit = 6,
            maxChars = 3000,
        )

        assertEquals(listOf(2), result.map { it.id })
    }

    @Test
    fun `respects count and character budgets`() {
        val result = ThreeLayerMemoryPolicy.selectLongTermMemories(
            memories = listOf(
                AssistantMemory(1, "cat likes fish"),
                AssistantMemory(2, "cat sleeps by the window"),
            ),
            query = "cat",
            limit = 1,
            maxChars = 8,
        )

        assertEquals(1, result.size)
        assertEquals(8, result.single().content.length)
    }

    @Test
    fun `recent chats become fallback instead of per turn injection`() {
        val layered = Assistant(
            enableRecentChatsReference = true,
            enableThreeLayerMemory = true,
            enableCrossWindowMemory = true,
        )
        assertFalse(ThreeLayerMemoryPolicy.shouldInjectRecentChats(layered))
        assertTrue(ThreeLayerMemoryPolicy.shouldInjectRecentChats(
            layered.copy(enableCrossWindowMemory = false)
        ))
        assertTrue(ThreeLayerMemoryPolicy.shouldInjectRecentChats(
            layered.copy(enableThreeLayerMemory = false)
        ))
    }
}
