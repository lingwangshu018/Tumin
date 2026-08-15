from pathlib import Path

p = Path('app/src/main/java/me/rerere/rikkahub/ui/pages/voice/VideoCallPage.kt')
s = p.read_text(encoding='utf-8-sig')

if 'import androidx.compose.foundation.layout.imePadding' not in s:
    s = s.replace(
        'import androidx.compose.foundation.layout.height\n',
        'import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.imePadding\n',
        1,
    )

old = """            OutlinedTextField(
                value = typedInput,
                onValueChange = { typedInput = it },
                modifier = Modifier.fillMaxWidth(),"""
new = """            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
            ) {
                OutlinedTextField(
                    value = typedInput,
                    onValueChange = { typedInput = it },
                    modifier = Modifier.fillMaxWidth(),"""
if old not in s:
    raise SystemExit('input field anchor not found')
s = s.replace(old, new, 1)

old2 = """            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {"""
new2 = """                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {"""
if old2 not in s:
    raise SystemExit('button row anchor not found')
s = s.replace(old2, new2, 1)

old3 = """                CallButton(HugeIcons.Cancel01, \"挂断\", Color(0xFFE5484D)) {
                    service?.endCall()
                    VoiceCallService.stop(context)
                    onBack()
                }
            }
        }
"""
new3 = """                CallButton(HugeIcons.Cancel01, \"挂断\", Color(0xFFE5484D)) {
                    service?.endCall()
                    VoiceCallService.stop(context)
                    onBack()
                }
                }
            }
        }
"""
if old3 not in s:
    raise SystemExit('closing anchor not found')
s = s.replace(old3, new3, 1)

p.write_text(s, encoding='utf-8')
