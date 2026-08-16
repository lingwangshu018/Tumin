package me.rerere.rikkahub.data.relationship

import me.rerere.rikkahub.data.model.RelationshipEvent
import me.rerere.rikkahub.data.model.RelationshipEventType
import me.rerere.rikkahub.data.model.RelationshipState

/**
 * Tracks ordinary interaction locally so long-term bonds can grow without another model call.
 * One completed user/assistant exchange counts as one interaction.
 */
internal object RelationshipActivityTracker {
    private const val INTERACTIONS_PER_BOND_GROWTH = 20
    private const val ACTIVE_DAYS_PER_BOND_GROWTH = 3

    data class Result(
        val state: RelationshipState,
        val bondGrowthEvent: RelationshipEvent? = null,
    )

    fun record(
        current: RelationshipState,
        epochDay: Long,
    ): Result {
        val isNewDay = epochDay != current.lastInteractionEpochDay
        val isNextDay = current.lastInteractionEpochDay >= 0L && epochDay == current.lastInteractionEpochDay + 1L

        val tracked = current.copy(
            totalInteractionCount = current.totalInteractionCount + 1,
            activeDayCount = current.activeDayCount + if (isNewDay) 1 else 0,
            consecutiveActiveDays = when {
                !isNewDay -> current.consecutiveActiveDays
                current.lastInteractionEpochDay < 0L -> 1
                isNextDay -> current.consecutiveActiveDays + 1
                else -> 1
            },
            lastInteractionEpochDay = epochDay,
        ).normalized()

        val interactionGrowthDue =
            tracked.totalInteractionCount - tracked.lastBondGrowthInteractionCount >= INTERACTIONS_PER_BOND_GROWTH
        val activeDayGrowthDue =
            tracked.activeDayCount - tracked.lastBondGrowthActiveDayCount >= ACTIVE_DAYS_PER_BOND_GROWTH

        if (!interactionGrowthDue && !activeDayGrowthDue) return Result(tracked)

        val reason = when {
            interactionGrowthDue && activeDayGrowthDue -> "持续的日常交流和多日陪伴让关系自然更稳。"
            activeDayGrowthDue -> "连续多日保持联系，让彼此的陪伴感更稳定。"
            else -> "一段时间内持续而稳定的交流，让关系自然更熟悉。"
        }
        val intensity = if (activeDayGrowthDue && tracked.consecutiveActiveDays >= 3) 2 else 1
        val checkpointed = tracked.copy(
            lastBondGrowthInteractionCount = tracked.totalInteractionCount,
            lastBondGrowthActiveDayCount = tracked.activeDayCount,
        )
        return Result(
            state = checkpointed,
            bondGrowthEvent = RelationshipEvent(
                meaningful = true,
                type = RelationshipEventType.BOND_GROWTH,
                intensity = intensity,
                summary = reason,
            ),
        )
    }
}
