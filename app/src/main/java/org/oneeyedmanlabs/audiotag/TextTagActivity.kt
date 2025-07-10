package org.oneeyedmanlabs.audiotag

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.launch
import org.oneeyedmanlabs.audiotag.service.AudioTaggerApplication
import org.oneeyedmanlabs.audiotag.service.TTSService
import org.oneeyedmanlabs.audiotag.ui.theme.AudioTagTheme
import org.oneeyedmanlabs.audiotag.ui.theme.ThemeManager

/**
 * Activity for creating text-based audio tags
 * Users type text which is played back using TTS when the NFC tag is scanned
 */
class TextTagActivity : ComponentActivity() {
    
    private lateinit var ttsService: TTSService
    
    // State
    private var textContent = mutableStateOf("")
    private var tagTitle = mutableStateOf("")
    private var tagDescription = mutableStateOf("")
    private var selectedGroups = mutableStateOf<Set<String>>(emptySet())
    private var newGroupText = mutableStateOf("")
    private var isCreating = mutableStateOf(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize TTS service
        ttsService = TTSService(this)
        ttsService.initialize {
            Log.d("TextTagActivity", "TTS initialized")
            ttsService.speak("Create a text tag. Type your message and tap preview to hear how it will sound.")
        }
        
        setContent {
            val currentTheme by ThemeManager.getCurrentThemeState()
            AudioTagTheme(themeOption = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TextTagScreen(
                        textContent = textContent.value,
                        tagTitle = tagTitle.value,
                        tagDescription = tagDescription.value,
                        selectedGroups = selectedGroups.value,
                        newGroupText = newGroupText.value,
                        isCreating = isCreating.value,
                        onTextContentChange = { textContent.value = it },
                        onTagTitleChange = { tagTitle.value = it },
                        onTagDescriptionChange = { tagDescription.value = it },
                        onGroupToggle = { group ->
                            selectedGroups.value = if (selectedGroups.value.contains(group)) {
                                selectedGroups.value - group
                            } else {
                                selectedGroups.value + group
                            }
                        },
                        onNewGroupTextChange = { newGroupText.value = it },
                        onAddNewGroup = { addNewGroup() },
                        onPreview = { previewText() },
                        onCreate = { createTextTag() },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }
    
    private fun addNewGroup() {
        val groupName = newGroupText.value.trim()
        if (groupName.isNotEmpty()) {
            selectedGroups.value = selectedGroups.value + groupName
            newGroupText.value = ""
        }
    }
    
    private fun previewText() {
        val content = textContent.value.trim()
        if (content.isNotEmpty()) {
            ttsService.speak(content)
        }
    }
    
    private fun createTextTag() {
        val content = textContent.value.trim()
        val title = tagTitle.value.trim()
        
        if (content.isEmpty()) {
            ttsService.speak("Please enter some text content")
            return
        }
        
        if (title.isEmpty()) {
            ttsService.speak("Please enter a title for your tag")
            return
        }
        
        isCreating.value = true
        
        // Launch NFCWritingActivity with text content
        lifecycleScope.launch {
            try {
                val intent = Intent(this@TextTagActivity, NFCWritingActivity::class.java).apply {
                    putExtra("text_content", content)
                    putExtra("tag_title", title)
                    putExtra("tag_description", tagDescription.value.trim().takeIf { it.isNotEmpty() })
                    putExtra("tag_groups", selectedGroups.value.toTypedArray())
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Log.e("TextTagActivity", "Error creating text tag", e)
                ttsService.speak("Error creating text tag")
                isCreating.value = false
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ttsService.shutdown()
    }
}

@Composable
fun TextTagScreen(
    textContent: String,
    tagTitle: String,
    tagDescription: String,
    selectedGroups: Set<String>,
    newGroupText: String,
    isCreating: Boolean,
    onTextContentChange: (String) -> Unit,
    onTagTitleChange: (String) -> Unit,
    onTagDescriptionChange: (String) -> Unit,
    onGroupToggle: (String) -> Unit,
    onNewGroupTextChange: (String) -> Unit,
    onAddNewGroup: () -> Unit,
    onPreview: () -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        // Title
        Text(
            text = "Create Text Tag",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
        )
        
        // Text Content Input
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
                Text(
                    text = "Message Content",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                OutlinedTextField(
                    value = textContent,
                    onValueChange = onTextContentChange,
                    label = { Text("Type your message *") },
                    placeholder = { Text("This text will be spoken when the tag is scanned") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Enter the text message that will be spoken when the NFC tag is scanned" },
                    minLines = 3,
                    maxLines = 8
                )
            }
        }
        
        // Tag Metadata
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
                Text(
                    text = "Tag Information",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Title
                OutlinedTextField(
                    value = tagTitle,
                    onValueChange = onTagTitleChange,
                    label = { Text("Title *") },
                    placeholder = { Text("Short name for this tag") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Enter a title for this text tag" },
                    singleLine = true
                )
                
                // Description
                OutlinedTextField(
                    value = tagDescription,
                    onValueChange = onTagDescriptionChange,
                    label = { Text("Description (optional)") },
                    placeholder = { Text("Brief description of this tag") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Enter an optional description for this tag" },
                    maxLines = 2
                )
                
                // Groups
                GroupSelectionSection(
                    selectedGroups = selectedGroups,
                    newGroupText = newGroupText,
                    onGroupToggle = onGroupToggle,
                    onNewGroupTextChange = onNewGroupTextChange,
                    onAddNewGroup = onAddNewGroup
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action Buttons
        if (isCreating) {
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            text = "Creating text tag...",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Preview Button
                OutlinedButton(
                    onClick = onPreview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics { contentDescription = "Preview how the text will sound using text-to-speech" },
                    enabled = textContent.trim().isNotEmpty()
                ) {
                    Text(
                        text = "🔊 Preview",
                        fontSize = 18.sp
                    )
                }
                
                // Create Button
                Button(
                    onClick = onCreate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .semantics { contentDescription = "Create text tag and proceed to NFC writing" },
                    enabled = textContent.trim().isNotEmpty() && tagTitle.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "💬 Create Text Tag",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Cancel Button
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics { contentDescription = "Cancel text tag creation and return to main screen" }
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
    
}

@Composable
fun GroupSelectionSection(
    selectedGroups: Set<String>,
    newGroupText: String,
    onGroupToggle: (String) -> Unit,
    onNewGroupTextChange: (String) -> Unit,
    onAddNewGroup: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Groups:",
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp
        )
        
        // Common groups
        val commonGroups = listOf("Work", "Personal", "Music", "Notes", "Info", "Family")
        
        // Display available groups as chips
        val allGroups = (commonGroups + selectedGroups).distinct().sorted()
        if (allGroups.isNotEmpty()) {
            Text(
                text = "Select groups:",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium
            )
            
            // Use FlowRow-like layout with multiple rows
            Column {
                allGroups.chunked(2).forEach { rowGroups ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowGroups.forEach { group ->
                            FilterChip(
                                onClick = { onGroupToggle(group) },
                                label = { Text(group, fontSize = 14.sp, maxLines = 1) },
                                selected = selectedGroups.contains(group),
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                        // Fill remaining space if row has fewer than 2 items
                        repeat(2 - rowGroups.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        
        // Add new group
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newGroupText,
                onValueChange = onNewGroupTextChange,
                label = { Text("New group") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Enter name for new group" }
            )
            TextButton(
                onClick = onAddNewGroup,
                enabled = newGroupText.trim().isNotEmpty()
            ) {
                Text("Add")
            }
        }
    }
}