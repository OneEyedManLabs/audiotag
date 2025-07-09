package org.oneeyedmanlabs.audiotag

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.oneeyedmanlabs.audiotag.service.AudioRecordingService
import org.oneeyedmanlabs.audiotag.service.AudioTaggerApplication
import org.oneeyedmanlabs.audiotag.service.TTSService
import org.oneeyedmanlabs.audiotag.ui.theme.AudioTagTheme

/**
 * Clean, accessible recording activity
 * Features: Large buttons, visual timer, countdown beeps, auto-stop
 */
class RecordingActivity : ComponentActivity() {
    
    private lateinit var audioRecordingService: AudioRecordingService
    private lateinit var ttsService: TTSService
    private var vibrator: Vibrator? = null
    private var recordingTimer: CountDownTimer? = null
    private var recordedFilePath: String? = null
    
    // Recording state
    private var recordingState = mutableStateOf(RecordingState.READY)
    private var timeRemaining = mutableStateOf(30) // 30 second default
    private var showPermissionDialog = mutableStateOf(false)
    
    // Re-recording state
    private var isRerecording = false
    private var rerecordTagId: String? = null
    private var rerecordTitle: String? = null
    private var rerecordDescription: String? = null
    private var rerecordGroups: List<String>? = null
    
    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCountdown()
        } else {
            showPermissionDialog.value = true
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize services
        audioRecordingService = AudioRecordingService(this)
        ttsService = TTSService(this)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemService(Vibrator::class.java)
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        
        // Check if this is a re-recording
        checkRerecordingIntent()
        
        // Initialize TTS
        ttsService.initialize {
            Log.d("RecordingActivity", "TTS initialized")
            // Give initial instruction when TTS is ready
            val message = if (isRerecording) {
                "Ready to re-record. Tap anywhere on the screen to start recording."
            } else {
                "Ready to record. Tap anywhere on the screen to start recording."
            }
            ttsService.speak(message)
        }
        
        setContent {
            AudioTagTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RecordingScreen(
                        recordingState = recordingState.value,
                        timeRemaining = timeRemaining.value,
                        onStartRecording = { checkPermissionAndStart() },
                        onStopRecording = { stopRecording() },
                        onCancel = { cancelRecording() },
                        showPermissionDialog = showPermissionDialog.value,
                        onDismissPermissionDialog = { showPermissionDialog.value = false }
                    )
                }
            }
        }
    }
    
    private fun checkRerecordingIntent() {
        rerecordTagId = intent.getStringExtra("rerecord_tag_id")
        rerecordTitle = intent.getStringExtra("rerecord_title")
        rerecordDescription = intent.getStringExtra("rerecord_description")
        rerecordGroups = intent.getStringArrayExtra("rerecord_groups")?.toList()
        
        isRerecording = rerecordTagId != null
        
        if (isRerecording) {
            Log.d("RecordingActivity", "Re-recording mode for tag: $rerecordTagId")
        }
    }
    
    private fun checkPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCountdown()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    
    private fun startCountdown() {
        recordingState.value = RecordingState.COUNTDOWN
        ttsService.speak("Get ready. When recording starts, tap anywhere on the screen to finish.")
        
        // Start countdown after TTS - adjust timing based on TTS setting
        lifecycleScope.launch {
            val ttsEnabled = SettingsActivity.getTTSEnabled(this@RecordingActivity)
            if (ttsEnabled) {
                delay(4000) // Wait for the TTS message
            } else {
                delay(1000) // Much shorter delay when TTS is disabled
            }
            playCountdownBeeps() // This function handles its own delays and calls startRecording()
        }
    }
    
    private fun startRecording() {
        val fileName = "audio_${System.currentTimeMillis()}"
        recordedFilePath = audioRecordingService.startRecording(fileName)
        
        if (recordedFilePath != null) {
            recordingState.value = RecordingState.RECORDING
            startTimer()
            vibrateCompat(100) // Haptic feedback
        } else {
            recordingState.value = RecordingState.ERROR
            ttsService.speak("Failed to start recording")
        }
    }
    
    private fun startTimer() {
        recordingTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt()
                timeRemaining.value = secondsLeft
                
                // Warning beeps at 5 and 3 seconds (no voice to avoid recording it)
                if (secondsLeft == 5 || secondsLeft == 3) {
                    playBeeps(1)
                }
            }
            
            override fun onFinish() {
                stopRecording()
            }
        }.start()
    }
    
    private fun stopRecording() {
        recordingTimer?.cancel()
        recordedFilePath = audioRecordingService.stopRecording()
        
        if (recordedFilePath != null) {
            recordingState.value = RecordingState.COMPLETED
            vibrateCompat(200) // Completion haptic
            playBeeps(2) // Success beeps
            
            if (isRerecording) {
                // Handle re-recording - update existing tag directly
                handleRerecording()
            } else {
                // Launch NFC writing activity for new recording
                val intent = Intent(this, NFCWritingActivity::class.java).apply {
                    putExtra("audio_file_path", recordedFilePath)
                }
                startActivity(intent)
            }
        } else {
            recordingState.value = RecordingState.ERROR
            ttsService.speak("Failed to save recording")
        }
    }
    
    private fun handleRerecording() {
        lifecycleScope.launch {
            try {
                val application = application as AudioTaggerApplication
                val repository = application.repository
                
                // Get the existing tag
                val existingTag = repository.getTag(rerecordTagId!!)
                if (existingTag != null) {
                    // Update the existing tag with new audio content, preserving all other metadata
                    val updatedTag = existingTag.copy(
                        content = recordedFilePath!!,
                        title = rerecordTitle ?: existingTag.title,
                        description = rerecordDescription ?: existingTag.description,
                        groups = rerecordGroups ?: existingTag.groups,
                        createdAt = System.currentTimeMillis() // Update timestamp
                    )
                    
                    // Save the updated tag
                    repository.updateTag(updatedTag)
                    
                    Log.d("RecordingActivity", "Re-recording completed for tag: ${rerecordTagId}")
                    ttsService.speak("Re-recording completed successfully.")
                    
                    // Close this activity after a brief delay
                    delay(2000)
                    finish()
                } else {
                    Log.e("RecordingActivity", "Original tag not found for re-recording: $rerecordTagId")
                    ttsService.speak("Error: Original tag not found")
                    finish()
                }
            } catch (e: Exception) {
                Log.e("RecordingActivity", "Error during re-recording", e)
                ttsService.speak("Error during re-recording")
                finish()
            }
        }
    }
    
    private fun cancelRecording() {
        recordingTimer?.cancel()
        audioRecordingService.cancelRecording()
        recordedFilePath = null
        finish() // Return to main screen
    }
    
    // Removed proceedToNFCWriting - now automatic
    
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
    
    private fun playCountdownBeeps() {
        // Standard recording countdown: three short beeps, one long beep
        lifecycleScope.launch {
            val toneGen = ToneGenerator(AudioManager.STREAM_SYSTEM, 40) // Reduced volume to 40%
            try {
                // First short beep - 0.25s ON (higher frequency tone)
                toneGen.startTone(ToneGenerator.TONE_DTMF_D) // Higher frequency
                vibrateCompat(30)
                delay(250) // 0.25s beep (reduced duration)
                toneGen.stopTone()
                delay(500) // 0.5s OFF (increased gap)
                
                // Second short beep - 0.25s ON (higher frequency tone)
                toneGen.startTone(ToneGenerator.TONE_DTMF_D) // Higher frequency
                vibrateCompat(30)
                delay(250) // 0.25s beep (reduced duration)
                toneGen.stopTone()
                delay(500) // 0.5s OFF (increased gap)
                
                // Third short beep - 0.25s ON (higher frequency tone)
                toneGen.startTone(ToneGenerator.TONE_DTMF_D) // Higher frequency
                vibrateCompat(30)
                delay(250) // 0.25s beep (reduced duration)
                toneGen.stopTone()
                delay(500) // Gap before long beep
                
                // Long beep to signal recording start - 1.5s continuous (same frequency)
                toneGen.startTone(ToneGenerator.TONE_DTMF_D) // Same frequency as short beeps
                vibrateCompat(150)
                delay(1500) // 1.5s long beep
                toneGen.stopTone()
                
                // Ensure all audio hardware has finished processing before recording
                delay(600) // 0.6s delay to balance clean audio without losing recording start
                
            } finally {
                toneGen.release()
            }
            
            // Start recording only after all beeps are completely finished
            startRecording()
        }
    }
    
    private fun playBeeps(count: Int) {
        // General purpose beeps (for success, warnings, etc.)
        lifecycleScope.launch {
            val toneGen = ToneGenerator(AudioManager.STREAM_SYSTEM, 60)
            try {
                repeat(count) {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    vibrateCompat(50)
                    delay(200)
                }
            } finally {
                toneGen.release()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        recordingTimer?.cancel()
        ttsService.shutdown()
    }
}

enum class RecordingState {
    READY,
    COUNTDOWN, 
    RECORDING,
    COMPLETED,
    ERROR
}

@Composable
fun RecordingScreen(
    recordingState: RecordingState,
    timeRemaining: Int,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancel: () -> Unit,
    showPermissionDialog: Boolean,
    onDismissPermissionDialog: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .then(
                when (recordingState) {
                    RecordingState.READY -> Modifier.clickable { onStartRecording() }
                    RecordingState.RECORDING -> Modifier.clickable { onStopRecording() }
                    else -> Modifier
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        
        // Title
        Text(
            text = "Record Audio Tag",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 48.dp)
        )
        
        // Recording Status Display
        RecordingStatusCard(
            recordingState = recordingState,
            timeRemaining = timeRemaining
        )
        
        // Reduced spacer - less empty space
        Spacer(modifier = Modifier.height(32.dp))
        
        // Action Area
        when (recordingState) {
            RecordingState.READY -> {
                // Large call-to-action card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "👆",
                                fontSize = 40.sp
                            )
                            Text(
                                text = "Tap to Start",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Touch anywhere on this screen",
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            RecordingState.COUNTDOWN -> {
                CountdownDisplay()
            }
            RecordingState.RECORDING -> {
                // No buttons needed - entire screen is tappable
                Spacer(modifier = Modifier.height(140.dp))
            }
            RecordingState.COMPLETED -> {
                CompletedState(onCancel = onCancel)
            }
            RecordingState.ERROR -> {
                ErrorButtons(onCancel = onCancel)
            }
        }
    }
    
    // Permission Dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = onDismissPermissionDialog,
            title = { Text("Audio Permission Required") },
            text = { Text("AudioTagger needs microphone permission to record audio tags. Please grant permission in Settings.") },
            confirmButton = {
                TextButton(onClick = onDismissPermissionDialog) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun RecordingStatusCard(
    recordingState: RecordingState,
    timeRemaining: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (recordingState) {
                RecordingState.RECORDING -> MaterialTheme.colorScheme.errorContainer
                RecordingState.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
                RecordingState.ERROR -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
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
            when (recordingState) {
                RecordingState.READY -> {
                    // Large animated microphone icon
                    val infiniteTransition = rememberInfiniteTransition(label = "ready")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.9f,
                        targetValue = 1.1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000),
                            repeatMode = RepeatMode.Reverse
                        ), label = "scale"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🎤",
                            fontSize = (64 * scale).sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    
                    Text(
                        text = "Ready to Record",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap anywhere to start recording",
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                RecordingState.COUNTDOWN -> {
                    Text(
                        text = "⏱️ Get Ready",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Recording starting soon...",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
                RecordingState.RECORDING -> {
                    // Pulsing red circle for recording
                    val infiniteTransition = rememberInfiniteTransition(label = "recording")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse
                        ), label = "alpha"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = alpha)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔴",
                            fontSize = 32.sp
                        )
                    }
                    
                    Text(
                        text = "RECORDING",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Tap anywhere to finish",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${timeRemaining}s remaining",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                RecordingState.COMPLETED -> {
                    Text(
                        text = "✅ Recording Complete",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Ready to save to NFC tag",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
                RecordingState.ERROR -> {
                    Text(
                        text = "❌ Recording Failed",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Please try again",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}


@Composable
fun CountdownDisplay() {
    // Larger countdown display to match button sizing
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🎵",
                    fontSize = 32.sp
                )
                Text(
                    text = "Get ready to speak...",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}


@Composable
fun CompletedState(
    onCancel: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Large instruction card - no button needed
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📱",
                        fontSize = 40.sp
                    )
                    Text(
                        text = "Scan your NFC tag now",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Hold phone close to tag",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        // Only cancel button needed
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Text(
                text = "Discard & Go Back",
                fontSize = 20.sp
            )
        }
    }
}

@Composable
fun ErrorButtons(
    onCancel: () -> Unit
) {
    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp) // Larger error button too
    ) {
        Text(
            text = "Go Back",
            fontSize = 20.sp
        )
    }
}