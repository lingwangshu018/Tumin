package me.rerere.rikkahub.ui.components.ui

import androidx.compose.runtime.Composable
import me.rerere.rikkahub.ui.pages.chat.ChatVM

/**
 * The update controls now live exclusively on the About page.
 * Keep this overload as a no-op so the chat drawer no longer renders a duplicate update section.
 */
@Composable
fun UpdateCard(vm: ChatVM) = Unit
