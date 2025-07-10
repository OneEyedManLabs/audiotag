package org.oneeyedmanlabs.audiotag.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.oneeyedmanlabs.audiotag.SettingsActivity
import org.oneeyedmanlabs.audiotag.data.TagEntity
import org.oneeyedmanlabs.audiotag.repository.TagRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unified export service for AudioTagger
 * Handles: Full backup, group export, large files export, sharing
 */
class ExportService(private val context: Context, private val repository: TagRepository) {
    
    companion object {
        private const val TAG = "ExportService"
        private const val EXPORT_VERSION = "1.0"
        private const val AUTHORITY = "org.oneeyedmanlabs.audiotag.fileprovider"
    }
    
    enum class ExportType {
        FULL_BACKUP,
        GROUP_EXPORT,
        LARGE_FILES_ONLY,
        VISIBLE_TAGS
    }
    
    data class ExportOptions(
        val type: ExportType,
        val selectedGroups: Set<String> = emptySet(),
        val specificTags: List<TagEntity> = emptyList(),
        val includeSettings: Boolean = true,
        val includeAllAudio: Boolean = true
    )
    
    data class ExportResult(
        val success: Boolean,
        val filePath: String? = null,
        val fileUri: Uri? = null,
        val fileName: String? = null,
        val fileSize: Long = 0,
        val tagCount: Int = 0,
        val audioFileCount: Int = 0,
        val errorMessage: String? = null
    )
    
    /**
     * Create export based on options and return result for sharing
     */
    suspend fun createExport(options: ExportOptions): ExportResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting export: ${options.type}")
            
            // Get tags to export based on type
            val tagsToExport = getTagsForExport(options)
            if (tagsToExport.isEmpty() && options.type != ExportType.FULL_BACKUP) {
                return@withContext ExportResult(
                    success = false,
                    errorMessage = "No tags found to export"
                )
            }
            
            // Generate filename
            val fileName = generateFileName(options)
            val exportFile = File(context.cacheDir, fileName)
            
            // Create export ZIP
            createExportZip(exportFile, tagsToExport, options)
            
            // Create content URI for sharing
            val contentUri = FileProvider.getUriForFile(
                context,
                AUTHORITY,
                exportFile
            )
            
            val audioFileCount = tagsToExport.count { it.type == "audio" }
            
            Log.d(TAG, "Export created: $fileName (${exportFile.length()} bytes, ${tagsToExport.size} tags)")
            
            ExportResult(
                success = true,
                filePath = exportFile.absolutePath,
                fileUri = contentUri,
                fileName = fileName,
                fileSize = exportFile.length(),
                tagCount = tagsToExport.size,
                audioFileCount = audioFileCount
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            ExportResult(
                success = false,
                errorMessage = e.message ?: "Unknown error during export"
            )
        }
    }
    
    /**
     * Get tags based on export options
     */
    private suspend fun getTagsForExport(options: ExportOptions): List<TagEntity> {
        return when (options.type) {
            ExportType.FULL_BACKUP -> {
                repository.getAllTagsList()
            }
            ExportType.GROUP_EXPORT -> {
                val allTags = repository.getAllTagsList()
                allTags.filter { tag ->
                    tag.groups.any { group -> options.selectedGroups.contains(group) }
                }
            }
            ExportType.LARGE_FILES_ONLY -> {
                val allTags = repository.getAllTagsList()
                allTags.filter { tag ->
                    tag.type == "audio" && File(tag.content).let { file ->
                        file.exists() && (file.absolutePath.contains("audio_large") || file.length() >= 1024 * 1024) // 1MB threshold
                    }
                }
            }
            ExportType.VISIBLE_TAGS -> {
                options.specificTags
            }
        }
    }
    
    /**
     * Generate appropriate filename for export type
     */
    private fun generateFileName(options: ExportOptions): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = dateFormat.format(Date())
        
        return when (options.type) {
            ExportType.FULL_BACKUP -> "AudioTagger_FullBackup_$date.zip"
            ExportType.GROUP_EXPORT -> {
                val groupName = options.selectedGroups.firstOrNull() ?: "Groups"
                val sanitizedName = groupName.replace(Regex("[^a-zA-Z0-9]"), "_")
                "AudioTagger_${sanitizedName}_$date.zip"
            }
            ExportType.LARGE_FILES_ONLY -> "AudioTagger_LargeFiles_$date.zip"
            ExportType.VISIBLE_TAGS -> "AudioTagger_VisibleTags_$date.zip"
        }
    }
    
    /**
     * Create the export ZIP file
     */
    private suspend fun createExportZip(
        exportFile: File,
        tags: List<TagEntity>,
        options: ExportOptions
    ) = withContext(Dispatchers.IO) {
        
        ZipOutputStream(FileOutputStream(exportFile)).use { zip ->
            
            // Add manifest.json
            addManifestToZip(zip, options, tags)
            
            // Add tags.json
            addTagsToZip(zip, tags)
            
            // Add settings if requested
            if (options.includeSettings) {
                addSettingsToZip(zip)
            }
            
            // Add audio files
            if (options.includeAllAudio) {
                addAudioFilesToZip(zip, tags)
            }
            
            // Add README.txt
            addReadmeToZip(zip, options)
        }
    }
    
    /**
     * Add manifest with export metadata
     */
    private fun addManifestToZip(zip: ZipOutputStream, options: ExportOptions, tags: List<TagEntity>) {
        val manifest = JSONObject().apply {
            put("export_version", EXPORT_VERSION)
            put("export_type", options.type.name)
            put("created_date", System.currentTimeMillis())
            put("app_version", "2.0") // TODO: Get from BuildConfig
            put("tag_count", tags.size)
            put("audio_file_count", tags.count { it.type == "audio" })
            put("text_tag_count", tags.count { it.type == "tts" })
            if (options.selectedGroups.isNotEmpty()) {
                put("exported_groups", JSONArray(options.selectedGroups.toList()))
            }
        }
        
        addTextFileToZip(zip, "manifest.json", manifest.toString(2))
    }
    
    /**
     * Add tags database as JSON
     */
    private fun addTagsToZip(zip: ZipOutputStream, tags: List<TagEntity>) {
        val tagsArray = JSONArray()
        
        for (tag in tags) {
            val tagObject = JSONObject().apply {
                put("tagId", tag.tagId)
                put("type", tag.type)
                put("content", if (tag.type == "audio") "audio/${File(tag.content).name}" else tag.content)
                put("title", tag.title)
                put("description", tag.description)
                put("groups", JSONArray(tag.groups))
                put("locale", tag.locale)
                put("createdAt", tag.createdAt)
            }
            tagsArray.put(tagObject)
        }
        
        val tagsJson = JSONObject().apply {
            put("tags", tagsArray)
            put("export_timestamp", System.currentTimeMillis())
        }
        
        addTextFileToZip(zip, "tags.json", tagsJson.toString(2))
    }
    
    /**
     * Add app settings to export
     */
    private fun addSettingsToZip(zip: ZipOutputStream) {
        try {
            val settings = JSONObject().apply {
                put("tts_enabled", SettingsActivity.getTTSEnabled(context))
                put("theme_option", SettingsActivity.getThemeOption(context).name)
                put("export_timestamp", System.currentTimeMillis())
            }
            
            addTextFileToZip(zip, "settings.json", settings.toString(2))
        } catch (e: Exception) {
            Log.w(TAG, "Could not export settings", e)
        }
    }
    
    /**
     * Add audio files to ZIP
     */
    private fun addAudioFilesToZip(zip: ZipOutputStream, tags: List<TagEntity>) {
        val audioTags = tags.filter { it.type == "audio" }
        
        for (tag in audioTags) {
            try {
                val audioFile = File(tag.content)
                if (audioFile.exists()) {
                    val fileName = audioFile.name
                    zip.putNextEntry(ZipEntry("audio/$fileName"))
                    
                    FileInputStream(audioFile).use { input ->
                        input.copyTo(zip)
                    }
                    
                    zip.closeEntry()
                    Log.d(TAG, "Added audio file: $fileName (${audioFile.length()} bytes)")
                } else {
                    Log.w(TAG, "Audio file not found: ${tag.content}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding audio file: ${tag.content}", e)
            }
        }
    }
    
    /**
     * Add human-readable README
     */
    private fun addReadmeToZip(zip: ZipOutputStream, options: ExportOptions) {
        val readme = buildString {
            appendLine("AudioTagger Export")
            appendLine("=".repeat(50))
            appendLine()
            appendLine("Export Type: ${options.type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}")
            appendLine("Created: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine()
            appendLine("Contents:")
            appendLine("- manifest.json: Export metadata and information")
            appendLine("- tags.json: Tag database entries and metadata")
            appendLine("- audio/: Audio files referenced by tags")
            if (options.includeSettings) {
                appendLine("- settings.json: App settings and preferences")
            }
            appendLine()
            appendLine("To import:")
            appendLine("1. Open AudioTagger app")
            appendLine("2. Go to Settings > Backup & Data")
            appendLine("3. Choose 'Import Export File'")
            appendLine("4. Select this file")
            appendLine()
            appendLine("Compatible with AudioTagger v2.0+")
            appendLine("Visit: https://github.com/oneeyedmanlabs/audiotagger")
        }
        
        addTextFileToZip(zip, "README.txt", readme)
    }
    
    /**
     * Helper to add text content to ZIP
     */
    private fun addTextFileToZip(zip: ZipOutputStream, fileName: String, content: String) {
        zip.putNextEntry(ZipEntry(fileName))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
    
    /**
     * Share export file using Android share intent
     */
    suspend fun shareExport(exportResult: ExportResult, title: String = "Share AudioTagger Export"): Intent? {
        if (!exportResult.success || exportResult.fileUri == null) {
            return null
        }
        
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, exportResult.fileUri)
            putExtra(Intent.EXTRA_SUBJECT, exportResult.fileName)
            putExtra(Intent.EXTRA_TEXT, buildString {
                appendLine("AudioTagger Export")
                appendLine()
                appendLine("📁 File: ${exportResult.fileName}")
                appendLine("📊 ${exportResult.tagCount} tags, ${exportResult.audioFileCount} audio files")
                appendLine("💾 Size: ${formatFileSize(exportResult.fileSize)}")
                appendLine()
                appendLine("Import this file in AudioTagger app:")
                appendLine("Settings > Backup & Data > Import Export File")
            })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    /**
     * Format file size for display
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))}MB"
        }
    }
}