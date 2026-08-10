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
 * 让情侣空间里的绑定助手用它自己的模型和角色设定来发动态、看照片、评论与回复。
 * 图片沿用普通聊天使用的 UIMessagePart.Image(url) 多模态消息格式。
 */
class CoupleAiService : KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val providerManager: ProviderManager by inject()

    suspend fun commentOnUserPost(
        assistantId: String,
        postContent: String,
        imageUris: List<String> = emptyList(),
    ): String? {
        val photoInstruction = if (imageUris.isNotEmpty()) {
            "这条动态还附带了 ${imageUris.size} 张照片。请先认真看照片本身，再结合文字自然评论；可以提到你确实从图片中看到的具体内容，但不要猜测看不清或不存在的细节。"
        } else {
            "这条动态没有附带照片。"
        }
        return generate(
            assistantId = assistantId,
            task = """
                你正在情侣空间的 QQ 空间动态里看到恋人刚发的内容：
                「${postContent.ifBlank { "（没有配文字）" }}」

                $photoInstruction

                请以你自己的性格和你们的关系，自然评论这条动态。
                只输出评论正文，不要解释，不要写“评论：”，不要使用系统说明。
                评论像真实恋人留言，通常 1～3 句即可。
            """.trimIndent(),
            imageUris = imageUris,
        )
    }

    suspend fun replyToUserComment(
        assistantId: String,
        postContent: String,
        userComment: String,
        imageUris: List<String> = emptyList(),
    ): String? = generate(
        assistantId = assistantId,
        task = """
            这是你自己在情侣空间发的一条 QQ 空间动态：
            「${postContent.ifBlank { "（没有配文字）" }}」
            ${if (imageUris.isNotEmpty()) "这条动态还附带了 ${imageUris.size} 张照片，你可以结合照片内容理解上下文。" else ""}

            恋人在下面评论了：
            「$userComment」

            请以你自己的性格直接回复恋人的这条评论。
            只输出回复正文，不要解释，不要写“回复：”。
            像真实 QQ 空间留言互动，通常 1～3 句即可。
        """.trimIndent(),
        imageUris = imageUris,
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

    /**
     * 优先发送真正的多模态消息。如果当前模型/网关不支持视觉输入，
     * 会自动退回文字模式，至少保证评论功能不中断，同时不会假装看见了图片内容。
     */
    private suspend fun generate(
        assistantId: String,
        task: String,
        imageUris: List<String> = emptyList(),
    ): String? {
        val multimodal = runCatching {
            generateOnce(assistantId, task, imageUris.take(9))
        }.getOrNull()
        if (!multimodal.isNullOrBlank()) return multimodal

        if (imageUris.isEmpty()) return null

        val fallbackTask = """
            $task

            注意：当前模型或接口没有成功读取这些照片，因此这次不要描述、猜测任何图片细节。
            只根据动态文字和关系上下文自然回应；如果动态只有图片没有文字，可以只做简短、不涉及具体画面内容的回应。
        """.trimIndent()
        return runCatching { generateOnce(assistantId, fallbackTask, emptyList()) }.getOrNull()
    }

    private suspend fun generateOnce(
        assistantId: String,
        task: String,
        imageUris: List<String>,
    ): String? {
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
            appendLine("如果收到照片输入，你必须基于实际可见内容回应；看不清的地方不要编造。")
            appendLine("这里的内容会直接展示给恋人，所以不要输出分析过程、格式说明或系统提示。")
        }

        val userParts = buildList<UIMessagePart> {
            add(UIMessagePart.Text(task))
            imageUris.filter { it.isNotBlank() }.take(9).forEach { uri ->
                add(UIMessagePart.Image(uri))
            }
        }
        val messages = listOf(
            UIMessage(
                role = MessageRole.SYSTEM,
                parts = listOf(UIMessagePart.Text(systemPrompt)),
            ),
            UIMessage(
                role = MessageRole.USER,
                parts = userParts,
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

        return streamed.lastOrNull { it.role == MessageRole.ASSISTANT }
            ?.parts
            ?.filterIsInstance<UIMessagePart.Text>()
            ?.joinToString("\n") { it.text }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}
