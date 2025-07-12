package org.oneeyedmanlabs.audiotag.service

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.oneeyedmanlabs.audiotag.SettingsActivity
import org.oneeyedmanlabs.audiotag.data.TagEntity
import org.oneeyedmanlabs.audiotag.repository.TagRepository
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Service for importing AudioTagger export files
 * Handles ZIP files created by ExportService
 */
class ImportService(private val context: Context, private val repository: TagRepository) {
    
    companion object {
        private const val TAG = "ImportService"
        private const val SUPPORTED_VERSION = "1.0"
    }
    
    data class ImportResult(
        val success: Boolean,
        val tagsImported: Int = 0,
        val audioFilesImported: Int = 0,
        val settingsImported: Boolean = false,
        val skippedTags: Int = 0,
        val errorMessage: String? = null,
        val warnings: List<String> = emptyList()
    )
    
    data class ImportPreview(
        val isValid: Boolean,
        val exportType: String? = null,
        val tagCount: Int = 0,
        val audioFileCount: Int = 0,
        val textTagCount: Int = 0,
        val hasSettings: Boolean = false,
        val exportedGroups: List<String> = emptyList(),
        val createdDate: Long? = null,
        val errorMessage: String? = null
    )
    
    /**
     * Preview import file contents without importing
     */
    suspend fun previewImport(uri: Uri): ImportPreview = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportPreview(false, errorMessage = "Cannot open file")
            
            inputStream.use { stream ->
                ZipInputStream(stream).use { zipStream ->
                    var manifest: JSONObject? = null
                    var tagsJson: JSONObject? = null
                    var hasSettings = false
                    
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            "manifest.json" -> {
                                manifest = JSONObject(readZipEntryContent(zipStream))
                            }
                            "tags.json" -> {
                                tagsJson = JSONObject(readZipEntryContent(zipStream))
                            }
                            "settings.json" -> {
                                hasSettings = true
                            }
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                    
                    if (manifest == null || tagsJson == null) {
                        return@withContext ImportPreview(false, errorMessage = "Invalid export file format")
                    }
                    
                    // Validate version compatibility
                    val exportVersion = manifest.optString("export_version", "unknown")
                    if (exportVersion != SUPPORTED_VERSION) {
                        Log.w(TAG, "Version mismatch: export=$exportVersion, supported=$SUPPORTED_VERSION")
                    }
                    
                    val tags = tagsJson.getJSONArray("tags")
                    var audioCount = 0
                    var textCount = 0
                    
                    for (i in 0 until tags.length()) {
                        val tag = tags.getJSONObject(i)
                        when (tag.getString("type")) {
                            "audio" -> audioCount++
                            "tts" -> textCount++
                        }
                    }
                    
                    val exportedGroups = if (manifest.has("exported_groups")) {
                        val groupsArray = manifest.getJSONArray("exported_groups")
                        List(groupsArray.length()) { i -> groupsArray.getString(i) }
                    } else {
                        emptyList()
                    }
                    
                    ImportPreview(
                        isValid = true,
                        exportType = manifest.optString("export_type", "UNKNOWN"),
                        tagCount = tags.length(),
                        audioFileCount = audioCount,
                        textTagCount = textCount,
                        hasSettings = hasSettings,
                        exportedGroups = exportedGroups,
                        createdDate = manifest.optLong("created_date", 0L)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error previewing import", e)
            ImportPreview(false, errorMessage = e.message ?: "Unknown error")
        }
    }
    
    /**
     * Import export file with options
     */
    suspend fun importFile(
        uri: Uri,
        replaceExisting: Boolean = false,
        importSettings: Boolean = false
    ): ImportResult = withContext(Dispatchers.IO) {
        
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult(false, errorMessage = "Cannot open file")
            
            val tempDir = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            
            try {
                // Extract ZIP contents
                val extractedFiles = extractZipContents(inputStream, tempDir)
                
                // Validate required files
                val manifestFile = File(tempDir, "manifest.json")
                val tagsFile = File(tempDir, "tags.json")
                
                if (!manifestFile.exists() || !tagsFile.exists()) {
                    return@withContext ImportResult(false, errorMessage = "Invalid export file: missing required files")
                }
                
                // Read manifest and tags
                val manifest = JSONObject(manifestFile.readText())
                val tagsJson = JSONObject(tagsFile.readText())
                
                // Import tags and audio files
                val importStats = importTagsAndAudio(tagsJson, tempDir, replaceExisting)
                
                // Import settings if requested
                var settingsImported = false
                if (importSettings) {
                    val settingsFile = File(tempDir, "settings.json")
                    if (settingsFile.exists()) {
                        settingsImported = importAppSettings(settingsFile)
                    }
                }
                
                Log.d(TAG, "Import completed: ${importStats.first} tags, ${importStats.second} audio files")
                
                ImportResult(
                    success = true,
                    tagsImported = importStats.first,
                    audioFilesImported = importStats.second,
                    settingsImported = settingsImported,
                    skippedTags = importStats.third
                )
                
            } finally {
                // Clean up temp directory
                tempDir.deleteRecursively()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            ImportResult(false, errorMessage = e.message ?: "Unknown error during import")
        }
    }
    
    /**
     * Extract ZIP contents to temporary directory
     */
    private fun extractZipContents(inputStream: InputStream, targetDir: File): List<String> {
        val extractedFiles = mutableListOf<String>()
        
        ZipInputStream(inputStream).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val targetFile = File(targetDir, entry.name)
                    targetFile.parentFile?.mkdirs()
                    
                    FileOutputStream(targetFile).use { output ->
                        zipStream.copyTo(output)
                    }
                    
                    extractedFiles.add(entry.name)
                    Log.d(TAG, "Extracted: ${entry.name} (${targetFile.length()} bytes)")
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }
        
        return extractedFiles
    }
    
    /**
     * Import tags and associated audio files
     * Returns: (tagsImported, audioFilesImported, skippedTags)
     */
    private suspend fun importTagsAndAudio(
        tagsJson: JSONObject,
        tempDir: File,
        replaceExisting: Boolean
    ): Triple<Int, Int, Int> {
        
        val tags = tagsJson.getJSONArray("tags")
        var tagsImported = 0
        var audioFilesImported = 0
        var skippedTags = 0
        
        for (i in 0 until tags.length()) {
            try {
                val tagData = tags.getJSONObject(i)
                val tagId = tagData.getString("tagId")
                
                // Check if tag already exists
                val existingTag = repository.getTag(tagId)
                if (existingTag != null && !replaceExisting) {
                    skippedTags++
                    Log.d(TAG, "Skipped existing tag: $tagId")
                    continue
                }
                
                // Import audio file if needed
                var finalContent = tagData.getString("content")
                if (tagData.getString("type") == "audio") {
                    val audioFileName = File(finalContent).name
                    val sourceAudioFile = File(tempDir, "audio/$audioFileName")
                    
                    if (sourceAudioFile.exists()) {
                        // Copy audio file to app storage
                        finalContent = copyAudioFileToStorage(sourceAudioFile, audioFileName)
                        audioFilesImported++
                    } else {
                        Log.w(TAG, "Audio file not found in export: $audioFileName")
                        skippedTags++
                        continue
                    }
                }
                
                // Create tag entity
                val groups = mutableListOf<String>()
                val groupsArray = tagData.getJSONArray("groups")
                for (j in 0 until groupsArray.length()) {
                    groups.add(groupsArray.getString(j))
                }
                
                val tagEntity = TagEntity(
                    tagId = tagId,
                    type = tagData.getString("type"),
                    content = finalContent,
                    title = tagData.getString("title"),
                    description = tagData.optString("description").takeIf { it.isNotEmpty() },
                    groups = groups,
                    locale = tagData.optString("locale").takeIf { it.isNotEmpty() },
                    createdAt = tagData.getLong("createdAt")
                )
                
                // Save to database
                repository.insertTag(tagEntity)
                tagsImported++
                
                Log.d(TAG, "Imported tag: ${tagEntity.title}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error importing tag $i", e)
                skippedTags++
            }
        }
        
        return Triple(tagsImported, audioFilesImported, skippedTags)
    }
    
    /**
     * Copy audio file to appropriate app storage location
     */
    private suspend fun copyAudioFileToStorage(sourceFile: File, fileName: String): String {
        // Use backup service to categorize the file appropriately
        val backupService = BackupService(context, repository)
        
        // First copy to a temporary location in files dir
        val tempFile = File(context.filesDir, "temp_import_$fileName")
        FileInputStream(sourceFile).use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        
        // Let backup service categorize it
        val finalPath = backupService.handleNewAudioFile(tempFile.absolutePath)
        
        return finalPath
    }
    
    /**
     * Import app settings
     */
    private fun importAppSettings(settingsFile: File): Boolean {
        return try {
            val settings = JSONObject(settingsFile.readText())
            
            if (settings.has("tts_enabled")) {
                SettingsActivity.setTTSEnabled(context, settings.getBoolean("tts_enabled"))
            }
            
            if (settings.has("theme_option")) {
                val themeString = settings.getString("theme_option")
                try {
                    val themeOption = org.oneeyedmanlabs.audiotag.ui.theme.ThemeOption.valueOf(themeString)
                    SettingsActivity.setThemeOption(context, themeOption)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Unknown theme option: $themeString")
                }
            }
            
            Log.d(TAG, "Settings imported successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error importing settings", e)
            false
        }
    }
    
    /**
     * Read content from ZIP entry
     */
    private fun readZipEntryContent(zipStream: ZipInputStream): String {
        return zipStream.readBytes().toString(Charsets.UTF_8)
    }
}