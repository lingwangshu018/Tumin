from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    s = p.read_text(encoding="utf-8-sig")
    if new in s:
        print(f"{path}: already patched")
        return
    if old not in s:
        raise SystemExit(f"{path}: expected anchor not found")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")
    print(f"{path}: patched")


replace_once(
    "ai/src/main/java/me/rerere/ai/ui/Message.kt",
    '''                        } else {
                            // Has ID - find and update by ID, or insert new
                            val existsPart = acc.find {
                                it is UIMessagePart.Tool && it.toolCallId == deltaPart.toolCallId
                            } as? UIMessagePart.Tool
                            if (existsPart == null) {
                                acc + deltaPart.copy()
                            } else {
                                acc.map { part ->
                                    if (part is UIMessagePart.Tool && part.toolCallId == deltaPart.toolCallId) {
                                        part.merge(deltaPart)
                                    } else part
                                }
                            }
                        }
''',
    '''                        } else {
                            // A streamed tool call may arrive as an arguments-only fragment first,
                            // followed later by the fragment that finally carries the real id/name.
                            // Merge that concrete fragment into the blank placeholder instead of
                            // appending a second Tool, otherwise an empty-name Tool can survive into
                            // history and become an invalid function_response on the next request.
                            val existsPart = acc.find {
                                it is UIMessagePart.Tool && it.toolCallId == deltaPart.toolCallId
                            } as? UIMessagePart.Tool
                            if (existsPart != null) {
                                acc.map { part ->
                                    if (part is UIMessagePart.Tool && part.toolCallId == deltaPart.toolCallId) {
                                        part.merge(deltaPart)
                                    } else part
                                }
                            } else {
                                val blankPlaceholder = acc.lastOrNull {
                                    it is UIMessagePart.Tool && it.toolCallId.isBlank()
                                } as? UIMessagePart.Tool
                                if (blankPlaceholder != null) {
                                    acc.map { part ->
                                        if (part === blankPlaceholder) part.merge(deltaPart) else part
                                    }
                                } else {
                                    acc + deltaPart.copy()
                                }
                            }
                        }
''',
)

replace_once(
    "app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt",
    '''                val tools = messages.last().getTools().filter { !it.isExecuted }
                if (tools.isEmpty()) {
                    // no tool calls, break
                    break
                }
''',
    '''                // Never execute or persist an incomplete streamed tool fragment.
                // OpenAI-compatible gateways are allowed to split tool_calls so an intermediate
                // delta can temporarily have an empty id/name. If such a fragment survives the
                // stream merge, drop it here before execution and before it can poison history.
                val incompleteTools = messages.last().getTools().filter {
                    !it.isExecuted && (it.toolCallId.isBlank() || it.toolName.isBlank())
                }
                if (incompleteTools.isNotEmpty()) {
                    Log.w(TAG, "generateText: dropping incomplete streamed tool fragments: ${incompleteTools.size}")
                    val lastMessage = messages.last()
                    val cleanedParts = lastMessage.parts.filterNot { part ->
                        part is UIMessagePart.Tool && !part.isExecuted &&
                            (part.toolCallId.isBlank() || part.toolName.isBlank())
                    }
                    messages = messages.dropLast(1) + lastMessage.copy(parts = cleanedParts)
                    emit(GenerationChunk.Messages(messages))
                }

                val tools = messages.last().getTools().filter {
                    !it.isExecuted && it.toolCallId.isNotBlank() && it.toolName.isNotBlank()
                }
                if (tools.isEmpty()) {
                    // no valid tool calls, break
                    break
                }
''',
)
