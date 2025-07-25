package org.oneeyedmanlabs.audiotag

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.oneeyedmanlabs.audiotag.service.NFCService
import org.oneeyedmanlabs.audiotag.ui.theme.AudioTagTheme
import org.oneeyedmanlabs.audiotag.ui.theme.ThemeManager
import org.oneeyedmanlabs.audiotag.ui.theme.ThemeOption

/**
 * Clean slate MainActivity for AudioTagger V2
 * Simple, accessible design with large buttons
 */
class MainActivity : ComponentActivity() {
    
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFilters: Array<IntentFilter>? = null
    private val nfcService = NFCService()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize NFC
        setupNFC()
        
        setContent {
            val currentTheme by ThemeManager.getCurrentThemeState()
            AudioTagTheme(themeOption = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onRecordAudio = {
                            startActivity(Intent(this@MainActivity, RecordingActivity::class.java))
                        },
                        onCreateTextTag = {
                            startActivity(Intent(this@MainActivity, TextTagActivity::class.java))
                        },
                        onMyTags = {
                            startActivity(Intent(this@MainActivity, TagListActivity::class.java))
                        },
                        onSettings = {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        },
                        onHelp = {
                            startActivity(Intent(this@MainActivity, HelpActivity::class.java))
                        }
                    )
                }
            }
        }
    }
    
    private fun setupNFC() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        if (nfcAdapter == null) {
            // Device doesn't support NFC
            return
        }
        
        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
        
        val nfcIntentFilter = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        intentFilters = arrayOf(nfcIntentFilter)
    }
    
    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, null)
    }
    
    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNFCIntent(intent)
    }
    
    private fun handleNFCIntent(intent: Intent) {
        val action = intent.action
        if (NfcAdapter.ACTION_TAG_DISCOVERED == action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == action) {
            
            // Extract tag ID from intent
            val tagId = nfcService.getTagIdFromIntent(intent)
            
            if (tagId != null) {
                // Launch TagInfoActivity with foreground flag
                val tagInfoIntent = Intent(this, TagInfoActivity::class.java).apply {
                    putExtra("tag_id", tagId)
                    putExtra("foreground_nfc_scan", true) // Mark as foreground NFC scan
                }
                startActivity(tagInfoIntent)
            }
        }
    }
}

@Composable
fun MainScreen(
    onRecordAudio: () -> Unit = {},
    onCreateTextTag: () -> Unit = {},
    onMyTags: () -> Unit = {},
    onSettings: () -> Unit = {},
    onHelp: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        
        // App title
        Text(
            text = "AudioTagger",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 48.dp)
        )
        
        Text(
            text = "Label your world with audio or text",
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Main action buttons - Large and accessible
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            
            // Record Audio Button
            Button(
                onClick = onRecordAudio,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .semantics { contentDescription = "Record new audio tag. Tap to start recording." },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "🎤 Record Audio",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            // Create Text Tag Button
            Button(
                onClick = onCreateTextTag,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .semantics { contentDescription = "Create new text tag using typed message. Tap to start." },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(
                    text = "💬 Create Text Tag",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            // My Tags Button  
            Button(
                onClick = onMyTags,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .semantics { contentDescription = "View and manage saved audio and text tags" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Text(
                    text = "📋 My Tags",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            // Settings Button
            OutlinedButton(
                onClick = onSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = "Open app settings and preferences" }
            ) {
                Text(
                    text = "⚙️ Settings",
                    fontSize = 18.sp
                )
            }
            
            // Help Button
            OutlinedButton(
                onClick = onHelp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = "View help and usage instructions" }
            ) {
                Text(
                    text = "❓ Help",
                    fontSize = 18.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Status indicator
        Text(
            text = "Ready to create audio tags",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }
}