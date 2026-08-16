package me.rerere.rikkahub.data.relationship

import me.rerere.rikkahub.data.model.RelationshipEvent
import me.rerere.rikkahub.data.model.RelationshipEventType
import me.rerere.rikkahub.data.model.RelationshipState

internal object RelationshipHistoryManager {
    data class HistoryUpdate(
        val unresolvedIssues: List<String>,
        val resolvedIssues: List<String>,
        val milestones: List<String>,
        val milestoneKeys: List<String>,
        val extraEffects: List<String> = emptyList(),
    )

    fun apply(current: RelationshipState, event: RelationshipEvent): HistoryUpdate {
        var unresolved = current.unresolvedIssues
        var resolved = current.resolvedIssues
        var milestones = current.milestones
        var milestoneKeys = current.milestoneKeys
        val effects = mutableListOf<String>()

        val explicitIssue = event.targetIssue.clean()
        when (event.type) {
            RelationshipEventType.CONFLICT,
            RelationshipEventType.BETRAYAL -> {
                val issue = explicitIssue ?: event.summary.clean()?.takeIf { it.length >= 6 }
                if (issue != null && unresolved.none { sameIssue(it, issue) }) {
                    unresolved = unresolved + issue
                    effects += "opened unresolved issue"
                }
            }

            RelationshipEventType.RECONCILIATION -> {
                val matched = when {
                    explicitIssue != null -> unresolved.firstOrNull { sameIssue(it, explicitIssue) }
                    unresolved.size == 1 -> unresolved.single()
                    else -> null
                }
                if (matched != null) {
                    unresolved = unresolved.filterNot { sameIssue(it, matched) }
                    resolved = resolved + matched
                    effects += "resolved relationship issue"
                }
            }

            // An apology can improve trust/conflict, but does not by itself prove the issue is resolved.
            RelationshipEventType.APOLOGY -> Unit
            else -> Unit
        }

        val automatic = automaticMilestone(event.type)
        val explicitMilestone = event.milestone.clean()
        val milestoneKey = automatic?.first ?: explicitMilestone?.let { "custom:${canonical(it)}" }
        val milestoneText = explicitMilestone ?: automatic?.second
        if (milestoneKey != null && milestoneText != null && milestoneKey !in milestoneKeys) {
            milestoneKeys = milestoneKeys + milestoneKey
            milestones = milestones + milestoneText
            effects += "recorded milestone"
        }

        return HistoryUpdate(
            unresolvedIssues = unresolved.distinctBy(::canonical),
            resolvedIssues = resolved.distinctBy(::canonical),
            milestones = milestones.distinct(),
            milestoneKeys = milestoneKeys.distinct(),
            extraEffects = effects,
        )
    }

    private fun automaticMilestone(type: RelationshipEventType): Pair<String, String>? = when (type) {
        RelationshipEventType.CONFESSION -> "event:confession" to "第一次留下明确的表白记录"
        RelationshipEventType.RELATIONSHIP_CONFIRMED -> "event:relationship_confirmed" to "双方明确确认了恋爱关系"
        RelationshipEventType.COMMITMENT -> "event:commitment" to "双方作出了重要的长期承诺"
        RelationshipEventType.BREAKUP -> "event:breakup" to "关系经历了一次明确的分手"
        RelationshipEventType.BETRAYAL -> "event:betrayal" to "关系经历了一次严重的信任破裂"
        else -> null
    }

    private fun sameIssue(a: String, b: String): Boolean {
        val ca = canonical(a)
        val cb = canonical(b)
        return ca == cb || (ca.length >= 6 && cb.length >= 6 && (ca.contains(cb) || cb.contains(ca)))
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    private fun canonical(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("[\\s，。！？、,.!?;；:：'\"“”‘’（）()【】\\[\\]-]+"), "")
}
