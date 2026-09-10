package org.oneeyedmanlabs.audiotag

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.oneeyedmanlabs.audiotag.service.NFCTagRouter
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

    // Refreshed in onResume so the notice clears when the user comes back
    // from turning NFC on.
    private val nfcState = mutableStateOf(NfcState.ON)
    
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
                        nfcState = nfcState.value,
                        onOpenNfcSettings = { openNfcSettings() },
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
    }
    
    override fun onResume() {
        super.onResume()
        nfcState.value = when {
            nfcAdapter == null -> NfcState.UNAVAILABLE
            nfcAdapter?.isEnabled != true -> NfcState.OFF
            else -> NfcState.ON
        }
        // Null filters: while this screen is in front, every tag comes to us.
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
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
        if (!NFCTagRouter.isNfcIntent(intent)) return
        // Same routing as a background scan: known tags play, new tags offer
        // to record. Stays in this task so Back returns here.
        lifecycleScope.launch {
            try {
                NFCTagRouter.open(this@MainActivity, intent, newTask = false)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to route NFC tag", e)
            }
        }
    }

    private fun openNfcSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
    }
}

enum class NfcState { ON, OFF, UNAVAILABLE }

@Composable
fun MainScreen(
    nfcState: NfcState = NfcState.ON,
    onOpenNfcSettings: () -> Unit = {},
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
            text = "AudioTag",
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
        when (nfcState) {
            NfcState.ON -> Text(
                text = "Ready to create audio tags",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
            NfcState.OFF -> NfcNotice(
                message = "NFC is turned off. Tags cannot be scanned until it is on.",
                buttonText = "Turn on NFC",
                onClick = onOpenNfcSettings
            )
            NfcState.UNAVAILABLE -> NfcNotice(
                message = "This device has no NFC, so tags cannot be scanned.",
                buttonText = null,
                onClick = {}
            )
        }
    }
}

@Composable
private fun NfcNotice(message: String, buttonText: String?, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = message },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = message,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            if (buttonText != null) {
                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics { contentDescription = "Open NFC settings to turn NFC on" }
                ) {
                    Text(text = buttonText, fontSize = 18.sp)
                }
            }
        }
    }
}
