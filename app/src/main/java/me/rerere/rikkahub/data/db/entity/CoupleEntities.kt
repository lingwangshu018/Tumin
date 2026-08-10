package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "couple_relationship", indices = [Index("assistant_id", unique = true)])
data class CoupleRelationshipEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("assistant_id") val assistantId: String,
    @ColumnInfo("started_at") val startedAt: Long,
    @ColumnInfo("created_at") val createdAt: Long,
)

@Entity(tableName = "couple_post", indices = [Index("relationship_id"), Index("created_at")])
data class CouplePostEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("relationship_id") val relationshipId: String,
    @ColumnInfo("author") val author: String,
    val content: String,
    @ColumnInfo("image_uri") val imageUri: String? = null,
    val liked: Boolean = false,
    @ColumnInfo("created_at") val createdAt: Long,
)

@Entity(
    tableName = "couple_comment",
    indices = [Index("relationship_id"), Index("post_id"), Index("created_at")],
)
data class CoupleCommentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("relationship_id") val relationshipId: String,
    @ColumnInfo("post_id") val postId: String,
    val author: String,
    val content: String,
    @ColumnInfo("created_at") val createdAt: Long,
)

@Entity(tableName = "couple_diary", indices = [Index("relationship_id"), Index("entry_date")])
data class CoupleDiaryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("relationship_id") val relationshipId: String,
    val author: String,
    val title: String,
    val content: String,
    @ColumnInfo("entry_date") val entryDate: Long,
    val folder: String? = null,
    val paper: String? = null,
    val reply: String? = null,
    @ColumnInfo("reply_at") val replyAt: Long? = null,
    @ColumnInfo("created_at") val createdAt: Long,
)

@Entity(tableName = "couple_anniversary", indices = [Index("relationship_id"), Index("event_date")])
data class CoupleAnniversaryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("relationship_id") val relationshipId: String,
    val title: String,
    @ColumnInfo("event_date") val eventDate: Long,
    val yearly: Boolean = true,
    @ColumnInfo("created_at") val createdAt: Long,
)
