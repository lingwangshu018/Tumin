package me.rerere.rikkahub.ui.pet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.repository.CompanionStateRepository
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

/**
 * Read-only Relationship File shared by future pet entry points.
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
    val relationship by relationshipFlow.collectAsState(initial = null)

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("$characterName · Relationship File", style = MaterialTheme.typography.headlineSmall)

            if (relationship == null) {
                Text("正在读取关系记录……", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }

            val snapshot = requireNotNull(relationship)
            Text(snapshot.stage.label, style = MaterialTheme.typography.titleMedium)
            Text(snapshot.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider()
            Text("关系维度", style = MaterialTheme.typography.titleMedium)
            RelationshipMetric("亲密", snapshot.intimacy)
            RelationshipMetric("信任", snapshot.trust)
            RelationshipMetric("吸引", snapshot.attraction)
            RelationshipMetric("安全感", snapshot.security)
            RelationshipMetric("冲突", snapshot.conflict)

            RelationshipSection("里程碑", snapshot.milestones)
            RelationshipSection(
                "最近变化",
                snapshot.recentChanges.map { change ->
                    if (change.effects.isEmpty()) change.summary
                    else "${change.summary} · ${change.effects.joinToString(" / ")}"
                },
            )
            RelationshipSection("未解决的问题", snapshot.unresolvedIssues)
        }
    }
}

@Composable
private fun RelationshipMetric(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${value.coerceIn(0, 100)} / 100")
    }
}

@Composable
private fun RelationshipSection(title: String, items: List<String>) {
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (items.isEmpty()) {
        Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        items.forEach { item -> Text("• $item") }
    }
}
