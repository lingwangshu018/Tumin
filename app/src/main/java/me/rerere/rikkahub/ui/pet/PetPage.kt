package me.rerere.rikkahub.ui.pet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.ui.context.LocalSettings
import kotlin.uuid.Uuid

/**
 * First dedicated in-app pet page.
 *
 * It resolves the selected assistant from Settings, then delegates all Relationship observation to [PetSurface].
 * The page remains read-only: it never receives CompanionStateRepository or any mutation callback.
 */
@Composable
fun PetPage(
    assistantId: Uuid,
    onBack: () -> Unit,
) {
    val settings = LocalSettings.current
    val assistant = remember(settings.assistants, assistantId) {
        settings.getAssistantById(assistantId)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TextButton(onClick = onBack) {
                Text("← 返回")
            }

            Text(
                text = "桌宠",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "TA 会根据你们现在的关系表现出不同状态。点击桌宠可以查看 Relationship File。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (assistant == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("没有找到这个 Character")
                    Text(
                        "可能已经被删除或切换。返回后重新选择即可。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                PetSurface(assistant = assistant)
            }
        }
    }
}
