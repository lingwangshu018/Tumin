package me.rerere.rikkahub.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.model.CompanionState
import kotlin.uuid.Uuid

/**
 * Owns the persistent companion state for each assistant/persona.
 * Callers only observe or replace a complete state, so chat, calls and pets share one source of truth.
 */
class CompanionStateRepository(context: Context) {
    private val preferences = context.getSharedPreferences("companion_states", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val flows = mutableMapOf<String, MutableStateFlow<CompanionState>>()

    @Synchronized
    fun observe(assistantId: Uuid): StateFlow<CompanionState> {
        val key = assistantId.toString()
        return flows.getOrPut(key) { MutableStateFlow(read(key)) }
    }

    @Synchronized
    fun update(assistantId: Uuid, transform: (CompanionState) -> CompanionState) {
        val key = assistantId.toString()
        val flow = flows.getOrPut(key) { MutableStateFlow(read(key)) }
        val updated = transform(flow.value)
        preferences.edit().putString(key, json.encodeToString(updated)).apply()
        flow.value = updated
    }

    private fun read(key: String): CompanionState = preferences.getString(key, null)
        ?.let { runCatching { json.decodeFromString<CompanionState>(it) }.getOrNull() }
        ?: CompanionState()
}
