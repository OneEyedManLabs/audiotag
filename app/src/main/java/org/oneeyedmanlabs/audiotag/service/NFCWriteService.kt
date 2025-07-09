package org.oneeyedmanlabs.audiotag.service

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.util.Log
import java.io.IOException
import java.nio.charset.StandardCharsets

class NFCWriteService {
    
    fun writeAudioTaggerRecord(tag: Tag, tagId: String): Boolean {
        return try {
            // Create only a text record with the tag ID - no AAR needed since we have intent filters
            val textRecord = createAudioTaggerTextRecord(tagId)
            val ndefMessage = NdefMessage(arrayOf(textRecord))
            
            // Try to write to the tag
            writeNdefMessage(tag, ndefMessage)
        } catch (e: Exception) {
            Log.e("NFCWriteService", "Failed to write NFC tag", e)
            false
        }
    }
    
    private fun createAudioTaggerTextRecord(tagId: String): NdefRecord {
        // Create a text record with our specific identifier that's less likely to conflict
        val payload = "org.oneeyedmanlabs.audiotag:$tagId"
        return createTextRecord(payload)
    }
    
    private fun createApplicationRecord(): NdefRecord {
        // Create an Android Application Record (AAR) that launches AudioTagger
        val packageName = "org.oneeyedmanlabs.audiotag"
        return NdefRecord.createApplicationRecord(packageName)
    }
    
    private fun createTextRecord(text: String): NdefRecord {
        val language = "en"
        val languageBytes = language.toByteArray(StandardCharsets.US_ASCII)
        val textBytes = text.toByteArray(StandardCharsets.UTF_8)
        
        val payload = ByteArray(1 + languageBytes.size + textBytes.size)
        payload[0] = languageBytes.size.toByte()
        System.arraycopy(languageBytes, 0, payload, 1, languageBytes.size)
        System.arraycopy(textBytes, 0, payload, 1 + languageBytes.size, textBytes.size)
        
        return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, ByteArray(0), payload)
    }
    
    private fun writeNdefMessage(tag: Tag, ndefMessage: NdefMessage): Boolean {
        return try {
            // Try Ndef first (if tag is already formatted)
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (ndef.isWritable && ndef.maxSize >= ndefMessage.toByteArray().size) {
                    ndef.writeNdefMessage(ndefMessage)
                    ndef.close()
                    Log.d("NFCWriteService", "Successfully wrote to Ndef tag")
                    return true
                }
                ndef.close()
            }
            
            // Try NdefFormatable (if tag is blank/new)
            val ndefFormatable = NdefFormatable.get(tag)
            if (ndefFormatable != null) {
                ndefFormatable.connect()
                ndefFormatable.format(ndefMessage)
                ndefFormatable.close()
                Log.d("NFCWriteService", "Successfully formatted and wrote to tag")
                return true
            }
            
            Log.w("NFCWriteService", "Tag is not Ndef writable")
            false
            
        } catch (e: IOException) {
            Log.e("NFCWriteService", "IO Error writing to tag", e)
            false
        } catch (e: Exception) {
            Log.e("NFCWriteService", "General error writing to tag", e)
            false
        }
    }
    
    fun isTagWritable(tag: Tag): Boolean {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return ndef.isWritable
        }
        
        val ndefFormatable = NdefFormatable.get(tag)
        return ndefFormatable != null
    }
}