/*
 * Float memory bridge settings kept outside Tumin's native assistant/memory models.
 */
package me.rerere.rikkahub.data.memory

import android.content.Context

/** Persistent user-facing settings for Tumin <-> Float memory interoperability. */
data class FloatMemoryBridgeConfig(
    val enabled: Boolean = false,
    val allowTuminReadFloatRecent: Boolean = true,
    val allowFloatReadTuminRecent: Boolean = true,
    val sharedRecentContextLimit: Int = 20,
    val allowTuminReadFloatLongTerm: Boolean = true,
    val allowFloatReadTuminLongTerm: Boolean = true,
    val autoSyncImportantLongTerm: Boolean = false,
)

class FloatMemoryBridgeSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): FloatMemoryBridgeConfig = FloatMemoryBridgeConfig(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        allowTuminReadFloatRecent = prefs.getBoolean(KEY_TUMIN_READ_FLOAT_RECENT, true),
        allowFloatReadTuminRecent = prefs.getBoolean(KEY_FLOAT_READ_TUMIN_RECENT, true),
        sharedRecentContextLimit = prefs.getInt(KEY_RECENT_LIMIT, 20).coerceIn(1, 200),
        allowTuminReadFloatLongTerm = prefs.getBoolean(KEY_TUMIN_READ_FLOAT_LONG, true),
        allowFloatReadTuminLongTerm = prefs.getBoolean(KEY_FLOAT_READ_TUMIN_LONG, true),
        autoSyncImportantLongTerm = prefs.getBoolean(KEY_AUTO_SYNC_LONG, false),
    )

    fun save(config: FloatMemoryBridgeConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putBoolean(KEY_TUMIN_READ_FLOAT_RECENT, config.allowTuminReadFloatRecent)
            .putBoolean(KEY_FLOAT_READ_TUMIN_RECENT, config.allowFloatReadTuminRecent)
            .putInt(KEY_RECENT_LIMIT, config.sharedRecentContextLimit.coerceIn(1, 200))
            .putBoolean(KEY_TUMIN_READ_FLOAT_LONG, config.allowTuminReadFloatLongTerm)
            .putBoolean(KEY_FLOAT_READ_TUMIN_LONG, config.allowFloatReadTuminLongTerm)
            .putBoolean(KEY_AUTO_SYNC_LONG, config.autoSyncImportantLongTerm)
            .apply()
    }

    companion object {
        private const val PREFS = "float_memory_bridge_v1"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TUMIN_READ_FLOAT_RECENT = "tumin_read_float_recent"
        private const val KEY_FLOAT_READ_TUMIN_RECENT = "float_read_tumin_recent"
        private const val KEY_RECENT_LIMIT = "recent_limit"
        private const val KEY_TUMIN_READ_FLOAT_LONG = "tumin_read_float_long_term"
        private const val KEY_FLOAT_READ_TUMIN_LONG = "float_read_tumin_long_term"
        private const val KEY_AUTO_SYNC_LONG = "auto_sync_important_long_term"
    }
}
