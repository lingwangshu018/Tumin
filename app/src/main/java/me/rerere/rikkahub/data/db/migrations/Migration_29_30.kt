package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_29_30 : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `couple_relationship` (`id` TEXT NOT NULL, `assistant_id` TEXT NOT NULL, `started_at` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_couple_relationship_assistant_id` ON `couple_relationship` (`assistant_id`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `couple_post` (`id` TEXT NOT NULL, `relationship_id` TEXT NOT NULL, `author` TEXT NOT NULL, `content` TEXT NOT NULL, `image_uri` TEXT, `liked` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_couple_post_relationship_id` ON `couple_post` (`relationship_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_couple_post_created_at` ON `couple_post` (`created_at`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `couple_diary` (`id` TEXT NOT NULL, `relationship_id` TEXT NOT NULL, `author` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `entry_date` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_couple_diary_relationship_id` ON `couple_diary` (`relationship_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_couple_diary_entry_date` ON `couple_diary` (`entry_date`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `couple_anniversary` (`id` TEXT NOT NULL, `relationship_id` TEXT NOT NULL, `title` TEXT NOT NULL, `event_date` INTEGER NOT NULL, `yearly` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_couple_anniversary_relationship_id` ON `couple_anniversary` (`relationship_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_couple_anniversary_event_date` ON `couple_anniversary` (`event_date`)")
    }
}
