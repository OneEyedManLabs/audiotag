package org.oneeyedmanlabs.audiotag

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.oneeyedmanlabs.audiotag.data.TagEntity
import org.oneeyedmanlabs.audiotag.repository.TagRepository
import org.oneeyedmanlabs.audiotag.service.AudioTaggerApplication
import org.oneeyedmanlabs.audiotag.service.NFCService
import org.oneeyedmanlabs.audiotag.service.TTSService
import org.oneeyedmanlabs.audiotag.ui.theme.AudioTagTheme
import org.oneeyedmanlabs.audiotag.ui.theme.ThemeManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for displaying and playing individual audio tags
 * Handles both foreground NFC scans (auto-play) and background launches (manual play)
 */
class TagInfoActivity : ComponentActivity() {
    
    private lateinit var repository: TagRepository
    private lateinit var nfcService: NFCService
    private lateinit var ttsService: TTSService
    private var vibrator: Vibrator? = null
    private var nfcAdapter: NfcAdapter? = null
    private var mediaPlayer: MediaPlayer? = null
    
    // State
    private var tagEntity = mutableStateOf<TagEntity?>(null)
    private var playbackState = mutableStateOf(PlaybackState.IDLE)
    private var showEditDialog = mutableStateOf(false)
    private var isBackgroundLaunch = false
    private var hasPlayedAutomatically = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize services
        val application = application as AudioTaggerApplication
        repository = application.repository
        nfcService = NFCService()
        ttsService = TTSService(this)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemService(Vibrator::class.java)
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        // Check if this was a background launch vs foreground launch
        // Auto-play for: NFC writing completion, foreground NFC scans, auto_play flag, tag list launches, NFC intents, or when launched from task root
        val isNFCIntent = intent.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
                         intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
                         intent.action == NfcAdapter.ACTION_TECH_DISCOVERED ||
                         intent.getBooleanExtra("from_nfc_scan", false)
        isBackgroundLaunch = !isTaskRoot && 
                           intent.getBooleanExtra("from_nfc_writing", false) != true &&
                           intent.getBooleanExtra("foreground_nfc_scan", false) != true &&
                           intent.getBooleanExtra("auto_play", false) != true &&
                           intent.getBooleanExtra("from_tag_list", false) != true &&
                           !isNFCIntent
        
        // Initialize TTS
        ttsService.initialize {
            Log.d("TagInfoActivity", "TTS initialized")
        }
        
        // Handle initial intent
        handleIntent(intent)
        
        setContent {
            val currentTheme by ThemeManager.getCurrentThemeState()
            AudioTagTheme(themeOption = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TagInfoScreen(
                        tagEntity = tagEntity.value,
                        playbackState = playbackState.value,
                        showEditDialog = showEditDialog.value,
                        isBackgroundLaunch = isBackgroundLaunch,
                        hasPlayedAutomatically = hasPlayedAutomatically,
                        onPlayAudio = { playAudio() },
                        onStopAudio = { stopAudio() },
                        onEditTag = { showEditDialog.value = true },
                        onConfirmEdit = { title, description, groups -> editTag(title, description, groups) },
                        onRerecord = { rerecordAudio() },
                        onDismissEdit = { showEditDialog.value = false },
                        onClose = { navigateToMain() }
                    )
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        enableNFCForegroundDispatch()
        
        // Refresh tag data when returning from re-recording
        val currentTag = tagEntity.value
        if (currentTag != null) {
            loadTagInfo(currentTag.tagId)
        }
    }
    
    override fun onPause() {
        super.onPause()
        disableNFCForegroundDispatch()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent) {
        // Get tag ID from intent
        val tagId = intent.getStringExtra("tag_id") ?: run {
            // Try to extract from NFC intent - this is a foreground scan
            extractTagIdFromNFCIntent(intent)
        }
        
        if (tagId != null) {
            // If this is a new NFC intent (not initial launch), treat as foreground scan
            if (extractTagIdFromNFCIntent(intent) != null || intent.getBooleanExtra("from_nfc_scan", false)) {
                // Reset auto-play state for new NFC scan
                Log.d("TagInfoActivity", "New NFC intent detected - resetting auto-play state")
                hasPlayedAutomatically = false
                isBackgroundLaunch = false
            }
            loadTagInfo(tagId)
        } else {
            Log.e("TagInfoActivity", "No tag ID found in intent")
            finish()
        }
    }
    
    private fun extractTagIdFromNFCIntent(intent: Intent): String? {
        val action = intent.action
        if (NfcAdapter.ACTION_TAG_DISCOVERED == action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == action) {
            
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            }
            return tag?.let { nfcService.getTagIdFromIntent(intent) }
        }
        return null
    }
    
    private fun loadTagInfo(tagId: String) {
        lifecycleScope.launch {
            try {
                val tag = repository.getTag(tagId)
                if (tag != null) {
                    tagEntity.value = tag
                    
                    // If this is a foreground NFC scan and we haven't played yet, auto-play
                    Log.d("TagInfoActivity", "Auto-play check: isBackgroundLaunch=$isBackgroundLaunch, hasPlayedAutomatically=$hasPlayedAutomatically")
                    if (!isBackgroundLaunch && !hasPlayedAutomatically) {
                        Log.d("TagInfoActivity", "Foreground NFC scan - auto-playing audio")
                        hasPlayedAutomatically = true
                        delay(500) // Brief delay for UI to load
                        playAudio()
                    } else {
                        Log.d("TagInfoActivity", "Auto-play skipped - conditions not met")
                    }
                } else {
                    Log.w("TagInfoActivity", "Tag not found: $tagId")
                    ttsService.speak("Tag not found")
                    finish()
                }
            } catch (e: Exception) {
                Log.e("TagInfoActivity", "Error loading tag info", e)
                ttsService.speak("Error loading tag information")
                finish()
            }
        }
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
    
    private fun playAudio() {
        val tag = tagEntity.value ?: return
        
        playbackState.value = PlaybackState.PLAYING
        vibrateCompat(100)
        
        when (tag.type) {
            "tts" -> {
                ttsService.speak(tag.content)
                // TTS doesn't have a completion callback, so we'll reset after a delay
                lifecycleScope.launch {
                    delay(5000) // Reasonable time for TTS
                    if (playbackState.value == PlaybackState.PLAYING) {
                        playbackState.value = PlaybackState.IDLE
                    }
                }
            }
            "audio" -> {
                playAudioFile(tag.content)
            }
            else -> {
                Log.w("TagInfoActivity", "Unknown tag type: ${tag.type}")
                ttsService.speak("Unknown tag type")
                playbackState.value = PlaybackState.IDLE
            }
        }
    }
    
    private fun playAudioFile(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e("TagInfoActivity", "Audio file not found: $filePath")
                ttsService.speak("Audio file not found")
                playbackState.value = PlaybackState.IDLE
                return
            }
            
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                
                setOnPreparedListener {
                    Log.d("TagInfoActivity", "Audio prepared - starting playback")
                    start()
                }
                
                setOnCompletionListener {
                    Log.d("TagInfoActivity", "Audio playback completed")
                    playbackState.value = PlaybackState.IDLE
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e("TagInfoActivity", "Audio playback error: what=$what, extra=$extra")
                    ttsService.speak("Audio playback error")
                    playbackState.value = PlaybackState.IDLE
                    mediaPlayer?.release()
                    mediaPlayer = null
                    true
                }
                
                prepareAsync()
            }
            
        } catch (e: Exception) {
            Log.e("TagInfoActivity", "Error playing audio file", e)
            ttsService.speak("Error playing audio")
            playbackState.value = PlaybackState.IDLE
        }
    }
    
    private fun stopAudio() {
        playbackState.value = PlaybackState.IDLE
        ttsService.stop()
        mediaPlayer?.let {
            it.release()
            mediaPlayer = null
        }
    }
    
    private fun enableNFCForegroundDispatch() {
        nfcAdapter?.let { adapter ->
            if (adapter.isEnabled) {
                val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val pendingIntent = android.app.PendingIntent.getActivity(
                    this, 0, intent, android.app.PendingIntent.FLAG_MUTABLE
                )
                adapter.enableForegroundDispatch(this, pendingIntent, null, null)
            }
        }
    }
    
    private fun disableNFCForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(this)
    }
    
    private fun editTag(newTitle: String, newDescription: String?, newGroups: List<String>) {
        val currentTag = tagEntity.value ?: return
        
        lifecycleScope.launch {
            try {
                val updatedTag = currentTag.copy(
                    title = newTitle.trim(),
                    description = newDescription?.trim()?.takeIf { it.isNotEmpty() },
                    groups = newGroups.map { it.trim() }.filter { it.isNotEmpty() }
                )
                repository.updateTag(updatedTag)
                Log.d("TagInfoActivity", "Updated tag: ${currentTag.tagId} -> title: $newTitle, desc: $newDescription, groups: $newGroups")
                
                // Update the local state
                tagEntity.value = updatedTag
                showEditDialog.value = false
                
            } catch (e: Exception) {
                Log.e("TagInfoActivity", "Error updating tag", e)
                showEditDialog.value = false
            }
        }
    }
    
    private fun rerecordAudio() {
        val currentTag = tagEntity.value ?: return
        
        // Launch recording activity with tag info to preserve metadata
        val intent = Intent(this, RecordingActivity::class.java).apply {
            putExtra("rerecord_tag_id", currentTag.tagId)
            putExtra("rerecord_title", currentTag.title)
            putExtra("rerecord_description", currentTag.description)
            putExtra("rerecord_groups", currentTag.groups.toTypedArray())
        }
        startActivity(intent)
    }
    
    private fun navigateToMain() {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(mainIntent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
        ttsService.shutdown()
    }
}

enum class PlaybackState {
    IDLE,
    PLAYING
}

@Composable
fun TagInfoScreen(
    tagEntity: TagEntity?,
    playbackState: PlaybackState,
    showEditDialog: Boolean,
    isBackgroundLaunch: Boolean,
    hasPlayedAutomatically: Boolean,
    onPlayAudio: () -> Unit,
    onStopAudio: () -> Unit,
    onEditTag: () -> Unit,
    onConfirmEdit: (String, String?, List<String>) -> Unit,
    onRerecord: () -> Unit,
    onDismissEdit: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        
        // Title
        Text(
            text = "Audio Tag",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 24.dp)
        )
        
        // Tag Info Display (scrollable content)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 16.dp)
        ) {
            if (tagEntity != null) {
                TagInfoCard(
                    tagEntity = tagEntity,
                    playbackState = playbackState,
                    isBackgroundLaunch = isBackgroundLaunch,
                    hasPlayedAutomatically = hasPlayedAutomatically
                )
            } else {
                LoadingCard()
            }
        }
        
        // Action Buttons (fixed at bottom)
        if (tagEntity != null) {
            when (playbackState) {
                PlaybackState.IDLE -> {
                    PlaybackButtons(
                        onPlayAudio = onPlayAudio,
                        onEditTag = onEditTag,
                        onClose = onClose,
                        isBackgroundLaunch = isBackgroundLaunch
                    )
                }
                PlaybackState.PLAYING -> {
                    PlayingButtons(
                        onStopAudio = onStopAudio,
                        onEditTag = onEditTag,
                        onClose = onClose
                    )
                }
            }
        } else {
            // Loading state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
    
    // Edit dialog
    if (showEditDialog) {
        EnhancedEditTagDialog(
            currentTag = tagEntity,
            onConfirm = onConfirmEdit,
            onRerecord = onRerecord,
            onDismiss = onDismissEdit
        )
    }
}

@Composable
fun TagInfoCard(
    tagEntity: TagEntity,
    playbackState: PlaybackState,
    isBackgroundLaunch: Boolean,
    hasPlayedAutomatically: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (playbackState == PlaybackState.PLAYING) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tag icon based on type
            Text(
                text = if (tagEntity.type == "audio") "🎵" else "💬",
                fontSize = 48.sp
            )
            
            // Tag title
            Text(
                text = tagEntity.title.ifEmpty { "Untitled Tag" },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Description (if provided)
            if (!tagEntity.description.isNullOrBlank()) {
                Text(
                    text = tagEntity.description,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            // Groups (if any)
            if (tagEntity.groups.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    items(tagEntity.groups) { group ->
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = group,
                                    fontSize = 14.sp
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
            
            // Tag type only (removed creation date)
            Text(
                text = "Type: ${tagEntity.type.uppercase()}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Playback status
            if (playbackState == PlaybackState.PLAYING) {
                Text(
                    text = "🔊 Playing...",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (isBackgroundLaunch && !hasPlayedAutomatically) {
                Text(
                    text = "Tap Play to hear audio",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Loading tag info...",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PlaybackButtons(
    onPlayAudio: () -> Unit,
    onEditTag: () -> Unit,
    onClose: () -> Unit,
    isBackgroundLaunch: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Large play button
        Button(
            onClick = onPlayAudio,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics { 
                    contentDescription = if (isBackgroundLaunch) "Play this audio tag" else "Play this audio tag again"
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🔊",
                    fontSize = 40.sp
                )
                Text(
                    text = if (isBackgroundLaunch) "Play Audio" else "Play Again",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Edit button
        OutlinedButton(
            onClick = onEditTag,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .semantics { contentDescription = "Edit this tag's title, description, and groups" }
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null, // Icon description handled by button
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Edit Tag",
                fontSize = 20.sp
            )
        }
        
        // Close button
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .semantics { contentDescription = "Close tag information and return to main screen" }
        ) {
            Text(
                text = "Close",
                fontSize = 20.sp
            )
        }
    }
}

@Composable
fun PlayingButtons(
    onStopAudio: () -> Unit,
    onEditTag: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Stop button
        Button(
            onClick = onStopAudio,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics { contentDescription = "Stop playing this audio tag" },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "⏹️",
                    fontSize = 40.sp
                )
                Text(
                    text = "Stop Audio",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Edit button
        OutlinedButton(
            onClick = onEditTag,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Edit Tag",
                fontSize = 20.sp
            )
        }
        
        // Close button
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Text(
                text = "Close",
                fontSize = 20.sp
            )
        }
    }
}
@Composable
fun EnhancedEditTagDialog(
    currentTag: TagEntity?,
    onConfirm: (String, String?, List<String>) -> Unit,
    onRerecord: () -> Unit,
    onDismiss: () -> Unit
) {
    var titleValue by remember { mutableStateOf(currentTag?.title ?: "") }
    var descriptionValue by remember { mutableStateOf(currentTag?.description ?: "") }
    var newGroupText by remember { mutableStateOf("") }
    var selectedGroups by remember { mutableStateOf(currentTag?.groups?.toSet() ?: emptySet()) }
    
    // For demo purposes, some common group suggestions
    val commonGroups = listOf("Work", "Personal", "Music", "Notes", "Instructions", "Family")
    val availableGroups = (commonGroups + selectedGroups).distinct().sorted()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Tag",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Title field
                OutlinedTextField(
                    value = titleValue,
                    onValueChange = { titleValue = it },
                    label = { Text("Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Description field
                OutlinedTextField(
                    value = descriptionValue,
                    onValueChange = { descriptionValue = it },
                    label = { Text("Description (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Groups section
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Groups:",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    
                    // Selected groups display
                    if (selectedGroups.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 40.dp)
                        ) {
                            items(selectedGroups.toList()) { group ->
                                FilterChip(
                                    onClick = { selectedGroups = selectedGroups - group },
                                    label = { Text(group, fontSize = 14.sp) },
                                    selected = true,
                                    trailingIcon = {
                                        Text("×", fontSize = 14.sp)
                                    },
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }
                    }
                    
                    // Available groups to add
                    val unselectedGroups = availableGroups.filter { it !in selectedGroups }
                    if (unselectedGroups.isNotEmpty()) {
                        Text(
                            text = "Add group:",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 40.dp)
                        ) {
                            items(unselectedGroups) { group ->
                                FilterChip(
                                    onClick = { selectedGroups = selectedGroups + group },
                                    label = { Text(group, fontSize = 14.sp) },
                                    selected = false,
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }
                    }
                }
                
                // Create new group
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newGroupText,
                        onValueChange = { newGroupText = it },
                        label = { Text("New group") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            if (newGroupText.trim().isNotEmpty()) {
                                selectedGroups = selectedGroups + newGroupText.trim()
                                newGroupText = ""
                            }
                        },
                        enabled = newGroupText.trim().isNotEmpty()
                    ) {
                        Text("Add")
                    }
                }
                
                // Re-record Audio button (for audio tags only)
                if (currentTag?.type == "audio") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onDismiss() // Close dialog first
                            onRerecord() // Then trigger re-record
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("🎤", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Re-record Audio")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (titleValue.trim().isNotEmpty()) {
                        onConfirm(
                            titleValue.trim(),
                            descriptionValue.trim().takeIf { it.isNotEmpty() },
                            selectedGroups.toList()
                        )
                    }
                },
                enabled = titleValue.trim().isNotEmpty()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
