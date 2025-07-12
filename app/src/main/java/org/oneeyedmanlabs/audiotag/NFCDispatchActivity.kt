package org.oneeyedmanlabs.audiotag

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.oneeyedmanlabs.audiotag.repository.TagRepository
import org.oneeyedmanlabs.audiotag.service.AudioTaggerApplication
import org.oneeyedmanlabs.audiotag.service.NFCService

/**
 * Invisible activity that handles NFC intents from background
 * Routes to TagInfoActivity for known tags or UnknownTagActivity for new tags
 */
class NFCDispatchActivity : Activity() {
    
    private val nfcService = NFCService()
    private lateinit var repository: TagRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize repository
        val application = application as AudioTaggerApplication
        repository = application.repository
        
        Log.d("NFCDispatchActivity", "=== NFC Dispatch Activity Created ===")
        Log.d("NFCDispatchActivity", "Intent: ${intent}")
        Log.d("NFCDispatchActivity", "Intent action: ${intent.action}")
        
        // Handle the NFC intent immediately
        handleNFCIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("NFCDispatchActivity", "=== New NFC Intent ===")
        handleNFCIntent(intent)
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
                
                // Check if tag exists in database
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        var finalTagId = tagId
                        var existingTag = repository.getTag(tagId)
                        
                        // If NDEF-embedded tag ID doesn't exist, try hardware tag ID as fallback
                        if (existingTag == null) {
                            val hardwareTagId = nfcService.getHardwareTagIdFromIntent(intent)
                            if (hardwareTagId != null && hardwareTagId != tagId) {
                                Log.d("NFCDispatchActivity", "NDEF tag ID not found, trying hardware ID: $hardwareTagId")
                                existingTag = repository.getTag(hardwareTagId)
                                if (existingTag != null) {
                                    finalTagId = hardwareTagId
                                    Log.d("NFCDispatchActivity", "Found tag using hardware ID")
                                } else {
                                    // Use hardware ID for unknown tag creation
                                    finalTagId = hardwareTagId
                                    Log.d("NFCDispatchActivity", "Using hardware ID for unknown tag")
                                }
                            }
                        }
                        
                        // Switch back to main thread for UI operations
                        runOnUiThread {
                            if (existingTag != null) {
                                // Known tag - launch TagInfoActivity
                                Log.d("NFCDispatchActivity", "✅ Known tag found - launching TagInfoActivity")
                                val tagInfoIntent = Intent(this@NFCDispatchActivity, TagInfoActivity::class.java).apply {
                                    putExtra("tag_id", finalTagId)
                                    putExtra("from_nfc_scan", true)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(tagInfoIntent)
                            } else {
                                // Unknown tag - launch UnknownTagActivity
                                Log.d("NFCDispatchActivity", "🆕 Unknown tag detected - launching UnknownTagActivity")
                                val unknownTagIntent = Intent(this@NFCDispatchActivity, UnknownTagActivity::class.java).apply {
                                    putExtra("tag_id", finalTagId)
                                    putExtra("from_nfc_scan", true)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(unknownTagIntent)
                            }
                            
                            // Close this invisible activity
                            finish()
                        }
                        
                    } catch (e: Exception) {
                        Log.e("NFCDispatchActivity", "❌ Error checking tag in database", e)
                        runOnUiThread { finish() }
                    }
                }
            } else {
                Log.e("NFCDispatchActivity", "❌ No valid tag ID found in intent")
                finish()
            }
        } else {
            Log.d("NFCDispatchActivity", "❌ Non-NFC intent action: ${intent.action}")
            finish()
        }
    }
}