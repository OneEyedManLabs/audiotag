package org.oneeyedmanlabs.audiotag.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [TagEntity::class],
    version = 2,
    exportSchema = false
)
abstract class TagDatabase : RoomDatabase() {
    
    abstract fun tagDao(): TagDao
    
    companion object {
        @Volatile
        private var INSTANCE: TagDatabase? = null
        
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns to existing table
                database.execSQL("ALTER TABLE tags ADD COLUMN description TEXT")
                database.execSQL("ALTER TABLE tags ADD COLUMN groups TEXT NOT NULL DEFAULT ''")
                
                // Rename 'label' column to 'title'
                database.execSQL("""
                    CREATE TABLE tags_new (
                        tagId TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        content TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        groups TEXT NOT NULL DEFAULT '',
                        locale TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """)
                
                // Copy data from old table to new table
                database.execSQL("""
                    INSERT INTO tags_new (tagId, type, content, title, description, groups, locale, createdAt)
                    SELECT tagId, type, content, label, NULL, '', locale, createdAt FROM tags
                """)
                
                // Drop old table and rename new table
                database.execSQL("DROP TABLE tags")
                database.execSQL("ALTER TABLE tags_new RENAME TO tags")
            }
        }
        
        fun getDatabase(context: Context): TagDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TagDatabase::class.java,
                    "tag_database"
                ).addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}