from pathlib import Path


def replace_once(path: str, old: str, new: str):
    p = Path(path)
    s = p.read_text(encoding='utf-8-sig')
    if new in s:
        print(path, 'already patched')
        return
    if old not in s:
        raise SystemExit(f'{path}: anchor not found')
    p.write_text(s.replace(old, new, 1), encoding='utf-8')
    print(path, 'patched')

assistant_path = 'app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt'
replace_once(
    assistant_path,
    '    val enableRecentChatsReference: Boolean = false,\n',
    '    val enableRecentChatsReference: Boolean = false,\n    val enableCrossWindowMemory: Boolean = true, // 同人格跨窗口近期生活流（v1）\n',
)

gh = 'app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt'
replace_once(
    gh,
    'import me.rerere.rikkahub.data.model.AssistantMemory\n',
    'import me.rerere.rikkahub.data.model.AssistantMemory\nimport me.rerere.rikkahub.data.memory.CrossWindowMemoryStore\n',
)
replace_once(
    gh,
    '''        var messages: List<UIMessage> = messages.map { message ->\n            message.copy(parts = message.parts.filterNot { part ->\n                part is UIMessagePart.Tool && (part.toolName.isBlank() || part.toolCallId.isBlank())\n            })\n        }.filterNot { it.parts.isEmpty() }\n\n        val companionHardRouteToolNames = setOf(\n''',
    '''        var messages: List<UIMessage> = messages.map { message ->\n            message.copy(parts = message.parts.filterNot { part ->\n                part is UIMessagePart.Tool && (part.toolName.isBlank() || part.toolCallId.isBlank())\n            })\n        }.filterNot { it.parts.isEmpty() }\n\n        // Cross-window memory v1: log the current user turn, then consume only unseen\n        // events from other windows of the same assistant. No model call is used here.\n        val crossWindowMemoryStore = CrossWindowMemoryStore(context)\n        val crossWindowMemoryPrompt = if (assistant.enableCrossWindowMemory && !conversationId.isNullOrBlank()) {\n            messages.lastOrNull { it.role == MessageRole.USER }?.let { userMessage ->\n                crossWindowMemoryStore.append(\n                    assistantId = assistant.id.toString(),\n                    conversationId = conversationId,\n                    messageId = userMessage.id.toString(),\n                    role = "user",\n                    text = userMessage.toText(),\n                )\n            }\n            crossWindowMemoryStore.consumeForeignDelta(\n                assistantId = assistant.id.toString(),\n                conversationId = conversationId,\n            ).prompt\n        } else {\n            ""\n        }\n\n        val companionHardRouteToolNames = setOf(\n''',
)
replace_once(
    gh,
    '''                    conversationSystemPrompt = conversationSystemPrompt,\n                    workspaceCwd = workspaceCwd,\n                )\n''',
    '''                    conversationSystemPrompt = conversationSystemPrompt,\n                    workspaceCwd = workspaceCwd,\n                    crossWindowMemoryPrompt = crossWindowMemoryPrompt,\n                )\n''',
)
replace_once(
    gh,
    '''            )\n        }\n \n    }.throttleLatest(STREAM_UI_THROTTLE_MS)\n''',
    '''            )\n        }\n\n        // Persist the final visible assistant reply once the whole generation/tool loop finishes.\n        if (assistant.enableCrossWindowMemory && !conversationId.isNullOrBlank()) {\n            messages.lastOrNull { it.role == MessageRole.ASSISTANT && it.toText().isNotBlank() }?.let { assistantMessage ->\n                crossWindowMemoryStore.append(\n                    assistantId = assistant.id.toString(),\n                    conversationId = conversationId,\n                    messageId = assistantMessage.id.toString(),\n                    role = "assistant",\n                    text = assistantMessage.toText(),\n                )\n            }\n        }\n \n    }.throttleLatest(STREAM_UI_THROTTLE_MS)\n''',
)
replace_once(
    gh,
    '''        conversationSystemPrompt: String? = null,\n        workspaceCwd: String? = null,\n    ) {\n''',
    '''        conversationSystemPrompt: String? = null,\n        workspaceCwd: String? = null,\n        crossWindowMemoryPrompt: String = "",\n    ) {\n''',
)
replace_once(
    gh,
    '''                if (assistant.enableRecentChatsReference) {\n                    appendLine()\n                    append(buildRecentChatsPrompt(assistant, conversationRepo))\n                }\n \n                // 代码文件命名和ZIP打包功能说明\n''',
    '''                if (assistant.enableRecentChatsReference) {\n                    appendLine()\n                    append(buildRecentChatsPrompt(assistant, conversationRepo))\n                }\n\n                if (crossWindowMemoryPrompt.isNotBlank()) {\n                    appendLine()\n                    appendLine()\n                    append(crossWindowMemoryPrompt)\n                }\n \n                // 代码文件命名和ZIP打包功能说明\n''',
)
