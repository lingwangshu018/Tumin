import { useState } from 'react'

type Page = 'chat' | 'couple' | 'settings' | 'about'

const REPOSITORY_URL = 'https://github.com/lingwangshu018/Tumin'
const DOWNLOAD_URL = `${REPOSITORY_URL}/releases/latest`
const ICON_URL = `${import.meta.env.BASE_URL}icon.png`

const conversations = [
  ['今天', '晚安故事与明日计划', '刚刚'],
  ['今天', '一起准备周末约会', '20:18'],
  ['昨天', '整理我们的旅行清单', '昨天'],
]

function Sidebar({ page, onPage, open, onClose }: { page: Page; onPage: (page: Page) => void; open: boolean; onClose: () => void }) {
  const go = (next: Page) => { onPage(next); onClose() }
  return (
    <>
      <button className={`drawer-scrim ${open ? 'show' : ''}`} aria-label="关闭菜单" onClick={onClose} />
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <div className="brand-row">
          <img src={ICON_URL} alt="兔眠 Tumin" />
          <div><strong>兔眠</strong><span>Tumin</span></div>
        </div>
        <button className="new-chat" onClick={() => go('chat')}><span>＋</span> 新对话</button>

        <div className="conversation-heading"><span>最近对话</span><button aria-label="搜索">⌕</button></div>
        <div className="conversation-list">
          {conversations.map(([group, title, time], index) => (
            <button className={`conversation ${index === 0 && page === 'chat' ? 'active' : ''}`} key={title} onClick={() => go('chat')}>
              <span className="conversation-dot" />
              <span><small>{group}</small><b>{title}</b></span><time>{time}</time>
            </button>
          ))}
        </div>

        <nav className="side-nav">
          <button className={page === 'couple' ? 'active' : ''} onClick={() => go('couple')}><span>♡</span> 情侣空间</button>
          <button className={page === 'settings' ? 'active' : ''} onClick={() => go('settings')}><span>⚙</span> 设置</button>
          <button className={page === 'about' ? 'active' : ''} onClick={() => go('about')}><span>ⓘ</span> 关于兔眠</button>
        </nav>
      </aside>
    </>
  )
}

function Topbar({ title, subtitle, onMenu }: { title: string; subtitle?: string; onMenu: () => void }) {
  return <header className="topbar">
    <button className="menu-button" onClick={onMenu} aria-label="打开菜单">☰</button>
    <div className="top-title"><strong>{title}</strong>{subtitle && <span>{subtitle}</span>}</div>
    <div className="top-actions"><button aria-label="搜索">⌕</button><button aria-label="更多">•••</button></div>
  </header>
}

function ChatPage({ onMenu }: { onMenu: () => void }) {
  const [draft, setDraft] = useState('')
  const [sent, setSent] = useState<string[]>([])
  const send = () => { if (draft.trim()) { setSent([...sent, draft.trim()]); setDraft('') } }
  return <section className="page chat-page">
    <Topbar title="小兔" subtitle="陪你生活的 AI 伴侣" onMenu={onMenu} />
    <div className="messages">
      <div className="date-pill">今天 22:36</div>
      <div className="message assistant-message">
        <img src={ICON_URL} alt="小兔" />
        <div><b>小兔</b><p>晚上好呀。今天过得怎么样？我已经把我们的纪念日和周末计划整理好了。</p></div>
      </div>
      <div className="message user-message"><p>今天有一点累，但看到你就好多了。</p></div>
      <div className="message assistant-message">
        <img src={ICON_URL} alt="小兔" />
        <div><b>小兔</b><p>那今晚就慢一点。要不要先写两句日记，再听一个短短的晚安故事？</p>
          <div className="suggestions"><button>写今日心情</button><button>讲晚安故事</button></div>
        </div>
      </div>
      {sent.map((text, index) => <div className="message user-message" key={index}><p>{text}</p></div>)}
    </div>
    <div className="composer-wrap">
      <div className="composer-tools"><button>＋</button><button>⌘</button><button>♡</button><span>小兔 · Tumin</span></div>
      <div className="composer"><textarea value={draft} onChange={e => setDraft(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() } }} placeholder="和小兔说点什么…" /><button onClick={send}>↑</button></div>
      <small>网页预览界面 · 完整 AI 对话请下载兔眠 APK</small>
    </div>
  </section>
}

function CouplePage({ onMenu }: { onMenu: () => void }) {
  return <section className="page soft-page">
    <Topbar title="情侣空间" subtitle="只属于你们两个人" onMenu={onMenu} />
    <div className="page-scroll couple-content">
      <div className="couple-hero">
        <div className="avatar-stack"><div className="person-avatar">你</div><span>♡</span><img src={ICON_URL} alt="小兔" /></div>
        <h1>你和小兔</h1><p>相伴的第 1 天，也会是往后很多很多天。</p>
        <div className="love-stats"><div><b>1</b><span>相伴天数</span></div><div><b>0</b><span>共同回忆</span></div><div><b>1</b><span>重要日子</span></div></div>
      </div>
      <div className="section-title"><div><b>我们的生活</b><span>一起记录每个温柔瞬间</span></div></div>
      <div className="feature-grid">
        <article className="feature-card moments"><span>♡</span><div><b>朋友圈</b><p>记录你和小兔的生活动态</p></div><i>›</i></article>
        <article className="feature-card diary"><span>▤</span><div><b>日记</b><p>写下个人与共同回忆</p></div><i>›</i></article>
        <article className="feature-card anniversary"><span>✦</span><div><b>纪念日</b><p>收藏每一个值得记住的日子</p></div><i>›</i></article>
      </div>
      <div className="memory-card"><span>今日</span><h3>给未来的我们</h3><p>“愿每一次打开兔眠，都能找到被认真记住的感觉。”</p></div>
    </div>
  </section>
}

function SettingsPage({ onMenu }: { onMenu: () => void }) {
  const rows = [['AI 模型与服务商', '连接你常用的模型'], ['助手设定', '名字、性格与记忆'], ['显示与主题', '颜色、字体和聊天背景'], ['数据与备份', '导入、导出与同步'], ['隐私与安全', '权限、应用锁和安全审计']]
  return <section className="page soft-page"><Topbar title="设置" subtitle="让兔眠更适合你" onMenu={onMenu} /><div className="page-scroll settings-content">
    <div className="profile-card"><img src={ICON_URL} alt="兔眠" /><div><b>兔眠 Tumin</b><span>版本 2.3.1</span></div><em>已就绪</em></div>
    <div className="settings-group">{rows.map(([title, text], index) => <button key={title}><span className={`setting-icon icon-${index}`}>{['✦','♙','◐','⇅','◇'][index]}</span><div><b>{title}</b><small>{text}</small></div><i>›</i></button>)}</div>
    <p className="settings-note">网页展示版不会读取你的手机数据。完整设置功能位于 Android 应用中。</p>
  </div></section>
}

function AboutPage({ onMenu }: { onMenu: () => void }) {
  return <section className="page soft-page"><Topbar title="关于兔眠" onMenu={onMenu} /><div className="page-scroll about-content">
    <img className="about-logo" src={ICON_URL} alt="兔眠 Tumin" /><h1>兔眠 <span>Tumin</span></h1><p className="about-lead">不止是聊天，更是生活在一起的 AI 伴侣。</p>
    <div className="about-actions"><a className="primary" href={DOWNLOAD_URL}>下载 Android APK</a><a href={REPOSITORY_URL}>查看 GitHub 源码</a></div>
    <div className="about-panel"><h2>兔眠能做什么</h2><div className="about-features"><span>长期记忆</span><span>情侣空间</span><span>生活工具</span><span>插件扩展</span><span>语音陪伴</span><span>本地数据</span></div></div>
    <div className="about-panel"><h2>开源与致谢</h2><p>兔眠基于 RikkaHub 二次开发。个人、非商业和小规模使用遵循仓库内的 AGPL 分段许可；商业用途请查看完整许可证。</p></div>
    <footer>兔眠 Tumin · Made with ♡</footer>
  </div></section>
}

export default function App() {
  const [page, setPage] = useState<Page>('chat')
  const [drawer, setDrawer] = useState(false)
  return <div className="app-shell">
    <Sidebar page={page} onPage={setPage} open={drawer} onClose={() => setDrawer(false)} />
    <main className="main-stage">
      {page === 'chat' && <ChatPage onMenu={() => setDrawer(true)} />}
      {page === 'couple' && <CouplePage onMenu={() => setDrawer(true)} />}
      {page === 'settings' && <SettingsPage onMenu={() => setDrawer(true)} />}
      {page === 'about' && <AboutPage onMenu={() => setDrawer(true)} />}
      <nav className="mobile-nav">
        <button className={page === 'chat' ? 'active' : ''} onClick={() => setPage('chat')}><span>⌂</span>聊天</button>
        <button className={page === 'couple' ? 'active' : ''} onClick={() => setPage('couple')}><span>♡</span>情侣空间</button>
        <button className={page === 'settings' ? 'active' : ''} onClick={() => setPage('settings')}><span>⚙</span>设置</button>
      </nav>
    </main>
  </div>
}
