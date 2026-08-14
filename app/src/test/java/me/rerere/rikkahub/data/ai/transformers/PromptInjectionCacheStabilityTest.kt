/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class PromptInjectionCacheStabilityTest {
    private fun dynamicLorebookEntry(
        position: InjectionPosition,
        content: String,
        priority: Int = 0,
        keywords: List<String> = listOf("trigger"),
    ) = PromptInjection.RegexInjection(
        position = position,
        content = content,
        priority = priority,
        keywords = keywords,
        constantActive = false,
    )

    private fun constantLorebookEntry(
        position: InjectionPosition,
        content: String,
    ) = PromptInjection.RegexInjection(
        position = position,
        content = content,
        constantActive = true,
    )

    private fun transformWithLorebook(
        messages: List<UIMessage>,
        entries: List<PromptInjection.RegexInjection>,
    ): List<UIMessage> {
        val lorebookId = Uuid.random()
        return transformMessages(
            messages = messages,
            assistant = Assistant(lorebookIds = setOf(lorebookId)),
            modeInjections = emptyList(),
            lorebooks = listOf(Lorebook(id = lorebookId, entries = entries)),
        )
    }

    @Test
    fun `dynamic after-system lorebook becomes trailing system text block`() {
        val result = transformWithLorebook(
            messages = listOf(
                UIMessage.system("Stable system"),
                UIMessage.user("please trigger this"),
            ),
            entries = listOf(
                dynamicLorebookEntry(
                    position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                    content = "Dynamic lore",
                )
            ),
        )

        assertEquals(MessageRole.SYSTEM, result.first().role)
        val textParts = result.first().parts.filterIsInstance<UIMessagePart.Text>()
        assertEquals(2, textParts.size)
        assertEquals("Stable system", textParts[0].text)
        assertEquals("Dynamic lore", textParts[1].text)
    }

    @Test
    fun `dynamic before-system lorebook also keeps stable prefix and system authority`() {
        val result = transformWithLorebook(
            messages = listOf(
                UIMessage.system("Stable system"),
                UIMessage.user("trigger"),
            ),
            entries = listOf(
                dynamicLorebookEntry(
                    position = InjectionPosition.BEFORE_SYSTEM_PROMPT,
                    content = "Dynamic before lore",
                )
            ),
        )

        val system = result.first()
        assertEquals(MessageRole.SYSTEM, system.role)
        val textParts = system.parts.filterIsInstance<UIMessagePart.Text>()
        assertEquals(listOf("Stable system", "Dynamic before lore"), textParts.map { it.text })
    }

    @Test
    fun `constant lorebook keeps configured system position in stable block`() {
        val result = transformWithLorebook(
            messages = listOf(
                UIMessage.system("Base system"),
                UIMessage.user("hello"),
            ),
            entries = listOf(
                constantLorebookEntry(
                    position = InjectionPosition.BEFORE_SYSTEM_PROMPT,
                    content = "Constant lore",
                )
            ),
        )

        val textParts = result.first().parts.filterIsInstance<UIMessagePart.Text>()
        assertEquals(1, textParts.size)
        assertTrue(textParts.single().text.startsWith("Constant lore\nBase system"))
    }

    @Test
    fun `constant lore stays stable while triggered lore gets separate tail block`() {
        val result = transformWithLorebook(
            messages = listOf(
                UIMessage.system("Base system"),
                UIMessage.user("trigger"),
            ),
            entries = listOf(
                constantLorebookEntry(
                    position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                    content = "Constant lore",
                ),
                dynamicLorebookEntry(
                    position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                    content = "Dynamic lore",
                ),
            ),
        )

        val textParts = result.first().parts.filterIsInstance<UIMessagePart.Text>()
        assertEquals(2, textParts.size)
        assertEquals("Base system\nConstant lore", textParts[0].text)
        assertEquals("Dynamic lore", textParts[1].text)
    }

    @Test
    fun `dynamic non-system lorebook keeps original chat injection behavior`() {
        val result = transformWithLorebook(
            messages = listOf(
                UIMessage.system("Stable system"),
                UIMessage.user("trigger"),
                UIMessage.assistant("reply"),
            ),
            entries = listOf(
                dynamicLorebookEntry(
                    position = InjectionPosition.BOTTOM_OF_CHAT,
                    content = "Dynamic chat lore",
                )
            ),
        )

        assertEquals(1, result.first().parts.filterIsInstance<UIMessagePart.Text>().size)
        assertEquals("Stable system", result.first().toText())
        assertTrue(result.any { it.role == MessageRole.USER && it.toText() == "Dynamic chat lore" })
    }

    @Test
    fun `multiple dynamic system lorebook entries keep priority order in tail block`() {
        val result = transformWithLorebook(
            messages = listOf(
                UIMessage.system("Stable system"),
                UIMessage.user("trigger"),
            ),
            entries = listOf(
                dynamicLorebookEntry(
                    position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                    content = "Low",
                    priority = 1,
                ),
                dynamicLorebookEntry(
                    position = InjectionPosition.BEFORE_SYSTEM_PROMPT,
                    content = "High",
                    priority = 10,
                ),
            ),
        )

        val textParts = result.first().parts.filterIsInstance<UIMessagePart.Text>()
        assertEquals("High\nLow", textParts.last().text)
    }

    @Test
    fun `dynamic system lorebook creates system message when none exists`() {
        val result = transformWithLorebook(
            messages = listOf(UIMessage.user("trigger")),
            entries = listOf(
                dynamicLorebookEntry(
                    position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                    content = "Dynamic only",
                )
            ),
        )

        assertEquals(MessageRole.SYSTEM, result.first().role)
        assertEquals("Dynamic only", result.first().toText())
    }
}
