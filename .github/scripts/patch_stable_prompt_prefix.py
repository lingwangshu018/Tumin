from pathlib import Path

path = Path('app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt')
raw = path.read_bytes()
text = raw.decode('utf-8-sig')
newline = '\r\n' if '\r\n' in text else ('\r' if '\r' in text else '\n')

internal_anchor = '        val internalMessages = buildList {'
internal_pos = text.index(internal_anchor)
start_marker = '                // 记忆'
end_marker = '                // 代码文件命名和ZIP打包功能说明'
start = text.index(start_marker, internal_pos)
end = text.index(end_marker, start)
dynamic_block = text[start:end].rstrip('\r\n')
text = text[:start] + text[end:]

dynamic_decl_lines = [
    '        // Prompt-cache stability: recall results change with every user turn, so keep them',
    '        // out of SYSTEM. They remain available to the model immediately before the latest user turn.',
    '        val dynamicMemoryContext = buildString {',
    dynamic_block,
    '        }.trim()',
    '',
]
dynamic_decl = newline.join(dynamic_decl_lines) + newline
text = text.replace(internal_anchor, dynamic_decl + internal_anchor, 1)

old_history = '            addAll(messages.limitContext(assistant.contextMessageSize))'
new_history = newline.join([
    '            val limitedMessages = messages.limitContext(assistant.contextMessageSize)',
    '            if (dynamicMemoryContext.isNotBlank()) {',
    '                val latestUserIndex = limitedMessages.indexOfLast { it.role == MessageRole.USER }',
    '                val memoryContextMessage = UIMessage.user(',
    '                    "<memory_context>\\n$dynamicMemoryContext\\n</memory_context>"',
    '                )',
    '                if (latestUserIndex >= 0) {',
    '                    addAll(limitedMessages.take(latestUserIndex))',
    '                    add(memoryContextMessage)',
    '                    addAll(limitedMessages.drop(latestUserIndex))',
    '                } else {',
    '                    addAll(limitedMessages)',
    '                    add(memoryContextMessage)',
    '                }',
    '            } else {',
    '                addAll(limitedMessages)',
    '            }',
])
if old_history not in text:
    raise SystemExit('history insertion anchor not found')
text = text.replace(old_history, new_history, 1)

new_internal_pos = text.index(internal_anchor)
prefix = text[:new_internal_pos]
if '// 外置记忆库召回' not in prefix or 'crossWindowMemoryPrompt.isNotBlank()' not in prefix:
    raise SystemExit('dynamic memory extraction incomplete')
system_region = text[new_internal_pos:text.index('        // Token observation only:', new_internal_pos)]
if '// 外置记忆库召回' in system_region or 'crossWindowMemoryPrompt.isNotBlank()' in system_region:
    raise SystemExit('dynamic memory still present in system prompt')
if text.count('<memory_context>') != 1:
    raise SystemExit('unexpected memory_context marker count')

path.write_bytes(text.encode('utf-8'))
