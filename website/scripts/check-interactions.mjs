import fs from 'node:fs'
const source = fs.readFileSync(new URL('../src/App.tsx', import.meta.url), 'utf8') + fs.readFileSync(new URL('../src/MigratedPages.tsx', import.meta.url), 'utf8') + fs.readFileSync(new URL('../src/SecondBatchPages.tsx', import.meta.url), 'utf8')
const required = ['onClick={() => go(\'moments\')}', 'onClick={() => go(\'diary\')}', 'onClick={() => go(\'anniversaries\')}', 'chat/completions', 'localStorage.setItem', '确认绑定', '保存设置', '助手设置', '聊天记录', '搜索消息', '收藏消息', '快捷消息', '聊天统计', '导出全部数据', '服务商与模型', '提示词库', '进阶记忆', '显示与主题', '开始翻译']
const missing = required.filter(token => !source.includes(token))
if (missing.length) { console.error('Missing interactive paths:', missing); process.exit(1) }
const inertButtons = [...source.matchAll(/<button(?![^>]*(?:onClick|type=\"submit\"|type=\"button\"))[^>]*>/g)].map(match => match[0])
if (inertButtons.length) { console.error('Inert buttons:', inertButtons); process.exit(1) }
console.log('All primary web interactions are wired.')
