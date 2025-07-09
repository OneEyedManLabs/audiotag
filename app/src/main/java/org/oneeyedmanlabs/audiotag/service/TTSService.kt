package org.oneeyedmanlabs.audiotag.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class TTSService(private val context: Context) {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    fun initialize(onInitialized: () -> Unit = {}) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                isInitialized = true
                onInitialized()
                Log.d("TTSService", "TTS initialized successfully")
            } else {
                Log.e("TTSService", "TTS initialization failed")
            }
        }
    }
    
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (isInitialized) {
            tts?.speak(text, queueMode, null, null)
        } else {
            Log.w("TTSService", "TTS not initialized, cannot speak: $text")
        }
    }
    
    fun stop() {
        tts?.stop()
    }
    
    fun shutdown() {
        tts?.shutdown()
        isInitialized = false
    }
    
    fun isReady(): Boolean = isInitialized
}