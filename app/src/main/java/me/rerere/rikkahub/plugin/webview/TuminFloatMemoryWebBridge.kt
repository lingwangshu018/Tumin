/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.plugin.webview

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.memory.TuminFloatMemoryBridge
import me.rerere.rikkahub.data.repository.MemoryRepository
import org.koin.java.KoinJavaComponent

/**
 * Thin WebView transport for Float <-> Tumin memory interoperability.
 *
 * Keep this outside PluginWebViewPage so upstream changes to the main plugin WebView
 * are less likely to conflict with the memory bridge implementation.
 */
object TuminFloatMemoryWebBridge {
    fun handleUrl(context: Context, webView: WebView, url: String): Boolean {
        if (
            !url.startsWith("bridge://memoryGetRecent") &&
            !url.startsWith("bridge://memoryGetLongTerm") &&
            !url.startsWith("bridge://memoryListAssistants")
        ) {
            return false
        }

        val uri = Uri.parse(url)
        val method = uri.host ?: return false
        val callbackId = uri.getQueryParameter("callbackId").orEmpty()
        val assistantId = uri.getQueryParameter("assistantId").orEmpty()
        val limit = uri.getQueryParameter("limit")?.toIntOrNull()?.coerceIn(1, 200) ?: 20

        when (method) {
            "memoryGetRecent" -> {
                val memoryRepository = KoinJavaComponent.get(MemoryRepository::class.java)
                val bridge = TuminFloatMemoryBridge(context, memoryRepository)
                val result = bridge.readRecentForFloat(assistantId, limit)
                postResult(webView, callbackId, result)
            }
            "memoryGetLongTerm" -> {
                val memoryRepository = KoinJavaComponent.get(MemoryRepository::class.java)
                val bridge = TuminFloatMemoryBridge(context, memoryRepository)
                CoroutineScope(Dispatchers.IO).launch {
                    val result = runCatching {
                        bridge.readLongTermForFloat(assistantId, limit)
                    }.getOrElse { error ->
                        """{"success":false,"kind":"long_term","items":[],"error":${jsonString(error.message ?: "Unknown error")}}"""
                    }
                    postResult(webView, callbackId, result)
                }
            }
            "memoryListAssistants" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val result = runCatching {
                        val settingsStore = KoinJavaComponent.get(SettingsStore::class.java)
                        val assistants = settingsStore.settingsFlow.first().assistants
                        assistants.joinToString(prefix = "{\"success\":true,\"assistants\":[", postfix = "]}") { assistant ->
                            "{\"id\":${jsonString(assistant.id.toString())},\"name\":${jsonString(assistant.name)}}"
                        }
                    }.getOrElse { error ->
                        """{"success":false,"assistants":[],"error":${jsonString(error.message ?: "Unknown error")}}"""
                    }
                    postResult(webView, callbackId, result)
                }
            }
            else -> return false
        }
        return true
    }

    fun inject(webView: WebView) {
        webView.evaluateJavascript(javascript, null)
    }

    private fun postResult(webView: WebView, callbackId: String, resultJson: String) {
        webView.post {
            webView.evaluateJavascript(
                "window.__bridgeResult(${jsonString(callbackId)}, $resultJson);",
                null,
            )
        }
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

    private const val javascript = """
(function() {
    if (!window.Bridge || window.__tuminFloatMemoryBridgeReady) return;
    window.__tuminFloatMemoryBridgeReady = true;

    function memoryBridgeCall(method, params) {
        return new Promise(function(resolve) {
            window.__bridgeResultId = window.__bridgeResultId || 0;
            window.__bridgeCallbacks = window.__bridgeCallbacks || {};
            var callbackId = 'mem_cb_' + (++window.__bridgeResultId);
            window.__bridgeCallbacks[callbackId] = resolve;
            var url = 'bridge://' + method + '?callbackId=' + encodeURIComponent(callbackId);
            for (var key in params) {
                if (Object.prototype.hasOwnProperty.call(params, key)) {
                    url += '&' + encodeURIComponent(key) + '=' + encodeURIComponent(String(params[key]));
                }
            }
            var iframe = document.createElement('iframe');
            iframe.style.display = 'none';
            iframe.src = url;
            document.body.appendChild(iframe);
            setTimeout(function() {
                if (iframe.parentNode) iframe.parentNode.removeChild(iframe);
            }, 100);
        });
    }

    window.Bridge.memoryGetRecent = function(assistantId, limit) {
        return memoryBridgeCall('memoryGetRecent', {
            assistantId: assistantId || '',
            limit: limit || 20
        });
    };

    window.Bridge.memoryGetLongTerm = function(assistantId, limit) {
        return memoryBridgeCall('memoryGetLongTerm', {
            assistantId: assistantId || '',
            limit: limit || 200
        });
    };

    window.Bridge.memoryListAssistants = function() {
        return memoryBridgeCall('memoryListAssistants', {});
    };
})();
"""
}
