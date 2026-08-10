package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.*

@Dao
interface CoupleDAO {
    @Query("SELECT * FROM couple_relationship LIMIT 1")
    fun relationship(): Flow<CoupleRelationshipEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRelationship(value: CoupleRelationshipEntity)

    @Query("DELETE FROM couple_relationship")
    suspend fun clearRelationship()

    @Query("SELECT * FROM couple_post WHERE relationship_id = :id ORDER BY created_at DESC")
    fun posts(id: String): Flow<List<CouplePostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePost(value: CouplePostEntity)

    @Delete suspend fun deletePost(value: CouplePostEntity)

    @Query("SELECT * FROM couple_comment WHERE relationship_id = :id ORDER BY created_at ASC")
    fun comments(id: String): Flow<List<CoupleCommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveComment(value: CoupleCommentEntity)

    @Delete suspend fun deleteComment(value: CoupleCommentEntity)

    @Query("SELECT * FROM couple_diary WHERE relationship_id = :id ORDER BY entry_date DESC")
    fun diaries(id: String): Flow<List<CoupleDiaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDiary(value: CoupleDiaryEntity)

    @Delete suspend fun deleteDiary(value: CoupleDiaryEntity)

    @Query("SELECT * FROM couple_anniversary WHERE relationship_id = :id ORDER BY event_date ASC")
    fun anniversaries(id: String): Flow<List<CoupleAnniversaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAnniversary(value: CoupleAnniversaryEntity)

    @Delete suspend fun deleteAnniversary(value: CoupleAnniversaryEntity)
}
