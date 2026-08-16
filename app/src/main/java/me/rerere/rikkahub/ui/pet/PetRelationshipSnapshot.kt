package me.rerere.rikkahub.ui.pet

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.model.RelationshipState
import me.rerere.rikkahub.data.model.RelationshipStage
import me.rerere.rikkahub.data.repository.CompanionStateRepository
import kotlin.uuid.Uuid

/**
 * Read-only relationship projection for pet surfaces.
 *
 * The pet layer deliberately receives a snapshot instead of CompanionStateRepository itself so it cannot mutate
 * relationship values. A new assistant id creates a new observation pipeline, which keeps personas isolated.
 */
data class PetRelationshipSnapshot(
    val stage: RelationshipStage,
    val intimacy: Int,
    val trust: Int,
    val attraction: Int,
    val security: Int,
    val conflict: Int,
    val summary: String,
    val milestones: List<String>,
    val recentChanges: List<PetRelationshipChange>,
    val unresolvedIssues: List<String>,
    val resolvedIssues: List<String>,
    val totalInteractionCount: Int,
    val meaningfulInteractionCount: Int,
    val activeDayCount: Int,
    val consecutiveActiveDays: Int,
    val updatedAt: Long,
    val behavior: PetRelationshipBehavior,
)

data class PetRelationshipChange(
    val summary: String,
    val effects: List<String>,
    val createdAt: Long,
)

enum class PetRelationshipBehavior {
    RESERVED,
    WARM,
    CLOSE,
    AFFECTIONATE,
    DEVOTED,
    GUARDED,
}

class PetRelationshipSource(
    private val companionStateRepository: CompanionStateRepository,
) {
    fun observe(assistantId: Uuid): Flow<PetRelationshipSnapshot> = companionStateRepository.observe(assistantId)
        .map { state -> state.relationship.toPetRelationshipSnapshot() }
        .distinctUntilChanged()
}

fun RelationshipState.toPetRelationshipSnapshot(): PetRelationshipSnapshot {
    val normalized = normalized()
    return PetRelationshipSnapshot(
        stage = normalized.stage,
        intimacy = normalized.intimacy,
        trust = normalized.trust,
        attraction = normalized.attraction,
        security = normalized.security,
        conflict = normalized.conflict,
        summary = normalized.summary,
        milestones = normalized.milestones,
        recentChanges = normalized.recentChanges.map { change ->
            PetRelationshipChange(
                summary = change.summary,
                effects = change.effects,
                createdAt = change.createdAt,
            )
        },
        unresolvedIssues = normalized.unresolvedIssues,
        resolvedIssues = normalized.resolvedIssues,
        totalInteractionCount = normalized.totalInteractionCount,
        meaningfulInteractionCount = normalized.meaningfulInteractionCount,
        activeDayCount = normalized.activeDayCount,
        consecutiveActiveDays = normalized.consecutiveActiveDays,
        updatedAt = normalized.updatedAt,
        behavior = normalized.toPetRelationshipBehavior(),
    )
}

private fun RelationshipState.toPetRelationshipBehavior(): PetRelationshipBehavior {
    if (conflict >= 65 && security < 45) return PetRelationshipBehavior.GUARDED
    return when (stage) {
        RelationshipStage.ACQUAINTANCE -> PetRelationshipBehavior.RESERVED
        RelationshipStage.FAMILIAR -> PetRelationshipBehavior.WARM
        RelationshipStage.AMBIGUOUS -> PetRelationshipBehavior.CLOSE
        RelationshipStage.ROMANCE -> PetRelationshipBehavior.AFFECTIONATE
        RelationshipStage.COMMITTED -> PetRelationshipBehavior.DEVOTED
    }
}
