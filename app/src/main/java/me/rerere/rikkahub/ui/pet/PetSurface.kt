package me.rerere.rikkahub.ui.pet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.RelationshipState
import me.rerere.rikkahub.data.repository.CompanionStateRepository
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import org.koin.compose.koinInject

/**
 * First in-app pet surface backed by the selected assistant's read-only Relationship snapshot.
 *
 * Tapping the surface opens Relationship File. The surface does not receive any mutation API.
 */
@Composable
fun PetSurface(
    assistant: Assistant,
    modifier: Modifier = Modifier,
) {
    val repository: CompanionStateRepository = koinInject()
    val relationshipFlow = remember(assistant.id) { PetRelationshipSource(repository).observe(assistant.id) }
    val relationship by relationshipFlow.collectAsState(
        initial = RelationshipState().toPetRelationshipSnapshot(),
    )
    val presentation = remember(relationship) { relationship.toPetPresentation() }
    var showRelationshipFile by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showRelationshipFile = true },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UIAvatar(
                name = assistant.name.ifBlank { "TA" },
                value = assistant.avatar,
                modifier = Modifier.size(76.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = assistant.name.ifBlank { "TA" },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = presentation.statusLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = presentation.actionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = presentation.interactionHint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showRelationshipFile) {
        RelationshipFileSheet(
            assistantId = assistant.id,
            characterName = assistant.name.ifBlank { "TA" },
            onDismissRequest = { showRelationshipFile = false },
        )
    }
}
