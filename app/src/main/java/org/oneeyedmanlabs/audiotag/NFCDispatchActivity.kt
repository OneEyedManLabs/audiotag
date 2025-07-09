package org.oneeyedmanlabs.audiotag

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import org.oneeyedmanlabs.audiotag.service.NFCService

/**
 * Invisible activity that handles NFC intents from background
 * Routes to TagInfoActivity for tag playback
 */
class NFCDispatchActivity : Activity() {
    
    private val nfcService = NFCService()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("NFCDispatchActivity", "=== NFC Dispatch Activity Created ===")
        Log.d("NFCDispatchActivity", "Intent: ${intent}")
        Log.d("NFCDispatchActivity", "Intent action: ${intent.action}")
        
        // Handle the NFC intent immediately
        handleNFCIntent(intent)
        
        // Close this activity immediately - we don't need UI
        Log.d("NFCDispatchActivity", "Finishing NFCDispatchActivity")
        finish()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("NFCDispatchActivity", "=== New NFC Intent ===")
        handleNFCIntent(intent)
        finish()
    }
    
    private fun handleNFCIntent(intent: Intent) {
        Log.d("NFCDispatchActivity", "=== Handling NFC Intent ===")
        Log.d("NFCDispatchActivity", "Intent action: ${intent.action}")
        
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            
            Log.d("NFCDispatchActivity", "✅ Valid NFC action detected")
            
            // Extract tag ID from intent
            val tagId = nfcService.getTagIdFromIntent(intent)
            
            if (tagId != null) {
                Log.d("NFCDispatchActivity", "✅ NFC Tag extracted from Intent: $tagId")
                
                // Launch TagInfoActivity with the tag ID
                val tagInfoIntent = Intent(this, TagInfoActivity::class.java).apply {
                    putExtra("tag_id", tagId)
                    putExtra("from_nfc_scan", true) // Mark this as coming from NFC scan
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                try {
                    Log.d("NFCDispatchActivity", "🚀 Starting TagInfoActivity...")
                    startActivity(tagInfoIntent)
                    Log.d("NFCDispatchActivity", "✅ TagInfoActivity started successfully for tag: $tagId")
                } catch (e: Exception) {
                    Log.e("NFCDispatchActivity", "❌ Failed to start TagInfoActivity", e)
                }
            } else {
                Log.e("NFCDispatchActivity", "❌ No valid tag ID found in intent")
            }
        } else {
            Log.d("NFCDispatchActivity", "❌ Non-NFC intent action: ${intent.action}")
        }
    }
}