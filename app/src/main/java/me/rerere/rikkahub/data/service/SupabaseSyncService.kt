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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.data.datastore.SettingsStore
import org.koin.core.context.GlobalContext

class SupabaseSyncService : Service() {

    companion object {
        private const val TAG = "SupabaseSyncService"
        const val ACTION_SUPABASE_SYNC = "com.lingwangshu018.tumin.SUPABASE_SYNC"
        private const val REQUEST_CODE = 10002
        private const val INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

        private const val PREFS_NAME = "supabase_sync_prefs"
        private const val KEY_NEXT_TRIGGER_TIME = "next_trigger_time"
        private const val KEY_SYNCING = "syncing"

        fun markSyncing(context: Context, syncing: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SYNCING, syncing)
                .apply()
        }

        fun isSyncing(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SYNCING, false)
        }

        fun scheduleNext(context: Context) {
            val triggerTime = System.currentTimeMillis() + INTERVAL_MS

            // 只保存下次触发时间，不清除同步中标记（由Service完成后清除）
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_NEXT_TRIGGER_TIME, triggerTime)
                .apply()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, SupabaseSyncReceiver::class.java).apply {
                action = ACTION_SUPABASE_SYNC
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }

            Log.d(TAG, "Scheduled Supabase sync at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(triggerTime))}")
        }

        fun getNextTriggerTime(context: Context): Long? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val triggerTime = prefs.getLong(KEY_NEXT_TRIGGER_TIME, 0L)
            return if (triggerTime > 0) triggerTime else null
        }

        fun cancel(context: Context) {
            // 清除保存的触发时间和同步中标记
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_NEXT_TRIGGER_TIME)
                .remove(KEY_SYNCING)
                .apply()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, SupabaseSyncReceiver::class.java).apply {
                action = ACTION_SUPABASE_SYNC
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                Log.d(TAG, "Cancelled Supabase sync alarm")
            }
        }

        fun triggerNow(context: Context) {
            Log.d(TAG, "triggerNow called")
            // 立即安排下次同步时间让UI显示倒计时
            scheduleNext(context)
            // 标记正在同步中
            markSyncing(context, true)
            // 启动Service执行同步
            val serviceIntent = Intent(context, SupabaseSyncService::class.java)
            try {
                context.startForegroundService(serviceIntent)
                Log.d(TAG, "startForegroundService called successfully")
            } catch (e: Exception) {
                Log.e(TAG, "startForegroundService failed, trying direct sync", e)
                // Service启动失败，直接在后台协程执行同步
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val settingsStore = org.koin.core.context.GlobalContext.getOrNull()?.get<SettingsStore>()
                        if (settingsStore == null) {
                            Log.e(TAG, "SettingsStore not available, cannot sync")
                            return@launch
                        }
                        val settings = settingsStore.settingsFlowRaw.first()
                        val supabaseSetting = settings.systemToolsSetting
                        if (supabaseSetting.supabaseEnabled && supabaseSetting.supabaseUrl.isNotBlank() && supabaseSetting.supabaseApiKey.isNotBlank()) {
                            val service = SupabaseService(
                                supabaseUrl = supabaseSetting.supabaseUrl,
                                supabaseApiKey = supabaseSetting.supabaseApiKey,
                                tableName = supabaseSetting.supabaseTableName
                            )
                            val result = service.collectAndUpload(context)
                            if (result.isSuccess) {
                                Log.d(TAG, "Direct sync completed successfully")
                            } else {
                                Log.e(TAG, "Direct sync failed", result.exceptionOrNull())
                            }
                        }
                    } catch (e2: Exception) {
                        Log.e(TAG, "Direct sync error", e2)
                    } finally {
                        scheduleNext(context)
                        markSyncing(context, false)
                    }
                }
            }
        }

        fun rescheduleIfEnabled(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val settingsStore = GlobalContext.get().get<SettingsStore>()
                    val settings = settingsStore.settingsFlowRaw.first()
                    if (settings.systemToolsSetting.supabaseEnabled &&
                        settings.systemToolsSetting.supabaseUrl.isNotBlank() &&
                        settings.systemToolsSetting.supabaseApiKey.isNotBlank()
                    ) {
                        scheduleNext(context)
                        Log.d(TAG, "Rescheduled Supabase sync")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reschedule Supabase sync", e)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notification = androidx.core.app.NotificationCompat.Builder(this, CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Supabase 数据同步")
                .setSmallIcon(me.rerere.rikkahub.R.drawable.small_icon)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
                .build()
            startForeground(20003, notification)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            markSyncing(this, false)
            scheduleNext(this)
            stopSelf()
            return START_NOT_STICKY
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settingsStore = GlobalContext.get().get<SettingsStore>()
                val settings = settingsStore.settingsFlowRaw.first()
                val supabaseSetting = settings.systemToolsSetting

                val service = SupabaseService(
                    supabaseUrl = supabaseSetting.supabaseUrl,
                    supabaseApiKey = supabaseSetting.supabaseApiKey,
                    tableName = supabaseSetting.supabaseTableName
                )

                val result = service.collectAndUpload(this@SupabaseSyncService)
                if (result.isSuccess) {
                    Log.d(TAG, "Supabase sync completed successfully")
                } else {
                    Log.e(TAG, "Supabase sync failed", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Supabase sync error", e)
            } finally {
                // 安排下一次同步
                scheduleNext(this@SupabaseSyncService)
                // 同步完成，清除同步中标记
                markSyncing(this@SupabaseSyncService, false)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class SupabaseSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            SupabaseSyncService.ACTION_SUPABASE_SYNC -> {
                Log.d("SupabaseSyncService", "Supabase sync alarm triggered")
                SupabaseSyncService.markSyncing(context, true)
                val serviceIntent = Intent(context, SupabaseSyncService::class.java)
                try {
                    context.startForegroundService(serviceIntent)
                } catch (e: Exception) {
                    Log.e("SupabaseSyncService", "Failed to start service from receiver", e)
                    // Service启动失败，用fallback
                    SupabaseSyncService.triggerNow(context)
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d("SupabaseSyncService", "Boot completed, rescheduling Supabase sync")
                SupabaseSyncService.rescheduleIfEnabled(context)

                // 开机事件追踪：常驻前台服务不会随系统重启自动复活，需手动拉起一次。
                // 独立 try-catch，不能影响原有 reschedule 逻辑。
                try {
                    DeviceEventTrackingService.startIfEnabled(context)
                } catch (e: Exception) {
                    Log.e("SupabaseSyncService", "Boot: start DeviceEventTrackingService failed", e)
                }

                // 推送一条 boot 事件。独立 try-catch + 协程，不阻塞 onReceive。
                try {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val settingsStore = GlobalContext.get().get<SettingsStore>()
                            val settings = settingsStore.settingsFlowRaw.first()
                            val s = settings.systemToolsSetting
                            if (s.deviceEventTrackingEnabled &&
                                s.supabaseEnabled &&
                                s.supabaseUrl.isNotBlank() &&
                                s.supabaseApiKey.isNotBlank()
                            ) {
                                val service = SupabaseService(
                                    supabaseUrl = s.supabaseUrl,
                                    supabaseApiKey = s.supabaseApiKey,
                                    tableName = s.supabaseTableName
                                )
                                val result = service.insertDeviceEvent("boot")
                                if (result.isSuccess) {
                                    Log.d("SupabaseSyncService", "Boot: pushed boot event")
                                } else {
                                    Log.e("SupabaseSyncService", "Boot: push boot event failed", result.exceptionOrNull())
                                }
                            } else {
                                Log.d("SupabaseSyncService", "Boot: deviceEventTrackingEnabled=false or config incomplete, skip boot event")
                            }
                        } catch (e: Exception) {
                            Log.e("SupabaseSyncService", "Boot: insertDeviceEvent coroutine error", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SupabaseSyncService", "Boot: launch boot event coroutine failed", e)
                }
            }
        }
    }
}
