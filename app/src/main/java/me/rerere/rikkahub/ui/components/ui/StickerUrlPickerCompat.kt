package me.rerere.rikkahub.ui.components.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private const val STICKER_NAME_SEPARATOR = '\u001F'

/**
 * Compatibility overload for the existing EmojiPicker bridge.
 * Keeps the sticker semantic name together with its remote image URL.
 */
@Composable
fun StickerUrlPicker(
    modifier: Modifier = Modifier,
    height: Int = 320,
    onStickerSelected: (String) -> Unit,
) {
    StickerUrlPicker(
        modifier = modifier,
        height = height,
        onStickerSelected = { name, url ->
            onStickerSelected(name.trim() + STICKER_NAME_SEPARATOR + url.trim())
        },
    )
}
