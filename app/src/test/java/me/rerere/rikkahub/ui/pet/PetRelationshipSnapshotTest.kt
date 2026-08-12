package me.rerere.rikkahub.ui.pet

import me.rerere.rikkahub.data.model.RelationshipChange
import me.rerere.rikkahub.data.model.RelationshipStage
import me.rerere.rikkahub.data.model.RelationshipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetRelationshipSnapshotTest {
    @Test
    fun projectionKeepsRelationshipFileFields() {
        val state = RelationshipState(
            stage = RelationshipStage.ROMANCE,
            intimacy = 72,
            trust = 68,
            attraction = 84,
            security = 61,
            conflict = 12,
            summary = "越来越亲近。",
            milestones = listOf("第一次认真谈心"),
            recentChanges = listOf(
                RelationshipChange(
                    summary = "一次坦诚交流",
                    effects = listOf("信任上升"),
                    createdAt = 123L,
                ),
            ),
            unresolvedIssues = listOf("还没说开的担心"),
            updatedAt = 456L,
        )

        val snapshot = state.toPetRelationshipSnapshot()

        assertEquals(RelationshipStage.ROMANCE, snapshot.stage)
        assertEquals(72, snapshot.intimacy)
        assertEquals(68, snapshot.trust)
        assertEquals(84, snapshot.attraction)
        assertEquals(61, snapshot.security)
        assertEquals(12, snapshot.conflict)
        assertEquals("越来越亲近。", snapshot.summary)
        assertEquals(listOf("第一次认真谈心"), snapshot.milestones)
        assertEquals("一次坦诚交流", snapshot.recentChanges.single().summary)
        assertEquals(listOf("信任上升"), snapshot.recentChanges.single().effects)
        assertEquals(listOf("还没说开的担心"), snapshot.unresolvedIssues)
        assertEquals(PetRelationshipBehavior.AFFECTIONATE, snapshot.behavior)
    }

    @Test
    fun projectionNormalizesScoresBeforePetUsesThem() {
        val snapshot = RelationshipState(
            intimacy = 130,
            trust = -5,
            attraction = 101,
            security = -1,
            conflict = 222,
        ).toPetRelationshipSnapshot()

        assertEquals(100, snapshot.intimacy)
        assertEquals(0, snapshot.trust)
        assertEquals(100, snapshot.attraction)
        assertEquals(0, snapshot.security)
        assertEquals(100, snapshot.conflict)
        assertTrue(snapshot.behavior == PetRelationshipBehavior.GUARDED)
    }

    @Test
    fun highConflictOverridesRelationshipStageForPetBehaviorOnly() {
        val snapshot = RelationshipState(
            stage = RelationshipStage.COMMITTED,
            security = 30,
            conflict = 80,
        ).toPetRelationshipSnapshot()

        assertEquals(RelationshipStage.COMMITTED, snapshot.stage)
        assertEquals(PetRelationshipBehavior.GUARDED, snapshot.behavior)
    }
}
