package me.rerere.rikkahub.ui.components.ui

import androidx.compose.runtime.Composable
import me.rerere.rikkahub.ui.pages.chat.ChatVM

/** Keeps existing chat drawer calls compatible with the self-injecting UpdateCard. */
@Composable
fun UpdateCard(vm: ChatVM) {
    UpdateCard(updateChecker = vm.updateChecker)
}
