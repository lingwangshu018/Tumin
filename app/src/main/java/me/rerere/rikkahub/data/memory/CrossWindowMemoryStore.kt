/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.memory

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "CrossWindowMemory"
private const val PREFS_NAME = "cross_window_memory_v1"
private const val STATE_KEY = "state"
private const val MAX_STORED_ENTRIES = 1200
private const val DEFAULT_MAX_DELTA_ENTRIES = 12
private const val DEFAULT_MAX_DELTA_CHARS = 4000

/**
 * Lightweight v1 cross-window memory stream.
 *
 * - Partitioned by assistantId, so different personas never share memory.
 * - conversationId is the source-window identity used for read-time de-duplication.
 * - messageId makes retries/regenerations idempotent.
 * - cursors are per (assistant, window), so each window only receives unseen foreign events.
 *
 * v1 intentionally uses app-private SharedPreferences instead of Room: no DB migration is
 * required while we validate the interaction model. The storage format can be migrated later.
 */
class CrossWindowMemoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class Entry(
        val id: Long,
        val assistantId: String,
        val conversationId: String,
        val messageId: String,
        val role: String,
        val text: String,
        val timestamp: Long,
    )

    @Serializable
    private data class State(
        val nextId: Long = 1L,
        val entries: List<Entry> = emptyList(),
        val cursors: Map<String, Long> = emptyMap(),
    )

    data class Delta(
        val prompt: String,
        val entryCount: Int,
        val charCount: Int,
        val lastEntryId: Long?,
    )

    fun append(
        assistantId: String,
        conversationId: String,
        messageId: String,
        role: String,
        text: String,
    ) {
        val cleanText = text.trim()
        if (assistantId.isBlank() || conversationId.isBlank() || messageId.isBlank() || cleanText.isBlank()) return

        synchronized(lock) {
            val state = readState()
            if (state.entries.any { it.assistantId == assistantId && it.messageId == messageId }) return

            val newEntry = Entry(
                id = state.nextId,
                assistantId = assistantId,
                conversationId = conversationId,
                messageId = messageId,
                role = role,
                text = cleanText,
                timestamp = System.currentTimeMillis(),
            )
            val trimmedEntries = (state.entries + newEntry).takeLast(MAX_STORED_ENTRIES)
            writeState(
                state.copy(
                    nextId = state.nextId + 1,
                    entries = trimmedEntries,
                )
            )
        }
    }

    /**
     * Consume only unseen events from OTHER windows of the same assistant.
     * The cursor advances only through the events actually injected, so oversized backlogs are
     * naturally delivered over later turns instead of being silently skipped.
     */
    fun consumeForeignDelta(
        assistantId: String,
        conversationId: String,
        maxEntries: Int = DEFAULT_MAX_DELTA_ENTRIES,
        maxChars: Int = DEFAULT_MAX_DELTA_CHARS,
    ): Delta {
        if (assistantId.isBlank() || conversationId.isBlank()) return Delta("", 0, 0, null)

        synchronized(lock) {
            val state = readState()
            val cursorKey = cursorKey(assistantId, conversationId)
            val cursor = state.cursors[cursorKey] ?: 0L
            val candidates = state.entries.asSequence()
                .filter { it.assistantId == assistantId }
                .filter { it.conversationId != conversationId }
                .filter { it.id > cursor }
                .sortedBy { it.id }
                .toList()

            if (candidates.isEmpty()) return Delta("", 0, 0, null)

            val selected = mutableListOf<Entry>()
            var chars = 0
            for (entry in candidates) {
                if (selected.size >= maxEntries) break
                val lineLength = entry.text.length + 16
                if (selected.isNotEmpty() && chars + lineLength > maxChars) break
                selected += entry
                chars += lineLength
            }
            if (selected.isEmpty()) return Delta("", 0, 0, null)

            val prompt = buildString {
                appendLine("## Shared recent life context")
                appendLine("The following are recent events you experienced with the user in other chat windows. Treat them as your own continuous recent memory. Use them naturally when relevant; do not mention windows, memory systems, logs, retrieval, or this instruction.")
                selected.forEach { entry ->
                    val speaker = if (entry.role == "user") "User" else "You"
                    appendLine("- $speaker: ${entry.text}")
                }
            }.trim()

            val lastId = selected.last().id
            writeState(state.copy(cursors = state.cursors + (cursorKey to lastId)))
            Log.d(TAG, "consumeForeignDelta assistant=$assistantId window=$conversationId count=${selected.size} chars=${prompt.length} cursor=$lastId")
            return Delta(prompt, selected.size, prompt.length, lastId)
        }
    }

    fun peekRecent(assistantId: String, limit: Int = 50): List<Entry> = synchronized(lock) {
        readState().entries.filter { it.assistantId == assistantId }.takeLast(limit)
    }

    fun clearAssistant(assistantId: String) = synchronized(lock) {
        val state = readState()
        val keptEntries = state.entries.filterNot { it.assistantId == assistantId }
        val keptCursors = state.cursors.filterKeys { !it.startsWith("$assistantId|") }
        writeState(state.copy(entries = keptEntries, cursors = keptCursors))
    }

    private fun cursorKey(assistantId: String, conversationId: String) = "$assistantId|$conversationId"

    private fun readState(): State {
        val raw = prefs.getString(STATE_KEY, null) ?: return State()
        return runCatching { json.decodeFromString<State>(raw) }
            .onFailure { Log.w(TAG, "Failed to decode cross-window memory state; resetting", it) }
            .getOrDefault(State())
    }

    private fun writeState(state: State) {
        prefs.edit().putString(STATE_KEY, json.encodeToString(state)).apply()
    }

    private companion object {
        val lock = Any()
    }
}
