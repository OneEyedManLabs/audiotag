package org.oneeyedmanlabs.audiotag

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.oneeyedmanlabs.audiotag.data.TagEntity
import org.oneeyedmanlabs.audiotag.repository.TagRepository
import org.oneeyedmanlabs.audiotag.service.AudioTaggerApplication
import org.oneeyedmanlabs.audiotag.service.ExportService
import org.oneeyedmanlabs.audiotag.service.TTSService
import org.oneeyedmanlabs.audiotag.ui.theme.AudioTagTheme
import org.oneeyedmanlabs.audiotag.ui.theme.ThemeManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for displaying and managing all saved audio tags
 * Provides list view with play, edit, and delete functionality
 */
class TagListActivity : ComponentActivity() {
    
    private lateinit var repository: TagRepository
    private lateinit var ttsService: TTSService
    private lateinit var exportService: ExportService
    private var vibrator: Vibrator? = null
    private var mediaPlayer: MediaPlayer? = null
    
    private var tags = mutableStateOf<List<TagEntity>>(emptyList())
    private var allTags = mutableStateOf<List<TagEntity>>(emptyList())
    private var isLoading = mutableStateOf(true)
    private var showDeleteDialog = mutableStateOf<TagEntity?>(null)
    private var currentlyPlayingTag = mutableStateOf<String?>(null)
    private var selectedGroup = mutableStateOf<String?>(null)
    private var showExportDialog = mutableStateOf(false)
    private var isExporting = mutableStateOf(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize services
        val application = application as AudioTaggerApplication
        repository = application.repository
        ttsService = TTSService(this)
        exportService = application.exportService
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        
        // Initialize TTS
        ttsService.initialize {
            Log.d("TagListActivity", "TTS initialized")
        }
        
        // Load tags
        loadTags()
        
        setContent {
            val currentTheme by ThemeManager.getCurrentThemeState()
            AudioTagTheme(themeOption = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TagListScreen(
                        tags = tags.value,
                        allTags = allTags.value,
                        isLoading = isLoading.value,
                        showDeleteDialog = showDeleteDialog.value,
                        currentlyPlayingTag = currentlyPlayingTag.value,
                        selectedGroup = selectedGroup.value,
                        showExportDialog = showExportDialog.value,
                        isExporting = isExporting.value,
                        onPlayTag = { tag -> playTag(tag) },
                        onStopTag = { tag -> stopTag(tag) },
                        onCardClick = { tag -> openTagInfo(tag) },
                        onDeleteTag = { tag -> showDeleteDialog.value = tag },
                        onConfirmDelete = { tag -> deleteTag(tag) },
                        onDismissDeleteDialog = { showDeleteDialog.value = null },
                        onGroupFilter = { group -> filterByGroup(group) },
                        onExportGroup = { showExportDialog.value = true },
                        onDismissExportDialog = { showExportDialog.value = false },
                        onConfirmExport = { confirmExport() },
                        onBack = { finish() }
                    )
                }
            }
        }
    }
    
    private fun loadTags() {
        lifecycleScope.launch {
            try {
                isLoading.value = true
                val loadedTags = repository.getAllTagsList().sortedByDescending { it.createdAt }
                allTags.value = loadedTags
                applyGroupFilter()
                Log.d("TagListActivity", "Loaded ${loadedTags.size} tags")
            } catch (e: Exception) {
                Log.e("TagListActivity", "Error loading tags", e)
                allTags.value = emptyList()
                tags.value = emptyList()
            } finally {
                isLoading.value = false
            }
        }
    }
    
    private fun filterByGroup(group: String?) {
        selectedGroup.value = group
        applyGroupFilter()
    }
    
    private fun applyGroupFilter() {
        val filteredTags = if (selectedGroup.value == null) {
            allTags.value
        } else {
            allTags.value.filter { tag ->
                tag.groups.contains(selectedGroup.value)
            }
        }
        tags.value = filteredTags
    }
    
    private fun vibrateCompat(duration: Long) {
        vibrator?.let { vibrator ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        }
    }
    
    private fun playTag(tag: TagEntity) {
        // Stop any currently playing audio
        stopAllAudio()
        
        currentlyPlayingTag.value = tag.tagId
        vibrateCompat(100)
        
        when (tag.type) {
            "tts" -> {
                ttsService.speak(tag.content)
                // TTS doesn't have a completion callback, so we'll reset after a delay
                lifecycleScope.launch {
                    delay(5000) // Reasonable time for TTS
                    if (currentlyPlayingTag.value == tag.tagId) {
                        currentlyPlayingTag.value = null
                    }
                }
            }
            "audio" -> {
                playAudioFile(tag)
            }
            else -> {
                Log.w("TagListActivity", "Unknown tag type: ${tag.type}")
                ttsService.speak("Unknown tag type")
                currentlyPlayingTag.value = null
            }
        }
    }
    
    private fun stopTag(tag: TagEntity) {
        if (currentlyPlayingTag.value == tag.tagId) {
            stopAllAudio()
            currentlyPlayingTag.value = null
        }
    }
    
    private fun playAudioFile(tag: TagEntity) {
        try {
            val file = File(tag.content)
            if (!file.exists()) {
                Log.e("TagListActivity", "Audio file not found: ${tag.content}")
                ttsService.speak("Audio file not found")
                currentlyPlayingTag.value = null
                return
            }
            
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tag.content)
                
                setOnPreparedListener {
                    Log.d("TagListActivity", "Audio prepared - starting playback")
                    start()
                }
                
                setOnCompletionListener {
                    Log.d("TagListActivity", "Audio playback completed")
                    currentlyPlayingTag.value = null
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e("TagListActivity", "Audio playback error: what=$what, extra=$extra")
                    ttsService.speak("Audio playback error")
                    currentlyPlayingTag.value = null
                    mediaPlayer?.release()
                    mediaPlayer = null
                    true
                }
                
                prepareAsync()
            }
            
        } catch (e: Exception) {
            Log.e("TagListActivity", "Error playing audio file", e)
            ttsService.speak("Error playing audio")
            currentlyPlayingTag.value = null
        }
    }
    
    private fun stopAllAudio() {
        ttsService.stop()
        mediaPlayer?.let {
            it.release()
            mediaPlayer = null
        }
    }
    
    private fun openTagInfo(tag: TagEntity) {
        val intent = Intent(this, TagInfoActivity::class.java).apply {
            putExtra("tag_id", tag.tagId)
            putExtra("from_tag_list", true) // Mark as coming from tag list
            // Don't auto-play when coming from card click
        }
        startActivity(intent)
    }
    
    private fun deleteTag(tag: TagEntity) {
        lifecycleScope.launch {
            try {
                repository.deleteTagById(tag.tagId)
                Log.d("TagListActivity", "Deleted tag: ${tag.tagId}")
                
                // Refresh the list
                loadTags()
                showDeleteDialog.value = null
                
            } catch (e: Exception) {
                Log.e("TagListActivity", "Error deleting tag", e)
                showDeleteDialog.value = null
            }
        }
    }
    
    private fun confirmExport() {
        lifecycleScope.launch {
            try {
                isExporting.value = true
                showExportDialog.value = false
                
                val currentTags = tags.value
                val groupToExport = selectedGroup.value
                
                val (options, shareTitle, description) = if (groupToExport != null) {
                    // Export specific group
                    ttsService.speak("Exporting $groupToExport group")
                    Triple(
                        ExportService.ExportOptions(
                            type = ExportService.ExportType.GROUP_EXPORT,
                            selectedGroups = setOf(groupToExport),
                            includeSettings = false,
                            includeAllAudio = true
                        ),
                        "Share $groupToExport Tags",
                        "$groupToExport group"
                    )
                } else {
                    // Export all currently visible tags
                    ttsService.speak("Exporting visible tags")
                    Triple(
                        ExportService.ExportOptions(
                            type = ExportService.ExportType.VISIBLE_TAGS,
                            specificTags = currentTags,
                            includeSettings = false,
                            includeAllAudio = true
                        ),
                        "Share Tags",
                        "visible tags"
                    )
                }
                
                val result = exportService.createExport(options)
                
                if (result.success) {
                    val shareIntent = exportService.shareExport(result, shareTitle)
                    if (shareIntent != null) {
                        ttsService.speak("Export complete. ${result.tagCount} tags exported. Opening share menu.")
                        startActivity(Intent.createChooser(shareIntent, shareTitle))
                    } else {
                        ttsService.speak("Export created but sharing failed")
                    }
                } else {
                    ttsService.speak("Export failed: ${result.errorMessage}")
                    Log.e("TagListActivity", "Export failed: ${result.errorMessage}")
                }
                
            } catch (e: Exception) {
                Log.e("TagListActivity", "Export error", e)
                ttsService.speak("Export error occurred")
            } finally {
                isExporting.value = false
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAllAudio()
        ttsService.shutdown()
    }
}

@Composable
fun TagListScreen(
    tags: List<TagEntity>,
    allTags: List<TagEntity>,
    isLoading: Boolean,
    showDeleteDialog: TagEntity?,
    currentlyPlayingTag: String?,
    selectedGroup: String?,
    showExportDialog: Boolean,
    isExporting: Boolean,
    onPlayTag: (TagEntity) -> Unit,
    onStopTag: (TagEntity) -> Unit,
    onCardClick: (TagEntity) -> Unit,
    onDeleteTag: (TagEntity) -> Unit,
    onConfirmDelete: (TagEntity) -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onGroupFilter: (String?) -> Unit,
    onExportGroup: () -> Unit,
    onDismissExportDialog: () -> Unit,
    onConfirmExport: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        // Header with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.padding(start = 0.dp)
            ) {
                Text(
                    text = "← Back",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Text(
                text = "My Tags",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.width(60.dp)) // Balance the back button
        }
        
        // Export/Share section
        if (tags.isNotEmpty() && !isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onExportGroup,
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .semantics { contentDescription = "Export and share displayed tags" }
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onSecondary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Exporting...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text("📤", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Export/Share",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        
        // Group filter
        if (!isLoading && allTags.isNotEmpty()) {
            val availableGroups = allTags.flatMap { it.groups }.distinct().sorted()
            if (availableGroups.isNotEmpty()) {
                GroupFilterRow(
                    availableGroups = availableGroups,
                    selectedGroup = selectedGroup,
                    onGroupFilter = onGroupFilter
                )
            }
        }
        
        // Content
        when {
            isLoading -> {
                LoadingTagList()
            }
            tags.isEmpty() -> {
                EmptyTagList()
            }
            else -> {
                TagList(
                    tags = tags,
                    currentlyPlayingTag = currentlyPlayingTag,
                    onPlayTag = onPlayTag,
                    onStopTag = onStopTag,
                    onCardClick = onCardClick,
                    onDeleteTag = onDeleteTag
                )
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog != null) {
        DeleteTagDialog(
            tag = showDeleteDialog,
            onConfirm = { onConfirmDelete(showDeleteDialog) },
            onDismiss = onDismissDeleteDialog
        )
    }
    
    // Export confirmation dialog
    if (showExportDialog) {
        ExportGroupDialog(
            groupName = selectedGroup,
            tagCount = tags.size,
            onConfirm = onConfirmExport,
            onDismiss = onDismissExportDialog
        )
    }
}

@Composable
fun LoadingTagList() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading tags...",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyTagList() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "📱",
                fontSize = 64.sp
            )
            Text(
                text = "No Tags Yet",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Start by recording your first audio tag!",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TagList(
    tags: List<TagEntity>,
    currentlyPlayingTag: String?,
    onPlayTag: (TagEntity) -> Unit,
    onStopTag: (TagEntity) -> Unit,
    onCardClick: (TagEntity) -> Unit,
    onDeleteTag: (TagEntity) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tags) { tag ->
            TagListItem(
                tag = tag,
                isPlaying = currentlyPlayingTag == tag.tagId,
                onPlayTag = { onPlayTag(tag) },
                onStopTag = { onStopTag(tag) },
                onCardClick = { onCardClick(tag) },
                onDeleteTag = { onDeleteTag(tag) }
            )
        }
    }
}

@Composable
fun TagListItem(
    tag: TagEntity,
    isPlaying: Boolean,
    onPlayTag: () -> Unit,
    onStopTag: () -> Unit,
    onCardClick: () -> Unit,
    onDeleteTag: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Tag info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Tag label
                    Text(
                        text = tag.title.ifEmpty { "Untitled Tag" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Description (if provided)
                    if (!tag.description.isNullOrBlank()) {
                        Text(
                            text = tag.description,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    // Groups (if any)
                    if (tag.groups.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            items(tag.groups.take(3)) { group -> // Limit to 3 groups in list view
                                AssistChip(
                                    onClick = { },
                                    label = {
                                        Text(
                                            text = group,
                                            fontSize = 12.sp
                                        )
                                    },
                                    modifier = Modifier.height(24.dp),
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    )
                                )
                            }
                            if (tag.groups.size > 3) {
                                item {
                                    Text(
                                        text = "+${tag.groups.size - 3}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Tag type and date
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    val createdDate = Date(tag.createdAt)
                    
                    Text(
                        text = "${tag.type.uppercase()} • ${dateFormat.format(createdDate)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                // Tag type icon
                Text(
                    text = if (tag.type == "audio") "🎵" else "💬",
                    fontSize = 32.sp,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            
            // Action buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Play/Stop button - large and prominent
                Button(
                    onClick = if (isPlaying) onStopTag else onPlayTag,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop audio playback" else "Play audio tag",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPlaying) "Stop Audio" else "Play Audio",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Delete button
                OutlinedButton(
                    onClick = onDeleteTag,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete audio tag",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete",
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DeleteTagDialog(
    tag: TagEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Tag",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete \"${tag.title.ifEmpty { "Untitled Tag" }}\"? This action cannot be undone."
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ExportGroupDialog(
    groupName: String?,
    tagCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (groupName != null) "Export Group" else "Export Tags",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = if (groupName != null) {
                    "Export all tags in the \"$groupName\" group? This will create a shareable file containing all audio files and metadata from this group."
                } else {
                    "Export all $tagCount visible tags? This will create a shareable file containing all audio files and metadata from the currently displayed tags."
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GroupFilterRow(
    availableGroups: List<String>,
    selectedGroup: String?,
    onGroupFilter: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Filter by group:",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "All" filter option
            item {
                FilterChip(
                    onClick = { onGroupFilter(null) },
                    label = { Text("All") },
                    selected = selectedGroup == null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
            
            // Group filter options
            items(availableGroups) { group ->
                FilterChip(
                    onClick = { onGroupFilter(group) },
                    label = { Text(group) },
                    selected = selectedGroup == group,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}