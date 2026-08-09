import { useReveal } from './useReveal'

const REPOSITORY_URL = 'https://github.com/lingwangshu018/orangechat'
const DOWNLOAD_URL = `${REPOSITORY_URL}/releases/latest`

function Navbar() {
  return (
    <nav className="fixed top-0 left-0 right-0 z-50 bg-white/80 backdrop-blur-xl border-b border-neutral-100">
      <div className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <img src="/icon.png" alt="橘瓣" className="w-8 h-8 rounded-xl" />
          <span className="font-bold text-lg text-neutral-900">橘瓣</span>
        </div>
        <div className="hidden md:flex items-center gap-8 text-sm text-neutral-600">
          <a href="#features" className="hover:text-brand transition-colors">独有功能</a>
          <a href="#plugins" className="hover:text-brand transition-colors">插件系统</a>
          <a href="#tools" className="hover:text-brand transition-colors">AI 工具</a>
          <a href="#architecture" className="hover:text-brand transition-colors">架构对比</a>
        </div>
        <a
          href={DOWNLOAD_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="px-5 py-2 bg-brand text-white text-sm font-medium rounded-full hover:bg-brand-dark transition-colors"
        >
          下载
        </a>
      </div>
    </nav>
  )
}

function Hero() {
  return (
    <section className="relative min-h-screen flex items-center justify-center overflow-hidden pt-16">
      {/* Background gradient */}
      <div className="absolute inset-0 bg-gradient-to-br from-orange-50 via-white to-amber-50" />
      <div className="absolute top-20 right-20 w-96 h-96 bg-brand/5 rounded-full blur-3xl" />
      <div className="absolute bottom-20 left-20 w-80 h-80 bg-orange-200/20 rounded-full blur-3xl" />

      {/* Floating decorative elements */}
      <div className="absolute top-32 left-[15%] text-5xl animate-float" style={{ animationDelay: '0s' }}>🍊</div>
      <div className="absolute top-48 right-[20%] text-4xl animate-float" style={{ animationDelay: '1s' }}>💬</div>
      <div className="absolute bottom-32 left-[25%] text-4xl animate-float" style={{ animationDelay: '0.5s' }}>🧩</div>
      <div className="absolute bottom-48 right-[15%] text-5xl animate-float" style={{ animationDelay: '1.5s' }}>⌚</div>

      <div className="relative z-10 text-center px-6 max-w-4xl mx-auto">
        <div className="animate-fade-in-up">
          <img
            src="/icon.png"
            alt="橘瓣"
            className="w-28 h-28 rounded-3xl mx-auto mb-8 animate-pulse-glow"
          />
        </div>
        <h1
          className="text-5xl md:text-7xl font-extrabold text-neutral-900 mb-4 animate-fade-in-up"
          style={{ animationDelay: '0.1s' }}
        >
          橘瓣 <span className="text-brand">OrangeChat</span>
        </h1>
        <p
          className="text-xl md:text-2xl text-neutral-600 mb-6 animate-fade-in-up"
          style={{ animationDelay: '0.2s' }}
        >
          不止是聊天 · 更是<strong className="text-brand">生活在一起</strong>的 AI 伴侣平台
        </p>
        <p
          className="text-base text-neutral-500 mb-10 max-w-2xl mx-auto animate-fade-in-up"
          style={{ animationDelay: '0.3s' }}
        >
          基于 RikkaHub 深度定制，在原生聊天体验之上，构建了完整的插件生态与智能生活服务
        </p>
        <div
          className="flex flex-col sm:flex-row gap-4 justify-center animate-fade-in-up"
          style={{ animationDelay: '0.4s' }}
        >
          <a
            href={DOWNLOAD_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 px-8 py-4 bg-brand text-white font-semibold rounded-full hover:bg-brand-dark transition-all hover:scale-105 shadow-lg shadow-brand/25"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
            </svg>
            下载橘瓣
          </a>
          <a
            href={REPOSITORY_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 px-8 py-4 bg-neutral-900 text-white font-semibold rounded-full hover:bg-neutral-800 transition-all hover:scale-105"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
              <path d="M3.609 1.814L13.792 12 3.61 22.186a.996.996 0 01-.61-.92V2.734a1 1 0 01.609-.92zm10.89 10.893l2.302 2.302-10.937 6.333 8.635-8.635zm3.199-3.199l2.302 1.33a1 1 0 010 1.724l-2.302 1.33-2.543-2.543 2.543-2.541zM5.864 2.658L16.8 8.99l-2.302 2.303-8.634-8.635z"/>
            </svg>
            查看源码
          </a>
        </div>

        {/* Screenshots preview */}
        <div
          className="mt-16 flex justify-center gap-4 animate-fade-in-up"
          style={{ animationDelay: '0.5s' }}
        >
          <img src="/img/chat.png" alt="聊天界面" className="h-72 md:h-80 rounded-2xl shadow-2xl shadow-neutral-200/50 border border-neutral-200/50" />
          <img src="/img/desktop.png" alt="模型选择" className="hidden md:block h-72 md:h-80 rounded-2xl shadow-2xl shadow-neutral-200/50 border border-neutral-200/50" />
          <img src="/img/assistants.png" alt="智能体" className="h-72 md:h-80 rounded-2xl shadow-2xl shadow-neutral-200/50 border border-neutral-200/50" />
        </div>
      </div>
    </section>
  )
}

function Philosophy() {
  const ref = useReveal()
  return (
    <section ref={ref} className="py-24 md:py-32 bg-neutral-950 text-white relative overflow-hidden">
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_rgba(255,107,0,0.1)_0%,_transparent_70%)]" />
      <div className="relative z-10 max-w-3xl mx-auto px-6 text-center">
        <div className="reveal">
          <span className="inline-block px-4 py-1.5 bg-brand/10 text-brand rounded-full text-sm font-medium mb-8">
            🍊 为什么做橘瓣
          </span>
        </div>
        <blockquote className="reveal text-2xl md:text-3xl leading-relaxed font-light text-neutral-200">
          <p className="mb-6">
            AI 不应该只活在对话框里。
          </p>
          <p className="mb-6">
            它应该知道你在哪、知道你今天走了多少步、<br className="hidden md:block" />
            知道你昨晚几点睡的。
          </p>
          <p className="mb-6">
            它应该能陪你一起看书、一起养宠物、<br className="hidden md:block" />
            一起经营一家小商店。
          </p>
          <p className="mb-8">
            它应该能记住你说过的每一句重要的话，<br className="hidden md:block" />
            在你需要的时候主动出现。
          </p>
          <p className="text-brand font-semibold text-xl">
            橘瓣就是这样一个尝试。
          </p>
        </blockquote>
      </div>
    </section>
  )
}

const uniqueFeatures = [
  {
    icon: '💕',
    title: '情侣空间',
    subtitle: '和 TA 生活在一起',
    description: '绑定一个现有 AI 助手作为恋人，在专属空间记录恋爱天数、朋友圈、日记和纪念日。',
    highlight: '朋友圈 + 日记 + 纪念日'
  },
  {
    icon: '🧩',
    title: '插件系统',
    subtitle: '核心能力',
    description: '基于 QuickJS 沙箱的完整插件框架，让 JS 插件能安全执行并调用宿主能力。支持声明式 UI、WebView 页面、事件钩子等高级功能。',
    highlight: '7 个内置插件'
  },
  {
    icon: '📍',
    title: '生活感知',
    subtitle: '位置与生活',
    description: 'AI 可获取你的当前位置，搜索附近餐饮商店，读取应用使用统计，甚至调用摄像头拍照——真正融入你的生活。',
    highlight: '4 项生活能力'
  },
  {
    icon: '⌚',
    title: '健康数据',
    subtitle: '身体感知',
    description: '通过 Gadgetbridge 同步智能手环/手表的健康数据——步数、心率、睡眠等，让 AI 了解你的身体状况。',
    highlight: 'Gadgetbridge'
  },
  {
    icon: '🧠',
    title: '记忆系统',
    subtitle: '永不遗忘',
    description: 'HNSW 向量索引实现语义检索，记忆银行管理完整生命周期，Supabase 云端同步，还有每日自动总结。',
    highlight: '向量索引 + 记忆银行'
  },
  {
    icon: '💌',
    title: '主动消息',
    subtitle: 'AI 找你',
    description: 'AI 不只是被动等你说话。它会在你需要的时候主动出现——提醒你吃饭、告诉你该睡了、或者突然说一句想你了。',
    highlight: '从被动到主动'
  },
  {
    icon: '🎨',
    title: '个性化定制',
    subtitle: '你的风格',
    description: '头像框、气泡透明度、思维链样式、输入框背景、字体包导入——每一处细节都可以按你的喜好调整。',
    highlight: '5 项个性化'
  },
]

function UniqueFeatures() {
  const ref = useReveal()
  return (
    <section id="features" ref={ref} className="py-24 md:py-32 bg-surface">
      <div className="max-w-6xl mx-auto px-6">
        <div className="text-center mb-16 reveal">
          <span className="inline-block px-4 py-1.5 bg-brand/10 text-brand rounded-full text-sm font-medium mb-4">
            ✨ 橘瓣独有
          </span>
          <h2 className="text-3xl md:text-4xl font-bold text-neutral-900 mb-4">
            与 RikkaHub 不一样的地方
          </h2>
          <p className="text-neutral-500 max-w-2xl mx-auto">
            在原生聊天体验之上，我们构建了完整的插件生态与智能生活服务
          </p>
        </div>
        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
          {uniqueFeatures.map((feature, i) => (
            <div
              key={i}
              className="reveal group relative bg-card rounded-2xl p-8 border border-neutral-100 hover:border-brand/20 hover:shadow-xl hover:shadow-brand/5 transition-all duration-300"
              style={{ transitionDelay: `${i * 80}ms` }}
            >
              <div className="text-4xl mb-4">{feature.icon}</div>
              <div className="text-xs font-medium text-brand mb-2 uppercase tracking-wider">{feature.subtitle}</div>
              <h3 className="text-xl font-bold text-neutral-900 mb-3">{feature.title}</h3>
              <p className="text-neutral-500 text-sm leading-relaxed mb-4">{feature.description}</p>
              <span className="inline-block px-3 py-1 bg-orange-50 text-brand text-xs font-medium rounded-full">
                {feature.highlight}
              </span>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

const builtInPlugins = [
  { icon: '🌤️', name: 'Weather', desc: '天气查询插件，调用 wttr.in API', tag: '入门级' },
  { icon: '🍜', name: 'What to Eat', desc: '今天吃什么？随机推荐美食', tag: '最简插件' },
  { icon: '📖', name: '共读', desc: '和 AI 一起阅读，含阅读器 UI', tag: '高级 · 含 UI' },
  { icon: '📱', name: 'Moments', desc: '朋友圈，AI 社交动态', tag: '高级 · 含 Supabase' },
  { icon: '💾', name: 'Supabase Memory', desc: '云端长期记忆存储', tag: '高级 · 含 memoryBank' },
  { icon: '🛡️', name: 'Purify Backup', desc: '数据净化备份，去除敏感信息', tag: '实用工具' },
  { icon: '📚', name: 'Plugin Guide', desc: '插件开发文档工具（13 个主题）', tag: '开发辅助' },
]

function PluginSystem() {
  const ref = useReveal()
  return (
    <section id="plugins" ref={ref} className="py-24 md:py-32 bg-white">
      <div className="max-w-6xl mx-auto px-6">
        <div className="text-center mb-16 reveal">
          <span className="inline-block px-4 py-1.5 bg-brand/10 text-brand rounded-full text-sm font-medium mb-4">
            🧩 核心
          </span>
          <h2 className="text-3xl md:text-4xl font-bold text-neutral-900 mb-4">
            插件系统
          </h2>
          <p className="text-neutral-500 max-w-2xl mx-auto">
            基于 QuickJS 沙箱的完整插件框架，让 JS 插件能安全执行并调用宿主能力
          </p>
        </div>

        <div className="grid lg:grid-cols-2 gap-12 items-start">
          {/* Plugin architecture */}
          <div className="reveal">
            <h3 className="text-lg font-bold text-neutral-900 mb-6 flex items-center gap-2">
              <span className="w-2 h-2 bg-brand rounded-full"></span>
              插件架构
            </h3>
            <div className="bg-neutral-950 rounded-2xl p-6 text-sm font-mono text-neutral-300 overflow-x-auto">
              <pre>{`plugin/
├── loader/
│   ├── PluginLoader.kt       # 插件加载器
│   └── PluginSandbox.kt      # QuickJS 沙箱
├── manager/PluginManager.kt  # 生命周期管理
├── model/
│   ├── PluginManifest.kt     # manifest 解析
│   └── PluginUI.kt           # 声明式 UI
├── provider/PluginToolProvider.kt
├── scanner/PluginScanner.kt
└── ui/
    ├── PluginDetailPage.kt
    ├── PluginManagePage.kt
    ├── PluginUIDeclarativePage.kt
    └── PluginWebViewPage.kt`}</pre>
            </div>

            <h3 className="text-lg font-bold text-neutral-900 mt-8 mb-4 flex items-center gap-2">
              <span className="w-2 h-2 bg-brand rounded-full"></span>
              开发示例
            </h3>
            <div className="bg-neutral-950 rounded-2xl p-6 text-sm font-mono text-neutral-300 overflow-x-auto">
              <pre>{`// manifest.json
{
  "id": "com.example.hello",
  "name": "打招呼",
  "tools": [{
    "name": "say_hello",
    "parameters": [
      { "name": "name", "type": "string" }
    ]
  }]
}

// main.js
function say_hello(params) {
  return { success: true,
    message: 'Hello, ' + params.name + '!' };
}
exports.say_hello = say_hello;`}</pre>
            </div>
          </div>

          {/* Built-in plugins */}
          <div className="reveal">
            <h3 className="text-lg font-bold text-neutral-900 mb-6 flex items-center gap-2">
              <span className="w-2 h-2 bg-brand rounded-full"></span>
              内置插件
            </h3>
            <div className="space-y-4">
              {builtInPlugins.map((plugin, i) => (
                <div
                  key={i}
                  className="flex items-start gap-4 p-4 bg-surface rounded-xl border border-neutral-100 hover:border-brand/20 hover:shadow-md transition-all"
                >
                  <span className="text-2xl">{plugin.icon}</span>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-semibold text-neutral-900">{plugin.name}</span>
                      <span className="px-2 py-0.5 bg-orange-50 text-brand text-xs rounded-full">{plugin.tag}</span>
                    </div>
                    <p className="text-sm text-neutral-500">{plugin.desc}</p>
                  </div>
                </div>
              ))}
            </div>

            <h3 className="text-lg font-bold text-neutral-900 mt-8 mb-4 flex items-center gap-2">
              <span className="w-2 h-2 bg-brand rounded-full"></span>
              沙箱内置 API
            </h3>
            <div className="grid grid-cols-1 gap-2">
              {[
                { api: 'fetch(url, options)', desc: '同步 HTTP 请求' },
                { api: 'memoryBank.recall(query)', desc: '语义搜索记忆' },
                { api: 'memoryBank.save(content)', desc: '保存记忆' },
                { api: 'config', desc: '用户配置值' },
                { api: 'console.log/info/warn', desc: '输出到 Logcat' },
              ].map((item, i) => (
                <div key={i} className="flex items-center gap-3 p-3 bg-neutral-50 rounded-lg">
                  <code className="text-xs font-mono text-brand bg-orange-50 px-2 py-1 rounded">{item.api}</code>
                  <span className="text-xs text-neutral-500">{item.desc}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

const tools = [
  { icon: '⏰', name: 'AlarmTool', desc: '闹钟管理' },
  { icon: '📱', name: 'AppUsageTool', desc: 'App 使用情况' },
  { icon: '🔋', name: 'BatteryTool', desc: '电池状态' },
  { icon: '📅', name: 'CalendarTool', desc: '日历读写' },
  { icon: '📸', name: 'CameraTool', desc: '拍照' },
  { icon: '📍', name: 'ExploreNearbyTool', desc: '附近搜索' },
  { icon: '⌚', name: 'GadgetbridgeTool', desc: '健康数据' },
  { icon: '🎵', name: 'MusicTool', desc: '音乐控制' },
  { icon: '💬', name: 'SmsTool', desc: '短信读取' },
  { icon: '⚙️', name: 'SystemTools', desc: '系统信息' },
  { icon: '📦', name: 'ZipFilesTool', desc: '文件打包' },
]

function ToolsSection() {
  const ref = useReveal()
  return (
    <section id="tools" ref={ref} className="py-24 md:py-32 bg-surface">
      <div className="max-w-6xl mx-auto px-6">
        <div className="text-center mb-16 reveal">
          <span className="inline-block px-4 py-1.5 bg-brand/10 text-brand rounded-full text-sm font-medium mb-4">
            🛠️ 扩展
          </span>
          <h2 className="text-3xl md:text-4xl font-bold text-neutral-900 mb-4">
            丰富的内置工具
          </h2>
          <p className="text-neutral-500 max-w-2xl mx-auto">
            在原版 4 个工具基础上新增 11 个，让 AI 真正与你的设备深度交互
          </p>
        </div>

        {/* Comparison bar */}
        <div className="reveal max-w-2xl mx-auto mb-12">
          <div className="flex items-center justify-between mb-3 text-sm">
            <span className="text-neutral-500">RikkaHub</span>
            <span className="font-bold text-neutral-900">4 个工具</span>
          </div>
          <div className="h-4 bg-neutral-200 rounded-full overflow-hidden mb-6">
            <div className="h-full bg-neutral-400 rounded-full" style={{ width: '27%' }}></div>
          </div>
          <div className="flex items-center justify-between mb-3 text-sm">
            <span className="text-brand font-medium">橘瓣 OrangeChat</span>
            <span className="font-bold text-brand">15 个工具 (+11)</span>
          </div>
          <div className="h-4 bg-orange-100 rounded-full overflow-hidden">
            <div className="h-full bg-gradient-to-r from-brand to-brand-light rounded-full animate-gradient" style={{ width: '100%' }}></div>
          </div>
        </div>

        {/* Tools grid */}
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
          {tools.map((tool, i) => (
            <div
              key={i}
              className="reveal group bg-card p-4 rounded-xl border border-neutral-100 hover:border-brand/20 hover:shadow-lg hover:shadow-brand/5 text-center transition-all duration-300"
              style={{ transitionDelay: `${i * 50}ms` }}
            >
              <div className="text-3xl mb-2">{tool.icon}</div>
              <div className="text-xs font-semibold text-neutral-900 mb-1 truncate">{tool.name}</div>
              <div className="text-xs text-neutral-400">{tool.desc}</div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

function ArchitectureComparison() {
  const ref = useReveal()
  return (
    <section id="architecture" ref={ref} className="py-24 md:py-32 bg-white">
      <div className="max-w-6xl mx-auto px-6">
        <div className="text-center mb-16 reveal">
          <span className="inline-block px-4 py-1.5 bg-brand/10 text-brand rounded-full text-sm font-medium mb-4">
            🏗️ 对比
          </span>
          <h2 className="text-3xl md:text-4xl font-bold text-neutral-900 mb-4">
            架构对比
          </h2>
          <p className="text-neutral-500 max-w-2xl mx-auto">
            与原版 RikkaHub 的代码差异，橘瓣做了大量扩展
          </p>
        </div>

        <div className="grid md:grid-cols-2 gap-8 reveal">
          {/* RikkaHub */}
          <div className="bg-neutral-50 rounded-2xl p-8 border border-neutral-200">
            <div className="flex items-center gap-3 mb-6">
              <div className="w-10 h-10 bg-neutral-300 rounded-xl flex items-center justify-center text-white font-bold text-sm">R</div>
              <div>
                <h3 className="font-bold text-neutral-900">RikkaHub</h3>
                <p className="text-xs text-neutral-400">原版</p>
              </div>
            </div>
            <div className="space-y-4">
              <div>
                <div className="flex items-center justify-between text-sm mb-2">
                  <span className="text-neutral-600">Services</span>
                  <span className="font-bold text-neutral-400">3</span>
                </div>
                <div className="space-y-1">
                  <div className="px-3 py-1.5 bg-neutral-200 rounded-lg text-xs text-neutral-600">ChatService</div>
                  <div className="px-3 py-1.5 bg-neutral-200 rounded-lg text-xs text-neutral-600">ConversationSession</div>
                  <div className="px-3 py-1.5 bg-neutral-200 rounded-lg text-xs text-neutral-600">WebServerService</div>
                </div>
              </div>
              <div>
                <div className="flex items-center justify-between text-sm mb-2">
                  <span className="text-neutral-600">AI Tools</span>
                  <span className="font-bold text-neutral-400">4</span>
                </div>
              </div>
              <div>
                <div className="flex items-center justify-between text-sm mb-2">
                  <span className="text-neutral-600">插件系统</span>
                  <span className="font-bold text-neutral-400">无</span>
                </div>
              </div>
            </div>
          </div>

          {/* OrangeChat */}
          <div className="bg-gradient-to-br from-orange-50 to-amber-50 rounded-2xl p-8 border border-brand/20 shadow-lg shadow-brand/5">
            <div className="flex items-center gap-3 mb-6">
              <img src="/icon.png" alt="橘瓣" className="w-10 h-10 rounded-xl" />
              <div>
                <h3 className="font-bold text-neutral-900">橘瓣 OrangeChat</h3>
                <p className="text-xs text-brand">增强版</p>
              </div>
            </div>
            <div className="space-y-4">
              <div>
                <div className="flex items-center justify-between text-sm mb-2">
                  <span className="text-neutral-600">Services</span>
                  <span className="font-bold text-brand">18 (+15) 🚀</span>
                </div>
                <div className="space-y-1">
                  <div className="px-3 py-1.5 bg-white/60 rounded-lg text-xs text-neutral-600">ChatService <span className="text-brand">增强</span></div>
                  <div className="px-3 py-1.5 bg-white/60 rounded-lg text-xs text-neutral-600">ConversationSession</div>
                  <div className="px-3 py-1.5 bg-white/60 rounded-lg text-xs text-neutral-600">WebServerService</div>
                  <div className="px-3 py-1.5 bg-brand/10 rounded-lg text-xs text-brand font-medium">AmapService ★</div>
                  <div className="px-3 py-1.5 bg-brand/10 rounded-lg text-xs text-brand font-medium">LocationService ★</div>
                  <div className="px-3 py-1.5 bg-brand/10 rounded-lg text-xs text-brand font-medium">GadgetbridgeService ★</div>
                  <div className="px-3 py-1.5 bg-brand/10 rounded-lg text-xs text-brand font-medium">SupabaseSyncService ★</div>
                  <div className="px-3 py-1.5 bg-brand/10 rounded-lg text-xs text-brand font-medium">ProactiveMessageService ★</div>
                  <div className="px-3 py-1.5 bg-neutral-200/50 rounded-lg text-xs text-neutral-400">... +10 more</div>
                </div>
              </div>
              <div>
                <div className="flex items-center justify-between text-sm mb-2">
                  <span className="text-neutral-600">AI Tools</span>
                  <span className="font-bold text-brand">15 (+11) 🔧</span>
                </div>
              </div>
              <div>
                <div className="flex items-center justify-between text-sm mb-2">
                  <span className="text-neutral-600">插件系统</span>
                  <span className="font-bold text-brand">★★★ 完整框架</span>
                </div>
                <div className="px-3 py-1.5 bg-brand/10 rounded-lg text-xs text-brand font-medium">
                  QuickJS Sandbox · PluginLoader · 7 内置插件
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

function DownloadSection() {
  const ref = useReveal()
  return (
    <section ref={ref} className="py-24 md:py-32 bg-gradient-to-br from-neutral-950 via-neutral-900 to-neutral-950 text-white relative overflow-hidden">
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_rgba(255,107,0,0.15)_0%,_transparent_60%)]" />
      <div className="relative z-10 max-w-3xl mx-auto px-6 text-center">
        <div className="reveal">
          <img src="/icon.png" alt="橘瓣" className="w-20 h-20 rounded-2xl mx-auto mb-8 animate-pulse-glow" />
          <h2 className="text-3xl md:text-5xl font-bold mb-4">
            开始使用<span className="text-brand">橘瓣</span>
          </h2>
          <p className="text-neutral-400 mb-10 text-lg">
            让 AI 不止活在对话框里
          </p>
          <div className="flex flex-col sm:flex-row gap-4 justify-center">
            <a
              href={DOWNLOAD_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center justify-center gap-2 px-8 py-4 bg-brand text-white font-semibold rounded-full hover:bg-brand-dark transition-all hover:scale-105 shadow-lg shadow-brand/30 text-lg"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
              </svg>
              下载最新版 APK
            </a>
            <a
              href={REPOSITORY_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center justify-center gap-2 px-8 py-4 bg-white/10 text-white font-semibold rounded-full hover:bg-white/20 transition-all hover:scale-105 border border-white/10 text-lg"
            >
              <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
                <path d="M3.609 1.814L13.792 12 3.61 22.186a.996.996 0 01-.61-.92V2.734a1 1 0 01.609-.92zm10.89 10.893l2.302 2.302-10.937 6.333 8.635-8.635zm3.199-3.199l2.302 1.33a1 1 0 010 1.724l-2.302 1.33-2.543-2.543 2.543-2.541zM5.864 2.658L16.8 8.99l-2.302 2.303-8.634-8.635z"/>
              </svg>
              GitHub 源码
            </a>
          </div>
          <div className="mt-8 flex items-center justify-center gap-6 text-sm text-neutral-500">
            <span className="flex items-center gap-1.5">
              <svg className="w-4 h-4 text-green-500" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd"/>
              </svg>
              Android 8.0+
            </span>
            <span className="flex items-center gap-1.5">
              <svg className="w-4 h-4 text-green-500" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd"/>
              </svg>
              开源免费
            </span>
            <span className="flex items-center gap-1.5">
              <svg className="w-4 h-4 text-green-500" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd"/>
              </svg>
              AGPL v3 / 商业许可
            </span>
          </div>
        </div>
      </div>
    </section>
  )
}

function Footer() {
  return (
    <footer className="bg-neutral-950 text-neutral-400 py-12 border-t border-neutral-800">
      <div className="max-w-6xl mx-auto px-6">
        <div className="flex flex-col md:flex-row items-center justify-between gap-6">
          <div className="flex items-center gap-3">
            <img src="/icon.png" alt="橘瓣" className="w-8 h-8 rounded-lg" />
            <div>
              <span className="text-white font-semibold">橘瓣 OrangeChat</span>
              <p className="text-xs text-neutral-500">让 AI 不止活在对话框里</p>
            </div>
          </div>
          <div className="flex items-center gap-6 text-sm">
            <a
              href={REPOSITORY_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-white transition-colors flex items-center gap-1.5"
            >
              <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
              </svg>
              GitHub
            </a>
            <a
              href={DOWNLOAD_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-white transition-colors"
            >
              下载
            </a>
            <a
              href="https://github.com/rikkahub/rikkahub"
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-white transition-colors"
            >
              RikkaHub
            </a>
          </div>
        </div>
        <div className="mt-8 pt-6 border-t border-neutral-800 text-center text-xs text-neutral-600">
          <p>橘瓣 OrangeChat · 遵循仓库分段双重许可 · 致谢 RikkaHub</p>
        </div>
      </div>
    </footer>
  )
}

export default function App() {
  return (
    <div className="bg-white text-neutral-900">
      <Navbar />
      <Hero />
      <Philosophy />
      <UniqueFeatures />
      <PluginSystem />
      <ToolsSection />
      <ArchitectureComparison />
      <DownloadSection />
      <Footer />
    </div>
  )
}