package me.rerere.rikkahub.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CompanionStateTest {
    @Test
    fun relationshipValuesStayBounded() {
        val state = RelationshipState(intimacy = 140, conflict = -8).normalized()
        assertEquals(100, state.intimacy)
        assertEquals(0, state.conflict)
    }

    @Test
    fun repeatedMilestonesAreDeduplicated() {
        val state = RelationshipState(milestones = listOf("第一次聊天", "第一次聊天")).normalized()
        assertEquals(listOf("第一次聊天"), state.milestones)
    }
}
