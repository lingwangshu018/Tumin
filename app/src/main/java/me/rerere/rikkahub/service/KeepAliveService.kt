/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity

/**
 * 前台保活服务，用于提升 App 后台存活能力，确保定时消息等功能稳定运行。
 *
 * 特性：
 * - START_STICKY：被系统杀死后自动重启
 * - stopWithTask="false"：用户从最近任务列表划掉 App 时服务不被停止
 * - 低优先级常驻通知，低调不打扰用户
 * - 兼容 Android 8.0+ NotificationChannel 要求
 * - 国产 ROM 兼容：划掉任务后通过发送广播尝试自启动
 */
class KeepAliveService : Service() {

    companion object {
        private const val TAG = "KeepAliveService"

        // 通知渠道 ID
        private const val CHANNEL_ID = "keep_alive_channel"
        // 前台服务通知 ID
        private const val NOTIFICATION_ID = 30001
        // 自启动广播 Action
        const val ACTION_RESTART_KEEP_ALIVE = "com.lingwangshu018.tumin.RESTART_KEEP_ALIVE"

        /**
         * 检查服务是否正在运行（高版本系统 getRunningServices 可能受限，仅作参考）
         */
        fun isRunning(context: Context): Boolean {
            return try {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                manager.getRunningServices(Integer.MAX_VALUE)
                    .any { it.service.className == KeepAliveService::class.java.name }
            } catch (e: Exception) {
                Log.w(TAG, "isRunning 检查失败，默认返回 false", e)
                false
            }
        }

        /**
         * 启动保活服务（若未运行）
         */
        fun start(context: Context) {
            if (isRunning(context)) {
                Log.d(TAG, "服务已在运行，跳过启动")
                return
            }
            val intent = Intent(context, KeepAliveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "保活服务启动请求已发送")
            } catch (e: Exception) {
                Log.e(TAG, "启动保活服务失败", e)
            }
        }

        /**
         * 停止保活服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            context.stopService(intent)
            Log.d(TAG, "保活服务停止请求已发送")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        // 构建点击通知后打开应用的 PendingIntent
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, RouteActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 构建低优先级常驻通知
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.keep_alive_notification_title))
            .setContentText(getString(R.string.keep_alive_notification_content))
            .setSmallIcon(R.drawable.small_icon)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setShowWhen(false)
            .setSilent(true)
            .build()

        // 启动前台服务
        // Android 14+ 对 dataSync 类型前台服务有每 24 小时 6 小时的累计配额限制，
        // 配额耗尽时 startForeground 会抛 ForegroundServiceStartNotAllowedException。
        // 这里捕获异常并优雅降级，避免崩溃整个 App。
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "startForeground 失败（可能是 dataSync 前台服务时长配额耗尽），停止保活服务避免崩溃",
                e
            )
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    /**
     * 用户从最近任务列表划掉 App 时触发。
     * stopWithTask="false" 使 Service 不会自动停止，但部分国产 ROM 仍会强杀进程。
     * 此处发送广播尝试在 Service 被回收后重新拉起，提高在小米/华为/OPPO/vivo/荣耀等 ROM 上的存活率。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved")
        try {
            sendBroadcast(Intent(ACTION_RESTART_KEEP_ALIVE).apply {
                setPackage(packageName)
            })
        } catch (e: Exception) {
            Log.e(TAG, "发送保活重启广播失败", e)
        }
    }

    /**
     * 创建通知渠道（Android 8.0+ 必需）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.keep_alive_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.keep_alive_channel_desc)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
