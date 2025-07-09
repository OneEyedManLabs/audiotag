package org.oneeyedmanlabs.audiotag

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
        vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        
        // Initialize TTS
        ttsService.initialize {
            Log.d("RecordingActivity", "TTS initialized")
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
        ttsService.speak("Get ready. Recording in 3, 2, 1")
        
        // Start countdown after TTS
        lifecycleScope.launch {
            delay(3000) // Wait for TTS
            playBeeps(3) // Countdown beeps
            delay(1500) // Time for beeps
            startRecording()
        }
    }
    
    private fun startRecording() {
        val fileName = "audio_${System.currentTimeMillis()}"
        recordedFilePath = audioRecordingService.startRecording(fileName)
        
        if (recordedFilePath != null) {
            recordingState.value = RecordingState.RECORDING
            startTimer()
            vibrator?.vibrate(100) // Haptic feedback
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
                
                // Warning beeps at 5 and 3 seconds
                if (secondsLeft == 5 || secondsLeft == 3) {
                    playBeeps(1)
                    if (secondsLeft == 5) {
                        ttsService.speak("5 seconds remaining")
                    }
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
            vibrator?.vibrate(200) // Completion haptic
            playBeeps(2) // Success beeps
            
            // Launch NFC writing activity
            val intent = Intent(this, NFCWritingActivity::class.java).apply {
                putExtra("audio_file_path", recordedFilePath)
            }
            startActivity(intent)
        } else {
            recordingState.value = RecordingState.ERROR
            ttsService.speak("Failed to save recording")
        }
    }
    
    private fun cancelRecording() {
        recordingTimer?.cancel()
        audioRecordingService.cancelRecording()
        recordedFilePath = null
        finish() // Return to main screen
    }
    
    // Removed proceedToNFCWriting - now automatic
    
    private fun playBeeps(count: Int) {
        // Simple system beep using ToneGenerator
        repeat(count) {
            vibrator?.vibrate(50)
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
            .padding(24.dp),
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
        
        // Action Buttons - Now take up more space
        when (recordingState) {
            RecordingState.READY -> {
                ReadyButtons(
                    onStartRecording = onStartRecording,
                    onCancel = onCancel
                )
            }
            RecordingState.COUNTDOWN -> {
                CountdownDisplay()
            }
            RecordingState.RECORDING -> {
                RecordingButtons(
                    onStopRecording = onStopRecording,
                    timeRemaining = timeRemaining
                )
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
                    Text(
                        text = "🎤 Ready to Record",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap 'Start Recording' when ready",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
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
                        text = "${timeRemaining}s remaining",
                        fontSize = 18.sp,
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
fun ReadyButtons(
    onStartRecording: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Much larger start recording button
        Button(
            onClick = onStartRecording,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp), // Much taller
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🎤",
                    fontSize = 32.sp
                )
                Text(
                    text = "Start Recording",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp) // Larger cancel button too
        ) {
            Text(
                text = "Cancel",
                fontSize = 20.sp
            )
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
fun RecordingButtons(
    onStopRecording: () -> Unit,
    timeRemaining: Int
) {
    // Very large stop button - takes up lots of space for easy access
    Button(
        onClick = onStopRecording,
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp), // Very tall stop button
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
                text = "STOP RECORDING",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${timeRemaining}s left",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
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