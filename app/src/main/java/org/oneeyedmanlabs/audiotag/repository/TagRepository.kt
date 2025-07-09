package org.oneeyedmanlabs.audiotag.repository

import org.oneeyedmanlabs.audiotag.data.TagDao
import org.oneeyedmanlabs.audiotag.data.TagEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
class TagRepository(
    private val tagDao: TagDao
) {
    
    fun getAllTags(): Flow<List<TagEntity>> = tagDao.getAllTags()
    
    suspend fun getAllTagsList(): List<TagEntity> {
        return tagDao.getAllTags().first()
    }
    
    suspend fun getTag(tagId: String): TagEntity? = tagDao.getTag(tagId)
    
    suspend fun insertTag(tag: TagEntity) = tagDao.insertTag(tag)
    
    suspend fun updateTag(tag: TagEntity) = tagDao.updateTag(tag)
    
    suspend fun deleteTag(tag: TagEntity) = tagDao.deleteTag(tag)
    
    suspend fun deleteTagById(tagId: String) = tagDao.deleteTagById(tagId)
    
    suspend fun getTagCount(): Int = tagDao.getTagCount()
}