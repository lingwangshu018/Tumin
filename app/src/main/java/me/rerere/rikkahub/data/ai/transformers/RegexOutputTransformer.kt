/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.utils.StickerAiSupport
import org.koin.core.component.KoinComponent

object RegexOutputTransformer : OutputMessageTransformer, KoinComponent {
    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val assistant = ctx.assistant
        val regexProcessed = if (assistant.regexes.isEmpty()) {
            messages
        } else {
            messages.map { message ->
                val scope = when (message.role) {
                    MessageRole.ASSISTANT -> AssistantAffectScope.ASSISTANT
                    else -> return@map message
                }
                message.copy(
                    parts = message.parts.map { part ->
                        when (part) {
                            is UIMessagePart.Text -> {
                                part.copy(text = part.text.replaceRegexes(assistant, scope, visual = false))
                            }

                            is UIMessagePart.Reasoning -> {
                                part.copy(reasoning = part.reasoning.replaceRegexes(assistant, scope, visual = false))
                            }

                            else -> part
                        }
                    }
                )
            }
        }
        return StickerAiSupport.replaceDirectives(
            context = ctx.context,
            assistantId = assistant.id.toString(),
            messages = regexProcessed,
        )
    }
}
