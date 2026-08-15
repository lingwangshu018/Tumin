from pathlib import Path

p = Path('app/src/main/java/me/rerere/rikkahub/ui/pages/voice/VideoCallPage.kt')
raw = p.read_bytes()
has_bom = raw.startswith(b'\xef\xbb\xbf')
text = raw.decode('utf-8-sig')
newline = '\r\n' if '\r\n' in text else '\n'

old = """    LaunchedEffect(callMessages.size, liveAssistantText, liveUserText) {
        dialogueScrollState.animateScrollTo(dialogueScrollState.maxValue)
    }
"""
new = """    LaunchedEffect(callMessages.size, liveAssistantText, liveUserText) {
        // Follow new turns only while the viewer is already near the bottom. If they scroll up
        // to reread earlier video-call messages, streaming output must not steal the scroll.
        val distanceFromBottom = dialogueScrollState.maxValue - dialogueScrollState.value
        if (distanceFromBottom <= 160) {
            dialogueScrollState.animateScrollTo(dialogueScrollState.maxValue)
        }
    }
"""
if old not in text:
    raise SystemExit('scroll effect anchor not found')
text = text.replace(old, new, 1)

text = text.replace('\r\n', '\n').replace('\n', newline)
out = text.encode('utf-8')
if has_bom:
    out = b'\xef\xbb\xbf' + out
p.write_bytes(out)
