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
import androidx.compose.material3.Switch
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
import me.rerere.rikkahub.data.model.CompanionState
import me.rerere.rikkahub.data.repository.CompanionStateRepository
import me.rerere.rikkahub.ui.hooks.rememberSharedPreferenceBoolean
import org.koin.compose.koinInject

/**
 * Character State + Relationship preview and control surface for the official Tumin bunny.
 * Companion state remains read-only from this UI.
 */
@Composable
fun PetSurface(
    assistant: Assistant,
    modifier: Modifier = Modifier,
) {
    val repository: CompanionStateRepository = koinInject()
    val companionFlow = remember(assistant.id) { repository.observe(assistant.id) }
    val companion by companionFlow.collectAsState(initial = CompanionState())
    val presentation = remember(companion) { companion.toFusedPetPresentation() }
    var showRelationshipFile by remember { mutableStateOf(false) }
    var petEnabled by rememberSharedPreferenceBoolean("in_app_pet_enabled", false)

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
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OfficialBunnyPet(
                presentation = presentation,
                modifier = Modifier.size(112.dp),
                onClick = { showRelationshipFile = true },
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "兔眠兔 · ${assistant.name.ifBlank { "TA" }}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = presentation.statusLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = presentation.actionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("在 App 内显示桌宠", style = MaterialTheme.typography.titleSmall)
                Text(
                    "开启后会在兔眠页面里陪着你，语音/视频通话时自动隐藏。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = petEnabled,
                onCheckedChange = { petEnabled = it },
            )
        }
        Text(
            text = presentation.interactionHint,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showRelationshipFile) {
        RelationshipFileSheet(
            assistantId = assistant.id,
            characterName = assistant.name.ifBlank { "TA" },
            onDismissRequest = { showRelationshipFile = false },
        )
    }
}
