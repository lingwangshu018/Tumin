package me.rerere.rikkahub.ui.pet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.RelationshipStage
import me.rerere.rikkahub.data.model.RelationshipState
import me.rerere.rikkahub.data.repository.CompanionStateRepository
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

/**
 * Read-only Relationship File shared by pet/companion entry points.
 * It observes the selected assistant/persona and never exposes relationship mutation actions.
 */
@Composable
fun RelationshipFileSheet(
    assistantId: Uuid,
    characterName: String,
    onDismissRequest: () -> Unit,
) {
    val repository: CompanionStateRepository = koinInject()
    val relationshipFlow = remember(assistantId) { PetRelationshipSource(repository).observe(assistantId) }
    val relationship by relationshipFlow.collectAsState(initial = RelationshipState().toPetRelationshipSnapshot())
    val scrollState = rememberScrollState()

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RelationshipHero(
                characterName = characterName,
                relationship = relationship,
            )

            SectionHeading("♡ 关系温度", "一点点积攒起来的靠近")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(17.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    RelationshipMetric("亲密", relationship.intimacy, "♡")
                    RelationshipMetric("信任", relationship.trust, "✦")
                    RelationshipMetric("吸引", relationship.attraction, "✿")
                    RelationshipMetric("安全感", relationship.security, "⌂")
                    RelationshipMetric("冲突", relationship.conflict, "☁", inverse = true)
                }
            }

            SectionHeading("🐇 一起走过", "兔兔悄悄记下了你们的陪伴")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ContinuityCard(
                    modifier = Modifier.weight(1f),
                    icon = "💬",
                    value = relationship.totalInteractionCount.toString(),
                    label = "次对话",
                )
                ContinuityCard(
                    modifier = Modifier.weight(1f),
                    icon = "☀",
                    value = relationship.activeDayCount.toString(),
                    label = "个相伴日",
                )
                ContinuityCard(
                    modifier = Modifier.weight(1f),
                    icon = "♬",
                    value = relationship.consecutiveActiveDays.toString(),
                    label = "天连续陪伴",
                )
            }

            relationship.recentChanges.lastOrNull()?.let { change ->
                HighlightCard(
                    icon = "✦",
                    eyebrow = "最近悄悄发生的变化",
                    title = change.summary,
                    supporting = change.effects
                        .mapNotNull(::localizeEffect)
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(" · "),
                )
            }

            relationship.milestones.lastOrNull()?.let { milestone ->
                HighlightCard(
                    icon = "🎀",
                    eyebrow = "被兔兔收进纪念盒的时刻",
                    title = milestone,
                )
            }

            if (relationship.unresolvedIssues.isNotEmpty()) {
                RelationshipSection(
                    icon = "☁",
                    title = "还挂在心上的事",
                    subtitle = "先放在这里，等你们慢慢把它说开。",
                    items = relationship.unresolvedIssues.takeLast(3).reversed(),
                )
            }

            if (relationship.resolvedIssues.isNotEmpty()) {
                RelationshipSection(
                    icon = "🌷",
                    title = "我们一起跨过去啦",
                    subtitle = "不是忘掉，而是真的一起把它好好走过了。",
                    items = relationship.resolvedIssues.takeLast(3).reversed(),
                )
            }

            if (relationship.milestones.size > 1 || relationship.recentChanges.size > 1) {
                SectionHeading("✧ 关系小足迹", "最近几步，也都算数")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        relationship.recentChanges.takeLast(3).reversed().forEachIndexed { index, change ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Surface(
                                    modifier = Modifier.size(26.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(if (index == 0) "♡" else "·", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Text(
                                    text = change.summary,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelationshipHero(
    characterName: String,
    relationship: PetRelationshipSnapshot,
) {
    val stageDecoration = stageDecoration(relationship.stage)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "$characterName 的关系小档案",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "兔兔替你们好好收着 ♡",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                ) {
                    Text(
                        text = stageDecoration.first,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stageDecoration.second,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = relationship.stage.label,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stageWhisper(relationship.stage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
            ) {
                Text(
                    text = relationship.summary,
                    modifier = Modifier.padding(15.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RelationshipMetric(
    label: String,
    value: Int,
    icon: String,
    inverse: Boolean = false,
) {
    val normalized = value.coerceIn(0, 100)
    val progress = if (inverse) (100 - normalized) / 100f else normalized / 100f
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$icon  $label", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = metricDescription(label, normalized),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp)),
        )
    }
}

@Composable
private fun ContinuityCard(
    modifier: Modifier = Modifier,
    icon: String,
    value: String,
    label: String,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HighlightCard(
    icon: String,
    eyebrow: String,
    title: String,
    supporting: String? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(icon, style = MaterialTheme.typography.titleMedium)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                supporting?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RelationshipSection(
    icon: String,
    title: String,
    subtitle: String,
    items: List<String>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "$icon  $title",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            items.forEach { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
                ) {
                    Text(
                        text = item,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun stageDecoration(stage: RelationshipStage): Pair<String, String> = when (stage) {
    RelationshipStage.ACQUAINTANCE -> "刚刚认识" to "🐇"
    RelationshipStage.FAMILIAR -> "开始熟悉" to "🌱"
    RelationshipStage.AMBIGUOUS -> "心动冒泡" to "🫧"
    RelationshipStage.ROMANCE -> "甜甜恋爱" to "💗"
    RelationshipStage.COMMITTED -> "稳稳相伴" to "🎀"
}

private fun stageWhisper(stage: RelationshipStage): String = when (stage) {
    RelationshipStage.ACQUAINTANCE -> "故事才翻开第一页。"
    RelationshipStage.FAMILIAR -> "已经会记得彼此的小习惯啦。"
    RelationshipStage.AMBIGUOUS -> "有些话，好像开始藏不住了。"
    RelationshipStage.ROMANCE -> "喜欢已经变成了每天都能感觉到的事。"
    RelationshipStage.COMMITTED -> "不是一时心动，是认真地想一直走下去。"
}

private fun metricDescription(label: String, value: Int): String {
    if (label == "冲突") {
        return when {
            value <= 10 -> "软乎乎的"
            value <= 30 -> "偶尔拌嘴"
            value <= 55 -> "需要哄哄"
            else -> "有点皱巴巴"
        }
    }
    return when {
        value < 25 -> "小小芽芽"
        value < 45 -> "慢慢靠近"
        value < 65 -> "越来越熟"
        value < 85 -> "很重要啦"
        else -> "稳稳抱住"
    }
}

private fun localizeEffect(effect: String): String? {
    val trimmed = effect.trim()
    val replacements = listOf(
        "intimacy" to "亲密",
        "trust" to "信任",
        "attraction" to "吸引",
        "security" to "安全感",
        "conflict" to "冲突",
    )
    replacements.firstOrNull { (key, _) -> trimmed.startsWith("$key ") }?.let { (key, label) ->
        return "$label ${trimmed.removePrefix(key).trim()}"
    }
    return when (trimmed) {
        "opened unresolved issue" -> "留下了一件还需要好好说开的事"
        "resolved relationship issue" -> "一起把一件心事好好修好了"
        "recorded milestone" -> "又有一个值得收进纪念盒的时刻"
        else -> null
    }
}
