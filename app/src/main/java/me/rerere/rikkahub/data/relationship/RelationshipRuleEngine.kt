package me.rerere.rikkahub.data.relationship

import me.rerere.rikkahub.data.model.RelationshipChange
import me.rerere.rikkahub.data.model.RelationshipEvent
import me.rerere.rikkahub.data.model.RelationshipEventType
import me.rerere.rikkahub.data.model.RelationshipState
import kotlin.math.roundToInt

internal object RelationshipRuleEngine {
    private data class Delta(
        val intimacy: Int = 0,
        val trust: Int = 0,
        val attraction: Int = 0,
        val security: Int = 0,
        val conflict: Int = 0,
    )

    fun apply(current: RelationshipState, event: RelationshipEvent, now: Long = System.currentTimeMillis()): RelationshipState {
        if (!event.meaningful || event.type == RelationshipEventType.ROUTINE) return current.normalized()

        val intensity = RelationshipPolicy.clampIntensity(event.intensity).coerceAtLeast(1)
        val base = baseDelta(event.type)
        val intensityScale = when (intensity) {
            1 -> 0.6
            2 -> 0.8
            3 -> 1.0
            4 -> 1.25
            else -> 1.5
        }
        val repeatScale = RelationshipPolicy.repeatMultiplier(current.recentEventTypes, event.type.name)
        val rapidScale = if (current.lastMeaningfulEventAt > 0L && now - current.lastMeaningfulEventAt < 30_000L) 0.5 else 1.0
        val scale = intensityScale * repeatScale * rapidScale

        fun adjusted(value: Int, currentValue: Int): Int {
            val scaled = (value * scale).roundToInt()
            val growthAdjusted = if (scaled > 0) RelationshipPolicy.scalePositiveDelta(currentValue, scaled) else scaled
            return RelationshipPolicy.capPerEvent(growthAdjusted, intensity)
        }

        val di = adjusted(base.intimacy, current.intimacy)
        val dt = adjusted(base.trust, current.trust)
        val da = adjusted(base.attraction, current.attraction)
        val ds = adjusted(base.security, current.security)
        val dc = RelationshipPolicy.capPerEvent((base.conflict * scale).roundToInt(), intensity)

        val effects = buildList {
            if (di != 0) add("intimacy ${signed(di)}")
            if (dt != 0) add("trust ${signed(dt)}")
            if (da != 0) add("attraction ${signed(da)}")
            if (ds != 0) add("security ${signed(ds)}")
            if (dc != 0) add("conflict ${signed(dc)}")
        }

        val summary = event.summary.trim().ifBlank { defaultSummary(event.type) }
        val issueText = event.targetIssue?.trim().orEmpty()
        val unresolved = when (event.type) {
            RelationshipEventType.CONFLICT,
            RelationshipEventType.BETRAYAL -> if (issueText.isNotBlank()) current.unresolvedIssues + issueText else current.unresolvedIssues

            RelationshipEventType.APOLOGY,
            RelationshipEventType.RECONCILIATION -> if (issueText.isNotBlank()) {
                current.unresolvedIssues.filterNot { it.equals(issueText, ignoreCase = true) }
            } else if (event.type == RelationshipEventType.RECONCILIATION) {
                current.unresolvedIssues.dropLast(1)
            } else {
                current.unresolvedIssues
            }

            else -> current.unresolvedIssues
        }

        val milestone = event.milestone?.trim().orEmpty()
        val milestones = if (milestone.isNotBlank()) current.milestones + milestone else current.milestones
        val changed = current.copy(
            intimacy = current.intimacy + di,
            trust = current.trust + dt,
            attraction = current.attraction + da,
            security = current.security + ds,
            conflict = current.conflict + dc,
            summary = summary,
            milestones = milestones,
            recentChanges = current.recentChanges + RelationshipChange(summary = summary, effects = effects, createdAt = now),
            unresolvedIssues = unresolved,
            meaningfulInteractionCount = current.meaningfulInteractionCount + 1,
            lastMeaningfulEventAt = now,
            recentEventTypes = (current.recentEventTypes + event.type.name).takeLast(12),
            updatedAt = now,
        ).normalized()

        return changed.copy(stage = RelationshipStageResolver.resolve(changed, event)).normalized()
    }

    private fun baseDelta(type: RelationshipEventType): Delta = when (type) {
        RelationshipEventType.ROUTINE -> Delta()
        RelationshipEventType.AFFECTION -> Delta(intimacy = 2, attraction = 2, security = 1)
        RelationshipEventType.EMOTIONAL_SUPPORT -> Delta(intimacy = 2, trust = 3, security = 3, conflict = -1)
        RelationshipEventType.SELF_DISCLOSURE -> Delta(intimacy = 3, trust = 4, attraction = 1, security = 1)
        RelationshipEventType.TRUST_BUILDING -> Delta(intimacy = 1, trust = 4, security = 2)
        RelationshipEventType.FLIRTING -> Delta(intimacy = 2, attraction = 4)
        RelationshipEventType.JEALOUSY -> Delta(intimacy = 1, security = -2, conflict = 2)
        RelationshipEventType.GIFT -> Delta(intimacy = 2, attraction = 1, security = 1)
        RelationshipEventType.DATE -> Delta(intimacy = 3, trust = 2, attraction = 3, security = 2)
        RelationshipEventType.CONFLICT -> Delta(intimacy = -2, trust = -3, attraction = -1, security = -4, conflict = 6)
        RelationshipEventType.APOLOGY -> Delta(intimacy = 1, trust = 3, security = 2, conflict = -4)
        RelationshipEventType.RECONCILIATION -> Delta(intimacy = 2, trust = 2, attraction = 1, security = 3, conflict = -6)
        RelationshipEventType.CONFESSION -> Delta(intimacy = 4, trust = 2, attraction = 5, security = 1)
        RelationshipEventType.RELATIONSHIP_CONFIRMED -> Delta(intimacy = 5, trust = 4, attraction = 4, security = 4, conflict = -2)
        RelationshipEventType.COMMITMENT -> Delta(intimacy = 4, trust = 5, attraction = 2, security = 5, conflict = -2)
        RelationshipEventType.BETRAYAL -> Delta(intimacy = -7, trust = -10, attraction = -5, security = -10, conflict = 10)
        RelationshipEventType.BREAKUP -> Delta(intimacy = -8, trust = -6, attraction = -6, security = -9, conflict = 8)
        RelationshipEventType.SEPARATION -> Delta(intimacy = -4, trust = -2, attraction = -2, security = -5, conflict = 3)
        RelationshipEventType.MILESTONE -> Delta(intimacy = 3, trust = 2, attraction = 1, security = 2)
        RelationshipEventType.BOND_GROWTH -> Delta(intimacy = 2, trust = 1, security = 2)
    }

    private fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()

    private fun defaultSummary(type: RelationshipEventType): String = when (type) {
        RelationshipEventType.ROUTINE -> "普通互动，没有形成新的关系事件。"
        else -> "发生了 ${type.name.lowercase()} 关系事件。"
    }
}
