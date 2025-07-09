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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.oneeyedmanlabs.audiotag.service.NFCService
import org.oneeyedmanlabs.audiotag.ui.theme.AudioTagTheme

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
            AudioTagTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onRecordAudio = {
                            startActivity(Intent(this@MainActivity, RecordingActivity::class.java))
                        },
                        onMyTags = {
                            startActivity(Intent(this@MainActivity, TagListActivity::class.java))
                        },
                        onSettings = {
                            // TODO: Launch SettingsActivity
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
    onMyTags: () -> Unit = {},
    onSettings: () -> Unit = {}
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
            text = "Label your world with audio",
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
                    .height(72.dp),
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
            
            // My Tags Button  
            Button(
                onClick = onMyTags,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
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
            ) {
                Text(
                    text = "⚙️ Settings",
                    fontSize = 18.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Status indicator
        Text(
            text = "Ready to create audio tags",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}