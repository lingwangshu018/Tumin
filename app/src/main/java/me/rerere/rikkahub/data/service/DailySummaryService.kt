/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.plugin.loader.PluginLoader
import org.koin.core.context.GlobalContext

/**
 * 插件定时任务调度服务
 * 使用 AlarmManager 定时触发插件的 daily_cron 钩子
 * 遵循 ProactiveMessageService / SupabaseSyncService 的调度模式
 *
 * 这使得 supabase_memory 等插件的 daily_cron 钩子能够被定时执行，
 * 从而实现每日日记自动生成等功能。
 */
class DailySummaryService {

    companion object {
        const val TAG = "DailySummaryService"
        const val ACTION_DAILY_CRON = "com.lingwangshu018.tumin.DAILY_CRON"
        private const val REQUEST_CODE = 10003

        private const val PREFS_NAME = "daily_cron_prefs"
        private const val KEY_NEXT_TRIGGER_TIME = "next_trigger_time"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"

        /** 默认触发时间：每天凌晨 3:00 */
        private const val DEFAULT_HOUR = 3
        private const val DEFAULT_MINUTE = 0

        /**
         * 计算距离下次目标时间的毫秒数
         */
        private fun calculateDelayToNextTarget(hour: Int = DEFAULT_HOUR, minute: Int = DEFAULT_MINUTE): Long {
            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            // 如果今天的目标时间已过，设置为明天
            if (target.before(now)) {
                target.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }

            return target.timeInMillis - now.timeInMillis
        }

        /**
         * 调度下一次 daily_cron 闹钟
         * @param hour 目标小时 (0-23)
         * @param minute 目标分钟 (0-59)
         */
        fun scheduleNext(context: Context, hour: Int = DEFAULT_HOUR, minute: Int = DEFAULT_MINUTE) {
            val delayMs = calculateDelayToNextTarget(hour, minute)
            val triggerTime = System.currentTimeMillis() + delayMs

            // 保存调度配置
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_NEXT_TRIGGER_TIME, triggerTime)
                .putBoolean(KEY_ENABLED, true)
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .apply()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DailySummaryReceiver::class.java).apply {
                action = ACTION_DAILY_CRON
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.w(TAG, "Exact alarm permission not granted, using inexact alarm")
                }
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }

            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            Log.d(TAG, "Scheduled daily_cron at ${sdf.format(java.util.Date(triggerTime))} (delay: ${delayMs / 60000} min)")
        }

        /**
         * 取消调度
         */
        fun cancel(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_NEXT_TRIGGER_TIME)
                .putBoolean(KEY_ENABLED, false)
                .apply()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DailySummaryReceiver::class.java).apply {
                action = ACTION_DAILY_CRON
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                Log.d(TAG, "Cancelled daily_cron alarm")
            }
        }

        /**
         * 获取下次触发时间
         */
        fun getNextTriggerTime(context: Context): Long? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val triggerTime = prefs.getLong(KEY_NEXT_TRIGGER_TIME, 0L)
            return if (triggerTime > 0) triggerTime else null
        }

        /**
         * 是否已启用
         */
        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)
        }

        /**
         * 检查是否有插件声明了 daily_cron 钩子，如果有则调度
         * 在 App 启动和 BOOT_COMPLETED 时调用
         */
        fun rescheduleIfEnabled(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val pluginLoader = GlobalContext.get().getOrNull<PluginLoader>()
                    if (pluginLoader == null) {
                        Log.w(TAG, "PluginLoader not available, skipping daily_cron scheduling")
                        return@launch
                    }

                    val pluginsWithCron = pluginLoader.getPluginsWithDailyCron()
                    if (pluginsWithCron.isNotEmpty()) {
                        // 读取保存的调度时间，或使用默认值
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val hour = prefs.getInt(KEY_HOUR, DEFAULT_HOUR)
                        val minute = prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE)
                        scheduleNext(context, hour, minute)
                        Log.i(TAG, "Rescheduled daily_cron alarm (${pluginsWithCron.size} plugin(s) with daily_cron hook)")
                    } else {
                        cancel(context)
                        Log.i(TAG, "No plugins with daily_cron hook, skipping scheduling")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reschedule daily_cron", e)
                }
            }
        }

        /**
         * 立即触发一次 daily_cron（调试/手动触发用）
         */
        fun triggerNow(context: Context) {
            val serviceIntent = Intent(context, DailySummaryTriggerService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}

/**
 * daily_cron 闹钟接收器
 * 收到闹钟后启动 Foreground Service 执行插件的 daily_cron 钩子
 */
class DailySummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(DailySummaryService.TAG, "DailySummaryReceiver triggered, action=${intent.action}")
        when (intent.action) {
            DailySummaryService.ACTION_DAILY_CRON -> {
                val serviceIntent = Intent(context, DailySummaryTriggerService::class.java)
                context.startForegroundService(serviceIntent)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(DailySummaryService.TAG, "Boot completed, rescheduling daily_cron")
                DailySummaryService.rescheduleIfEnabled(context)
            }
        }
    }
}

/**
 * daily_cron 执行 Foreground Service
 * 调用 PluginLoader.callEvent("daily_cron", ...) 触发所有插件的 daily_cron 钩子
 * 执行完成后自动调度下一次闹钟
 */
class DailySummaryTriggerService : Service() {

    companion object {
        private const val TAG = "DailySummaryTrigger"
        private const val NOTIFICATION_ID = 20003
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "DailySummaryTriggerService started")

        val notification = NotificationCompat.Builder(this, CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("正在执行定时任务...")
            .setSmallIcon(R.drawable.small_icon)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pluginLoader = GlobalContext.get().getOrNull<PluginLoader>()
                if (pluginLoader == null) {
                    Log.w(TAG, "PluginLoader not available, skipping daily_cron")
                    return@launch
                }

                val pluginsWithCron = pluginLoader.getPluginsWithDailyCron()
                if (pluginsWithCron.isEmpty()) {
                    Log.i(TAG, "No plugins with daily_cron hook, skipping")
                    DailySummaryService.cancel(this@DailySummaryTriggerService)
                    return@launch
                }

                Log.i(TAG, "Dispatching daily_cron event to ${pluginsWithCron.size} plugin(s)...")
                
                // 构建事件参数，包含当前时间信息
                val now = java.time.LocalDateTime.now()
                val eventData = JsonObject(
                    mapOf(
                        "timestamp" to JsonPrimitive(now.toString()),
                        "date" to JsonPrimitive(now.toLocalDate().toString()),
                        "hour" to JsonPrimitive(now.hour),
                        "minute" to JsonPrimitive(now.minute)
                    )
                )
                
                pluginLoader.callEvent("daily_cron", eventData)
                Log.i(TAG, "daily_cron event dispatch completed")

                // 执行完成后调度下一次
                DailySummaryService.scheduleNext(this@DailySummaryTriggerService)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispatch daily_cron", e)
                // 即使失败也调度下一次，避免中断
                DailySummaryService.scheduleNext(this@DailySummaryTriggerService)
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
