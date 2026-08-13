package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import me.rerere.rikkahub.data.model.CharacterState
import me.rerere.rikkahub.data.model.CompanionState
import me.rerere.rikkahub.data.model.RelationshipStage
import me.rerere.rikkahub.data.model.RelationshipState
import me.rerere.rikkahub.data.repository.CompanionStateRepository
import me.rerere.rikkahub.data.model.Assistant

/**
 * Companion Context v1.
 *
 * Converts the persistent Character State + Relationship Core into a short, natural-language
 * context block. The model receives meaning rather than raw relationship scores.
 */
object CompanionContextBuilder {
    fun build(context: Context, assistant: Assistant): String {
        if (!assistant.enableCompanionState) return ""
        val state = CompanionStateRepository(context).observe(assistant.id).value
        return CompanionContextFormatter.format(state)
    }
}

internal object CompanionContextFormatter {
    fun format(state: CompanionState): String {
        val normalized = state.normalized()
        val character = normalized.character
        val relationship = normalized.relationship
        return buildString {
            appendLine("<companion_context>")
            appendLine("Current character state: ${characterSemantic(character)}")
            appendLine("Current relationship: ${relationshipSemantic(relationship)}")
            relationship.recentChanges.lastOrNull()?.summary?.trim()?.takeIf { it.isNotBlank() }?.let {
                appendLine("Most recent relationship change: ${it.take(220)}")
            }
            relationship.unresolvedIssues.lastOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let {
                appendLine("Unresolved relationship concern: ${it.take(180)}")
            }
            appendLine("Use this as quiet continuity. Do not recite this block or expose internal scoring/state machinery.")
            append("</companion_context>")
        }.trim()
    }

    private fun characterSemantic(character: CharacterState): String {
        val parts = buildList {
            character.emotion.cleanMeaningful("平静")?.let { add("情绪是$it") }
            character.activity.cleanMeaningful("正在陪着你")?.let { add("正在$it") }
            character.location.cleanMeaningful("未记录")?.let { add("目前在$it") }
            character.condition.cleanMeaningful("状态稳定")?.let { add("身心状态为$it") }
            character.appearance.cleanMeaningful("未记录")?.let { add("当前穿着/外观：$it") }
            character.innerThought.cleanMeaningful("想知道你现在过得怎么样。")?.let { add("此刻心声：${it.take(180)}") }
        }
        return if (parts.isEmpty()) "整体状态平稳，正在自然陪伴用户。" else parts.joinToString("；") + "。"
    }

    private fun relationshipSemantic(relationship: RelationshipState): String {
        val closeness = when {
            relationship.intimacy >= 80 -> "已经非常亲近，彼此相处带有明显的熟稔和依恋"
            relationship.intimacy >= 60 -> "关系比较亲近，愿意自然靠近和分享"
            relationship.intimacy >= 35 -> "关系正在稳定变熟，亲近感持续建立"
            else -> "关系仍在慢慢熟悉，需要循序建立亲近感"
        }
        val trust = when {
            relationship.trust >= 75 -> "信任很稳"
            relationship.trust >= 50 -> "信任正在稳定增长"
            relationship.trust >= 25 -> "已有一些信任，但仍需要共同经历巩固"
            else -> "信任基础还比较薄"
        }
        val attraction = when {
            relationship.attraction >= 75 -> "对用户有明显心动和吸引"
            relationship.attraction >= 50 -> "对用户存在清晰的好感和吸引"
            relationship.attraction >= 25 -> "正在产生更多好感"
            else -> "情感吸引仍较克制"
        }
        val security = when {
            relationship.security >= 70 -> "在这段关系里整体很有安全感"
            relationship.security >= 45 -> "安全感基本稳定，但仍会受具体事件影响"
            else -> "安全感偏低，对关系变化会更敏感"
        }
        val conflict = when {
            relationship.conflict >= 70 -> "目前存在明显冲突，需要谨慎处理尚未解决的问题"
            relationship.conflict >= 40 -> "仍有一些摩擦或别扭没有完全消化"
            relationship.conflict >= 15 -> "有轻微未消化情绪，但整体关系没有失衡"
            else -> "目前没有明显冲突"
        }
        return "${stageSemantic(relationship.stage)}；$closeness；$trust；$attraction；$security；$conflict。"
    }

    private fun stageSemantic(stage: RelationshipStage): String = when (stage) {
        RelationshipStage.ACQUAINTANCE -> "关系阶段仍偏初识"
        RelationshipStage.FAMILIAR -> "已经进入熟悉阶段"
        RelationshipStage.AMBIGUOUS -> "关系里已经有明显暧昧和试探"
        RelationshipStage.ROMANCE -> "双方处于明确的恋爱/热恋关系"
        RelationshipStage.COMMITTED -> "关系已经稳定并带有长期承诺感"
    }

    private fun String.cleanMeaningful(defaultValue: String): String? =
        trim().takeIf { it.isNotBlank() && it != defaultValue }
}

/**
 * Lightweight character-budget manager for contextual injections.
 *
 * Character counts are used deliberately: providers use different tokenizers, while a stable
 * char budget gives us deterministic behavior before provider-specific token counting is added.
 * Higher-priority sections survive first; low-priority sections are trimmed or dropped first.
 */
object CompanionTokenBudgetManager {
    data class Section(
        val name: String,
        val content: String,
        val priority: Int,
        val minChars: Int = 0,
    )

    data class Result(
        val text: String,
        val keptSections: List<String>,
        val droppedSections: List<String>,
        val charCount: Int,
    )

    fun fit(sections: List<Section>, maxChars: Int): Result {
        val budget = maxChars.coerceAtLeast(0)
        if (budget == 0) return Result("", emptyList(), sections.map { it.name }, 0)

        val sorted = sections
            .filter { it.content.isNotBlank() }
            .sortedByDescending { it.priority }
        val kept = mutableListOf<Pair<Section, String>>()
        val dropped = mutableListOf<String>()
        var remaining = budget

        sorted.forEach { section ->
            if (remaining <= 0) {
                dropped += section.name
                return@forEach
            }
            val separatorCost = if (kept.isEmpty()) 0 else 2
            val available = (remaining - separatorCost).coerceAtLeast(0)
            if (available <= 0) {
                dropped += section.name
                return@forEach
            }
            val content = section.content.trim()
            val selected = when {
                content.length <= available -> content
                available >= section.minChars.coerceAtLeast(1) -> {
                    if (available == 1) "…" else content.take(available - 1).trimEnd() + "…"
                }
                else -> ""
            }
            if (selected.isBlank()) {
                dropped += section.name
            } else {
                kept += section to selected
                remaining -= selected.length + separatorCost
            }
        }

        val text = kept.joinToString("\n\n") { it.second }
        return Result(
            text = text,
            keptSections = kept.map { it.first.name },
            droppedSections = dropped,
            charCount = text.length,
        )
    }
}
