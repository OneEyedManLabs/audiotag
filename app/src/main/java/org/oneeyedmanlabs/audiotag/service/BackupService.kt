package org.oneeyedmanlabs.audiotag.service

import android.content.Context
import android.util.Log
import org.oneeyedmanlabs.audiotag.data.TagEntity
import org.oneeyedmanlabs.audiotag.repository.TagRepository
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Smart backup service for AudioTagger
 * Handles file categorization and backup management
 */
class BackupService(private val context: Context, private val repository: TagRepository) {
    
    companion object {
        private const val TAG = "BackupService"
        private const val SMALL_FILE_THRESHOLD = 1_048_576 // 1MB in bytes
        private const val AUTO_BACKUP_LIMIT = 25_165_824 // ~24MB (leaving 1MB buffer for database/settings)
        
        private const val AUDIO_SMALL_DIR = "audio_small"
        private const val AUDIO_LARGE_DIR = "audio_large"
        private const val TEMP_DIR = "temp"
    }
    
    data class BackupStatus(
        val totalFiles: Int,
        val backedUpFiles: Int,
        val manualExportRequired: Int,
        val totalSize: Long,
        val backedUpSize: Long,
        val backupPercentage: Int
    )
    
    /**
     * Initialize backup directories and categorize existing files
     */
    suspend fun initializeBackupSystem() {
        Log.d(TAG, "Initializing backup system...")
        
        // Create backup directories
        createBackupDirectories()
        
        // Categorize existing audio files
        categorizeExistingFiles()
        
        Log.d(TAG, "Backup system initialized")
    }
    
    /**
     * Create necessary directories for backup organization
     */
    private fun createBackupDirectories() {
        val smallDir = File(context.filesDir, AUDIO_SMALL_DIR)
        val largeDir = File(context.filesDir, AUDIO_LARGE_DIR)
        val tempDir = File(context.filesDir, TEMP_DIR)
        
        smallDir.mkdirs()
        largeDir.mkdirs()
        tempDir.mkdirs()
    }
    
    /**
     * Categorize existing audio files into small/large based on size
     */
    private suspend fun categorizeExistingFiles() {
        try {
            val allTags = repository.getAllTagsList()
            var currentBackupSize = 0L
            
            for (tag in allTags) {
                if (tag.type == "audio") {
                    categorizeAudioFile(tag, currentBackupSize)
                    currentBackupSize = getCurrentBackupSize()
                }
            }
            
            Log.d(TAG, "Categorized ${allTags.size} files. Current backup size: ${currentBackupSize / 1024}KB")
        } catch (e: Exception) {
            Log.e(TAG, "Error categorizing existing files", e)
        }
    }
    
    /**
     * Categorize a single audio file and move it to appropriate directory
     */
    private suspend fun categorizeAudioFile(tag: TagEntity, currentBackupSize: Long) {
        val originalFile = File(tag.content)
        if (!originalFile.exists()) {
            Log.w(TAG, "Audio file not found: ${tag.content}")
            return
        }
        
        val fileSize = originalFile.length()
        val wouldExceedLimit = currentBackupSize + fileSize > AUTO_BACKUP_LIMIT
        
        val targetDir = if (fileSize < SMALL_FILE_THRESHOLD && !wouldExceedLimit) {
            AUDIO_SMALL_DIR
        } else {
            AUDIO_LARGE_DIR
        }
        
        moveFileToBackupDirectory(originalFile, targetDir, tag)
    }
    
    /**
     * Move file to appropriate backup directory and update database
     */
    private suspend fun moveFileToBackupDirectory(file: File, targetDirName: String, tag: TagEntity) {
        try {
            val targetDir = File(context.filesDir, targetDirName)
            val targetFile = File(targetDir, file.name)
            
            // Only move if not already in the correct location
            if (file.parent != targetDir.absolutePath) {
                copyFile(file, targetFile)
                
                // Update database with new path
                val updatedTag = tag.copy(content = targetFile.absolutePath)
                repository.insertTag(updatedTag)
                
                // Delete original file
                file.delete()
                
                Log.d(TAG, "Moved ${file.name} to $targetDirName (${targetFile.length()} bytes)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error moving file ${file.name}", e)
        }
    }
    
    /**
     * Handle new audio file and categorize it appropriately
     */
    suspend fun handleNewAudioFile(filePath: String): String {
        val file = File(filePath)
        if (!file.exists()) {
            Log.w(TAG, "New audio file not found: $filePath")
            return filePath
        }
        
        val fileSize = file.length()
        val currentBackupSize = getCurrentBackupSize()
        val wouldExceedLimit = currentBackupSize + fileSize > AUTO_BACKUP_LIMIT
        
        val targetDir = if (fileSize < SMALL_FILE_THRESHOLD && !wouldExceedLimit) {
            AUDIO_SMALL_DIR
        } else {
            AUDIO_LARGE_DIR
        }
        
        val targetDirFile = File(context.filesDir, targetDir)
        val targetFile = File(targetDirFile, file.name)
        
        return try {
            copyFile(file, targetFile)
            file.delete()
            
            Log.d(TAG, "Categorized new file ${file.name} to $targetDir (${targetFile.length()} bytes)")
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error handling new audio file", e)
            filePath // Return original path if categorization fails
        }
    }
    
    /**
     * Get current backup status and statistics
     */
    suspend fun getBackupStatus(): BackupStatus {
        return try {
            val allTags = repository.getAllTagsList()
            val audioTags = allTags.filter { it.type == "audio" }
            val textTags = allTags.filter { it.type == "tts" }
            
            var backedUpFiles = 0
            var backedUpSize = 0L
            var totalSize = 0L
            
            // Count text tags as automatically backed up (they're small and in database)
            backedUpFiles += textTags.size
            // Text tags have minimal size (just text), estimate ~1KB each
            backedUpSize += textTags.size * 1024L
            totalSize += textTags.size * 1024L
            
            // Process audio files
            for (tag in audioTags) {
                val file = File(tag.content)
                if (file.exists()) {
                    val fileSize = file.length()
                    totalSize += fileSize
                    
                    if (file.absolutePath.contains(AUDIO_SMALL_DIR)) {
                        backedUpFiles++
                        backedUpSize += fileSize
                    }
                }
            }
            
            val totalFiles = audioTags.size + textTags.size
            val backupPercentage = if (totalFiles > 0) {
                ((backedUpFiles.toFloat() / totalFiles) * 100).roundToInt()
            } else {
                100
            }
            
            BackupStatus(
                totalFiles = totalFiles,
                backedUpFiles = backedUpFiles,
                manualExportRequired = audioTags.size - (backedUpFiles - textTags.size),
                totalSize = totalSize,
                backedUpSize = backedUpSize,
                backupPercentage = backupPercentage
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting backup status", e)
            BackupStatus(0, 0, 0, 0L, 0L, 0)
        }
    }
    
    /**
     * Get current size of files in backup (small files directory)
     */
    private fun getCurrentBackupSize(): Long {
        val smallDir = File(context.filesDir, AUDIO_SMALL_DIR)
        return if (smallDir.exists()) {
            smallDir.listFiles()?.sumOf { it.length() } ?: 0L
        } else {
            0L
        }
    }
    
    /**
     * Copy file from source to destination
     */
    private fun copyFile(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }
    
    /**
     * Force re-categorization of all files (useful after settings change)
     */
    suspend fun recategorizeAllFiles() {
        Log.d(TAG, "Re-categorizing all files...")
        categorizeExistingFiles()
    }
}