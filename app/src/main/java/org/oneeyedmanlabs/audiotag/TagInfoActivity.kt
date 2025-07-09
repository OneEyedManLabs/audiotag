package org.oneeyedmanlabs.audiotag

import android.content.Intent
import android.media.MediaPlayer
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    private var isBackgroundLaunch = false
    private var hasPlayedAutomatically = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize services
        val application = application as AudioTaggerApplication
        repository = application.repository
        nfcService = NFCService()
        ttsService = TTSService(this)
        vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        // Check if this was a background launch vs foreground launch
        // Auto-play for: NFC writing completion, foreground NFC scans, auto_play flag, or when launched from task root
        isBackgroundLaunch = !isTaskRoot && 
                           intent.getBooleanExtra("from_nfc_writing", false) != true &&
                           intent.getBooleanExtra("foreground_nfc_scan", false) != true &&
                           intent.getBooleanExtra("auto_play", false) != true
        
        // Initialize TTS
        ttsService.initialize {
            Log.d("TagInfoActivity", "TTS initialized")
        }
        
        // Handle initial intent
        handleIntent(intent)
        
        setContent {
            AudioTagTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TagInfoScreen(
                        tagEntity = tagEntity.value,
                        playbackState = playbackState.value,
                        isBackgroundLaunch = isBackgroundLaunch,
                        hasPlayedAutomatically = hasPlayedAutomatically,
                        onPlayAudio = { playAudio() },
                        onStopAudio = { stopAudio() },
                        onClose = { navigateToMain() }
                    )
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        enableNFCForegroundDispatch()
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
            if (extractTagIdFromNFCIntent(intent) != null) {
                // Reset auto-play state for new NFC scan
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
            
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
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
                    if (!isBackgroundLaunch && !hasPlayedAutomatically) {
                        Log.d("TagInfoActivity", "Foreground NFC scan - auto-playing audio")
                        hasPlayedAutomatically = true
                        delay(500) // Brief delay for UI to load
                        playAudio()
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
    
    private fun playAudio() {
        val tag = tagEntity.value ?: return
        
        playbackState.value = PlaybackState.PLAYING
        vibrator?.vibrate(100)
        
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
    isBackgroundLaunch: Boolean,
    hasPlayedAutomatically: Boolean,
    onPlayAudio: () -> Unit,
    onStopAudio: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        
        // Title
        Text(
            text = "Audio Tag",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 48.dp)
        )
        
        // Tag Info Display
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
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Action Buttons
        if (tagEntity != null) {
            when (playbackState) {
                PlaybackState.IDLE -> {
                    PlaybackButtons(
                        onPlayAudio = onPlayAudio,
                        onClose = onClose,
                        isBackgroundLaunch = isBackgroundLaunch
                    )
                }
                PlaybackState.PLAYING -> {
                    PlayingButtons(
                        onStopAudio = onStopAudio,
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tag icon based on type
            Text(
                text = if (tagEntity.type == "audio") "🎵" else "💬",
                fontSize = 48.sp
            )
            
            // Tag label
            Text(
                text = tagEntity.label.ifEmpty { "Untitled Tag" },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Tag details
            val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
            val createdDate = Date(tagEntity.createdAt)
            
            Text(
                text = "Created: ${dateFormat.format(createdDate)}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
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
                .height(120.dp),
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
fun PlayingButtons(
    onStopAudio: () -> Unit,
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
                .height(120.dp),
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