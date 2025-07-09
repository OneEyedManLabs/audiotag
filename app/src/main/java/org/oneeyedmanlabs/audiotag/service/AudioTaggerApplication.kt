package org.oneeyedmanlabs.audiotag.service

import android.app.Application
import org.oneeyedmanlabs.audiotag.data.TagDatabase

class AudioTaggerApplication : Application() {
    
    val database by lazy { TagDatabase.getDatabase(this) }
    val repository by lazy { 
        org.oneeyedmanlabs.audiotag.repository.TagRepository(database.tagDao()) 
    }
}