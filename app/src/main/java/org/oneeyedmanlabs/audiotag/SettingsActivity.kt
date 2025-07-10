package org.oneeyedmanlabs.audiotag

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.oneeyedmanlabs.audiotag.service.AudioTaggerApplication
import org.oneeyedmanlabs.audiotag.service.BackupService
import org.oneeyedmanlabs.audiotag.service.ExportService
import org.oneeyedmanlabs.audiotag.service.ImportService
import org.oneeyedmanlabs.audiotag.service.TTSService
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.DocumentsContract
import org.oneeyedmanlabs.audiotag.ui.theme.AudioTagTheme
import org.oneeyedmanlabs.audiotag.ui.theme.ThemeManager
import org.oneeyedmanlabs.audiotag.ui.theme.ThemeOption
import org.oneeyedmanlabs.audiotag.ui.theme.AppTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Settings screen for AudioTagger app
 * Provides user preferences for accessibility and behavior
 */
class SettingsActivity : ComponentActivity() {
    
    private lateinit var ttsService: TTSService
    
    // Import state
    private var showImportDialog = mutableStateOf(false)
    private var importPreview = mutableStateOf<ImportService.ImportPreview?>(null)
    private var isImporting = mutableStateOf(false)
    private var backupStatus = mutableStateOf<BackupService.BackupStatus?>(null)
    private var currentImportUri: android.net.Uri? = null
    
    // File picker for imports
    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { handleImportFile(it) }
    }
    
    companion object {
        const val PREFS_NAME = "AudioTagSettings"
        const val PREF_TTS_ENABLED = "tts_enabled"
        const val PREF_THEME_OPTION = "theme_option"
        
        fun getTTSEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(PREF_TTS_ENABLED, true) // Default to enabled
        }
        
        fun setTTSEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(PREF_TTS_ENABLED, enabled).apply()
        }
        
        fun getThemeOption(context: Context): ThemeOption {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val themeString = prefs.getString(PREF_THEME_OPTION, ThemeOption.SYSTEM.name)
            return try {
                ThemeOption.valueOf(themeString ?: ThemeOption.SYSTEM.name)
            } catch (e: IllegalArgumentException) {
                ThemeOption.SYSTEM
            }
        }
        
        fun setThemeOption(context: Context, themeOption: ThemeOption) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(PREF_THEME_OPTION, themeOption.name).apply()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize TTS service
        ttsService = TTSService(this)
        ttsService.initialize()
        
        // Initialize backup system and load status
        lifecycleScope.launch {
            try {
                val app = application as AudioTaggerApplication
                app.backupService.initializeBackupSystem()
                backupStatus.value = app.backupService.getBackupStatus()
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error initializing backup system", e)
            }
        }
        
        setContent {
            val currentTheme by ThemeManager.getCurrentThemeState()
            AudioTagTheme(themeOption = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        onBack = { finish() },
                        ttsService = ttsService,
                        showImportDialog = showImportDialog.value,
                        importPreview = importPreview.value,
                        isImporting = isImporting.value,
                        backupStatus = backupStatus.value,
                        onImportFile = { importFileLauncher.launch(arrayOf("application/zip")) },
                        onConfirmImport = { importSettings, replaceExisting ->
                            performImport(importSettings, replaceExisting)
                        },
                        onDismissImportDialog = { showImportDialog.value = false },
                        onFullBackup = {
                            lifecycleScope.launch {
                                performExport(
                                    ExportService.ExportType.FULL_BACKUP,
                                    (application as AudioTaggerApplication).exportService,
                                    ttsService,
                                    this@SettingsActivity
                                )
                            }
                        },
                        onLargeFilesExport = {
                            lifecycleScope.launch {
                                performExport(
                                    ExportService.ExportType.LARGE_FILES_ONLY,
                                    (application as AudioTaggerApplication).exportService,
                                    ttsService,
                                    this@SettingsActivity
                                )
                            }
                        }
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ttsService.shutdown()
    }
    
    private suspend fun performExport(
        exportType: ExportService.ExportType,
        exportService: ExportService,
        ttsService: TTSService,
        context: Context
    ) {
        try {
            ttsService.speak("Starting export")
            
            val options = ExportService.ExportOptions(
                type = exportType,
                includeSettings = exportType == ExportService.ExportType.FULL_BACKUP,
                includeAllAudio = true
            )
            
            val result = exportService.createExport(options)
            
            if (result.success) {
                val shareIntent = exportService.shareExport(result)
                if (shareIntent != null) {
                    ttsService.speak("Export complete. ${result.tagCount} tags exported. Opening share menu.")
                    startActivity(Intent.createChooser(shareIntent, "Share AudioTagger Export"))
                } else {
                    ttsService.speak("Export created but sharing failed")
                }
            } else {
                ttsService.speak("Export failed: ${result.errorMessage}")
                Log.e("SettingsActivity", "Export failed: ${result.errorMessage}")
            }
            
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Export error", e)
            ttsService.speak("Export error occurred")
        }
    }
    
    private fun handleImportFile(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                isImporting.value = true
                ttsService.speak("Analyzing import file")
                
                val app = application as AudioTaggerApplication
                val preview = app.importService.previewImport(uri)
                
                if (preview.isValid) {
                    importPreview.value = preview
                    currentImportUri = uri
                    showImportDialog.value = true
                    
                    val groupText = if (preview.exportedGroups.isNotEmpty()) {
                        " from groups ${preview.exportedGroups.joinToString(", ")}"
                    } else {
                        ""
                    }
                    
                    ttsService.speak("Found ${preview.tagCount} tags$groupText. Showing import options.")
                } else {
                    ttsService.speak("Invalid import file: ${preview.errorMessage}")
                    Log.e("SettingsActivity", "Invalid import: ${preview.errorMessage}")
                }
                
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Import file handling error", e)
                ttsService.speak("Error reading import file")
            } finally {
                isImporting.value = false
            }
        }
    }
    
    private fun performImport(importSettings: Boolean, replaceExisting: Boolean) {
        val uri = currentImportUri ?: return
        
        lifecycleScope.launch {
            try {
                isImporting.value = true
                showImportDialog.value = false
                
                ttsService.speak("Starting import")
                
                val app = application as AudioTaggerApplication
                val result = app.importService.importFile(
                    uri = uri,
                    replaceExisting = replaceExisting,
                    importSettings = importSettings
                )
                
                if (result.success) {
                    val message = buildString {
                        append("Import complete. ")
                        append("${result.tagsImported} tags imported")
                        if (result.audioFilesImported > 0) {
                            append(", ${result.audioFilesImported} audio files")
                        }
                        if (result.skippedTags > 0) {
                            append(", ${result.skippedTags} tags skipped")
                        }
                        append(".")
                    }
                    ttsService.speak(message)
                    
                    // Refresh backup status
                    backupStatus.value = app.backupService.getBackupStatus()
                } else {
                    ttsService.speak("Import failed: ${result.errorMessage}")
                    Log.e("SettingsActivity", "Import failed: ${result.errorMessage}")
                }
                
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Import error", e)
                ttsService.speak("Import error occurred")
            } finally {
                isImporting.value = false
                currentImportUri = null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    ttsService: TTSService,
    showImportDialog: Boolean,
    importPreview: ImportService.ImportPreview?,
    isImporting: Boolean,
    backupStatus: BackupService.BackupStatus?,
    onImportFile: () -> Unit,
    onConfirmImport: (Boolean, Boolean) -> Unit,
    onDismissImportDialog: () -> Unit,
    onFullBackup: () -> Unit,
    onLargeFilesExport: () -> Unit
) {
    val context = LocalContext.current
    var ttsEnabled by remember { mutableStateOf(SettingsActivity.getTTSEnabled(context)) }
    var selectedTheme by remember { mutableStateOf(ThemeManager.currentTheme) }
    var showThemeDialog by remember { mutableStateOf(false) }
    
    val application = context.applicationContext as AudioTaggerApplication
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Header with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Return to main screen",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Text(
                text = "Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.width(48.dp)) // Balance the back button
        }
        
        // Settings content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Backup section
            SettingsSection(title = "Backup & Data") {
                BackupStatusCard(backupStatus = backupStatus)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Export buttons
                ExportButtonsSection(
                    onFullBackup = onFullBackup,
                    onLargeFilesExport = onLargeFilesExport,
                    onImport = onImportFile,
                    isImporting = isImporting
                )
            }
            
            // Appearance section
            SettingsSection(title = "Appearance") {
                // Theme Selection
                SettingsClickableItem(
                    title = "Color Theme",
                    description = "Choose your preferred color scheme",
                    value = AppTheme.availableThemes.find { it.option == selectedTheme }?.name ?: "System Default",
                    onClick = { showThemeDialog = true }
                )
            }
            
            // Accessibility section
            SettingsSection(title = "Accessibility") {
                // TTS Toggle
                SettingsToggleItem(
                    title = "Voice Instructions",
                    description = "Enable spoken prompts and instructions during recording and playback",
                    checked = ttsEnabled,
                    onCheckedChange = { enabled ->
                        ttsEnabled = enabled
                        SettingsActivity.setTTSEnabled(context, enabled)
                        // Provide TTS confirmation when enabling
                        if (enabled) {
                            ttsService.speak("Voice instructions enabled")
                        }
                    }
                )
            }
            
            // About section
            SettingsSection(title = "About") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "AudioTagger",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "NFC Audio Tag Management",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
    
    // Theme selection dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            selectedTheme = selectedTheme,
            onThemeSelected = { newTheme ->
                selectedTheme = newTheme
                ThemeManager.setTheme(context, newTheme)
                showThemeDialog = false
                
                // Inform user that theme has been applied immediately
                ttsService.speak("Theme changed to ${AppTheme.availableThemes.find { it.option == newTheme }?.name}.")
            },
            onDismiss = { showThemeDialog = false }
        )
    }
    
    // Import dialog
    if (showImportDialog && importPreview != null) {
        ImportDialog(
            preview = importPreview!!,
            onConfirm = { importSettings, replaceExisting ->
                onConfirmImport(importSettings, replaceExisting)
            },
            onDismiss = onDismissImportDialog
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        content()
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .semantics { 
                        contentDescription = if (checked) "Voice instructions enabled. Tap to disable." else "Voice instructions disabled. Tap to enable."
                    }
            )
        }
    }
}

@Composable
fun SettingsClickableItem(
    title: String,
    description: String,
    value: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$title: $value. Tap to change." },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Text(
                text = value,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}

@Composable
fun ThemeSelectionDialog(
    selectedTheme: ThemeOption,
    onThemeSelected: (ThemeOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Theme",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                AppTheme.availableThemes.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTheme == theme.option,
                            onClick = { onThemeSelected(theme.option) }
                        )
                        Text(
                            text = theme.name,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .weight(1f),
                            fontSize = 16.sp
                        )
                    }
                }
                
                Text(
                    text = "Theme changes are applied immediately.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun BackupStatusCard(backupStatus: BackupService.BackupStatus?) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with expand/collapse button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .semantics { contentDescription = if (isExpanded) "Backup status expanded. Tap to collapse details." else "Backup status collapsed. Tap to expand details." },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Auto Backup Status",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (backupStatus != null) {
                        Text(
                            text = "${backupStatus.backupPercentage}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (backupStatus.backupPercentage >= 80) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    
                    Text(
                        text = if (isExpanded) "▲" else "▼",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            if (backupStatus != null) {
                // Progress bar (always visible)
                LinearProgressIndicator(
                    progress = { backupStatus.backupPercentage / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (backupStatus.backupPercentage >= 80) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                
                // Basic summary (always visible)
                Text(
                    text = "${backupStatus.backedUpFiles} of ${backupStatus.totalFiles} files backed up",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
                
                // Expandable details
                if (isExpanded) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val backupSizeMB = backupStatus.backedUpSize / (1024 * 1024)
                        val totalSizeMB = backupStatus.totalSize / (1024 * 1024)
                        Text(
                            text = "${backupSizeMB}MB backed up of ${totalSizeMB}MB total",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                        
                        if (backupStatus.manualExportRequired > 0) {
                            Text(
                                text = "⚠️ ${backupStatus.manualExportRequired} large files require manual export",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Text(
                            text = "Small files (<1MB) are automatically backed up to your Google account. Large files can be exported manually.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            lineHeight = 14.sp
                        )
                    }
                }
            } else {
                Text(
                    text = "Loading backup status...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
fun ExportButtonsSection(
    onFullBackup: () -> Unit,
    onLargeFilesExport: () -> Unit,
    onImport: () -> Unit,
    isImporting: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Full backup export button
        OutlinedButton(
            onClick = onFullBackup,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics { contentDescription = "Export complete backup with all tags and audio files" }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📤")
                Text(
                    text = "Export Full Backup",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Large files export button
        OutlinedButton(
            onClick = onLargeFilesExport,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics { contentDescription = "Export large audio files not included in automatic backup" }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📁")
                Text(
                    text = "Export Large Files",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Import button
        Button(
            onClick = onImport,
            enabled = !isImporting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics { contentDescription = "Import AudioTagger export file to restore tags and audio" },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                } else {
                    Text("📥")
                }
                Text(
                    text = if (isImporting) "Importing..." else "Import Export File",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ImportDialog(
    preview: ImportService.ImportPreview,
    onConfirm: (importSettings: Boolean, replaceExisting: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var importSettings by remember { mutableStateOf(preview.hasSettings) }
    var replaceExisting by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Import Export File",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Preview information
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Export Contents:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = "• ${preview.tagCount} tags total",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (preview.audioFileCount > 0) {
                            Text(
                                text = "• ${preview.audioFileCount} audio files",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (preview.textTagCount > 0) {
                            Text(
                                text = "• ${preview.textTagCount} text tags",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (preview.exportedGroups.isNotEmpty()) {
                            Text(
                                text = "• Groups: ${preview.exportedGroups.joinToString(", ")}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (preview.hasSettings) {
                            Text(
                                text = "• App settings included",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (preview.exportType != null) {
                            Text(
                                text = "• Export type: ${preview.exportType}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Import options
                if (preview.hasSettings) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Import app settings",
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = importSettings,
                            onCheckedChange = { importSettings = it }
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Replace existing tags",
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Overwrite tags with matching IDs",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = replaceExisting,
                        onCheckedChange = { replaceExisting = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(importSettings, replaceExisting) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}