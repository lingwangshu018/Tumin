package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CharacterState(
    val emotion: String = "平静",
    val location: String = "未记录",
    val activity: String = "正在陪着你",
    val appearance: String = "未记录",
    val condition: String = "状态稳定",
    val innerThought: String = "想知道你现在过得怎么样。",
    val deepReflection: String = "",
    val recentConcern: String = "",
    val unspokenWords: String = "",
    val insecurity: String = "",
    val thoughtsAboutUser: String = "",
    val relationshipExpectation: String = "",
    val recentImpact: String = "",
    val updatedAt: Long = 0L,
)

@Serializable
enum class RelationshipStage(val label: String) {
    ACQUAINTANCE("初识"),
    FAMILIAR("熟悉"),
    AMBIGUOUS("暧昧"),
    ROMANCE("热恋"),
    COMMITTED("稳定关系"),
}

@Serializable
data class RelationshipChange(
    val summary: String,
    val effects: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class RelationshipState(
    val stage: RelationshipStage = RelationshipStage.ACQUAINTANCE,
    val intimacy: Int = 10,
    val trust: Int = 10,
    val attraction: Int = 10,
    val security: Int = 10,
    val conflict: Int = 0,
    val summary: String = "关系才刚刚开始，正在通过共同经历慢慢了解彼此。",
    val milestones: List<String> = emptyList(),
    val recentChanges: List<RelationshipChange> = emptyList(),
    val unresolvedIssues: List<String> = emptyList(),
    val updatedAt: Long = 0L,
)

@Serializable
data class CompanionState(
    val character: CharacterState = CharacterState(),
    val relationship: RelationshipState = RelationshipState(),
)
