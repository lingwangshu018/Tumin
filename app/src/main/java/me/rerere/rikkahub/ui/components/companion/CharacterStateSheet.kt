package me.rerere.rikkahub.ui.components.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.CharacterState

@Composable
fun CharacterStateSheet(
    characterName: String,
    state: CharacterState,
    onDismissRequest: () -> Unit,
    onOpenPet: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("$characterName · 现在", style = MaterialTheme.typography.headlineSmall)
            StateRow("心情", state.emotion)
            StateRow("地点", state.location)
            StateRow("正在做", state.activity)
            StateRow("穿着", state.appearance)
            StateRow("身心状态", state.condition)
            HorizontalDivider()
            Text("此刻心声", style = MaterialTheme.typography.titleMedium)
            Text(state.innerThought, style = MaterialTheme.typography.bodyLarge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "深入了解")
                }
                if (onOpenPet != null) {
                    TextButton(onClick = onOpenPet) {
                        Text("看看桌宠")
                    }
                }
            }
            if (expanded) {
                DeepState("深层内心剖析", state.deepReflection)
                DeepState("最近最在意什么", state.recentConcern)
                DeepState("没说出口的话", state.unspokenWords)
                DeepState("当前不安", state.insecurity)
                DeepState("对你的真实想法", state.thoughtsAboutUser)
                DeepState("对关系的期待", state.relationshipExpectation)
                DeepState("最近某件事的影响", state.recentImpact)
            }
        }
    }
}

@Composable
private fun StateRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "暂时没有记录" })
    }
}

@Composable
private fun DeepState(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(value.ifBlank { "TA 还没有把这部分想清楚。" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
