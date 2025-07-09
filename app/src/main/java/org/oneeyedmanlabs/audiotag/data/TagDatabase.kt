package org.oneeyedmanlabs.audiotag.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [TagEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TagDatabase : RoomDatabase() {
    
    abstract fun tagDao(): TagDao
    
    companion object {
        @Volatile
        private var INSTANCE: TagDatabase? = null
        
        fun getDatabase(context: Context): TagDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TagDatabase::class.java,
                    "tag_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}