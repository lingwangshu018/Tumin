package me.rerere.rikkahub.ui.pet

import me.rerere.rikkahub.data.model.CompanionState

private val reconcileWords = listOf("和好", "和解", "道歉", "原谅", "reconcile", "made up", "apolog")
private val sleepWords = listOf("睡", "困", "疲惫", "疲倦", "累", "休息", "sleep", "tired", "exhausted", "resting")
private val angryWords = listOf("生气", "愤怒", "恼火", "恼怒", "不爽", "气恼", "angry", "mad", "furious", "annoyed")
private val shyWords = listOf("害羞", "羞涩", "脸红", "不好意思", "shy", "embarrassed", "bashful")
private val lookWords = listOf("好奇", "期待", "专注", "认真", "留意", "curious", "interested", "attentive", "focused")

private fun String.containsAny(words: List<String>): Boolean {
    val normalized = lowercase()
    return words.any { normalized.contains(it.lowercase()) }
}

/**
 * Fuses Character State with the existing read-only Relationship presentation.
 * No relationship values are mutated here. Relationship remains the stable baseline;
 * strong, current Character State may temporarily override only the bunny's presentation.
 */
fun CompanionState.toFusedPetPresentation(): PetPresentation {
    val relationshipSnapshot = relationship.toPetRelationshipSnapshot()
    val base = relationshipSnapshot.toPetPresentation()
    val latestChange = relationship.recentChanges.lastOrNull()
    val characterText = listOf(character.emotion, character.activity, character.condition).joinToString(" ")

    val reconciliation = latestChange?.let { change ->
        (change.summary + " " + change.effects.joinToString(" ")).containsAny(reconcileWords)
    } == true && relationship.conflict < 60

    if (reconciliation) {
        return base.copy(
            statusLabel = "正在和你重新靠近",
            actionText = "刚刚的不愉快正在慢慢放下，愿意重新朝你靠过来。",
            interactionHint = "点一下看看你们最近的关系变化",
            motion = PetMotion.RECONCILE,
        )
    }

    if (characterText.containsAny(sleepWords)) {
        return base.copy(
            statusLabel = "现在有点困了",
            actionText = "困意压下来，安静地缩成一团休息一会儿。",
            motion = PetMotion.SLEEP,
        )
    }

    if (character.emotion.containsAny(angryWords)) {
        return base.copy(
            statusLabel = "现在有点生气",
            actionText = "情绪还没完全散掉，动作里明显带着一点气鼓鼓。",
            motion = PetMotion.ANGRY,
        )
    }

    // Guarded relationship is a stronger safety signal than mild shy/curious emotions.
    if (relationshipSnapshot.behavior == PetRelationshipBehavior.GUARDED) {
        return base
    }

    if (character.emotion.containsAny(shyWords)) {
        return base.copy(
            statusLabel = "有点不好意思",
            actionText = "被你看得有点害羞，想靠近又忍不住躲一下视线。",
            motion = PetMotion.SHY,
        )
    }

    if (character.emotion.containsAny(lookWords) || character.activity.containsAny(lookWords)) {
        return base.copy(
            statusLabel = "正在认真看着你",
            actionText = "注意力落在你身上，歪着脑袋留意你的一举一动。",
            motion = PetMotion.LOOK,
        )
    }

    return base
}
