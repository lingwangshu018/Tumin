package me.rerere.rikkahub.ui.pet

import me.rerere.rikkahub.data.model.RelationshipStage
import me.rerere.rikkahub.data.model.RelationshipState
import org.junit.Assert.assertEquals
import org.junit.Test

class PetPresentationTest {
    @Test
    fun romanceUsesAffectionatePresentation() {
        val presentation = RelationshipState(
            stage = RelationshipStage.ROMANCE,
            intimacy = 80,
            trust = 75,
            attraction = 88,
            security = 70,
            conflict = 5,
        ).toPetRelationshipSnapshot().toPetPresentation()

        assertEquals(PetMotion.AFFECTIONATE, presentation.motion)
        assertEquals("很喜欢和你贴近", presentation.statusLabel)
    }

    @Test
    fun committedUsesStayClosePresentation() {
        val presentation = RelationshipState(
            stage = RelationshipStage.COMMITTED,
            security = 82,
            conflict = 8,
        ).toPetRelationshipSnapshot().toPetPresentation()

        assertEquals(PetMotion.STAY_CLOSE, presentation.motion)
        assertEquals("已经把陪着你当成习惯", presentation.statusLabel)
    }

    @Test
    fun guardedStateOverridesCommittedPresentation() {
        val presentation = RelationshipState(
            stage = RelationshipStage.COMMITTED,
            security = 20,
            conflict = 90,
        ).toPetRelationshipSnapshot().toPetPresentation()

        assertEquals(PetMotion.CAUTIOUS, presentation.motion)
        assertEquals("现在有一点戒备", presentation.statusLabel)
    }
}
