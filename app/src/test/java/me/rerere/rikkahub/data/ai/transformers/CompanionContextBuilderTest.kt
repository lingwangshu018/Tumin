package me.rerere.rikkahub.data.ai.transformers

import me.rerere.rikkahub.data.model.CharacterState
import me.rerere.rikkahub.data.model.CompanionState
import me.rerere.rikkahub.data.model.RelationshipStage
import me.rerere.rikkahub.data.model.RelationshipState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompanionContextBuilderTest {
    @Test
    fun relationshipUsesNaturalLanguageInsteadOfRawScores() {
        val text = CompanionContextFormatter.format(
            CompanionState(
                character = CharacterState(emotion = "害羞", activity = "等你回消息"),
                relationship = RelationshipState(
                    stage = RelationshipStage.ROMANCE,
                    intimacy = 82,
                    trust = 77,
                    attraction = 88,
                    security = 73,
                    conflict = 8,
                ),
            ),
        )

        assertTrue("恋爱" in text || "热恋" in text)
        assertTrue("非常亲近" in text)
        assertTrue("明显心动" in text)
        assertTrue("害羞" in text)
        assertFalse("intimacy=" in text)
        assertFalse("82" in text)
        assertFalse("77" in text)
        assertFalse("88" in text)
    }

    @Test
    fun highConflictBecomesSemanticCaution() {
        val text = CompanionContextFormatter.format(
            CompanionState(
                relationship = RelationshipState(
                    stage = RelationshipStage.COMMITTED,
                    intimacy = 90,
                    trust = 70,
                    security = 25,
                    conflict = 82,
                ),
            ),
        )

        assertTrue("长期承诺" in text)
        assertTrue("安全感偏低" in text)
        assertTrue("明显冲突" in text)
    }

    @Test
    fun budgetKeepsHighPriorityAndDropsLowPriorityFirst() {
        val result = CompanionTokenBudgetManager.fit(
            sections = listOf(
                CompanionTokenBudgetManager.Section("core", "A".repeat(80), priority = 100, minChars = 40),
                CompanionTokenBudgetManager.Section("relationship", "B".repeat(70), priority = 90, minChars = 30),
                CompanionTokenBudgetManager.Section("sticker", "C".repeat(70), priority = 10, minChars = 30),
            ),
            maxChars = 130,
        )

        assertTrue("core" in result.keptSections)
        assertTrue("relationship" in result.keptSections)
        assertTrue("sticker" in result.droppedSections)
        assertTrue(result.charCount <= 130)
    }

    @Test
    fun zeroBudgetDropsEverything() {
        val result = CompanionTokenBudgetManager.fit(
            sections = listOf(
                CompanionTokenBudgetManager.Section("a", "hello", priority = 1),
            ),
            maxChars = 0,
        )

        assertTrue(result.text.isBlank())
        assertTrue(result.droppedSections == listOf("a"))
    }
}
