package org.oneeyedmanlabs.audiotag.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(tableName = "tags")
@TypeConverters(TagConverters::class)
data class TagEntity(
    @PrimaryKey 
    val tagId: String,
    val type: String, // "audio" or "tts"
    val content: String, // file path for audio, text for TTS
    val title: String, // renamed from "label" - user-defined name for management
    val description: String? = null, // optional description
    val groups: List<String> = emptyList(), // list of group names
    val locale: String? = null, // for future TTS locale support
    val createdAt: Long = System.currentTimeMillis()
)

class TagConverters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isEmpty()) emptyList() else value.split(",")
    }
}