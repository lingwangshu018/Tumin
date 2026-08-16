package me.rerere.rikkahub.data.relationship

import me.rerere.rikkahub.data.model.RelationshipEvent
import me.rerere.rikkahub.data.model.RelationshipEventType
import me.rerere.rikkahub.data.model.RelationshipStage
import me.rerere.rikkahub.data.model.RelationshipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelationshipCoreV2Test {
    @Test
    fun familiarCanGrowIntoAmbiguousWithoutExplicitRomance() {
        val current = RelationshipState(
            stage = RelationshipStage.FAMILIAR,
            intimacy = 44,
            trust = 36,
            attraction = 39,
            security = 40,
        )

        val next = RelationshipRuleEngine.apply(
            current,
            RelationshipEvent(
                meaningful = true,
                type = RelationshipEventType.FLIRTING,
                intensity = 3,
                summary = "双方出现了明确但尚未确认关系的暧昧互动",
            ),
            now = 100_000L,
        )

        assertEquals(RelationshipStage.AMBIGUOUS, next.stage)
    }

    @Test
    fun confessionAloneDoesNotCreateRomanceButConfirmationDoes() {
        val ambiguous = RelationshipState(
            stage = RelationshipStage.AMBIGUOUS,
            intimacy = 58,
            trust = 48,
            attraction = 55,
            security = 48,
        )

        val afterConfession = RelationshipRuleEngine.apply(
            ambiguous,
            RelationshipEvent(
                meaningful = true,
                type = RelationshipEventType.CONFESSION,
                intensity = 4,
                summary = "第一次明确表白",
            ),
            now = 100_000L,
        )
        assertEquals(RelationshipStage.AMBIGUOUS, afterConfession.stage)
        assertTrue(afterConfession.milestoneKeys.contains("event:confession"))

        val afterConfirmation = RelationshipRuleEngine.apply(
            afterConfession,
            RelationshipEvent(
                meaningful = true,
                type = RelationshipEventType.RELATIONSHIP_CONFIRMED,
                intensity = 4,
                summary = "双方明确确认恋爱关系",
            ),
            now = 200_000L,
        )
        assertEquals(RelationshipStage.ROMANCE, afterConfirmation.stage)
        assertTrue(afterConfirmation.milestoneKeys.contains("event:relationship_confirmed"))
    }

    @Test
    fun apologyDoesNotResolveIssueButReconciliationDoes() {
        val romance = RelationshipState(
            stage = RelationshipStage.ROMANCE,
            intimacy = 70,
            trust = 70,
            attraction = 70,
            security = 65,
        )
        val issue = "因为一次失约产生的不安"

        val afterConflict = RelationshipRuleEngine.apply(
            romance,
            RelationshipEvent(
                meaningful = true,
                type = RelationshipEventType.CONFLICT,
                intensity = 3,
                summary = "双方因为失约发生争执",
                targetIssue = issue,
            ),
            now = 100_000L,
        )
        assertTrue(afterConflict.unresolvedIssues.contains(issue))

        val afterApology = RelationshipRuleEngine.apply(
            afterConflict,
            RelationshipEvent(
                meaningful = true,
                type = RelationshipEventType.APOLOGY,
                intensity = 3,
                summary = "角色认真道歉",
                targetIssue = issue,
            ),
            now = 200_000L,
        )
        assertTrue(afterApology.unresolvedIssues.contains(issue))
        assertFalse(afterApology.resolvedIssues.contains(issue))

        val afterReconciliation = RelationshipRuleEngine.apply(
            afterApology,
            RelationshipEvent(
                meaningful = true,
                type = RelationshipEventType.RECONCILIATION,
                intensity = 3,
                summary = "双方谈开并接受了彼此的修复",
                targetIssue = issue,
            ),
            now = 300_000L,
        )
        assertFalse(afterReconciliation.unresolvedIssues.contains(issue))
        assertTrue(afterReconciliation.resolvedIssues.contains(issue))
    }

    @Test
    fun commitmentRequiresEstablishedRomanceAndEnoughTrust() {
        val ready = RelationshipState(
            stage = RelationshipStage.ROMANCE,
            intimacy = 76,
            trust = 66,
            attraction = 72,
            security = 58,
        )

        val committed = RelationshipRuleEngine.apply(
            ready,
            RelationshipEvent(
                meaningful = true,
                type = RelationshipEventType.COMMITMENT,
                intensity = 4,
                summary = "双方作出明确长期承诺",
            ),
            now = 100_000L,
        )

        assertEquals(RelationshipStage.COMMITTED, committed.stage)
        assertTrue(committed.milestoneKeys.contains("event:commitment"))
    }

    @Test
    fun automaticMilestonesAreDeduplicated() {
        val current = RelationshipState(
            stage = RelationshipStage.AMBIGUOUS,
            intimacy = 60,
            trust = 50,
            attraction = 60,
            security = 50,
        )
        val event = RelationshipEvent(
            meaningful = true,
            type = RelationshipEventType.CONFESSION,
            intensity = 4,
            summary = "明确表白",
        )

        val first = RelationshipRuleEngine.apply(current, event, now = 100_000L)
        val second = RelationshipRuleEngine.apply(first, event, now = 200_000L)

        assertEquals(1, second.milestoneKeys.count { it == "event:confession" })
        assertEquals(1, second.milestones.count { it == "第一次留下明确的表白记录" })
    }
}
