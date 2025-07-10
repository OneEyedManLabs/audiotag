package org.oneeyedmanlabs.audiotag.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * Service for converting audio to text using Android's SpeechRecognizer API
 * Provides speech-to-text functionality for automatic tag title generation
 */
class SpeechToTextService(private val context: Context) {
    
    companion object {
        private const val TAG = "SpeechToTextService"
    }
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    
    // State flows for UI observation
    private val _recognitionState = MutableStateFlow(RecognitionState.IDLE)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()
    
    private val _partialResults = MutableStateFlow("")
    val partialResults: StateFlow<String> = _partialResults.asStateFlow()
    
    private val _finalResult = MutableStateFlow("")
    val finalResult: StateFlow<String> = _finalResult.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Callbacks for easier integration
    private var onResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var onPartialResultCallback: ((String) -> Unit)? = null
    
    enum class RecognitionState {
        IDLE,
        PREPARING,
        LISTENING,
        PROCESSING,
        COMPLETED,
        ERROR
    }
    
    /**
     * Initialize the speech recognizer service
     */
    fun initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition not available on this device")
            _error.value = "Speech recognition not available on this device"
            _recognitionState.value = RecognitionState.ERROR
            return
        }
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(recognitionListener)
        
        Log.d(TAG, "SpeechToTextService initialized successfully")
    }
    
    /**
     * Start listening for speech input
     * @param language Optional language code (e.g., "en-US", "es-ES")
     * @param onResult Callback for final recognized text
     * @param onError Callback for errors
     * @param onPartialResult Callback for partial recognition results
     */
    fun startListening(
        language: String? = null,
        onResult: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
        onPartialResult: ((String) -> Unit)? = null
    ) {
        if (isListening) {
            Log.w(TAG, "Already listening, ignoring start request")
            return
        }
        
        if (speechRecognizer == null) {
            initialize()
        }
        
        // Set callbacks
        onResultCallback = onResult
        onErrorCallback = onError
        onPartialResultCallback = onPartialResult
        
        // Clear previous results
        _partialResults.value = ""
        _finalResult.value = ""
        _error.value = null
        
        // Create recognition intent
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            
            // Set language if provided, otherwise use device default
            val locale = language ?: Locale.getDefault().toString()
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
            
            // Optimize for short phrases/titles
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
            
            // Prefer on-device recognition if available (more private and faster)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        
        try {
            isListening = true
            _recognitionState.value = RecognitionState.PREPARING
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "Started listening for speech input")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            handleError("Failed to start speech recognition: ${e.message}")
        }
    }
    
    /**
     * Stop listening for speech input
     */
    fun stopListening() {
        if (!isListening) {
            return
        }
        
        try {
            speechRecognizer?.stopListening()
            Log.d(TAG, "Stopped listening for speech input")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition", e)
        }
    }
    
    /**
     * Cancel current recognition session
     */
    fun cancel() {
        if (!isListening) {
            return
        }
        
        try {
            speechRecognizer?.cancel()
            isListening = false
            _recognitionState.value = RecognitionState.IDLE
            Log.d(TAG, "Cancelled speech recognition")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling speech recognition", e)
        }
    }
    
    /**
     * Check if speech recognition is available on this device
     */
    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            isListening = false
            _recognitionState.value = RecognitionState.IDLE
            Log.d(TAG, "SpeechToTextService shut down")
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
    }
    
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
            _recognitionState.value = RecognitionState.LISTENING
        }
        
        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech detected")
            _recognitionState.value = RecognitionState.LISTENING
        }
        
        override fun onRmsChanged(rmsdB: Float) {
            // Audio level changed - could be used for UI feedback
        }
        
        override fun onBufferReceived(buffer: ByteArray?) {
            // Audio buffer received - not typically used
        }
        
        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech detected")
            _recognitionState.value = RecognitionState.PROCESSING
        }
        
        override fun onError(error: Int) {
            isListening = false
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions - RECORD_AUDIO permission required"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No clear speech detected - try speaking more clearly"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition busy - try again"
                SpeechRecognizer.ERROR_SERVER -> "Speech recognition service error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected - try speaking louder"
                else -> "Unknown error ($error)"
            }
            
            Log.e(TAG, "Recognition error: $errorMessage")
            handleError(errorMessage)
        }
        
        override fun onResults(results: Bundle?) {
            isListening = false
            _recognitionState.value = RecognitionState.COMPLETED
            
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0]
                Log.d(TAG, "Recognition completed: $recognizedText")
                
                _finalResult.value = recognizedText
                onResultCallback?.invoke(recognizedText)
            } else {
                Log.w(TAG, "No recognition results")
                handleError("No speech was recognized")
            }
        }
        
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val partialText = matches[0]
                Log.d(TAG, "Partial result: $partialText")
                
                _partialResults.value = partialText
                onPartialResultCallback?.invoke(partialText)
            }
        }
        
        override fun onEvent(eventType: Int, params: Bundle?) {
            // Additional events - not typically used
        }
    }
    
    private fun handleError(errorMessage: String) {
        isListening = false
        _recognitionState.value = RecognitionState.ERROR
        _error.value = errorMessage
        onErrorCallback?.invoke(errorMessage)
    }
}