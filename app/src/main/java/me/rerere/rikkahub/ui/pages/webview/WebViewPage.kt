/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.pages.webview

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Bug01
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.Refresh01
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.rikkahub.data.memory.FloatMemoryExchangeCache
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.WebViewState
import me.rerere.rikkahub.ui.components.webview.rememberWebViewState
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.utils.base64Decode
import org.json.JSONObject

private const val FLOAT_PORTAL_MARKER = "tumin://float"
private const val FLOAT_PORTAL_SETTINGS = "tumin_float_portal_v1"
private const val FLOAT_PORTAL_URL_KEY = "url"
private const val FLOAT_PORTAL_DATA = "plugin_data_float_isekai"
private const val FLOAT_MANIFEST_KEY = "__float_memory_bridge_manifest_v1"

private val KAOMIANJIN_BOOTSTRAP_JS = """
    (function () {
      try {
        if (!window.localStorage || !localStorage.getItem('ai_os_characters')) return;
        if (window.__tuminKaomianjinBootstrapping) return;
        window.__tuminKaomianjinBootstrapping = true;

        function loadScript(path) {
          return new Promise(function (resolve, reject) {
            var script = document.createElement('script');
            script.src = new URL(path, location.href).href;
            script.onload = resolve;
            script.onerror = reject;
            document.head.appendChild(script);
          });
        }

        Promise.resolve()
          .then(function () {
            return window.WorldBridge ? null : loadScript('world-bridge.js');
          })
          .then(function () {
            return window.KaomianjinMemoryInterop ? null : loadScript('kaomianjin-memory-interop.js');
          })
          .then(function () {
            if (!window.KaomianjinMemoryInterop) return null;
            return window.KaomianjinMemoryInterop.syncNow({ recentLimit: 20, longTermLimit: 50 });
          })
          .catch(function (error) {
            console.warn('[Tumin] kaomianjin memory bridge bootstrap failed', error);
          })
          .finally(function () {
            window.__tuminKaomianjinBootstrapping = false;
          });
      } catch (error) {
        window.__tuminKaomianjinBootstrapping = false;
        console.warn('[Tumin] kaomianjin memory bridge bootstrap failed', error);
      }
    })();
""".trimIndent()

private class FloatPortalBridge(private val prefs: SharedPreferences) {
    init {
        prefs.edit().putString(
            FLOAT_MANIFEST_KEY,
            JSONObject().apply {
                put("type", "float-memory-bridge")
                put("version", 1)
                put("updatedAt", System.currentTimeMillis())
            }.toString()
        ).apply()
    }

    @JavascriptInterface
    fun getData(key: String): String? {
        if (!isAllowedBridgeKey(key)) return null
        return prefs.getString(key, null)
    }

    @JavascriptInterface
    fun setData(key: String, value: String): Boolean {
        if (!isAllowedBridgeKey(key)) return false
        prefs.edit().putString(key, value).apply()
        return true
    }

    private fun isAllowedBridgeKey(key: String): Boolean =
        key == FLOAT_MANIFEST_KEY ||
            key == "__tumin_memory_bridge_assistants_v1" ||
            key.startsWith("__float_memory_bridge_snapshot_v1:") ||
            key.startsWith("__tumin_memory_bridge_snapshot_v1:")
}

private class FloatPortalWebViewClient(
    private val state: WebViewState,
    private val context: Context,
    private val allowedHost: String,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: android.webkit.WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        val target = request?.url ?: return false
        val scheme = target.scheme.orEmpty().lowercase()
        val host = target.host.orEmpty().lowercase()
        if ((scheme == "http" || scheme == "https") && host == allowedHost.lowercase()) return false
        if (scheme == "about") return false

        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, target))
        }
        return true
    }

    override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: Bitmap?) {
        state.isLoading = true
        state.currentUrl = url
    }

    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
        state.isLoading = false
        state.loadingProgress = 0f
        state.pageTitle = view?.title
        state.currentUrl = url
        state.canGoBack = view?.canGoBack() == true
        state.canGoForward = view?.canGoForward() == true
        CookieManager.getInstance().flush()
        view?.evaluateJavascript(KAOMIANJIN_BOOTSTRAP_JS, null)
    }
}

private fun normalizeFloatUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    val candidate = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    val parsed = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
    if (parsed.scheme !in setOf("http", "https") || parsed.host.isNullOrBlank()) return null
    return candidate.trimEnd('/')
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewPage(url: String, content: String) {
    val context = LocalContext.current
    val floatMode = url == FLOAT_PORTAL_MARKER
    val portalSettings = remember(floatMode) {
        context.getSharedPreferences(FLOAT_PORTAL_SETTINGS, Context.MODE_PRIVATE)
    }
    val portalData = remember(floatMode) {
        if (floatMode) context.getSharedPreferences(FLOAT_PORTAL_DATA, Context.MODE_PRIVATE) else null
    }
    val portalBridge = remember(floatMode, portalData) {
        portalData?.let { FloatPortalBridge(it) }
    }
    val worldBridge = remember(floatMode) {
        if (floatMode) WorldPortalBridge(context) else null
    }

    var configuredFloatUrl by remember(floatMode) {
        mutableStateOf(if (floatMode) portalSettings.getString(FLOAT_PORTAL_URL_KEY, "").orEmpty() else "")
    }
    var urlDraft by remember(floatMode, configuredFloatUrl) { mutableStateOf(configuredFloatUrl) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }

    LaunchedEffect(floatMode) {
        if (floatMode) {
            FloatMemoryExchangeCache(context).publishAssistantCatalog()
        }
    }

    val state = if (floatMode) {
        rememberWebViewState(
            url = configuredFloatUrl.ifBlank { "about:blank" },
            interfaces = buildMap {
                portalBridge?.let { put("TuminFloatBridge", it) }
                worldBridge?.let { put("TuminWorldBridge", it) }
            },
            settings = {
                builtInZoomControls = true
                displayZoomControls = false
            }
        )
    } else if (url.isNotEmpty()) {
        rememberWebViewState(
            url = url,
            settings = {
                builtInZoomControls = true
                displayZoomControls = false
            })
    } else {
        rememberWebViewState(
            data = content.base64Decode(),
            baseUrl = "https://rikkahub.local",
            mimeType = "text/html",
            settings = {
                builtInZoomControls = true
                displayZoomControls = false
            }
        )
    }

    var showDropdown by remember { mutableStateOf(false) }
    var showConsoleSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    BackHandler(state.canGoBack) {
        state.goBack()
    }

    if (floatMode && configuredFloatUrl.isBlank()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("异世界连接") },
                    navigationIcon = { BackButton() },
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("连接 Float", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "第一次使用时填入你部署好的 Float 地址。保存后，之后从“异世界连接”进入会直接打开。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = { urlDraft = it; urlError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Float 地址") },
                    placeholder = { Text("https://your-float-site.example") },
                    singleLine = true,
                    isError = urlError != null,
                    supportingText = { urlError?.let { Text(it) } },
                )
                Button(onClick = {
                    val normalized = normalizeFloatUrl(urlDraft)
                    if (normalized == null) {
                        urlError = "请输入有效的 http/https 地址"
                    } else {
                        portalSettings.edit().putString(FLOAT_PORTAL_URL_KEY, normalized).apply()
                        configuredFloatUrl = normalized
                        urlDraft = normalized
                        state.loadUrl(normalized)
                    }
                }) {
                    Text("保存并进入 Float")
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (floatMode) "异世界连接" else state.pageTitle?.takeIf { it.isNotEmpty() } ?: state.currentUrl ?: "",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(onClick = { state.reload() }) {
                        Icon(HugeIcons.Refresh01, contentDescription = "Refresh")
                    }

                    IconButton(
                        onClick = { state.goForward() },
                        enabled = state.canGoForward
                    ) {
                        Icon(HugeIcons.ArrowRight01, contentDescription = "Forward")
                    }

                    val urlHandler = LocalUriHandler.current
                    IconButton(
                        onClick = { showDropdown = true }
                    ) {
                        Icon(HugeIcons.MoreVertical, contentDescription = "More options")

                        DropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }
                        ) {
                            if (floatMode) {
                                DropdownMenuItem(
                                    text = { Text("更换 Float 地址") },
                                    leadingIcon = { Icon(HugeIcons.Earth, contentDescription = null) },
                                    onClick = {
                                        showDropdown = false
                                        urlDraft = configuredFloatUrl
                                        urlError = null
                                        showUrlDialog = true
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Open in Browser") },
                                leadingIcon = { Icon(HugeIcons.Earth, contentDescription = null) },
                                onClick = {
                                    showDropdown = false
                                    state.currentUrl?.let { current ->
                                        if (current.isNotBlank() && current != "about:blank") {
                                            urlHandler.openUri(current)
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Console Logs") },
                                leadingIcon = { Icon(HugeIcons.Bug01, contentDescription = null) },
                                onClick = {
                                    showDropdown = false
                                    showConsoleSheet = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) {
        val allowedHost = if (floatMode) Uri.parse(configuredFloatUrl).host.orEmpty() else ""
        val floatClient = remember(floatMode, allowedHost) {
            if (floatMode && allowedHost.isNotBlank()) {
                FloatPortalWebViewClient(state, context, allowedHost)
            } else null
        }
        WebView(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            onCreated = { webView ->
                if (floatMode) {
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(webView, true)
                    }
                }
            },
            onUpdated = { webView ->
                if (floatMode) {
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(webView, true)
                    }
                }
                if (floatClient != null && webView.webViewClient !== floatClient) {
                    webView.webViewClient = floatClient
                }
            }
        )
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("更换 Float 地址") },
            text = {
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = { urlDraft = it; urlError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Float 地址") },
                    singleLine = true,
                    isError = urlError != null,
                    supportingText = { urlError?.let { Text(it) } },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val normalized = normalizeFloatUrl(urlDraft)
                    if (normalized == null) {
                        urlError = "请输入有效的 http/https 地址"
                    } else {
                        portalSettings.edit().putString(FLOAT_PORTAL_URL_KEY, normalized).apply()
                        configuredFloatUrl = normalized
                        urlDraft = normalized
                        state.loadUrl(normalized)
                        showUrlDialog = false
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("取消") }
            }
        )
    }

    if (showConsoleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConsoleSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Console Logs",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                SelectionContainer {
                    LazyColumn {
                        items(state.consoleMessages) { message ->
                            Text(
                                text = "${message.messageLevel().name}: ${message.message()}\n" +
                                    "Source: ${message.sourceId()}:${message.lineNumber()}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JetbrainsMono,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                color = when (message.messageLevel().name) {
                                    "ERROR" -> MaterialTheme.colorScheme.error
                                    "WARNING" -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }

                if (state.consoleMessages.isEmpty()) {
                    Text(
                        text = "No console messages",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        )
    }
}
