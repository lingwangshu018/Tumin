package me.rerere.rikkahub.data.repository

import java.util.UUID
import me.rerere.rikkahub.data.db.dao.CoupleDAO
import me.rerere.rikkahub.data.db.entity.*
import org.json.JSONArray

class CoupleRepository(private val dao: CoupleDAO) {
    val relationship = dao.relationship()

    fun posts(id: String) = dao.posts(id)
    fun comments(id: String) = dao.comments(id)
    fun diaries(id: String) = dao.diaries(id)
    fun anniversaries(id: String) = dao.anniversaries(id)

    suspend fun bind(assistantId: String, startedAt: Long) {
        dao.clearRelationship()
        dao.saveRelationship(CoupleRelationshipEntity(UUID.randomUUID().toString(), assistantId, startedAt, System.currentTimeMillis()))
    }

    suspend fun addPost(
        relationshipId: String,
        author: String,
        content: String,
        imageUris: List<String> = emptyList(),
    ): CouplePostEntity {
        val post = CouplePostEntity(
            id = UUID.randomUUID().toString(),
            relationshipId = relationshipId,
            author = author,
            content = content,
            imageUri = imageUris.take(9).takeIf { it.isNotEmpty() }?.let { JSONArray(it).toString() },
            createdAt = System.currentTimeMillis(),
        )
        dao.savePost(post)
        return post
    }

    suspend fun toggleLike(post: CouplePostEntity) = dao.savePost(post.copy(liked = !post.liked))
    suspend fun deletePost(post: CouplePostEntity) = dao.deletePost(post)

    suspend fun addComment(
        relationshipId: String,
        postId: String,
        author: String,
        content: String,
    ): CoupleCommentEntity {
        val comment = CoupleCommentEntity(
            id = UUID.randomUUID().toString(),
            relationshipId = relationshipId,
            postId = postId,
            author = author,
            content = content,
            createdAt = System.currentTimeMillis(),
        )
        dao.saveComment(comment)
        return comment
    }

    suspend fun deleteComment(comment: CoupleCommentEntity) = dao.deleteComment(comment)

    suspend fun addDiary(
        relationshipId: String,
        author: String,
        title: String,
        content: String,
        date: Long,
        folder: String = "全部心事",
        paper: String = "ivory",
    ): CoupleDiaryEntity {
        val entry = CoupleDiaryEntity(
            id = UUID.randomUUID().toString(),
            relationshipId = relationshipId,
            author = author,
            title = title,
            content = content,
            entryDate = date,
            folder = folder,
            paper = paper,
            createdAt = System.currentTimeMillis(),
        )
        dao.saveDiary(entry)
        return entry
    }

    suspend fun saveDiaryReply(entry: CoupleDiaryEntity, reply: String) = dao.saveDiary(
        entry.copy(reply = reply, replyAt = System.currentTimeMillis())
    )

    suspend fun deleteDiary(entry: CoupleDiaryEntity) = dao.deleteDiary(entry)

    suspend fun addAnniversary(relationshipId: String, title: String, date: Long, yearly: Boolean) = dao.saveAnniversary(
        CoupleAnniversaryEntity(UUID.randomUUID().toString(), relationshipId, title, date, yearly, System.currentTimeMillis())
    )

    suspend fun deleteAnniversary(entry: CoupleAnniversaryEntity) = dao.deleteAnniversary(entry)
}
