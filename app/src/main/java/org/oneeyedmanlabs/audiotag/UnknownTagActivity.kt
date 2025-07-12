package org.oneeyedmanlabs.audiotag

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.oneeyedmanlabs.audiotag.service.TTSService
import org.oneeyedmanlabs.audiotag.ui.theme.AudioTagTheme
import org.oneeyedmanlabs.audiotag.ui.theme.ThemeManager

/**
 * Activity for handling unknown NFC tags
 * Provides options to record audio, create text tag, or cancel
 */
class UnknownTagActivity : ComponentActivity() {
    
    private lateinit var ttsService: TTSService
    private var tagId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get tag ID from intent
        tagId = intent.getStringExtra("tag_id")
        if (tagId == null) {
            Log.e("UnknownTagActivity", "No tag ID provided")
            finish()
            return
        }
        
        // Initialize TTS
        ttsService = TTSService(this)
        ttsService.initialize {
            Log.d("UnknownTagActivity", "TTS initialized")
            // Announce the unknown tag situation
            ttsService.speak("Unknown NFC tag detected. Choose what to create for this tag.")
        }
        
        Log.d("UnknownTagActivity", "Unknown tag activity started for tag: $tagId")
        
        setContent {
            val currentTheme by ThemeManager.getCurrentThemeState()
            AudioTagTheme(themeOption = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UnknownTagScreen(
                        tagId = tagId!!,
                        onRecordAudio = { startRecordingForTag() },
                        onCreateText = { startTextCreationForTag() },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ttsService.shutdown()
    }
    
    private fun startRecordingForTag() {
        Log.d("UnknownTagActivity", "Starting recording for unknown tag: $tagId")
        ttsService.speak("Starting audio recording")
        
        val recordingIntent = Intent(this, RecordingActivity::class.java).apply {
            putExtra("tag_id", tagId)
            putExtra("from_unknown_tag", true)
        }
        startActivity(recordingIntent)
        finish()
    }
    
    private fun startTextCreationForTag() {
        Log.d("UnknownTagActivity", "Starting text creation for unknown tag: $tagId")
        ttsService.speak("Creating text tag")
        
        val textTagIntent = Intent(this, TextTagActivity::class.java).apply {
            putExtra("tag_id", tagId)
            putExtra("from_unknown_tag", true)
        }
        startActivity(textTagIntent)
        finish()
    }
}

@Composable
fun UnknownTagScreen(
    tagId: String,
    onRecordAudio: () -> Unit,
    onCreateText: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(48.dp))
            
            Text(
                text = "Unknown NFC Tag",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Cancel and return to previous screen" }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Main content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.weight(1f)
        ) {
            // NFC icon representation
            Text(
                text = "📱",
                fontSize = 72.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Title and description
            Text(
                text = "New NFC Tag Detected",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "This NFC tag isn't associated with any audio yet. What would you like to create for it?",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Record Audio button
                Button(
                    onClick = onRecordAudio,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .semantics { contentDescription = "Record audio for this NFC tag" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎤", fontSize = 24.sp)
                        Text(
                            text = "Record Audio",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Create Text button
                Button(
                    onClick = onCreateText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .semantics { contentDescription = "Create text tag for this NFC tag" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💬", fontSize = 24.sp)
                        Text(
                            text = "Create Text Tag",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Cancel button
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics { contentDescription = "Cancel and return to previous screen" }
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        // Tag ID info at bottom
        Text(
            text = "Tag ID: ${tagId.take(16)}${if (tagId.length > 16) "..." else ""}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}