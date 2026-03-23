package com.vibez.keyboard.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.vibez.keyboard.language.KeyboardLanguage
import kotlinx.coroutines.*

/**
 * SpeechRecognitionManager
 *
 * Manages real-time, continuous speech recognition using Android's built-in
 * SpeechRecognizer. Automatically restarts after each result to provide
 * continuous (no time-limit) transcription.
 *
 * Supports English (en-US) and Greek (el-GR).
 */
class SpeechRecognitionManager(
    private val context: Context,
    private val listener: SpeechListener
) {
    // -------------------------------------------------------------------------
    // Listener interface
    // -------------------------------------------------------------------------
    interface SpeechListener {
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onAmplitudeChanged(amplitude: Float)
        fun onError(error: String)
        fun onRecordingStarted()
        fun onRecordingStopped()
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldContinue = false
    private var currentLanguage = KeyboardLanguage.ENGLISH
    private var lastPartialText = ""

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var amplitudeJob: Job? = null

    companion object {
        private const val TAG = "VibezSpeech"
        private const val MAX_AMPLITUDE = 32767f

        fun isAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context)
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------
    fun startListening(language: KeyboardLanguage) {
        if (!isAvailable(context)) {
            listener.onError("Speech recognition not available on this device")
            return
        }
        currentLanguage = language
        shouldContinue = true
        startRecognitionSession()
    }

    fun stopListening() {
        shouldContinue = false
        isListening = false
        amplitudeJob?.cancel()
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer", e)
        }
        recognizer = null
        listener.onRecordingStopped()
    }

    fun setLanguage(language: KeyboardLanguage) {
        if (currentLanguage != language && isListening) {
            currentLanguage = language
            // Restart with new language
            recognizer?.stopListening()
        } else {
            currentLanguage = language
        }
    }

    val isActive: Boolean get() = isListening

    // -------------------------------------------------------------------------
    // Internal recognition session
    // -------------------------------------------------------------------------
    private fun startRecognitionSession() {
        if (!shouldContinue) return

        // Clean up previous recognizer
        try {
            recognizer?.destroy()
        } catch (e: Exception) { /* ignore */ }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
                isListening = true
                listener.onRecordingStarted()
                startAmplitudePolling()
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Convert RMS dB to 0..1 amplitude
                val normalised = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                listener.onAmplitudeChanged(normalised)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "End of speech")
            }

            override fun onError(error: Int) {
                val msg = errorMessage(error)
                Log.w(TAG, "Recognition error: $msg ($error)")
                isListening = false

                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // Normal — restart if we should continue
                        if (shouldContinue) {
                            scope.launch {
                                delay(300)
                                startRecognitionSession()
                            }
                        }
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        scope.launch {
                            delay(500)
                            startRecognitionSession()
                        }
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        listener.onError("Microphone permission required")
                        shouldContinue = false
                    }
                    else -> {
                        listener.onError(msg)
                        if (shouldContinue) {
                            scope.launch {
                                delay(1000)
                                startRecognitionSession()
                            }
                        }
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""
                if (text.isNotEmpty()) {
                    lastPartialText = ""
                    listener.onFinalResult(text)
                }

                // Restart for continuous listening
                if (shouldContinue) {
                    scope.launch {
                        delay(100)
                        startRecognitionSession()
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""
                if (text.isNotEmpty() && text != lastPartialText) {
                    lastPartialText = text
                    listener.onPartialResult(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage.locale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Prefer offline if available
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // Long silence detection
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500)
        }

        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            listener.onError("Failed to start voice recognition")
        }
    }

    private fun startAmplitudePolling() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            while (isActive && isListening) {
                delay(50)
                // Amplitude is driven by onRmsChanged — this is a fallback
            }
        }
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------
    fun destroy() {
        shouldContinue = false
        amplitudeJob?.cancel()
        scope.cancel()
        try {
            recognizer?.destroy()
        } catch (e: Exception) { /* ignore */ }
        recognizer = null
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
        else -> "Unknown error ($error)"
    }
}
