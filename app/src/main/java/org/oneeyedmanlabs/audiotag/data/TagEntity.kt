package org.oneeyedmanlabs.audiotag.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey 
    val tagId: String,
    val type: String, // "audio" or "tts"
    val content: String, // file path for audio, text for TTS
    val label: String, // user-defined name for management
    val locale: String? = null, // for future TTS locale support
    val createdAt: Long = System.currentTimeMillis()
)