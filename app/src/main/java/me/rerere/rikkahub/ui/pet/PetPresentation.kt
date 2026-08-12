package me.rerere.rikkahub.ui.pet

/**
 * Pure presentation mapping for the pet surface.
 * Relationship values remain read-only; this model only decides how the pet presents itself.
 */
data class PetPresentation(
    val statusLabel: String,
    val actionText: String,
    val interactionHint: String,
    val motion: PetMotion,
)

enum class PetMotion {
    IDLE,
    GENTLE,
    APPROACH,
    AFFECTIONATE,
    STAY_CLOSE,
    CAUTIOUS,
}

fun PetRelationshipSnapshot.toPetPresentation(): PetPresentation = when (behavior) {
    PetRelationshipBehavior.RESERVED -> PetPresentation(
        statusLabel = "还在慢慢熟悉你",
        actionText = "安静地待在不远处，偶尔抬头看看你。",
        interactionHint = "点一下看看 TA 的 Relationship File",
        motion = PetMotion.IDLE,
    )

    PetRelationshipBehavior.WARM -> PetPresentation(
        statusLabel = "愿意靠近一点",
        actionText = "看见你时会主动靠近，陪在旁边。",
        interactionHint = "点一下，看看最近关系有什么变化",
        motion = PetMotion.GENTLE,
    )

    PetRelationshipBehavior.CLOSE -> PetPresentation(
        statusLabel = "正在明显偏向你",
        actionText = "会更频繁地凑过来，像是总想和你待在一起。",
        interactionHint = "点一下，看看 TA 最近在意什么",
        motion = PetMotion.APPROACH,
    )

    PetRelationshipBehavior.AFFECTIONATE -> PetPresentation(
        statusLabel = "很喜欢和你贴近",
        actionText = "见到你会自然地靠过来，动作里带着明显的亲昵。",
        interactionHint = "点一下打开 Relationship File",
        motion = PetMotion.AFFECTIONATE,
    )

    PetRelationshipBehavior.DEVOTED -> PetPresentation(
        statusLabel = "已经把陪着你当成习惯",
        actionText = "会安稳地守在你身边，不太愿意离开太久。",
        interactionHint = "点一下看看你们共同积累的关系记录",
        motion = PetMotion.STAY_CLOSE,
    )

    PetRelationshipBehavior.GUARDED -> PetPresentation(
        statusLabel = "现在有一点戒备",
        actionText = "仍然留在附近，但动作明显更克制，也会保持一点距离。",
        interactionHint = "点一下看看最近的冲突和未解决问题",
        motion = PetMotion.CAUTIOUS,
    )
}
