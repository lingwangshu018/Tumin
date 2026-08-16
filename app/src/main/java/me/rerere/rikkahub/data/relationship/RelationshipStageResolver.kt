package me.rerere.rikkahub.data.relationship

import me.rerere.rikkahub.data.model.RelationshipEvent
import me.rerere.rikkahub.data.model.RelationshipEventType
import me.rerere.rikkahub.data.model.RelationshipStage
import me.rerere.rikkahub.data.model.RelationshipState

internal object RelationshipStageResolver {
    fun resolve(current: RelationshipState, event: RelationshipEvent): RelationshipStage {
        val state = current.normalized()

        if (event.type == RelationshipEventType.BREAKUP || event.type == RelationshipEventType.SEPARATION) {
            return when (state.stage) {
                RelationshipStage.COMMITTED, RelationshipStage.ROMANCE -> RelationshipStage.FAMILIAR
                else -> state.stage
            }
        }

        if (
            event.type == RelationshipEventType.COMMITMENT &&
            state.stage == RelationshipStage.ROMANCE &&
            state.trust >= 65 &&
            state.security >= 55
        ) {
            return RelationshipStage.COMMITTED
        }

        if (
            event.type == RelationshipEventType.RELATIONSHIP_CONFIRMED &&
            state.intimacy >= 50 &&
            state.attraction >= 45 &&
            state.trust >= 40
        ) {
            return RelationshipStage.ROMANCE
        }

        return when (state.stage) {
            RelationshipStage.ACQUAINTANCE -> {
                if (state.intimacy >= 25 && state.trust >= 25) RelationshipStage.FAMILIAR
                else RelationshipStage.ACQUAINTANCE
            }

            RelationshipStage.FAMILIAR -> {
                if (state.intimacy >= 45 && state.attraction >= 40 && state.trust >= 35) {
                    RelationshipStage.AMBIGUOUS
                } else {
                    RelationshipStage.FAMILIAR
                }
            }

            RelationshipStage.AMBIGUOUS -> RelationshipStage.AMBIGUOUS
            RelationshipStage.ROMANCE -> RelationshipStage.ROMANCE
            RelationshipStage.COMMITTED -> RelationshipStage.COMMITTED
        }
    }
}
