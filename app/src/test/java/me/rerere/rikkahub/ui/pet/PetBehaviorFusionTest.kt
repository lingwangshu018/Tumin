package me.rerere.rikkahub.ui.pet

import me.rerere.rikkahub.data.model.CharacterState
import me.rerere.rikkahub.data.model.CompanionState
import me.rerere.rikkahub.data.model.RelationshipChange
import me.rerere.rikkahub.data.model.RelationshipState
import org.junit.Assert.assertEquals
import org.junit.Test

class PetBehaviorFusionTest {
    @Test
    fun reconciliationHasHighestPriority() {
        val state = CompanionState(
            character = CharacterState(emotion = "生气", condition = "很困"),
            relationship = RelationshipState(
                conflict = 25,
                recentChanges = listOf(RelationshipChange(summary = "刚刚和好，愿意重新靠近")),
            ),
        )
        assertEquals(PetMotion.RECONCILE, state.toFusedPetPresentation().motion)
    }

    @Test
    fun sleepOverridesOrdinaryRelationshipMotion() {
        val state = CompanionState(
            character = CharacterState(activity = "准备睡觉", condition = "有点疲惫"),
            relationship = RelationshipState(intimacy = 90, trust = 90, security = 90),
        )
        assertEquals(PetMotion.SLEEP, state.toFusedPetPresentation().motion)
    }

    @Test
    fun angerOverridesRelationshipBaseline() {
        val state = CompanionState(
            character = CharacterState(emotion = "生气"),
            relationship = RelationshipState(intimacy = 80, trust = 80, security = 80),
        )
        assertEquals(PetMotion.ANGRY, state.toFusedPetPresentation().motion)
    }

    @Test
    fun guardedRelationshipBlocksMildShyOverride() {
        val state = CompanionState(
            character = CharacterState(emotion = "害羞"),
            relationship = RelationshipState(conflict = 90, security = 10),
        )
        assertEquals(PetMotion.CAUTIOUS, state.toFusedPetPresentation().motion)
    }

    @Test
    fun shyAndCuriousStatesUsePreparedActions() {
        val shy = CompanionState(character = CharacterState(emotion = "害羞"))
        val curious = CompanionState(character = CharacterState(emotion = "好奇"))
        assertEquals(PetMotion.SHY, shy.toFusedPetPresentation().motion)
        assertEquals(PetMotion.LOOK, curious.toFusedPetPresentation().motion)
    }

    @Test
    fun calmStateFallsBackToRelationshipBaseline() {
        val state = CompanionState(
            character = CharacterState(emotion = "平静"),
            relationship = RelationshipState(intimacy = 10, trust = 10, security = 10),
        )
        assertEquals(PetMotion.IDLE, state.toFusedPetPresentation().motion)
    }
}
