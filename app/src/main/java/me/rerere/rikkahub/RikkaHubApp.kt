/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.runtime.Composer
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import me.rerere.common.android.appTempFolder
import com.whl.quickjs.android.QuickJSLoader
import me.rerere.rikkahub.di.appModule
import me.rerere.rikkahub.di.dataSourceModule
import me.rerere.rikkahub.di.repositoryModule
import me.rerere.rikkahub.di.viewModelModule
import me.rerere.rikkahub.plugin.di.pluginModule
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.service.DailySummaryService
import me.rerere.rikkahub.data.service.DeviceEventAiTriggerService
import me.rerere.rikkahub.data.service.DeviceEventTrackingService
import me.rerere.rikkahub.data.service.ProactiveMessageService
import me.rerere.rikkahub.data.service.SupabaseSyncService
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.WebServerService
import me.rerere.rikkahub.utils.CrashHandler
import me.rerere.rikkahub.utils.DatabaseUtil
import me.rerere.rikkahub.utils.UpdateChecker
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

private const val TAG = "RikkaHubApp"

const val CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID = "chat_completed"
const val CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID = "chat_live_update"
const val WEB_SERVER_NOTIFICATION_CHANNEL_ID = "web_server"
const val POMODORO_NOTIFICATION_CHANNEL_ID = "pomodoro_timer"
const val MUSIC_PLAYER_NOTIFICATION_CHANNEL_ID = "music_player"
const val DEVICE_EVENT_NOTIFICATION_CHANNEL_ID = "device_event_tracking"
const val VOICE_CALL_NOTIFICATION_CHANNEL_ID = "voice_call"
const val ANNOUNCEMENT_NOTIFICATION_CHANNEL_ID = "announcement"

class RikkaHubApp : Application() {
    companion object {
        var INSTANCE: RikkaHubApp? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        startKoin {
            androidLogger()
            androidContext(this@RikkaHubApp)
            workManagerFactory()
            modules(appModule, viewModelModule, dataSourceModule, repositoryModule, pluginModule)
        }
        this.createNotificationChannel()

        // 预热 ChatService 单例: 强制在主线程(Application.onCreate 由 Android 保证
        // 在主线程执行, 且先于同一进程内任何 Service/BroadcastReceiver/Activity 回调)
        // 解析并构造 ChatService。原因: ChatService.init 里有 ProcessLifecycleOwner
        // 的 addObserver, 该 API 强制要求主线程; 而 ChatService 是 Koin 普通 single
        // (懒汉式), 默认在"首次注入它的调用者所在线程"构造。进程冷启动后, 若第一个
        // 访问 chatService 的是 ProactiveMessageTriggerService(它在自己的
        // Dispatchers.IO 协程里首次访问), 就会在后台线程构造并抛
        // "addObserver must be called on the main thread" 崩溃。这里预热后, 任何
        // 后续入口(主动消息/语音通话/WebServer/新功能)拿到的都是已在主线程建好的现成单例。
        // 必须放在 startWebServerIfEnabled() 之前, 因为后者会间接触发 WebServerManager
        // -> chatService 解析链。
        runCatching { get<ChatService>() }.onFailure { e ->
            android.util.Log.e(TAG, "Failed to pre-warm ChatService singleton", e)
        }

        // set cursor window size to 32MB
        DatabaseUtil.setCursorWindowSize(32 * 1024 * 1024)

        // install crash handler
        CrashHandler.install(this)

        // Init QuickJS native library
        QuickJSLoader.init()

        // delete temp files
        deleteTempFiles()

        // sync upload files to DB
        syncManagedFiles()

        // Start WebServer if enabled in settings
        startWebServerIfEnabled()

        // Reschedule proactive message alarm if enabled
        rescheduleProactiveMessageIfEnabled()

        // Reschedule Supabase sync alarm if enabled
        rescheduleSupabaseSyncIfEnabled()

        // Start device event tracking (screen on/off realtime listener) if enabled
        startDeviceEventTrackingIfEnabled()

        // Start workflow trigger registry (event-driven automation)
        startWorkflowTriggers()

        // Start network change monitor (invalidates SSH DNS cache on WiFi<->cell handoff)
        startNetworkChangeMonitor()

        // Start App Lock guard (intercepts locked apps when opened) if any app is locked
        startAppLockGuardIfEnabled()

        // Start aggressive mode (device event AI trigger) if enabled
        startAggressiveModeIfEnabled()

        // Reschedule daily_cron alarm if plugins need it
        rescheduleDailyCronIfEnabled()

        // Diary summary is now generated entirely by Supabase Edge Function.
        // App no longer schedules local diary summary alarms.

        // Check GitHub Releases on launch when automatic updates are enabled (max once per day)
        checkForUpdatesOnLaunch()

        // Increment launch count
        incrementLaunchCount()

        // Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.Auto)
    }

    private fun checkForUpdatesOnLaunch() {
        get<AppScope>().launch {
            runCatching {
                get<UpdateChecker>().autoCheckIfDue(this@RikkaHubApp)
            }.onFailure {
                Log.w(TAG, "Automatic update check failed", it)
            }
        }
    }

    private fun incrementLaunchCount() {
        get<AppScope>().launch {
            runCatching {
                val store = get<SettingsStore>()
                val current = store.settingsFlowRaw.first()
                store.update(current.copy(launchCount = current.launchCount + 1))
                Log.i(TAG, "incrementLaunchCount: ${store.settingsFlowRaw.first().launchCount}")
            }.onFailure {
                Log.e(TAG, "incrementLaunchCount failed", it)
            }
        }
    }

    private fun deleteTempFiles() {
        get<AppScope>().launch(Dispatchers.IO) {
            val dir = appTempFolder
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    private fun syncManagedFiles() {
        get<AppScope>().launch(Dispatchers.IO) {
            runCatching {
                get<FilesManager>().syncFolder()
            }.onFailure {
                Log.e(TAG, "syncManagedFiles failed", it)
            }
        }
    }

    private fun rescheduleProactiveMessageIfEnabled() {
        get<AppScope>().launch {
            runCatching {
                val settings = get<SettingsStore>().settingsFlowRaw.first()
                if (settings.proactiveMessageSetting.enabled) {
                    ProactiveMessageService.scheduleNext(this@RikkaHubApp, settings.proactiveMessageSetting)
                    Log.i(TAG, "Rescheduled proactive message alarm on app start")
                }
            }.onFailure {
                Log.e(TAG, "rescheduleProactiveMessageIfEnabled failed", it)
            }
        }
    }

    private fun rescheduleSupabaseSyncIfEnabled() {
        SupabaseSyncService.rescheduleIfEnabled(this)
    }

    private fun startDeviceEventTrackingIfEnabled() {
        runCatching {
            DeviceEventTrackingService.startIfEnabled(this)
        }.onFailure {
            Log.e(TAG, "startDeviceEventTrackingIfEnabled failed", it)
        }
    }

    private fun startWorkflowTriggers() {
        runCatching {
            val registry = get<me.rerere.rikkahub.workflow.trigger.TriggerRegistry>()
            val engine = get<me.rerere.rikkahub.workflow.execution.WorkflowEngine>()
            registry.setEngineCallback(engine.triggerCallback)
            registry.start()
        }.onFailure {
            Log.e(TAG, "startWorkflowTriggers failed", it)
        }
    }

    private fun startNetworkChangeMonitor() {
        runCatching {
            me.rerere.rikkahub.utils.NetworkChangeMonitor.start(this)
        }.onFailure {
            Log.e(TAG, "startNetworkChangeMonitor failed", it)
        }
    }

    private fun startAppLockGuardIfEnabled() {
        runCatching {
            me.rerere.rikkahub.data.service.AppLockGuard.init(this)
        }.onFailure {
            Log.e(TAG, "startAppLockGuardIfEnabled failed", it)
        }
    }

    private fun startAggressiveModeIfEnabled() {
        runCatching {
            DeviceEventAiTriggerService.startIfEnabled(this)
        }.onFailure {
            Log.e(TAG, "startAggressiveModeIfEnabled failed", it)
        }
    }

    private fun rescheduleDailyCronIfEnabled() {
        DailySummaryService.rescheduleIfEnabled(this)
    }

    private fun startWebServerIfEnabled() {
        get<AppScope>().launch {
            runCatching {
                delay(500)
                val settings = get<SettingsStore>().settingsFlowRaw.first()
                if (settings.webServerEnabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            this@RikkaHubApp,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.w(TAG, "startWebServerIfEnabled: notification permission not granted, skipping")
                        return@launch
                    }
                    if (Build.VERSION.SDK_INT >= 37 &&
                        !settings.webServerLocalhostOnly &&
                        ContextCompat.checkSelfPermission(
                            this@RikkaHubApp,
                            android.Manifest.permission.ACCESS_LOCAL_NETWORK
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.w(TAG, "startWebServerIfEnabled: local network permission not granted, skipping")
                        return@launch
                    }
                    val intent = Intent(this@RikkaHubApp, WebServerService::class.java).apply {
                        action = WebServerService.ACTION_START
                        putExtra(WebServerService.EXTRA_PORT, settings.webServerPort)
                        putExtra(WebServerService.EXTRA_LOCALHOST_ONLY, settings.webServerLocalhostOnly)
                    }
                    startForegroundService(intent)
                }
            }.onFailure {
                Log.e(TAG, "startWebServerIfEnabled failed", it)
            }
        }
    }

    private fun createNotificationChannel() {
        val notificationManager = NotificationManagerCompat.from(this)
        val chatCompletedChannel = NotificationChannelCompat
            .Builder(
                CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
            .setName(getString(R.string.notification_channel_chat_completed))
            .setVibrationEnabled(true)
            .build()
        notificationManager.createNotificationChannel(chatCompletedChannel)

        val chatLiveUpdateChannel = NotificationChannelCompat
            .Builder(
                CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            )
            .setName(getString(R.string.notification_channel_chat_live_update))
            .setVibrationEnabled(false)
            .build()
        notificationManager.createNotificationChannel(chatLiveUpdateChannel)

        val webServerChannel = NotificationChannelCompat
            .Builder(WEB_SERVER_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.notification_channel_web_server))
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        notificationManager.createNotificationChannel(webServerChannel)

        val pomodoroChannel = NotificationChannelCompat
            .Builder(POMODORO_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("番茄钟")
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        notificationManager.createNotificationChannel(pomodoroChannel)

        val musicChannel = NotificationChannelCompat
            .Builder(MUSIC_PLAYER_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("音乐播放")
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        notificationManager.createNotificationChannel(musicChannel)

        val deviceEventChannel = NotificationChannelCompat
            .Builder(DEVICE_EVENT_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("设备状态同步")
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        notificationManager.createNotificationChannel(deviceEventChannel)

        val voiceCallChannel = NotificationChannelCompat
            .Builder(VOICE_CALL_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("语音通话")
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        notificationManager.createNotificationChannel(voiceCallChannel)

        val announcementChannel = NotificationChannelCompat
            .Builder(ANNOUNCEMENT_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("公告")
            .setVibrationEnabled(true)
            .build()
        notificationManager.createNotificationChannel(announcementChannel)
    }

    override fun onTerminate() {
        super.onTerminate()
        get<AppScope>().cancel()
        stopService(Intent(this, WebServerService::class.java))
    }
}

class AppScope : CoroutineScope by CoroutineScope(
    SupervisorJob()
        + Dispatchers.Main
        + CoroutineName("AppScope")
        + CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "AppScope exception", e)
    }
)
