/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import me.rerere.ai.core.TokenUsage
import me.rerere.rikkahub.data.db.dao.*
import me.rerere.rikkahub.data.db.entity.*
import me.rerere.rikkahub.data.security.SecurityAuditDao
import me.rerere.rikkahub.data.security.SecurityAuditEntity
import me.rerere.rikkahub.workflow.db.WorkflowDao
import me.rerere.rikkahub.workflow.db.WorkflowEntity
import me.rerere.rikkahub.workflow.db.WorkflowRunDao
import me.rerere.rikkahub.workflow.db.WorkflowRunEntity
import me.rerere.rikkahub.data.db.migrations.Migration_16_17
import me.rerere.rikkahub.data.db.migrations.Migration_24_25
import me.rerere.rikkahub.data.db.migrations.Migration_8_9
import me.rerere.rikkahub.utils.JsonInstant

@Database(
    entities = [
        ConversationEntity::class,
        MemoryEntity::class,
        GenMediaEntity::class,
        MessageNodeEntity::class,
        ManagedFileEntity::class,
        FavoriteEntity::class,
        MemoryBankEntity::class,
        WorkspaceEntity::class,
        FolderEntity::class,
        WorkflowEntity::class,
        WorkflowRunEntity::class,
        SshHostEntity::class,
        SecurityAuditEntity::class,
        CoupleRelationshipEntity::class,
        CouplePostEntity::class,
        CoupleCommentEntity::class,
        CoupleDiaryEntity::class,
        CoupleDiaryFolderEntity::class,
        CoupleAnniversaryEntity::class,
    ],
    version = 33,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9, spec = Migration_8_9::class),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 16, to = 17, spec = Migration_16_17::class),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 22, to = 23),
        AutoMigration(from = 26, to = 27),
        AutoMigration(from = 27, to = 28),
        AutoMigration(from = 28, to = 29),
        AutoMigration(from = 30, to = 31),
        AutoMigration(from = 31, to = 32),
        AutoMigration(from = 32, to = 33),
    ]
)
@TypeConverters(TokenUsageConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDAO
    abstract fun memoryDao(): MemoryDAO
    abstract fun genMediaDao(): GenMediaDAO
    abstract fun messageNodeDao(): MessageNodeDAO
    abstract fun managedFileDao(): ManagedFileDAO
    abstract fun favoriteDao(): FavoriteDAO
    abstract fun memoryBankDao(): MemoryBankDAO
    abstract fun workspaceDao(): WorkspaceDAO
    abstract fun folderDao(): FolderDAO
    abstract fun workflowDao(): WorkflowDao
    abstract fun workflowRunDao(): WorkflowRunDao
    abstract fun sshHostDao(): SshHostDao
    abstract fun securityAuditDao(): SecurityAuditDao
    abstract fun coupleDao(): CoupleDAO
}

object TokenUsageConverter {
    @TypeConverter
    fun fromTokenUsage(usage: TokenUsage?): String = JsonInstant.encodeToString(usage)

    @TypeConverter
    fun toTokenUsage(usage: String): TokenUsage? = JsonInstant.decodeFromString(usage)
}
