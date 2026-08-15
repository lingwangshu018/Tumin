from pathlib import Path

p = Path('app/src/main/java/me/rerere/rikkahub/ui/pages/voice/VideoCallPage.kt')
raw = p.read_bytes()
has_bom = raw.startswith(b'\xef\xbb\xbf')
text = raw.decode('utf-8-sig')
newline = '\r\n' if '\r\n' in text else '\n'

# Add scroll state variable after callMessages is built.
old = """    val latestCallAssistantText = callMessages
        .lastOrNull { it.role == MessageRole.ASSISTANT }
        ?.toText()
        .orEmpty()
    val latestCallUserText = callMessages
        .lastOrNull { it.role == MessageRole.USER }
        ?.toText()
        .orEmpty()

    // Service 的 assistantText 是当前轮最可靠的流式文本；历史消息只作为页面重组后的兜底。
    val storySource = uiState.assistantText.ifBlank { latestCallAssistantText }
    val story = remember(storySource, companion.character.activity) {
        storyPresentation(storySource, companion.character.activity)
    }
    val visibleUserText = uiState.userTranscript.ifBlank {
        if (uiState.status == VoiceCallStatus.Processing) latestCallUserText else ""
    }
"""
new = """    val latestCallAssistantText = callMessages
        .lastOrNull { it.role == MessageRole.ASSISTANT }
        ?.toText()
        .orEmpty()
    val latestCallUserText = callMessages
        .lastOrNull { it.role == MessageRole.USER }
        ?.toText()
        .orEmpty()
    val dialogueScrollState = rememberScrollState()

    // Keep the live turn visible while it is streaming, while persisted callMessages retain
    // every completed user/assistant turn for manual scrolling.
    val liveAssistantText = uiState.assistantText
        .takeIf { it.isNotBlank() && it != latestCallAssistantText }
        .orEmpty()
    val liveUserText = uiState.userTranscript
        .takeIf { it.isNotBlank() && it != latestCallUserText }
        .orEmpty()

    LaunchedEffect(callMessages.size, liveAssistantText, liveUserText) {
        dialogueScrollState.animateScrollTo(dialogueScrollState.maxValue)
    }
"""
if old not in text:
    raise SystemExit('state block anchor not found')
text = text.replace(old, new, 1)

old_ui_start = """                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (story.action.isNotBlank()) {"""
new_ui_start = """                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(dialogueScrollState)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    callMessages.forEach { message ->
                        when (message.role) {
                            MessageRole.USER -> {
                                val text = message.toText()
                                if (text.isNotBlank()) {
                                    VideoCallUserBubble(text)
                                }
                            }
                            MessageRole.ASSISTANT -> {
                                val text = message.toText()
                                if (text.isNotBlank()) {
                                    val messageStory = storyPresentation(text, companion.character.activity)
                                    VideoCallAssistantBubble(
                                        displayName = displayName,
                                        story = messageStory,
                                    )
                                }
                            }
                            else -> Unit
                        }
                    }

                    if (liveUserText.isNotBlank()) {
                        VideoCallUserBubble(liveUserText)
                    }
                    if (liveAssistantText.isNotBlank()) {
                        VideoCallAssistantBubble(
                            displayName = displayName,
                            story = storyPresentation(liveAssistantText, companion.character.activity),
                        )
                    }
                }
            }

"""
if old_ui_start not in text:
    raise SystemExit('UI start anchor not found')
start = text.index(old_ui_start)
end_marker = """                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
            ) {"""
end = text.find(end_marker, start)
if end < 0:
    raise SystemExit('UI end anchor not found')
text = text[:start] + new_ui_start + text[end + len("""                }
            }

"""):]

# Append small reusable bubbles before VideoCallHeader.
anchor = """@Composable
private fun VideoCallHeader("""
helpers = """@Composable
private fun VideoCallUserBubble(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.82f),
            color = Color.White.copy(alpha = .16f),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
                Text("你", color = Color.White.copy(.56f), fontSize = 11.sp)
                Text(
                    text,
                    color = Color.White.copy(.92f),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun VideoCallAssistantBubble(
    displayName: String,
    story: StoryPresentation,
) {
    if (story.action.isNotBlank()) {
        Surface(
            modifier = Modifier.padding(top = 12.dp),
            color = Color.Black.copy(alpha = .26f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                story.action,
                color = Color.White.copy(.76f),
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }

    if (story.dialogue.isNotBlank()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            color = Color.Black.copy(alpha = .48f),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 15.dp)) {
                Text(
                    displayName,
                    color = Color.White.copy(.62f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    story.dialogue,
                    color = Color.White,
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun VideoCallHeader("""
if anchor not in text:
    raise SystemExit('helper anchor not found')
text = text.replace(anchor, helpers, 1)

# Preserve original newline convention and BOM.
text = text.replace('\r\n', '\n').replace('\n', newline)
out = text.encode('utf-8')
if has_bom:
    out = b'\xef\xbb\xbf' + out
p.write_bytes(out)
