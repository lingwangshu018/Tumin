package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Safely brings every couple-space schema released during the 30..36 development
 * window to the current v37 shape without requiring the missing Room schema JSONs.
 *
 * The migration is intentionally idempotent: preview/test builds may already have
 * some of the tables or columns, so every change is guarded before it is applied.
 */
private class CoupleSchemaTo37Migration(startVersion: Int) : Migration(startVersion, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ensureCoupleCommentTable(db)
        ensureDiaryFolderTable(db)

        addColumnIfMissing(
            db,
            table = "couple_relationship",
            column = "journal_cover",
            definition = "TEXT NOT NULL DEFAULT 'rose_velvet'",
        )
        addColumnIfMissing(db, "couple_relationship", "journal_cover_title", "TEXT")
        addColumnIfMissing(db, "couple_relationship", "journal_cover_date", "TEXT")

        addColumnIfMissing(db, "couple_diary", "folder", "TEXT")
        addColumnIfMissing(db, "couple_diary", "paper", "TEXT")
        addColumnIfMissing(db, "couple_diary", "reply", "TEXT")
        addColumnIfMissing(db, "couple_diary", "reply_at", "INTEGER")
        addColumnIfMissing(db, "couple_diary", "reply_paper", "TEXT")
        addColumnIfMissing(
            db,
            table = "couple_diary",
            column = "bookmarked",
            definition = "INTEGER NOT NULL DEFAULT 0",
        )

        addColumnIfMissing(
            db,
            table = "couple_anniversary",
            column = "category",
            definition = "TEXT NOT NULL DEFAULT 'love'",
        )
        addColumnIfMissing(db, "couple_anniversary", "note", "TEXT")
        addColumnIfMissing(
            db,
            table = "couple_anniversary",
            column = "favorite",
            definition = "INTEGER NOT NULL DEFAULT 0",
        )
    }
}

val Migration_30_37: Migration = CoupleSchemaTo37Migration(30)
val Migration_31_37: Migration = CoupleSchemaTo37Migration(31)
val Migration_32_37: Migration = CoupleSchemaTo37Migration(32)
val Migration_33_37: Migration = CoupleSchemaTo37Migration(33)
val Migration_34_37: Migration = CoupleSchemaTo37Migration(34)
val Migration_35_37: Migration = CoupleSchemaTo37Migration(35)
val Migration_36_37: Migration = CoupleSchemaTo37Migration(36)

private fun ensureCoupleCommentTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `couple_comment` (
            `id` TEXT NOT NULL,
            `relationship_id` TEXT NOT NULL,
            `post_id` TEXT NOT NULL,
            `author` TEXT NOT NULL,
            `content` TEXT NOT NULL,
            `created_at` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent()
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_couple_comment_relationship_id` ON `couple_comment` (`relationship_id`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_couple_comment_post_id` ON `couple_comment` (`post_id`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_couple_comment_created_at` ON `couple_comment` (`created_at`)"
    )
}

private fun ensureDiaryFolderTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `couple_diary_folder` (
            `id` TEXT NOT NULL,
            `relationship_id` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `sort_order` INTEGER NOT NULL,
            `created_at` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent()
    )
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_couple_diary_folder_relationship_id_name` ON `couple_diary_folder` (`relationship_id`, `name`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_couple_diary_folder_sort_order` ON `couple_diary_folder` (`sort_order`)"
    )
}

private fun addColumnIfMissing(
    db: SupportSQLiteDatabase,
    table: String,
    column: String,
    definition: String,
) {
    if (!hasColumn(db, table, column)) {
        db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
    }
}

private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
    db.query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        if (nameIndex < 0) return false
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == column) return true
        }
    }
    return false
}
