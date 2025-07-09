package org.oneeyedmanlabs.audiotag

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.oneeyedmanlabs.audiotag.ui.theme.AudioTagTheme

/**
 * Help screen providing usage instructions and creative use cases for AudioTagger
 */
class HelpActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            AudioTagTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HelpScreen(
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit
) {
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
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Text(
                text = "Help & Instructions",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.width(48.dp)) // Balance the back button
        }
        
        // Help content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Getting Started section
            HelpSection(title = "Getting Started", icon = "🚀") {
                HelpCard(
                    title = "1. Record Audio",
                    description = "Tap 'Record Audio' to create a new audio tag. Follow the voice prompts to record your message."
                )
                HelpCard(
                    title = "2. Write to NFC Tag",
                    description = "After recording, hold your phone near an NFC tag to save the audio to it."
                )
                HelpCard(
                    title = "3. Play Back",
                    description = "Tap the NFC tag with your phone to instantly play the audio message."
                )
            }
            
            // Features section
            HelpSection(title = "Features", icon = "✨") {
                HelpCard(
                    title = "Tag Management",
                    description = "View all your audio tags in 'My Tags'. Edit titles, descriptions, and organize with groups."
                )
                HelpCard(
                    title = "Group Filtering",
                    description = "Create groups to organize your tags and filter by group in the tag list."
                )
                HelpCard(
                    title = "Re-record Audio",
                    description = "Update audio content without rescanning the NFC tag. Metadata is preserved."
                )
                HelpCard(
                    title = "Voice Instructions",
                    description = "Toggle voice prompts on/off in Settings for a quieter experience."
                )
            }
            
            // Creative Use Cases section
            HelpSection(title = "Creative Use Cases", icon = "💡") {
                HelpCard(
                    title = "Home Organization",
                    description = "Label storage boxes, pantry items, or seasonal decorations with audio descriptions."
                )
                HelpCard(
                    title = "Memory Preservation",
                    description = "Attach to photo albums, gifts, or keepsakes to record personal messages and memories."
                )
                HelpCard(
                    title = "Accessibility",
                    description = "Create audio labels for visually impaired users on household items, medications, or important documents."
                )
                HelpCard(
                    title = "Educational Tools",
                    description = "Create interactive learning materials - attach to books, flashcards, or educational toys."
                )
                HelpCard(
                    title = "Garden & Plants",
                    description = "Record care instructions, planting dates, or growth observations for your plants."
                )
                HelpCard(
                    title = "Cooking & Recipes",
                    description = "Attach to recipe cards or ingredient containers with cooking tips and modifications."
                )
                HelpCard(
                    title = "Travel & Adventures",
                    description = "Create audio souvenirs by recording stories about places you've visited."
                )
                HelpCard(
                    title = "Gift Enhancement",
                    description = "Add personal audio messages to gifts, making them more meaningful and memorable."
                )
                HelpCard(
                    title = "Workspace Organization",
                    description = "Label tools, equipment, or project materials with usage instructions or status updates."
                )
                HelpCard(
                    title = "Child Development",
                    description = "Create interactive story books or educational games for children using audio tags."
                )
            }
            
            // Tips section
            HelpSection(title = "Tips & Tricks", icon = "🔧") {
                HelpCard(
                    title = "NFC Tag Placement",
                    description = "Place NFC tags on flat surfaces away from metal objects for best scanning results."
                )
                HelpCard(
                    title = "Recording Quality",
                    description = "Record in a quiet environment and speak clearly for the best audio quality."
                )
                HelpCard(
                    title = "Group Organization",
                    description = "Use descriptive group names like 'Kitchen', 'Office', or 'Personal' to stay organized."
                )
                HelpCard(
                    title = "Regular Backups",
                    description = "Consider backing up important audio files as NFC tags can be damaged or lost."
                )
            }
        }
    }
}

@Composable
fun HelpSection(
    title: String,
    icon: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text(
                text = icon,
                fontSize = 20.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        content()
    }
}

@Composable
fun HelpCard(
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                lineHeight = 20.sp
            )
        }
    }
}