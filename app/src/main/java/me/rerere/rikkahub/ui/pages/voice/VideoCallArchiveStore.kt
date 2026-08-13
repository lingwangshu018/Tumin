package me.rerere.rikkahub.ui.pages.voice

import android.content.Context
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File
import java.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class VideoCallArchiveMessage(
    val role: String,
    val text: String,
)

@Serializable
data class VideoCallArchiveEntry(
    val id: String,
    val conversationId: String,
    val assistantId: String,
    val assistantName: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val endedNormally: Boolean = false,
    val messages: List<VideoCallArchiveMessage> = emptyList(),
)

/**
 * Lightweight app-private archive for story video calls.
 *
 * It stores only visible USER/ASSISTANT text snapshots. Tool calls, tool results,
 * reasoning and internal metadata are intentionally excluded.
 */
class VideoCallArchiveStore(context: Context) {
    private val appContext = context.applicationContext
    private val archiveDir = File(appContext.filesDir, "video_call_archives").apply { mkdirs() }
    private val archiveFile = File(archiveDir, "archives.json")

    @Synchronized
    fun list(): List<VideoCallArchiveEntry> = readAll()
        .sortedByDescending { it.startedAtEpochMillis }

    @Synchronized
    fun startSession(
        conversationId: Uuid,
        assistantId: Uuid,
        assistantName: String,
        messages: List<UIMessage>,
    ): String {
        recoverInterruptedSessions()
        val id = Uuid.random().toString()
        val entry = VideoCallArchiveEntry(
            id = id,
            conversationId = conversationId.toString(),
            assistantId = assistantId.toString(),
            assistantName = assistantName,
            startedAtEpochMillis = Instant.now().toEpochMilli(),
            messages = visibleSnapshot(messages),
        )
        writeAll(readAll() + entry)
        return id
    }

    @Synchronized
    fun updateSession(sessionId: String, messages: List<UIMessage>) {
        val all = readAll()
        val index = all.indexOfFirst { it.id == sessionId }
        if (index < 0) return
        val next = all.toMutableList()
        next[index] = next[index].copy(messages = visibleSnapshot(messages))
        writeAll(next)
    }

    @Synchronized
    fun finishSession(sessionId: String, messages: List<UIMessage>, endedNormally: Boolean = true) {
        val all = readAll()
        val index = all.indexOfFirst { it.id == sessionId }
        if (index < 0) return
        val next = all.toMutableList()
        next[index] = next[index].copy(
            endedAtEpochMillis = Instant.now().toEpochMilli(),
            endedNormally = endedNormally,
            messages = visibleSnapshot(messages),
        )
        writeAll(next)
    }

    @Synchronized
    fun deleteSession(sessionId: String) {
        writeAll(readAll().filterNot { it.id == sessionId })
    }

    /** Mark sessions left open by process death as interrupted, without deleting content. */
    @Synchronized
    fun recoverInterruptedSessions() {
        val all = readAll()
        var changed = false
        val now = Instant.now().toEpochMilli()
        val recovered = all.map { entry ->
            if (entry.endedAtEpochMillis == null) {
                changed = true
                entry.copy(endedAtEpochMillis = now, endedNormally = false)
            } else entry
        }
        if (changed) writeAll(recovered)
    }

    private fun visibleSnapshot(messages: List<UIMessage>): List<VideoCallArchiveMessage> =
        messages.mapNotNull { message ->
            val role = when (message.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                else -> return@mapNotNull null
            }
            val text = runCatching { message.toText().trim() }.getOrDefault("")
            if (text.isBlank()) null else VideoCallArchiveMessage(role = role, text = text)
        }

    private fun readAll(): List<VideoCallArchiveEntry> = runCatching {
        if (!archiveFile.exists()) return@runCatching emptyList()
        val raw = archiveFile.readText()
        if (raw.isBlank()) emptyList()
        else JsonInstant.decodeFromString<List<VideoCallArchiveEntry>>(raw)
    }.getOrDefault(emptyList())

    private fun writeAll(entries: List<VideoCallArchiveEntry>) {
        archiveDir.mkdirs()
        val temp = File(archiveDir, "archives.json.tmp")
        temp.writeText(JsonInstant.encodeToString(entries))
        if (archiveFile.exists()) archiveFile.delete()
        temp.renameTo(archiveFile)
    }
}
