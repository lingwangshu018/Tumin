import fs from 'node:fs'
const source = fs.readFileSync(new URL('../src/App.tsx', import.meta.url), 'utf8')
const required = ['onClick={() => go(\'moments\')}', 'onClick={() => go(\'diary\')}', 'onClick={() => go(\'anniversaries\')}', 'chat/completions', 'localStorage.setItem', '确认绑定', '保存设置']
const missing = required.filter(token => !source.includes(token))
if (missing.length) { console.error('Missing interactive paths:', missing); process.exit(1) }
const inertButtons = [...source.matchAll(/<button(?![^>]*(?:onClick|type=\"submit\"|type=\"button\"))[^>]*>/g)].map(match => match[0])
if (inertButtons.length) { console.error('Inert buttons:', inertButtons); process.exit(1) }
console.log('All primary web interactions are wired.')
