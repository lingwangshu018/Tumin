package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.rikkahub.ui.pages.life.MusicPlaybackSession

@Composable
fun ChatMusicHeader() {
    val playback by MusicPlaybackSession.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(playback.active, playback.isPlaying) {
        while (playback.active) {
            MusicPlaybackSession.syncPosition()
            delay(if (playback.isPlaying) 350L else 900L)
        }
    }

    if (!playback.active) return

    val lyricIndex = playback.lyricIndex()
    val previous = playback.lyrics.getOrNull(lyricIndex - 1)?.text.orEmpty()
    val current = playback.lyrics.getOrNull(lyricIndex)?.text.orEmpty()
    val next = playback.lyrics.getOrNull(lyricIndex + 1)?.text.orEmpty()
    val progress = if (playback.durationMs > 0L) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(if (expanded) 24.dp else 18.dp),
        color = Color(0xFFF8EEF1).copy(alpha = 0.96f),
        border = BorderStroke(1.dp, Color(0xFFE9CCD5)),
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = if (expanded) 12.dp else 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(shape = CircleShape, color = Color(0xFFFFDCE6)) {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        Text(if (playback.togetherMode) "💕" else "🎧")
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (playback.togetherMode) "正和 TA 一起听" else "正在播放",
                        color = Color(0xFFAA5C73),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${playback.title} · ${playback.artist}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(onClick = { MusicPlaybackSession.togglePlayPause() }) {
                    Text(if (playback.isPlaying) "Ⅱ" else "▶", color = Color(0xFF9F506A), fontWeight = FontWeight.Bold)
                }
                Text(if (expanded) "⌃" else "⌄", color = Color(0xFF9F7180))
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (playback.lyrics.isEmpty()) {
                    Text(
                        "这首歌还没有歌词。导入 LRC 后，这里会跟着播放进度显示歌词。",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        color = Color(0xFF8A737B),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        previous.ifBlank { "　" },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF9B8A90),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        current.ifBlank { "♪" },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        color = Color(0xFF7E3E55),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        next.ifBlank { "　" },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF9B8A90),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFB9617D),
                    trackColor = Color(0xFFE8D5DB),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMusicTime(playback.positionMs), style = MaterialTheme.typography.labelSmall, color = Color(0xFF8C747C))
                    Text(formatMusicTime(playback.durationMs), style = MaterialTheme.typography.labelSmall, color = Color(0xFF8C747C))
                }
            }
        }
    }
}

private fun formatMusicTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val total = ms / 1000L
    return "%02d:%02d".format(total / 60L, total % 60L)
}
