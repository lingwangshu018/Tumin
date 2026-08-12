package me.rerere.rikkahub.ui.pet

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.RelationshipState
import me.rerere.rikkahub.data.repository.CompanionStateRepository
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * App-internal pet host. It lives above the navigation surface, so it can accompany the user
 * across Tumin pages without requesting Android SYSTEM_ALERT_WINDOW permission.
 */
@Composable
fun InAppPetHost(
    assistant: Assistant,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val repository: CompanionStateRepository = koinInject()
    val relationshipFlow = remember(assistant.id) { PetRelationshipSource(repository).observe(assistant.id) }
    val relationship by relationshipFlow.collectAsState(
        initial = RelationshipState().toPetRelationshipSnapshot(),
    )
    val presentation = remember(relationship) { relationship.toPetPresentation() }
    var showRelationshipFile by remember(assistant.id) { mutableStateOf(false) }
    var dragX by remember(assistant.id) { mutableFloatStateOf(0f) }
    var dragY by remember(assistant.id) { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 12.dp, bottom = 96.dp)
                .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
                .size(124.dp)
                .pointerInput(assistant.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                    }
                },
        ) {
            OfficialBunnyPet(
                presentation = presentation,
                modifier = Modifier.fillMaxSize(),
                onClick = { showRelationshipFile = true },
            )
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
