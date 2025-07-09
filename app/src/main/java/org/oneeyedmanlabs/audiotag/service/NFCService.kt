package org.oneeyedmanlabs.audiotag.service

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.util.Log
import java.io.UnsupportedEncodingException

class NFCService {
    
    fun getTagId(tag: Tag): String {
        // Fallback to hardware tag ID
        val tagId = tag.id
        val hardwareId = bytesToHexString(tagId)
        Log.d("NFCService", "Using hardware tag ID: $hardwareId")
        return hardwareId
    }
    
    fun getTagIdFromIntent(intent: Intent): String? {
        // First try to read AudioTagger content from Intent NDEF data
        val audioTaggerId = readAudioTaggerIdFromIntent(intent)
        if (audioTaggerId != null) {
            Log.d("NFCService", "Found AudioTagger ID in Intent NDEF: $audioTaggerId")
            return audioTaggerId
        }
        
        // Fallback to hardware tag ID from Intent
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        }
        if (tag != null) {
            val hardwareId = bytesToHexString(tag.id)
            Log.d("NFCService", "Using hardware tag ID from Intent: $hardwareId")
            return hardwareId
        }
        
        Log.e("NFCService", "No tag found in Intent")
        return null
    }
    
    fun readAudioTaggerIdFromIntent(intent: Intent): String? {
        // For NDEF_DISCOVERED intents, the NDEF message is included in the intent
        val ndefMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }
        
        if (ndefMessages != null && ndefMessages.isNotEmpty()) {
            for (ndefMessageParcelable in ndefMessages) {
                val ndefMessage = ndefMessageParcelable as NdefMessage
                for (record in ndefMessage.records) {
                    if (record.tnf == NdefRecord.TNF_WELL_KNOWN && 
                        record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                        
                        val textContent = readTextRecord(record)
                        // Handle both old and new NDEF format
                        when {
                            textContent?.startsWith("org.oneeyedmanlabs.audiotag:") == true -> {
                                return textContent.substring("org.oneeyedmanlabs.audiotag:".length)
                            }
                            textContent?.startsWith("AUDIOTAG:") == true -> {
                                return textContent.substring("AUDIOTAG:".length)
                            }
                        }
                    }
                }
            }
        }
        
        return null
    }
    
    fun readAudioTaggerId(tag: Tag): String? {
        val ndef = Ndef.get(tag) ?: return null
        
        return try {
            ndef.connect()
            val ndefMessage = ndef.ndefMessage
            ndef.close()
            
            if (ndefMessage != null) {
                for (record in ndefMessage.records) {
                    if (record.tnf == NdefRecord.TNF_WELL_KNOWN && 
                        record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                        
                        val textContent = readTextRecord(record)
                        // Handle both old and new NDEF format
                        when {
                            textContent?.startsWith("org.oneeyedmanlabs.audiotag:") == true -> {
                                return textContent.substring("org.oneeyedmanlabs.audiotag:".length)
                            }
                            textContent?.startsWith("AUDIOTAG:") == true -> {
                                return textContent.substring("AUDIOTAG:".length)
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e("NFCService", "Error reading NDEF message", e)
            null
        }
    }
    
    private fun readTextRecord(record: NdefRecord): String? {
        return try {
            val payload = record.payload
            if (payload.isEmpty()) return null
            
            val textEncoding = if ((payload[0].toInt() and 128) == 0) "UTF-8" else "UTF-16"
            val languageCodeLength = payload[0].toInt() and 63
            
            String(payload, languageCodeLength + 1, payload.size - languageCodeLength - 1, charset(textEncoding))
        } catch (e: UnsupportedEncodingException) {
            Log.e("NFCService", "Error decoding text record", e)
            null
        }
    }
    
    private fun bytesToHexString(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }
    
    fun isNFCEnabled(nfcAdapter: NfcAdapter?): Boolean {
        return nfcAdapter?.isEnabled == true
    }
    
    fun logTagInfo(tag: Tag) {
        Log.d("NFCService", "Tag ID: ${getTagId(tag)}")
        Log.d("NFCService", "Tag Tech List: ${tag.techList.joinToString(", ")}")
    }
}