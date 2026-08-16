package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class RelationshipEventType {
    ROUTINE,
    AFFECTION,
    EMOTIONAL_SUPPORT,
    SELF_DISCLOSURE,
    TRUST_BUILDING,
    FLIRTING,
    JEALOUSY,
    GIFT,
    DATE,
    CONFLICT,
    APOLOGY,
    RECONCILIATION,
    CONFESSION,
    RELATIONSHIP_CONFIRMED,
    COMMITMENT,
    BETRAYAL,
    BREAKUP,
    SEPARATION,
    MILESTONE,
    BOND_GROWTH,
}

@Serializable
data class RelationshipEvent(
    val meaningful: Boolean = false,
    val type: RelationshipEventType = RelationshipEventType.ROUTINE,
    val intensity: Int = 0,
    val summary: String = "",
    val milestone: String? = null,
    val targetIssue: String? = null,
)
