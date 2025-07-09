package org.oneeyedmanlabs.audiotag

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.oneeyedmanlabs.audiotag.data.TagEntity
import org.oneeyedmanlabs.audiotag.repository.TagRepository
import org.oneeyedmanlabs.audiotag.service.AudioTaggerApplication
import org.oneeyedmanlabs.audiotag.service.TTSService
import org.oneeyedmanlabs.audiotag.ui.theme.AudioTagTheme
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
    private var vibrator: Vibrator? = null
    private var mediaPlayer: MediaPlayer? = null
    
    private var tags = mutableStateOf<List<TagEntity>>(emptyList())
    private var isLoading = mutableStateOf(true)
    private var showDeleteDialog = mutableStateOf<TagEntity?>(null)
    private var currentlyPlayingTag = mutableStateOf<String?>(null)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize services
        val application = application as AudioTaggerApplication
        repository = application.repository
        ttsService = TTSService(this)
        vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        
        // Initialize TTS
        ttsService.initialize {
            Log.d("TagListActivity", "TTS initialized")
        }
        
        // Load tags
        loadTags()
        
        setContent {
            AudioTagTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TagListScreen(
                        tags = tags.value,
                        isLoading = isLoading.value,
                        showDeleteDialog = showDeleteDialog.value,
                        currentlyPlayingTag = currentlyPlayingTag.value,
                        onPlayTag = { tag -> playTag(tag) },
                        onStopTag = { tag -> stopTag(tag) },
                        onEditTag = { tag -> editTag(tag) },
                        onDeleteTag = { tag -> showDeleteDialog.value = tag },
                        onConfirmDelete = { tag -> deleteTag(tag) },
                        onDismissDeleteDialog = { showDeleteDialog.value = null },
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
                val allTags = repository.getAllTagsList()
                tags.value = allTags.sortedByDescending { it.createdAt }
                Log.d("TagListActivity", "Loaded ${allTags.size} tags")
            } catch (e: Exception) {
                Log.e("TagListActivity", "Error loading tags", e)
                tags.value = emptyList()
            } finally {
                isLoading.value = false
            }
        }
    }
    
    private fun playTag(tag: TagEntity) {
        // Stop any currently playing audio
        stopAllAudio()
        
        currentlyPlayingTag.value = tag.tagId
        vibrator?.vibrate(100)
        
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
    
    private fun editTag(tag: TagEntity) {
        val intent = Intent(this, TagInfoActivity::class.java).apply {
            putExtra("tag_id", tag.tagId)
            putExtra("from_tag_list", true) // Mark as coming from tag list
            putExtra("edit_mode", true) // Mark as edit mode
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
    
    override fun onDestroy() {
        super.onDestroy()
        stopAllAudio()
        ttsService.shutdown()
    }
}

@Composable
fun TagListScreen(
    tags: List<TagEntity>,
    isLoading: Boolean,
    showDeleteDialog: TagEntity?,
    currentlyPlayingTag: String?,
    onPlayTag: (TagEntity) -> Unit,
    onStopTag: (TagEntity) -> Unit,
    onEditTag: (TagEntity) -> Unit,
    onDeleteTag: (TagEntity) -> Unit,
    onConfirmDelete: (TagEntity) -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
                    onEditTag = onEditTag,
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
    onEditTag: (TagEntity) -> Unit,
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
                onEditTag = { onEditTag(tag) },
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
    onEditTag: () -> Unit,
    onDeleteTag: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                        text = tag.label.ifEmpty { "Untitled Tag" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Tag type and date
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    val createdDate = Date(tag.createdAt)
                    
                    Text(
                        text = "${tag.type.uppercase()} • ${dateFormat.format(createdDate)}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPlaying) "Stop Audio" else "Play Audio",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Edit and Delete buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Edit button
                    OutlinedButton(
                        onClick = onEditTag,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Edit",
                            fontSize = 16.sp
                        )
                    }
                    
                    // Delete button
                    OutlinedButton(
                        onClick = onDeleteTag,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
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
                text = "Are you sure you want to delete \"${tag.label.ifEmpty { "Untitled Tag" }}\"? This action cannot be undone."
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