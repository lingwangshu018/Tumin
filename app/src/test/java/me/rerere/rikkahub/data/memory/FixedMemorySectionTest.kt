package me.rerere.rikkahub.data.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FixedMemorySectionTest {
    @Test
    fun savingFixedMemoryPreservesRolePrompt() {
        val original = "You are a gentle rabbit companion.\nAlways speak naturally."
        val updated = original.withFixedMemory("用户喜欢草莓蛋糕。")

        assertTrue(updated.startsWith(original))
        assertEquals("用户喜欢草莓蛋糕。", updated.extractFixedMemory())
    }

    @Test
    fun replacingFixedMemoryDoesNotDuplicateManagedBlock() {
        val first = "Role card".withFixedMemory("第一条")
        val second = first.withFixedMemory("第二条")

        assertEquals("第二条", second.extractFixedMemory())
        assertEquals(1, Regex("TUMIN_FIXED_MEMORY_BEGIN").findAll(second).count())
        assertFalse(second.contains("第一条"))
    }

    @Test
    fun clearingFixedMemoryRestoresRolePromptOnly() {
        val original = "Role card\nLine two"
        val withMemory = original.withFixedMemory("固定约定")

        assertEquals(original, withMemory.withFixedMemory(""))
    }
}
