package me.rerere.rikkahub.update

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.data.datastore.SettingsStore
import okhttp3.OkHttpClient
import okhttp3.Request

object AppUpdateChecker {
    private const val TAG = "AppUpdateChecker"
    private const val API = "https://api.github.com/repos/lingwangshu018/Tumin/releases/latest"
    private const val PREFS = "app_update_checker"
    private const val LAST_CHECK = "last_check"
    private const val INTERVAL = 6 * 60 * 60 * 1000L

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    fun schedule(context: Context, settingsStore: SettingsStore, scope: CoroutineScope, onUpdate: (ReleaseInfo) -> Unit) {
        scope.launch {
            delay(2500)
            if (!settingsStore.settingsFlow.value.displaySetting.showUpdates) return@launch
            if (!shouldCheck(context)) return@launch
            markChecked(context)
            runCatching { withContext(Dispatchers.IO) { fetchLatest() } }
                .onSuccess { release ->
                    if (release != null && isNewerVersion(release.version, BuildConfig.VERSION_NAME)) {
                        onUpdate(release)
                    }
                }
                .onFailure { Log.w(TAG, "Update check failed", it) }
        }
    }

    private fun shouldCheck(context: Context): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(LAST_CHECK, 0L)
        return System.currentTimeMillis() - last >= INTERVAL
    }

    private fun markChecked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(LAST_CHECK, System.currentTimeMillis()).apply()
    }

    private fun fetchLatest(): ReleaseInfo? {
        val request = Request.Builder().url(API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "OrangeChat/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val raw = json.decodeFromString<GitHubRelease>(response.body.string())
            return ReleaseInfo(raw.tagName.removePrefix("v"), raw.htmlUrl, raw.body)
        }
    }

    internal fun isNewerVersion(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(version: String) = version.removePrefix("v").substringBefore('-')
        .split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    data class ReleaseInfo(val version: String, val url: String, val notes: String)

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
        val body: String = "",
    )
}
