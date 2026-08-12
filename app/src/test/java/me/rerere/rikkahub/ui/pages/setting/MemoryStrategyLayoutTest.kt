package me.rerere.rikkahub.ui.pages.setting

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoryStrategyLayoutTest {
    @Test
    fun strategySelectorUsesFullWidthVerticalLayout() {
        val source = File("src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingMemoryPage.kt").readText()
        val strategyBlock = source.substringAfter("CardGroup(title = { Text(\"记忆策略\") })")
            .substringBefore("CardGroup(title = { Text(\"核心身份 · 常驻\") })")

        assertTrue(strategyBlock.contains("modifier = Modifier.fillMaxWidth()"))
        assertFalse(strategyBlock.contains("trailingContent"))
    }
}
