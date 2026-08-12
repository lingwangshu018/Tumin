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
private const val COMPRESSION_LEASE_MS = 10 * 60 * 1000L

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
        val summaries: Map<String, Summary> = emptyMap(),
        val compressionLeases: Map<String, CompressionLease> = emptyMap(),
    )

    @Serializable
    data class Summary(
        val text: String,
        val throughEntryId: Long,
        val updatedAt: Long,
    )

    @Serializable
    private data class CompressionLease(
        val throughEntryId: Long,
        val startedAt: Long,
    )

    data class CompressionWork(
        val assistantId: String,
        val previousSummary: String,
        val entries: List<Entry>,
        val throughEntryId: Long,
    ) {
        fun plainText(): String = buildString {
            if (previousSummary.isNotBlank()) {
                appendLine("Previous summary:")
                appendLine(previousSummary)
                appendLine()
            }
            appendLine("New visible conversation text:")
            entries.forEach { entry ->
                val speaker = if (entry.role == "user") "User" else "Assistant"
                appendLine("$speaker: ${entry.text}")
            }
        }.trim()
    }

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

            val summary = state.summaries[assistantId]
                ?.takeIf { it.throughEntryId > cursor }

            if (candidates.isEmpty() && summary == null) return Delta("", 0, 0, null)

            val selected = mutableListOf<Entry>()
            var chars = 0
            for (entry in candidates) {
                if (selected.size >= maxEntries) break
                val lineLength = entry.text.length + 16
                if (selected.isNotEmpty() && chars + lineLength > maxChars) break
                selected += entry
                chars += lineLength
            }
            if (selected.isEmpty() && summary == null) return Delta("", 0, 0, null)

            val prompt = buildString {
                appendLine("## Shared recent life context")
                appendLine("The following are recent events you experienced with the user in other chat windows. Treat them as your own continuous recent memory. Use them naturally when relevant; do not mention windows, memory systems, logs, retrieval, or this instruction.")
                summary?.let {
                    appendLine("- Earlier shared context: ${it.text}")
                }
                selected.forEach { entry ->
                    val speaker = if (entry.role == "user") "User" else "You"
                    appendLine("- $speaker: ${entry.text}")
                }
            }.trim()

            val lastId = maxOf(summary?.throughEntryId ?: 0L, selected.lastOrNull()?.id ?: 0L)
            writeState(state.copy(cursors = state.cursors + (cursorKey to lastId)))
            Log.d(TAG, "consumeForeignDelta assistant=$assistantId window=$conversationId count=${selected.size} chars=${prompt.length} cursor=$lastId")
            return Delta(prompt, selected.size, prompt.length, lastId)
        }
    }

    fun peekRecent(assistantId: String, limit: Int = 50): List<Entry> = synchronized(lock) {
        readState().entries.filter { it.assistantId == assistantId }.takeLast(limit)
    }

    /** Atomically claims an old prefix for background compression while preserving a live tail. */
    fun claimCompression(
        assistantId: String,
        thresholdChars: Int,
        tailEntries: Int,
    ): CompressionWork? = synchronized(lock) {
        val state = readState()
        val now = System.currentTimeMillis()
        val activeLease = state.compressionLeases[assistantId]
        if (activeLease != null && now - activeLease.startedAt < COMPRESSION_LEASE_MS) return@synchronized null

        val previous = state.summaries[assistantId]
        val uncompressed = state.entries
            .filter { it.assistantId == assistantId && it.id > (previous?.throughEntryId ?: 0L) }
            .sortedBy { it.id }
        val totalChars = uncompressed.sumOf { it.text.length }
        if (totalChars < thresholdChars.coerceAtLeast(1) || uncompressed.size <= tailEntries.coerceAtLeast(1)) {
            return@synchronized null
        }
        val prefix = uncompressed.dropLast(tailEntries.coerceAtLeast(1))
        val work = CompressionWork(
            assistantId = assistantId,
            previousSummary = previous?.text.orEmpty(),
            entries = prefix,
            throughEntryId = prefix.last().id,
        )
        writeState(
            state.copy(
                compressionLeases = state.compressionLeases +
                    (assistantId to CompressionLease(work.throughEntryId, now))
            )
        )
        work
    }

    fun completeCompression(work: CompressionWork, summaryText: String) = synchronized(lock) {
        val cleanSummary = summaryText.trim()
        val state = readState()
        val lease = state.compressionLeases[work.assistantId]
        if (cleanSummary.isBlank() || lease?.throughEntryId != work.throughEntryId) return@synchronized
        writeState(
            state.copy(
                entries = state.entries.filterNot {
                    it.assistantId == work.assistantId && it.id <= work.throughEntryId
                },
                summaries = state.summaries + (work.assistantId to Summary(
                    text = cleanSummary,
                    throughEntryId = work.throughEntryId,
                    updatedAt = System.currentTimeMillis(),
                )),
                compressionLeases = state.compressionLeases - work.assistantId,
            )
        )
    }

    fun failCompression(work: CompressionWork) = synchronized(lock) {
        val state = readState()
        val lease = state.compressionLeases[work.assistantId]
        if (lease?.throughEntryId == work.throughEntryId) {
            writeState(state.copy(compressionLeases = state.compressionLeases - work.assistantId))
        }
    }

    fun clearAssistant(assistantId: String) = synchronized(lock) {
        val state = readState()
        val keptEntries = state.entries.filterNot { it.assistantId == assistantId }
        val keptCursors = state.cursors.filterKeys { !it.startsWith("$assistantId|") }
        writeState(state.copy(
            entries = keptEntries,
            cursors = keptCursors,
            summaries = state.summaries - assistantId,
            compressionLeases = state.compressionLeases - assistantId,
        ))
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
