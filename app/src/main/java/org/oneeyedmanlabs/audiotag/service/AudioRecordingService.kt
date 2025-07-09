package org.oneeyedmanlabs.audiotag.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecordingService(private val context: Context) {
    
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentFilePath: String? = null
    
    fun startRecording(fileName: String): String? {
        try {
            val audioDir = File(context.filesDir, "audio_tags")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            
            val audioFile = File(audioDir, "$fileName.mp4")
            currentFilePath = audioFile.absolutePath
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(currentFilePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                
                prepare()
                start()
                isRecording = true
                
                Log.d("AudioRecordingService", "Recording started: $currentFilePath")
            }
            
            return currentFilePath
            
        } catch (e: IOException) {
            Log.e("AudioRecordingService", "Failed to start recording", e)
            releaseRecorder()
            return null
        }
    }
    
    fun stopRecording(): String? {
        return try {
            if (isRecording && mediaRecorder != null) {
                mediaRecorder?.stop()
                isRecording = false
                
                val filePath = currentFilePath
                releaseRecorder()
                
                Log.d("AudioRecordingService", "Recording stopped: $filePath")
                filePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("AudioRecordingService", "Failed to stop recording", e)
            releaseRecorder()
            null
        }
    }
    
    fun cancelRecording() {
        try {
            if (isRecording && mediaRecorder != null) {
                mediaRecorder?.stop()
                isRecording = false
                
                // Delete the file
                currentFilePath?.let { path ->
                    File(path).delete()
                    Log.d("AudioRecordingService", "Recording cancelled and file deleted: $path")
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRecordingService", "Error cancelling recording", e)
        } finally {
            releaseRecorder()
        }
    }
    
    private fun releaseRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
        currentFilePath = null
    }
    
    fun isCurrentlyRecording(): Boolean = isRecording
    
    fun getCurrentFilePath(): String? = currentFilePath
}