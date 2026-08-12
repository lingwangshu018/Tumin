package me.rerere.rikkahub.data.service

import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.model.CompanionState
import me.rerere.rikkahub.data.repository.CompanionStateRepository
import kotlin.uuid.Uuid

private const val TAG = "CompanionStateUpdater"

@Serializable
private data class CompanionStateDecision(
    val changed: Boolean = false,
    val reason: String = "",
    val state: CompanionState? = null,
)

/** Cheap local gate: obvious routine turns never trigger an extra model request. */
internal object CompanionEventGate {
    private val meaningfulWords = listOf(
        "喜欢", "爱", "想你", "讨厌", "生气", "难过", "委屈", "吃醋", "害怕", "担心",
        "对不起", "原谅", "约会", "礼物", "表白", "分手", "和好", "承诺", "以后", "永远",
        "第一次", "秘密", "重要", "记住", "搬家", "工作", "考试", "生日", "纪念日",
    )

    fun shouldEvaluate(userText: String, assistantText: String): Boolean {
        val visible = "$userText\n$assistantText".trim()
        if (userText.isBlank() || assistantText.isBlank()) return false
        return visible.length >= 220 || meaningfulWords.any { it in visible }
    }
}

class CompanionStateUpdater(
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val repository: CompanionStateRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun consider(assistantId: Uuid, userText: String, assistantText: String) {
        if (!CompanionEventGate.shouldEvaluate(userText, assistantText)) return
        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val assistant = settings.assistants.firstOrNull { it.id == assistantId } ?: return
            if (!assistant.enableCompanionState) return
            val model = settings.findModelById(settings.compressModelId)
                ?: settings.getCurrentChatModel()
                ?: return
            val provider = model.findProvider(settings.providers) ?: return
            val current = repository.observe(assistantId).value
            val prompt = buildPrompt(current, userText, assistantText)
            val result = providerManager.getProviderByType(provider).generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = TextGenerationParams(model = model),
            )
            val raw = result.choices.firstOrNull()?.message?.toText().orEmpty()
            val decision = parseDecision(raw) ?: return
            if (decision.changed && decision.state != null) {
                repository.update(assistantId) { decision.state }
            }
        }.onFailure { Log.w(TAG, "Background companion-state evaluation failed", it) }
    }

    private fun buildPrompt(current: CompanionState, userText: String, assistantText: String) = """
        You maintain a fictional companion's persistent state. Decide whether this exchange contains a meaningful
        emotional, relationship, location, activity, appearance, commitment, conflict, reconciliation or milestone
        change. Routine greetings and ordinary small talk MUST return changed=false.

        Emotion is temporary; relationship stage changes only after strong evidence and meaningful events.
        Preserve fields that are not supported by the exchange. Relationship scores are 0..100 and must change
        conservatively. Never expose scores in dialogue. Return JSON only, matching:
        {"changed":false,"reason":"...","state":null}
        or {"changed":true,"reason":"...","state":<complete updated state>}

        Current state:
        ${json.encodeToString(current)}

        Visible user text:
        ${userText.take(1200)}

        Visible assistant text:
        ${assistantText.take(1200)}
    """.trimIndent()

    private fun parseDecision(raw: String): CompanionStateDecision? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            json.decodeFromString<CompanionStateDecision>(raw.substring(start, end + 1))
        }.getOrNull()
    }
}
