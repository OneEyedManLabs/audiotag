package org.oneeyedmanlabs.audiotag.service

import android.app.Application
import org.oneeyedmanlabs.audiotag.data.TagDatabase
import org.oneeyedmanlabs.audiotag.ui.theme.ThemeManager

class AudioTaggerApplication : Application() {
    
    val database by lazy { TagDatabase.getDatabase(this) }
    val repository by lazy { 
        org.oneeyedmanlabs.audiotag.repository.TagRepository(database.tagDao()) 
    }
    val backupService by lazy {
        BackupService(this, repository)
    }
    val exportService by lazy {
        ExportService(this, repository)
    }
    val importService by lazy {
        ImportService(this, repository)
    }
    
    override fun onCreate() {
        super.onCreate()
        // Initialize theme manager with saved theme preference
        ThemeManager.initialize(this)
        
        // Initialize backup system in background
        // Note: We can't use lifecycleScope in Application, so we'll initialize on first use
    }
}