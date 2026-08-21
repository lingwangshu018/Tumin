package me.rerere.rikkahub.data.memory

import android.content.Context

/** Persistent user-facing settings for Tumin <-> kaomianjin memory interoperability. */
data class KaomianjinMemoryBridgeConfig(
    val enabled: Boolean = false,
    val allowTuminReadKaomianjinRecent: Boolean = true,
    val allowKaomianjinReadTuminRecent: Boolean = true,
    val sharedRecentContextLimit: Int = 20,
    val allowTuminReadKaomianjinLongTerm: Boolean = true,
    val allowKaomianjinReadTuminLongTerm: Boolean = true,
    val autoSyncImportantLongTerm: Boolean = false,
)

class KaomianjinMemoryBridgeSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): KaomianjinMemoryBridgeConfig = KaomianjinMemoryBridgeConfig(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        allowTuminReadKaomianjinRecent = prefs.getBoolean(KEY_TUMIN_READ_KAOMIANJIN_RECENT, true),
        allowKaomianjinReadTuminRecent = prefs.getBoolean(KEY_KAOMIANJIN_READ_TUMIN_RECENT, true),
        sharedRecentContextLimit = prefs.getInt(KEY_RECENT_LIMIT, 20).coerceIn(1, 200),
        allowTuminReadKaomianjinLongTerm = prefs.getBoolean(KEY_TUMIN_READ_KAOMIANJIN_LONG, true),
        allowKaomianjinReadTuminLongTerm = prefs.getBoolean(KEY_KAOMIANJIN_READ_TUMIN_LONG, true),
        autoSyncImportantLongTerm = prefs.getBoolean(KEY_AUTO_SYNC_LONG, false),
    )

    fun save(config: KaomianjinMemoryBridgeConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putBoolean(KEY_TUMIN_READ_KAOMIANJIN_RECENT, config.allowTuminReadKaomianjinRecent)
            .putBoolean(KEY_KAOMIANJIN_READ_TUMIN_RECENT, config.allowKaomianjinReadTuminRecent)
            .putInt(KEY_RECENT_LIMIT, config.sharedRecentContextLimit.coerceIn(1, 200))
            .putBoolean(KEY_TUMIN_READ_KAOMIANJIN_LONG, config.allowTuminReadKaomianjinLongTerm)
            .putBoolean(KEY_KAOMIANJIN_READ_TUMIN_LONG, config.allowKaomianjinReadTuminLongTerm)
            .putBoolean(KEY_AUTO_SYNC_LONG, config.autoSyncImportantLongTerm)
            .apply()
    }

    companion object {
        private const val PREFS = "kaomianjin_memory_bridge_v1"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TUMIN_READ_KAOMIANJIN_RECENT = "tumin_read_kaomianjin_recent"
        private const val KEY_KAOMIANJIN_READ_TUMIN_RECENT = "kaomianjin_read_tumin_recent"
        private const val KEY_RECENT_LIMIT = "recent_limit"
        private const val KEY_TUMIN_READ_KAOMIANJIN_LONG = "tumin_read_kaomianjin_long_term"
        private const val KEY_KAOMIANJIN_READ_TUMIN_LONG = "kaomianjin_read_tumin_long_term"
        private const val KEY_AUTO_SYNC_LONG = "auto_sync_important_long_term"
    }
}
