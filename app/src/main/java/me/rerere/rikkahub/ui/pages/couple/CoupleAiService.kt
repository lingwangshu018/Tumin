package me.rerere.rikkahub.ui.pages.couple

import kotlinx.coroutines.flow.first
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 让情侣空间里的绑定助手用它自己的模型和角色设定来发动态、评论与回复。
 * 这里刻意不使用固定文案，避免不同角色都说成同一种口吻。
 */
class CoupleAiService : KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val providerManager: ProviderManager by inject()

    suspend fun commentOnUserPost(assistantId: String, postContent: String): String? = generate(
        assistantId = assistantId,
        task = """
            你正在情侣空间的 QQ 空间动态里看到恋人刚发的内容：
            「$postContent」

            请以你自己的性格和你们的关系，自然评论这条动态。
            只输出评论正文，不要解释，不要写“评论：”，不要使用系统说明。
            评论像真实恋人留言，通常 1～3 句即可。
        """.trimIndent(),
    )

    suspend fun replyToUserComment(
        assistantId: String,
        postContent: String,
        userComment: String,
    ): String? = generate(
        assistantId = assistantId,
        task = """
            这是你自己在情侣空间发的一条 QQ 空间动态：
            「$postContent」

            恋人在下面评论了：
            「$userComment」

            请以你自己的性格直接回复恋人的这条评论。
            只输出回复正文，不要解释，不要写“回复：”。
            像真实 QQ 空间留言互动，通常 1～3 句即可。
        """.trimIndent(),
    )

    suspend fun createPost(assistantId: String, recentContext: String): String? = generate(
        assistantId = assistantId,
        task = """
            你现在可以主动在你和恋人的情侣空间里发一条 QQ 空间动态。
            最近的空间动态摘要如下：
            $recentContext

            请决定此刻你自己想发什么。可以是生活碎片、心情、想到恋人的瞬间、吐槽、分享或一句很短的话。
            不要复述摘要，也不要为了“完成任务”而生硬发言。
            只输出动态正文，不要解释，不要写“朋友圈/动态：”。控制在适合 QQ 空间的一小段内。
        """.trimIndent(),
    )

    private suspend fun generate(assistantId: String, task: String): String? {
        return runCatching {
            val settings = settingsStore.settingsFlow.first()
            val assistant = settings.assistants.firstOrNull { it.id.toString() == assistantId }
                ?: return null
            val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)
                ?: return null
            val providerSetting = model.findProvider(settings.providers)
                ?: return null
            val provider = providerManager.getProviderByType(providerSetting)

            val systemPrompt = buildString {
                if (assistant.systemPrompt.isNotBlank()) {
                    append(assistant.systemPrompt)
                    appendLine()
                    appendLine()
                }
                appendLine("## 情侣空间互动")
                appendLine("你正在以自己的身份使用与恋人的私人 QQ 空间。")
                appendLine("保持角色原本的性格、称呼习惯和关系状态，不要突然变成客服或旁白。")
                appendLine("这里的内容会直接展示给恋人，所以不要输出分析过程、格式说明或系统提示。")
            }

            val messages = listOf(
                UIMessage(
                    role = MessageRole.SYSTEM,
                    parts = listOf(UIMessagePart.Text(systemPrompt)),
                ),
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text(task)),
                ),
            )

            val params = TextGenerationParams(
                model = model,
                temperature = assistant.temperature,
                topP = assistant.topP,
                maxTokens = assistant.maxTokens,
                customHeaders = buildList {
                    addAll(assistant.customHeaders)
                    addAll(model.customHeaders)
                },
                customBody = buildList {
                    addAll(assistant.customBodies)
                    addAll(model.customBodies)
                },
            )

            var streamed = messages
            provider.streamText(
                providerSetting = providerSetting,
                messages = messages,
                params = params,
            ).collect { chunk ->
                streamed = streamed.handleMessageChunk(chunk = chunk, model = model)
            }

            streamed.lastOrNull { it.role == MessageRole.ASSISTANT }
                ?.parts
                ?.filterIsInstance<UIMessagePart.Text>()
                ?.joinToString("\n") { it.text }
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
