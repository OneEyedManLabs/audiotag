package org.oneeyedmanlabs.audiotag.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    
    @Query("SELECT * FROM tags ORDER BY createdAt DESC")
    fun getAllTags(): Flow<List<TagEntity>>
    
    @Query("SELECT * FROM tags WHERE tagId = :tagId")
    suspend fun getTag(tagId: String): TagEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity)
    
    @Update
    suspend fun updateTag(tag: TagEntity)
    
    @Delete
    suspend fun deleteTag(tag: TagEntity)
    
    @Query("DELETE FROM tags WHERE tagId = :tagId")
    suspend fun deleteTagById(tagId: String)
    
    @Query("SELECT COUNT(*) FROM tags")
    suspend fun getTagCount(): Int
}