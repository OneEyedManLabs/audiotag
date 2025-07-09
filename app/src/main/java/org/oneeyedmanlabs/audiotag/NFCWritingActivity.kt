package org.oneeyedmanlabs.audiotag

import android.content.Intent
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
import org.oneeyedmanlabs.audiotag.service.NFCWriteService
import org.oneeyedmanlabs.audiotag.service.TTSService
import org.oneeyedmanlabs.audiotag.ui.theme.AudioTagTheme
import java.io.File

/**
 * Activity for writing recorded audio to NFC tags
 * Handles NFC detection, database saving, and tag writing
 */
class NFCWritingActivity : ComponentActivity() {
    
    private lateinit var repository: TagRepository
    private lateinit var nfcService: NFCService
    private lateinit var nfcWriteService: NFCWriteService
    private lateinit var ttsService: TTSService
    private var vibrator: Vibrator? = null
    private var nfcAdapter: NfcAdapter? = null
    
    // State
    private var writingState = mutableStateOf(WritingState.WAITING_FOR_TAG)
    private var audioFilePath: String? = null
    private var tagId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get audio file path from intent
        audioFilePath = intent.getStringExtra("audio_file_path")
        if (audioFilePath == null) {
            Log.e("NFCWritingActivity", "No audio file path provided")
            finish()
            return
        }
        
        // Initialize services
        val application = application as AudioTaggerApplication
        repository = application.repository
        nfcService = NFCService()
        nfcWriteService = NFCWriteService()
        ttsService = TTSService(this)
        vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        
        // Initialize NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            writingState.value = WritingState.NFC_NOT_AVAILABLE
            return
        }
        
        // Initialize TTS
        ttsService.initialize {
            ttsService.speak("Recording saved. Now scan your NFC tag to associate it with this audio.")
        }
        
        setContent {
            AudioTagTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NFCWritingScreen(
                        writingState = writingState.value,
                        onCancel = { finish() },
                        onRetry = { retryWriting() }
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
        handleNFCIntent(intent)
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
    
    private fun handleNFCIntent(intent: Intent) {
        if (writingState.value != WritingState.WAITING_FOR_TAG) return
        
        val action = intent.action
        if (NfcAdapter.ACTION_TAG_DISCOVERED == action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == action) {
            
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                processNFCTag(tag)
            }
        }
    }
    
    private fun processNFCTag(tag: Tag) {
        writingState.value = WritingState.WRITING
        vibrator?.vibrate(200) // Haptic feedback
        
        lifecycleScope.launch {
            try {
                // Generate tag ID and label
                tagId = nfcService.getTagId(tag)
                val generatedLabel = "Audio Tag ${System.currentTimeMillis() % 10000}"
                
                // Verify audio file exists
                val audioFile = File(audioFilePath!!)
                if (!audioFile.exists()) {
                    Log.e("NFCWritingActivity", "Audio file not found: $audioFilePath")
                    writingState.value = WritingState.ERROR
                    ttsService.speak("Error: Audio file not found")
                    return@launch
                }
                
                // Check if tag already exists
                val existingTag = repository.getTag(tagId!!)
                
                val tagEntity = if (existingTag != null) {
                    // Update existing tag with new audio content
                    existingTag.copy(
                        content = audioFilePath!!,
                        createdAt = System.currentTimeMillis() // Update timestamp
                    )
                } else {
                    // Create new tag entity
                    TagEntity(
                        tagId = tagId!!,
                        type = "audio",
                        content = audioFilePath!!,
                        label = generatedLabel,
                        createdAt = System.currentTimeMillis()
                    )
                }
                
                // Save to database (upsert)
                repository.insertTag(tagEntity)
                Log.d("NFCWritingActivity", "Tag saved to database: $tagId")
                
                // Write to NFC tag
                val writeSuccess = nfcWriteService.writeAudioTaggerRecord(tag, tagId!!)
                
                if (writeSuccess) {
                    Log.d("NFCWritingActivity", "NFC write successful")
                    writingState.value = WritingState.SUCCESS
                    ttsService.speak("Success! Audio tag saved. You can now scan this tag to play your recording.")
                    
                    // Give TTS more time to finish, then go to TagInfoActivity
                    delay(5000) // Increased from 3000
                    
                    // Launch TagInfoActivity to show the new tag
                    val tagInfoIntent = Intent(this@NFCWritingActivity, TagInfoActivity::class.java).apply {
                        putExtra("tag_id", tagId)
                        putExtra("from_nfc_writing", true) // Mark this as coming from NFC writing
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(tagInfoIntent)
                    
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Log.e("NFCWritingActivity", "NFC write failed")
                    writingState.value = WritingState.ERROR
                    ttsService.speak("Failed to write to NFC tag. The audio is saved but not linked to the tag.")
                }
                
            } catch (e: Exception) {
                Log.e("NFCWritingActivity", "Error processing NFC tag", e)
                writingState.value = WritingState.ERROR
                ttsService.speak("Error saving audio tag: ${e.message}")
            }
        }
    }
    
    private fun retryWriting() {
        writingState.value = WritingState.WAITING_FOR_TAG
        ttsService.speak("Ready to try again. Scan your NFC tag.")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ttsService.shutdown()
    }
}

enum class WritingState {
    WAITING_FOR_TAG,
    WRITING,
    SUCCESS,
    ERROR,
    NFC_NOT_AVAILABLE
}

@Composable
fun NFCWritingScreen(
    writingState: WritingState,
    onCancel: () -> Unit,
    onRetry: () -> Unit
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
            text = "Save Audio Tag",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 48.dp)
        )
        
        // Status Display
        NFCWritingStatusCard(writingState = writingState)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Action Buttons
        when (writingState) {
            WritingState.WAITING_FOR_TAG -> {
                WaitingButtons(onCancel = onCancel)
            }
            WritingState.WRITING -> {
                WritingDisplay()
            }
            WritingState.SUCCESS -> {
                // No button needed - automatically closing
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Opening tag info...",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            WritingState.ERROR -> {
                ErrorButtons(
                    onRetry = onRetry,
                    onCancel = onCancel
                )
            }
            WritingState.NFC_NOT_AVAILABLE -> {
                NFCNotAvailableButtons(onCancel = onCancel)
            }
        }
    }
}

@Composable
fun NFCWritingStatusCard(writingState: WritingState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (writingState) {
                WritingState.WAITING_FOR_TAG -> MaterialTheme.colorScheme.primaryContainer
                WritingState.WRITING -> MaterialTheme.colorScheme.secondaryContainer
                WritingState.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                WritingState.ERROR -> MaterialTheme.colorScheme.errorContainer
                WritingState.NFC_NOT_AVAILABLE -> MaterialTheme.colorScheme.errorContainer
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
            when (writingState) {
                WritingState.WAITING_FOR_TAG -> {
                    Text(
                        text = "📱",
                        fontSize = 48.sp
                    )
                    Text(
                        text = "Scan NFC Tag",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Hold your phone close to the NFC tag",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                WritingState.WRITING -> {
                    Text(
                        text = "💾",
                        fontSize = 48.sp
                    )
                    Text(
                        text = "Saving...",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Writing audio to NFC tag",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
                WritingState.SUCCESS -> {
                    Text(
                        text = "✅",
                        fontSize = 48.sp
                    )
                    Text(
                        text = "Success!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Audio tag saved successfully",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
                WritingState.ERROR -> {
                    Text(
                        text = "❌",
                        fontSize = 48.sp
                    )
                    Text(
                        text = "Error",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Failed to save to NFC tag",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
                WritingState.NFC_NOT_AVAILABLE -> {
                    Text(
                        text = "📵",
                        fontSize = 48.sp
                    )
                    Text(
                        text = "NFC Not Available",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "This device doesn't support NFC",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun WaitingButtons(onCancel: () -> Unit) {
    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        Text(
            text = "Cancel",
            fontSize = 20.sp
        )
    }
}

@Composable
fun WritingDisplay() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.secondary
        )
    }
}


@Composable
fun ErrorButtons(
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Try Again",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Text(
                text = "Cancel",
                fontSize = 20.sp
            )
        }
    }
}

@Composable
fun NFCNotAvailableButtons(onCancel: () -> Unit) {
    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        Text(
            text = "Go Back",
            fontSize = 20.sp
        )
    }
}