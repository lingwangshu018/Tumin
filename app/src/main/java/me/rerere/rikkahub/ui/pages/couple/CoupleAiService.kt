package me.rerere.rikkahub.ui.pages.couple

import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.GenMediaRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
data class CoupleAiPostDraft(
    val content: String,
    val needImage: Boolean = false,
    val imagePrompt: String = "",
    val imageCount: Int = 0,
)

class CoupleAiService : KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val providerManager: ProviderManager by inject()
    private val filesManager: FilesManager by inject()
    private val genMediaRepository: GenMediaRepository by inject()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

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
            ${if (imageUris.isNotEmpty()) "这条动态还附带了 ${imageUris.size} 张照片，请结合你实际看到的照片内容理解上下文。" else ""}

            恋人在下面评论了：
            「$userComment」

            请以你自己的性格直接回复恋人的这条评论。
            只输出回复正文，不要解释，不要写“回复：”。
            像真实 QQ 空间留言互动，通常 1～3 句即可。
        """.trimIndent(),
        imageUris = imageUris,
    )

    suspend fun replyToDiary(
        assistantId: String,
        title: String,
        content: String,
        memoryContext: String = "",
    ): String? {
        val memorySection = if (memoryContext.isBlank()) {
            "这次没有附加旧日记记忆，请只根据当前这篇日记和你本来的角色关系写回信。"
        } else {
            """
                下面是你们过去日记中的少量记忆摘录，其中书签内容代表恋人特别想保留的重要页面：
                $memoryContext

                这些旧记忆只是背景，不是必须逐条回应。只有当它和当前日记真的有关时，才自然地提起一处过去的事情；不要生硬罗列旧日记，也不要为了显得“记得”而强行引用。
            """.trimIndent()
        }

        return generate(
            assistantId = assistantId,
            task = """
                你正在阅读恋人写给“我们的日记 / THE PRIVATE JOURNAL”的一篇私人日记。

                标题：$title

                正文：
                $content

                $memorySection

                请根据这篇日记本身写一封完整回信。你可以回应恋人的情绪、细节、回忆和你真实想说的话，但不要把它写成点评、摘要或心理分析。
                这是一封只会留在这篇日记下面、写给恋人的私人回信。保持你原本的性格、称呼习惯和你们的关系状态。
                如果旧日记里出现与现在明显呼应的事情，可以像真实恋人一样自然说“我还记得……”或用更符合你性格的方式提起，但不要编造没有提供过的回忆。
                不要修改或复述原文，不要写“AI回复”“分析”“以下是回信”等说明。
                直接输出完整回信正文，可以比 QQ 空间评论更长、更完整、更像一封真正的信。
            """.trimIndent(),
        )
    }

    suspend fun createPostDraft(assistantId: String, recentContext: String): CoupleAiPostDraft? {
        val raw = generate(
            assistantId = assistantId,
            task = """
                你现在可以主动在你和恋人的情侣空间里发一条 QQ 空间动态。
                最近的空间动态摘要如下：
                $recentContext

                请像真实的人使用 QQ 空间一样决定此刻你自己想发什么，可以是生活碎片、心情、想到恋人的瞬间、吐槽、分享、风景、食物、天气、房间、穿搭或一句很短的话。

                同时决定这条动态是否适合配图。不要每条都强行带图，大约一半左右的日常动态适合带图；纯情绪或很短的一句话可以不配图。
                如果配图，图片必须与动态正文在时间、地点、情绪和内容上相互一致，不要出现正文说在家却配海边、正文低落却配明显欢庆画面的冲突。
                配图提示词需要描述画面本身，不要在图里生成 QQ 空间界面、动态文字、水印或截图边框。

                只输出一个 JSON 对象，不要 Markdown 代码块、不要解释、不要额外文字：
                {"content":"最终动态正文","needImage":true,"imagePrompt":"详细且自然的配图提示词","imageCount":1}

                规则：
                - content 必须保持你原本的性格、称呼和关系状态，像你真的想发的动态。
                - needImage 只能是 true 或 false。
                - imageCount 为 0～4；通常 1 张即可，只有确实适合组图时才使用 2～4 张。
                - needImage=false 时 imagePrompt 必须为空字符串且 imageCount=0。
                - needImage=true 时 imagePrompt 必须足够具体，可直接交给图片生成模型使用。
            """.trimIndent(),
        ) ?: return null

        val cleaned = raw
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()
        val objectText = cleaned
            .substringAfter('{', missingDelimiterValue = "")
            .takeIf { it.isNotEmpty() }
            ?.let { "{" + it.substringBeforeLast('}', missingDelimiterValue = it) + "}" }
            ?: return CoupleAiPostDraft(content = cleaned.take(500))

        return runCatching {
            json.decodeFromString<CoupleAiPostDraft>(objectText)
        }.getOrElse {
            CoupleAiPostDraft(content = cleaned.take(500))
        }.let { draft ->
            if (draft.needImage && draft.imagePrompt.isNotBlank()) {
                draft.copy(imageCount = draft.imageCount.coerceIn(1, 4))
            } else {
                draft.copy(needImage = false, imagePrompt = "", imageCount = 0)
            }
        }
    }

    suspend fun generatePostImages(
        prompt: String,
        count: Int = 1,
    ): List<String> = runCatching {
        if (prompt.isBlank()) return emptyList()

        val settings = settingsStore.settingsFlow.first()
        val model = settings.findModelById(settings.imageGenerationModelId)
            ?: return emptyList()
        val provider = model.findProvider(settings.providers)
            ?: return emptyList()
        val providerSetting = settings.providers.find { it.id == provider.id }
            ?: return emptyList()

        val safeCount = count.coerceIn(1, 4)
        val params = ImageGenerationParams(
            model = model,
            prompt = prompt,
            numOfImages = safeCount,
            aspectRatio = ImageAspectRatio.SQUARE,
            customHeaders = model.customHeaders,
            customBody = model.customBodies,
        )
        val result = providerManager.getProviderByType(provider)
            .generateImage(providerSetting, params)

        result.items.take(safeCount).mapIndexed { index, item ->
            val imagesDir = filesManager.getImagesDir()
            val timestamp = System.currentTimeMillis()
            val safeModelName = model.displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(48)
            val imageFile = File(imagesDir, "couple_${timestamp}_${safeModelName}_$index.png")
            val createdFile = filesManager.createImageFileFromBase64(item.data, imageFile.absolutePath)

            genMediaRepository.insertMedia(
                GenMediaEntity(
                    path = "images/${createdFile.name}",
                    modelId = model.displayName,
                    prompt = prompt,
                    createAt = timestamp,
                    type = GenMediaEntity.TYPE_IMAGE_GENERATION,
                    sourcePaths = null,
                )
            )
            createdFile.toURI().toString()
        }
    }.getOrElse { emptyList() }

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
            appendLine("你正在以自己的身份和恋人共同使用情侣空间，其中包括 QQ 空间动态与“我们的日记 / THE PRIVATE JOURNAL”。")
            appendLine("保持角色原本的性格、称呼习惯和关系状态，不要突然变成客服、旁白或无关助手。")
            appendLine("你可以使用系统明确提供的旧日记记忆建立连续感，但绝不能编造未提供的共同经历。")
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
