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
import me.rerere.rikkahub.data.model.CharacterState
import me.rerere.rikkahub.data.model.CompanionState
import me.rerere.rikkahub.data.model.RelationshipEvent
import me.rerere.rikkahub.data.model.RelationshipEventType
import me.rerere.rikkahub.data.relationship.RelationshipActivityTracker
import me.rerere.rikkahub.data.relationship.RelationshipRuleEngine
import me.rerere.rikkahub.data.repository.CompanionStateRepository
import java.util.TimeZone
import kotlin.uuid.Uuid

private const val TAG = "CompanionStateUpdater"
private const val MILLIS_PER_DAY = 86_400_000L

@Serializable
private data class RawRelationshipEvent(
    val meaningful: Boolean = false,
    val type: String = "ROUTINE",
    val intensity: Int = 0,
    val summary: String = "",
    val milestone: String? = null,
    val targetIssue: String? = null,
)

@Serializable
private data class CompanionStateDecision(
    val changed: Boolean = false,
    val reason: String = "",
    val character: CharacterState? = null,
    val relationshipEvent: RawRelationshipEvent? = null,
)

/**
 * Cheap local gate. Most routine turns die here and cost no extra model request.
 * Short turns only pass on explicit relationship/emotional signals; long turns get a semantic check.
 */
internal object CompanionEventGate {
    private val explicitEventWords = listOf(
        "喜欢", "爱你", "爱上", "想你", "想念", "讨厌", "生气", "难过", "委屈", "吃醋", "害怕", "担心",
        "对不起", "道歉", "原谅", "约会", "礼物", "表白", "在一起", "分手", "和好", "承诺", "永远",
        "第一次", "秘密", "信任", "依赖", "安全感", "心疼", "亲亲", "抱抱", "拥抱", "亲吻", "暧昧",
        "吵架", "争吵", "冷战", "背叛", "离开我", "结婚", "订婚", "纪念日",
    )
    private val emotionalWords = listOf(
        "开心", "高兴", "感动", "难受", "哭", "孤独", "不安", "失望", "幸福", "陪我", "需要你", "重要",
    )

    fun shouldEvaluate(userText: String, assistantText: String): Boolean {
        if (userText.isBlank() || assistantText.isBlank()) return false
        val visible = "$userText\n$assistantText".trim()
        if (explicitEventWords.any { it in visible }) return true
        if (visible.length >= 180 && emotionalWords.any { it in visible }) return true
        return visible.length >= 360
    }
}

class CompanionStateUpdater(
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val repository: CompanionStateRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun consider(assistantId: Uuid, userText: String, assistantText: String) {
        if (userText.isBlank() || assistantText.isBlank()) return

        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val assistant = settings.assistants.firstOrNull { it.id == assistantId } ?: return
            if (!assistant.enableCompanionState) return

            // Every completed exchange contributes to long-term continuity locally.
            // This path never calls a model and can occasionally emit a tiny BOND_GROWTH event.
            val now = System.currentTimeMillis()
            val epochDay = localEpochDay(now)
            repository.update(assistantId) { previous ->
                val tracked = RelationshipActivityTracker.record(previous.relationship, epochDay)
                val relationship = tracked.bondGrowthEvent?.let { event ->
                    RelationshipRuleEngine.apply(tracked.state, event, now)
                } ?: tracked.state
                previous.copy(relationship = relationship)
            }

            if (!CompanionEventGate.shouldEvaluate(userText, assistantText)) {
                Log.d(TAG, "Skipped model analysis: local gate filtered routine turn")
                return
            }

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
            val event = decision.relationshipEvent?.toDomainEvent()
            val characterChanged = decision.character != null
            val relationshipChanged = event?.meaningful == true && event.type != RelationshipEventType.ROUTINE
            if (!decision.changed && !characterChanged && !relationshipChanged) return

            repository.update(assistantId) { previous ->
                val nextRelationship = if (relationshipChanged && event != null) {
                    RelationshipRuleEngine.apply(previous.relationship, event)
                } else {
                    previous.relationship
                }
                previous.copy(
                    character = decision.character ?: previous.character,
                    relationship = nextRelationship,
                )
            }
        }.onFailure { e ->
            Log.w(TAG, "Background companion-state evaluation failed: ${e.message}", e)
        }
    }

    private fun localEpochDay(now: Long): Long {
        val offset = TimeZone.getDefault().getOffset(now).toLong()
        return (now + offset) / MILLIS_PER_DAY
    }

    private fun buildPrompt(current: CompanionState, userText: String, assistantText: String) = """
        Analyze ONE exchange for a fictional companion. Be conservative. Routine greetings, ordinary affection,
        repeated pet names, and small talk are not meaningful relationship events by themselves.

        Your job is ONLY semantic classification. Never invent or output relationship scores or stages.
        Relationship math is performed locally by the app.

        Allowed relationship event types:
        ROUTINE, AFFECTION, EMOTIONAL_SUPPORT, SELF_DISCLOSURE, TRUST_BUILDING, FLIRTING, JEALOUSY, GIFT, DATE,
        CONFLICT, APOLOGY, RECONCILIATION, CONFESSION, RELATIONSHIP_CONFIRMED, COMMITMENT, BETRAYAL, BREAKUP,
        SEPARATION, MILESTONE.

        BOND_GROWTH is reserved for the app's local long-term tracker. Never output BOND_GROWTH.
        Intensity must be 0..5. Use 4-5 only for genuinely major events.
        milestone must be null unless this exchange creates a memorable first/commitment/relationship milestone.
        targetIssue should identify an unresolved issue only for conflict/apology/reconciliation when clear.

        If location/activity/appearance/emotion clearly changed, return a COMPLETE updated character object.
        Otherwise character must be null. Preserve unsupported character fields exactly.

        Current character:
        ${json.encodeToString(current.character)}
        Current relationship stage: ${current.relationship.stage.name}
        Current relationship summary: ${current.relationship.summary.take(240)}
        Latest unresolved issue: ${current.relationship.unresolvedIssues.lastOrNull()?.take(160) ?: "none"}

        User: ${userText.take(700)}
        Assistant: ${assistantText.take(700)}

        Return ONE JSON object only:
        {"changed":false,"reason":"routine","character":null,"relationshipEvent":{"meaningful":false,"type":"ROUTINE","intensity":0,"summary":"","milestone":null,"targetIssue":null}}
        or
        {"changed":true,"reason":"brief reason","character":null,"relationshipEvent":{"meaningful":true,"type":"EMOTIONAL_SUPPORT","intensity":3,"summary":"brief factual summary","milestone":null,"targetIssue":null}}
    """.trimIndent()

    private fun parseDecision(raw: String): CompanionStateDecision? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) {
            Log.w(TAG, "parseDecision: no JSON object found (len=${raw.length})")
            return null
        }
        val jsonStr = raw.substring(start, end + 1)
        return runCatching {
            json.decodeFromString<CompanionStateDecision>(jsonStr)
        }.onFailure { e ->
            Log.w(TAG, "parseDecision: invalid JSON: ${jsonStr.take(220)}", e)
        }.onSuccess { decision ->
            Log.d(TAG, "Decision: changed=${decision.changed}, event=${decision.relationshipEvent?.type}, reason=${decision.reason.take(80)}")
        }.getOrNull()
    }

    private fun RawRelationshipEvent.toDomainEvent(): RelationshipEvent {
        val parsedType = runCatching { RelationshipEventType.valueOf(type.trim().uppercase()) }
            .getOrDefault(RelationshipEventType.ROUTINE)
        return RelationshipEvent(
            meaningful = meaningful && parsedType != RelationshipEventType.ROUTINE,
            type = parsedType,
            intensity = intensity.coerceIn(0, 5),
            summary = summary,
            milestone = milestone,
            targetIssue = targetIssue,
        )
    }
}
