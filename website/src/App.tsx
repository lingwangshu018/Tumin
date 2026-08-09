import { FormEvent, useEffect, useMemo, useState } from 'react'

type Page = 'chat' | 'couple' | 'moments' | 'diary' | 'anniversaries' | 'settings' | 'about'
type Message = { id: string; role: 'user' | 'assistant'; content: string }
type Moment = { id: string; content: string; createdAt: string; liked: boolean }
type Diary = { id: string; title: string; content: string; date: string }
type Anniversary = { id: string; title: string; date: string }
type Settings = { endpoint: string; apiKey: string; model: string; assistantName: string; partnerName: string; bound: boolean; startedAt: string }

const REPOSITORY_URL = 'https://github.com/lingwangshu018/Tumin'
const DOWNLOAD_URL = `${REPOSITORY_URL}/releases/latest`
const ICON_URL = `${import.meta.env.BASE_URL}icon.png`
const today = () => new Date().toISOString().slice(0, 10)
const uid = () => crypto.randomUUID?.() ?? `${Date.now()}-${Math.random()}`

const defaults: Settings = { endpoint: 'https://api.openai.com/v1', apiKey: '', model: 'gpt-4o-mini', assistantName: '小兔', partnerName: '恋人', bound: false, startedAt: today() }
function stored<T>(key: string, fallback: T): T { try { return JSON.parse(localStorage.getItem(key) || '') as T } catch { return fallback } }

function Topbar({ title, subtitle, onMenu, onBack }: { title: string; subtitle?: string; onMenu: () => void; onBack?: () => void }) {
  return <header className="topbar">
    <button className="menu-button" onClick={onBack ?? onMenu} aria-label={onBack ? '返回' : '打开菜单'}>{onBack ? '‹' : '☰'}</button>
    <div className="top-title"><strong>{title}</strong>{subtitle && <span>{subtitle}</span>}</div>
    <div className="top-actions"><a aria-label="查看源码" href={REPOSITORY_URL}>⌁</a></div>
  </header>
}

function Sidebar({ page, go, open, close, newChat }: { page: Page; go: (p: Page) => void; open: boolean; close: () => void; newChat: () => void }) {
  const nav = (p: Page) => { go(p); close() }
  return <>
    <button className={`drawer-scrim ${open ? 'show' : ''}`} aria-label="关闭菜单" onClick={close} />
    <aside className={`sidebar ${open ? 'open' : ''}`}>
      <div className="brand-row"><img src={ICON_URL} alt="兔眠" /><div><strong>兔眠</strong><span>Tumin</span></div></div>
      <button className="new-chat" onClick={() => { newChat(); nav('chat') }}><span>＋</span> 新对话</button>
      <div className="conversation-heading"><span>功能</span></div>
      <nav className="side-nav primary-nav">
        <button className={page === 'chat' ? 'active' : ''} onClick={() => nav('chat')}><span>⌂</span> 聊天</button>
        <button className={['couple','moments','diary','anniversaries'].includes(page) ? 'active' : ''} onClick={() => nav('couple')}><span>♡</span> 情侣空间</button>
        <button className={page === 'settings' ? 'active' : ''} onClick={() => nav('settings')}><span>⚙</span> 设置</button>
        <button className={page === 'about' ? 'active' : ''} onClick={() => nav('about')}><span>ⓘ</span> 关于兔眠</button>
      </nav>
    </aside>
  </>
}

function ChatPage({ settings, messages, setMessages, onMenu, goSettings }: { settings: Settings; messages: Message[]; setMessages: (m: Message[]) => void; onMenu: () => void; goSettings: () => void }) {
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const send = async (preset?: string) => {
    const content = (preset ?? draft).trim(); if (!content || busy) return
    const next = [...messages, { id: uid(), role: 'user' as const, content }]; setMessages(next); setDraft('')
    if (!settings.apiKey) { setMessages([...next, { id: uid(), role: 'assistant', content: '请先在“设置 → AI 模型与服务商”中填写 API 密钥，我才能真正回复你。' }]); return }
    setBusy(true)
    try {
      const response = await fetch(`${settings.endpoint.replace(/\/$/, '')}/chat/completions`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${settings.apiKey}` }, body: JSON.stringify({ model: settings.model, messages: [{ role: 'system', content: `你是${settings.assistantName}，兔眠中的温柔 AI 伴侣。` }, ...next.map(({ role, content: text }) => ({ role, content: text }))] }) })
      if (!response.ok) throw new Error(`请求失败（${response.status}）`)
      const data = await response.json(); const answer = data.choices?.[0]?.message?.content || '服务商没有返回文字。'
      setMessages([...next, { id: uid(), role: 'assistant', content: answer }])
    } catch (error) { setMessages([...next, { id: uid(), role: 'assistant', content: `连接失败：${error instanceof Error ? error.message : '未知错误'}。请检查地址、密钥和模型。` }]) } finally { setBusy(false) }
  }
  return <section className="page chat-page"><Topbar title={settings.assistantName} subtitle="陪你生活的 AI 伴侣" onMenu={onMenu} />
    <div className="messages"><div className="date-pill">今天</div>{messages.map(m => m.role === 'assistant'
      ? <div className="message assistant-message" key={m.id}><img src={ICON_URL} alt={settings.assistantName} /><div><b>{settings.assistantName}</b><p>{m.content}</p></div></div>
      : <div className="message user-message" key={m.id}><p>{m.content}</p></div>)}{busy && <div className="message assistant-message"><img src={ICON_URL} alt="" /><div><b>{settings.assistantName}</b><p>正在想……</p></div></div>}</div>
    <div className="composer-wrap"><div className="suggestions"><button onClick={() => send('陪我聊聊今天发生的事')}>聊聊今天</button><button onClick={() => send('给我讲一个温柔的晚安故事')}>晚安故事</button><button onClick={goSettings}>模型设置</button></div>
      <div className="composer"><textarea value={draft} onChange={e => setDraft(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() } }} placeholder={`和${settings.assistantName}说点什么…`} /><button disabled={busy || !draft.trim()} onClick={() => send()}>↑</button></div>
      <small>对话保存在当前浏览器中 · 密钥不会上传到兔眠服务器</small></div>
  </section>
}

function CoupleHome({ settings, saveSettings, go, onMenu }: { settings: Settings; saveSettings: (s: Settings) => void; go: (p: Page) => void; onMenu: () => void }) {
  const [name, setName] = useState(settings.partnerName)
  const days = Math.max(1, Math.floor((Date.now() - new Date(settings.startedAt).getTime()) / 86400000) + 1)
  if (!settings.bound) return <section className="page soft-page"><Topbar title="情侣空间" onMenu={onMenu} /><div className="page-scroll empty-state"><img src={ICON_URL} alt="" /><h1>绑定一个人作为恋人</h1><p>绑定后，朋友圈、日记和纪念日都会属于你们两个人。</p><form onSubmit={e => { e.preventDefault(); if (name.trim()) saveSettings({ ...settings, partnerName: name.trim(), bound: true, startedAt: today() }) }}><input value={name} onChange={e => setName(e.target.value)} placeholder="恋人的名字" /><button type="submit">确认绑定</button></form></div></section>
  return <section className="page soft-page"><Topbar title="情侣空间" subtitle="只属于你们两个人" onMenu={onMenu} /><div className="page-scroll couple-content">
    <div className="couple-hero"><div className="avatar-stack"><div className="person-avatar">你</div><span>♡</span><img src={ICON_URL} alt={settings.partnerName} /></div><h1>你和{settings.partnerName}</h1><p>相伴的第 {days} 天，也会是往后很多很多天。</p><div className="love-stats"><div><b>{days}</b><span>相伴天数</span></div><div><b>∞</b><span>共同回忆</span></div><div><b>♡</b><span>彼此珍藏</span></div></div></div>
    <div className="feature-grid"><button className="feature-card moments" onClick={() => go('moments')}><span>💞</span><div><b>朋友圈</b><p>记录你和恋人的生活动态</p></div><i>›</i></button><button className="feature-card diary" onClick={() => go('diary')}><span>📖</span><div><b>日记</b><p>写下个人与共同回忆</p></div><i>›</i></button><button className="feature-card anniversary" onClick={() => go('anniversaries')}><span>🎂</span><div><b>纪念日</b><p>收藏每一个值得记住的日子</p></div><i>›</i></button></div>
    <button className="unbind" onClick={() => confirm('确定解除当前恋人绑定吗？') && saveSettings({ ...settings, bound: false })}>解除绑定</button>
  </div></section>
}

function CollectionPage<T extends { id: string }>({ kind, items, setItems, onBack, onMenu }: { kind: 'moments' | 'diary' | 'anniversaries'; items: T[]; setItems: (v: T[]) => void; onBack: () => void; onMenu: () => void }) {
  const labels = { moments: ['朋友圈','发布动态'], diary: ['日记','写日记'], anniversaries: ['纪念日','添加纪念日'] } as const
  const [open, setOpen] = useState(false); const [first, setFirst] = useState(''); const [second, setSecond] = useState(''); const [date, setDate] = useState(today())
  const submit = (e: FormEvent) => { e.preventDefault(); if (!first.trim()) return
    const item = kind === 'moments' ? { id: uid(), content: first.trim(), createdAt: new Date().toISOString(), liked: false } : kind === 'diary' ? { id: uid(), title: first.trim(), content: second.trim(), date } : { id: uid(), title: first.trim(), date }
    setItems([item as T, ...items]); setFirst(''); setSecond(''); setOpen(false)
  }
  return <section className="page soft-page"><Topbar title={labels[kind][0]} onMenu={onMenu} onBack={onBack} /><div className="page-scroll collection-content"><button className="primary-action" onClick={() => setOpen(true)}>＋ {labels[kind][1]}</button>
    {!items.length && <div className="empty-list"><span>{kind === 'moments' ? '💞' : kind === 'diary' ? '📖' : '🎂'}</span><h2>这里还空空的</h2><p>点击上方按钮，留下第一条共同回忆。</p></div>}
    <div className="entry-list">{items.map((raw: any) => <article className="entry-card" key={raw.id}><div><small>{raw.date || new Date(raw.createdAt).toLocaleString('zh-CN')}</small><h3>{raw.title || raw.content}</h3>{raw.title && kind === 'diary' && <p>{raw.content}</p>}</div><div className="entry-actions">{kind === 'moments' && <button onClick={() => setItems(items.map((x: any) => x.id === raw.id ? { ...x, liked: !x.liked } : x))}>{raw.liked ? '♥ 已喜欢' : '♡ 喜欢'}</button>}<button onClick={() => confirm('确定删除这一条吗？') && setItems(items.filter(x => x.id !== raw.id))}>删除</button></div></article>)}</div>
    {open && <div className="modal-backdrop" onClick={() => setOpen(false)}><form className="editor-modal" onSubmit={submit} onClick={e => e.stopPropagation()}><h2>{labels[kind][1]}</h2><label>{kind === 'moments' ? '内容' : '标题'}<input autoFocus value={first} onChange={e => setFirst(e.target.value)} /></label>{kind === 'diary' && <label>正文<textarea value={second} onChange={e => setSecond(e.target.value)} /></label>}{kind !== 'moments' && <label>日期<input type="date" value={date} onChange={e => setDate(e.target.value)} /></label>}<div><button type="button" onClick={() => setOpen(false)}>取消</button><button type="submit">保存</button></div></form></div>}
  </div></section>
}

function SettingsPage({ value, save, onMenu }: { value: Settings; save: (s: Settings) => void; onMenu: () => void }) {
  const [form, setForm] = useState(value); const [saved, setSaved] = useState(false)
  return <section className="page soft-page"><Topbar title="设置" subtitle="网页端与 APK 使用相同的 OpenAI 兼容配置" onMenu={onMenu} /><div className="page-scroll settings-content"><div className="profile-card"><img src={ICON_URL} alt="兔眠" /><div><b>兔眠 Tumin</b><span>网页端</span></div><em>{saved ? '已保存' : '本地配置'}</em></div>
    <form className="settings-form" onSubmit={e => { e.preventDefault(); save(form); setSaved(true); setTimeout(() => setSaved(false), 1800) }}><h2>AI 模型与服务商</h2><label>API 地址<input value={form.endpoint} onChange={e => setForm({ ...form, endpoint: e.target.value })} placeholder="https://api.openai.com/v1" /></label><label>API 密钥<input type="password" value={form.apiKey} onChange={e => setForm({ ...form, apiKey: e.target.value })} placeholder="sk-…" /></label><label>模型 ID<input value={form.model} onChange={e => setForm({ ...form, model: e.target.value })} placeholder="gpt-4o-mini" /></label><h2>伴侣设定</h2><label>AI 名字<input value={form.assistantName} onChange={e => setForm({ ...form, assistantName: e.target.value })} /></label><button type="submit">保存设置</button></form>
    <p className="settings-note">设置与对话仅保存在这个浏览器。清除浏览器数据后会一起删除。</p></div></section>
}

function AboutPage({ onMenu }: { onMenu: () => void }) { return <section className="page soft-page"><Topbar title="关于兔眠" onMenu={onMenu} /><div className="page-scroll about-content"><img className="about-logo" src={ICON_URL} alt="兔眠" /><h1>兔眠 <span>Tumin</span></h1><p className="about-lead">不止是聊天，更是生活在一起的 AI 伴侣。</p><div className="about-actions"><a className="primary" href={DOWNLOAD_URL}>下载 Android APK</a><a href={REPOSITORY_URL}>查看 GitHub 源码</a></div></div></section> }

export default function App() {
  const [page, setPage] = useState<Page>('chat'); const [drawer, setDrawer] = useState(false)
  const [settings, setSettings] = useState(() => stored('tumin.settings', defaults))
  const [messages, setMessages] = useState<Message[]>(() => stored('tumin.messages', [{ id: 'welcome', role: 'assistant', content: '晚上好呀。今天过得怎么样？' }]))
  const [moments, setMoments] = useState<Moment[]>(() => stored('tumin.moments', [])); const [diaries, setDiaries] = useState<Diary[]>(() => stored('tumin.diaries', [])); const [anniversaries, setAnniversaries] = useState<Anniversary[]>(() => stored('tumin.anniversaries', []))
  useEffect(() => localStorage.setItem('tumin.settings', JSON.stringify(settings)), [settings]); useEffect(() => localStorage.setItem('tumin.messages', JSON.stringify(messages)), [messages]); useEffect(() => localStorage.setItem('tumin.moments', JSON.stringify(moments)), [moments]); useEffect(() => localStorage.setItem('tumin.diaries', JSON.stringify(diaries)), [diaries]); useEffect(() => localStorage.setItem('tumin.anniversaries', JSON.stringify(anniversaries)), [anniversaries])
  const menu = () => setDrawer(true); const back = () => setPage('couple')
  const body = useMemo(() => page === 'chat' ? <ChatPage settings={settings} messages={messages} setMessages={setMessages} onMenu={menu} goSettings={() => setPage('settings')} /> : page === 'couple' ? <CoupleHome settings={settings} saveSettings={setSettings} go={setPage} onMenu={menu} /> : page === 'moments' ? <CollectionPage kind="moments" items={moments} setItems={setMoments} onBack={back} onMenu={menu} /> : page === 'diary' ? <CollectionPage kind="diary" items={diaries} setItems={setDiaries} onBack={back} onMenu={menu} /> : page === 'anniversaries' ? <CollectionPage kind="anniversaries" items={anniversaries} setItems={setAnniversaries} onBack={back} onMenu={menu} /> : page === 'settings' ? <SettingsPage value={settings} save={setSettings} onMenu={menu} /> : <AboutPage onMenu={menu} />, [page, settings, messages, moments, diaries, anniversaries])
  return <div className="app-shell"><Sidebar page={page} go={setPage} open={drawer} close={() => setDrawer(false)} newChat={() => setMessages([{ id: uid(), role: 'assistant', content: '新的对话开始啦。今天想聊什么？' }])} /><main className="main-stage">{body}<nav className="mobile-nav"><button className={page === 'chat' ? 'active' : ''} onClick={() => setPage('chat')}><span>⌂</span>聊天</button><button className={['couple','moments','diary','anniversaries'].includes(page) ? 'active' : ''} onClick={() => setPage('couple')}><span>♡</span>情侣空间</button><button className={page === 'settings' ? 'active' : ''} onClick={() => setPage('settings')}><span>⚙</span>设置</button></nav></main></div>
}
