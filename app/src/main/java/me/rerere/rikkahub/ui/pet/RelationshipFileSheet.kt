package me.rerere.rikkahub.ui.pet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

            Text(
                text = "关系温度",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    RelationshipMetric("亲密", relationship.intimacy)
                    RelationshipMetric("信任", relationship.trust)
                    RelationshipMetric("吸引", relationship.attraction)
                    RelationshipMetric("安全感", relationship.security)
                    RelationshipMetric("冲突", relationship.conflict, inverse = true)
                }
            }

            Text(
                text = "一起走过",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ContinuityCard(
                    modifier = Modifier.weight(1f),
                    value = relationship.totalInteractionCount.toString(),
                    label = "次对话",
                )
                ContinuityCard(
                    modifier = Modifier.weight(1f),
                    value = relationship.activeDayCount.toString(),
                    label = "个相伴日",
                )
                ContinuityCard(
                    modifier = Modifier.weight(1f),
                    value = relationship.consecutiveActiveDays.toString(),
                    label = "天连续陪伴",
                )
            }

            relationship.recentChanges.lastOrNull()?.let { change ->
                HighlightCard(
                    eyebrow = "最近的变化",
                    title = change.summary,
                    supporting = change.effects
                        .mapNotNull(::localizeEffect)
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(" · "),
                )
            }

            relationship.milestones.lastOrNull()?.let { milestone ->
                HighlightCard(
                    eyebrow = "重要时刻",
                    title = milestone,
                )
            }

            if (relationship.unresolvedIssues.isNotEmpty()) {
                RelationshipSection(
                    title = "还挂在心上的事",
                    subtitle = "这些事情还没有真正翻篇。",
                    items = relationship.unresolvedIssues.takeLast(3).reversed(),
                )
            }

            if (relationship.resolvedIssues.isNotEmpty()) {
                RelationshipSection(
                    title = "已经一起修好的事",
                    subtitle = "不是忘掉，而是已经认真走过。",
                    items = relationship.resolvedIssues.takeLast(3).reversed(),
                )
            }

            if (relationship.milestones.size > 1 || relationship.recentChanges.size > 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                Text(
                    text = "关系足迹",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                relationship.recentChanges.takeLast(3).reversed().forEach { change ->
                    Text(
                        text = "• ${change.summary}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "$characterName · 关系档案",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = relationship.stage.label,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = relationship.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RelationshipMetric(
    label: String,
    value: Int,
    inverse: Boolean = false,
) {
    val normalized = value.coerceIn(0, 100)
    val progress = if (inverse) (100 - normalized) / 100f else normalized / 100f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = metricDescription(label, normalized),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ContinuityCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
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
    eyebrow: String,
    title: String,
    supporting: String? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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

@Composable
private fun RelationshipSection(
    title: String,
    subtitle: String,
    items: List<String>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
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
            items.forEach { item ->
                Text(
                    text = "• $item",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun metricDescription(label: String, value: Int): String {
    if (label == "冲突") {
        return when {
            value <= 10 -> "很平稳"
            value <= 30 -> "有小摩擦"
            value <= 55 -> "需要留意"
            else -> "关系紧绷"
        }
    }
    return when {
        value < 25 -> "刚刚开始"
        value < 45 -> "慢慢靠近"
        value < 65 -> "已经很熟悉"
        value < 85 -> "关系很深"
        else -> "非常牢固"
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
        "opened unresolved issue" -> "留下了需要解决的问题"
        "resolved relationship issue" -> "修复了一件关系问题"
        "recorded milestone" -> "留下了新的重要时刻"
        else -> null
    }
}
