/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.utils

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.common.http.await
import me.rerere.rikkahub.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request

private const val GITHUB_OWNER = "lingwangshu018"
private const val GITHUB_REPO = "Tumin"
private const val API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
private const val UPDATE_PREFS = "app_update_checker"
private const val AUTO_CHECK_ENABLED = "auto_check_enabled"
private const val LAST_AUTO_CHECK_AT = "last_auto_check_at"
private const val LAST_NOTIFIED_VERSION = "last_notified_version"
private const val AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

class UpdateChecker(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    fun checkUpdate(): Flow<UiState<UpdateInfo>> = flow {
        emit(UiState.Loading)
        emit(UiState.Success(fetchUpdateInfo()))
    }.catch {
        emit(UiState.Error(it))
    }.flowOn(Dispatchers.IO)

    suspend fun fetchUpdateInfo(): UpdateInfo = withContext(Dispatchers.IO) {
        val response = client.newCall(
            Request.Builder()
                .url(API_URL)
                .get()
                .addHeader(
                    "User-Agent",
                    "Tumin ${BuildConfig.VERSION_NAME} #${BuildConfig.VERSION_CODE}"
                )
                .build()
        ).await()

        if (!response.isSuccessful) {
            throw Exception("检查更新失败（HTTP ${response.code}）")
        }

        val release = json.decodeFromString<GithubRelease>(response.body.string())
        val version = release.tagName.removePrefix("v").removePrefix("V")
        UpdateInfo(
            version = version,
            publishedAt = release.publishedAt,
            changelog = release.body.takeIf { !it.isNullOrBlank() } ?: "暂无更新说明",
            downloads = release.assets
                .filter { it.name.endsWith(".apk", ignoreCase = true) }
                .map { asset ->
                    UpdateDownload(
                        name = asset.name,
                        url = asset.browserDownloadUrl,
                        size = formatSize(asset.size)
                    )
                }
        )
    }

    fun isNewerVersion(info: UpdateInfo): Boolean =
        Version(info.version) > Version(BuildConfig.VERSION_NAME)

    fun isAutoCheckEnabled(context: Context): Boolean =
        context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(AUTO_CHECK_ENABLED, true)

    fun setAutoCheckEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(AUTO_CHECK_ENABLED, enabled)
            .apply()
    }

    /**
     * App 启动时调用。自动检查默认开启，同一设备 24 小时内最多请求 GitHub 一次。
     * 没有新版本时保持安静；只有发现新版本时才提示。
     */
    suspend fun autoCheckIfDue(context: Context): UpdateInfo? {
        val prefs = context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(AUTO_CHECK_ENABLED, true)) return null

        val now = System.currentTimeMillis()
        val lastCheckAt = prefs.getLong(LAST_AUTO_CHECK_AT, 0L)
        if (now - lastCheckAt < AUTO_CHECK_INTERVAL_MS) return null

        // 在真正发起请求前记下时间，避免一次启动周期内多个入口重复请求。
        prefs.edit().putLong(LAST_AUTO_CHECK_AT, now).apply()

        val info = runCatching { fetchUpdateInfo() }.getOrNull() ?: return null
        if (!isNewerVersion(info)) return null

        if (prefs.getString(LAST_NOTIFIED_VERSION, null) != info.version) {
            prefs.edit().putString(LAST_NOTIFIED_VERSION, info.version).apply()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context.applicationContext,
                    "发现兔眠新版本 ${info.version}，可前往关于页面下载更新",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        return info
    }

    fun downloadUpdate(context: Context, download: UpdateDownload) {
        runCatching {
            val request = DownloadManager.Request(download.url.toUri()).apply {
                setTitle(download.name)
                setDescription("正在下载兔眠更新包…")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, download.name)
                setMimeType("application/vnd.android.package-archive")
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
        }.onFailure {
            Toast.makeText(context, "下载更新失败，将打开浏览器", Toast.LENGTH_SHORT).show()
            context.openUrl(download.url)
        }
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1) {
            String.format("%.1f MB", mb)
        } else {
            String.format("%.0f KB", bytes.toDouble() / 1024)
        }
    }
}

@Serializable
data class UpdateDownload(
    val name: String,
    val url: String,
    val size: String,
)

@Serializable
data class UpdateInfo(
    val version: String,
    val publishedAt: String,
    val changelog: String,
    val downloads: List<UpdateDownload>,
)

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("published_at") val publishedAt: String,
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String,
    val size: Long,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

@JvmInline
value class Version(val value: String) : Comparable<Version> {
    private fun parse(): ParsedVersion {
        val withoutBuild = value.split("+").first()
        val hyphenIndex = withoutBuild.indexOf('-')
        val (coreStr, prereleaseStr) = if (hyphenIndex >= 0) {
            withoutBuild.substring(0, hyphenIndex) to withoutBuild.substring(hyphenIndex + 1)
        } else {
            withoutBuild to null
        }
        val core = coreStr.split(".").map { it.toIntOrNull() ?: 0 }
        val prerelease = prereleaseStr?.split(".")
        return ParsedVersion(core, prerelease)
    }

    override fun compareTo(other: Version): Int {
        val a = parse()
        val b = other.parse()
        val maxLen = maxOf(a.core.size, b.core.size)
        for (i in 0 until maxLen) {
            val ap = a.core.getOrElse(i) { 0 }
            val bp = b.core.getOrElse(i) { 0 }
            if (ap != bp) return ap.compareTo(bp)
        }
        return when {
            a.prerelease == null && b.prerelease == null -> 0
            a.prerelease != null && b.prerelease == null -> -1
            a.prerelease == null && b.prerelease != null -> 1
            else -> comparePrerelease(a.prerelease!!, b.prerelease!!)
        }
    }

    companion object {
        fun compare(version1: String, version2: String): Int =
            Version(version1).compareTo(Version(version2))

        private fun comparePrerelease(a: List<String>, b: List<String>): Int {
            val maxLen = maxOf(a.size, b.size)
            for (i in 0 until maxLen) {
                if (i >= a.size) return -1
                if (i >= b.size) return 1
                val aNum = a[i].toIntOrNull()
                val bNum = b[i].toIntOrNull()
                val cmp = when {
                    aNum != null && bNum != null -> aNum.compareTo(bNum)
                    aNum != null -> -1
                    bNum != null -> 1
                    else -> a[i].compareTo(b[i])
                }
                if (cmp != 0) return cmp
            }
            return 0
        }
    }
}

private data class ParsedVersion(
    val core: List<Int>,
    val prerelease: List<String>?,
)

operator fun String.compareTo(other: Version): Int = Version(this).compareTo(other)
operator fun Version.compareTo(other: String): Int = compareTo(Version(other))
