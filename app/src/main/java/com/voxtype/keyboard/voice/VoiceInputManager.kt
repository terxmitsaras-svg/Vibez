package com.voxtype.keyboard.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * VoiceInputManager – wraps Android's SpeechRecognizer to provide
 * "unlimited" (chunk-based) continuous voice-to-text recording.
 *
 * Strategy for unlimited recording:
 *  1. Start a recognition session.
 *  2. When the recognizer emits onResults (end of utterance) or
 *     onError (silence timeout), if [isActive] is still true,
 *     immediately start a new session and prepend committed text.
 *  3. On each cycle, partial and final results accumulate in [committedText].
 *  4. The caller receives live partials via [onPartialResult] and can
 *     retrieve final accumulated text via [stopAndCommit].
 */
class VoiceInputManager(
    private val context: Context,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onError: (Int) -> Unit
) {

    companion object {
        private const val TAG = "VoiceInputManager"

        // Errors that should trigger an automatic restart
        private val RESTARTABLE_ERRORS = setOf(
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY
        )
    }

    // ─── State ────────────────────────────────────────────────────────────────

    @Volatile private var isActive = false
    @Volatile private var isPaused = false

    private var committedText = StringBuilder()
    private var currentPartial = ""
    private var language = "en-US"

    // ─── Recognizer ───────────────────────────────────────────────────────────

    private var recognizer: SpeechRecognizer? = null
    private val audioLevelMonitor = AudioLevelMonitor { level ->
        // Forward level update to the overlay (via the partial callback channel)
        // We use a sentinel prefix so the IME can route it to the waveform view.
        // (Alternative: add a separate lambda – kept simple here.)
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Begin listening. Safe to call multiple times; restarts if already active.
     */
    fun startListening(languageCode: String) {
        stopListening()
        language = bcp47(languageCode)
        committedText.clear()
        currentPartial = ""
        isActive = true
        isPaused = false
        createRecognizer()
        startSession()
        audioLevelMonitor.start()
    }

    fun pauseListening() {
        if (!isActive) return
        isPaused = true
        recognizer?.stopListening()
    }

    fun resumeListening() {
        if (!isActive || !isPaused) return
        isPaused = false
        startSession()
    }

    /**
     * Stop recording and deliver accumulated text to [onFinalResult].
     */
    fun stopAndCommit() {
        isActive = false
        isPaused = false
        recognizer?.stopListening()
        audioLevelMonitor.stop()
        val full = buildFullText()
        if (full.isNotBlank()) {
            onFinalResult(full)
        }
        committedText.clear()
        currentPartial = ""
    }

    fun cancel() {
        isActive = false
        isPaused = false
        recognizer?.cancel()
        audioLevelMonitor.stop()
        committedText.clear()
        currentPartial = ""
    }

    fun stopListening() {
        isActive = false
        isPaused = false
        audioLevelMonitor.stop()
        recognizer?.run {
            stopListening()
            destroy()
        }
        recognizer = null
    }

    fun destroy() {
        stopListening()
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private fun createRecognizer() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(recognitionListener)
        }
    }

    private fun startSession() {
        if (!isActive || isPaused) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Extend silence thresholds as much as the platform allows
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            // Offline-first hint
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "startListening failed: ${e.message}")
        }
    }

    private fun buildFullText(): String {
        val base = committedText.toString().trim()
        val partial = currentPartial.trim()
        return when {
            base.isEmpty() -> partial
            partial.isEmpty() -> base
            else -> "$base $partial"
        }
    }

    // ─── RecognitionListener ──────────────────────────────────────────────────

    private val recognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // rmsdB is roughly -2 (silence) to +10 (loud speech)
            val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            // We don't push this directly to the UI from here; it is the
            // AudioLevelMonitor thread that updates the waveform.
            // Kept here for fallback / debugging purposes.
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
        }

        override fun onError(error: Int) {
            Log.w(TAG, "onError: $error")
            when {
                !isActive -> return
                error in RESTARTABLE_ERRORS -> {
                    // Seamlessly restart the recognition session
                    restartSession()
                }
                else -> {
                    isActive = false
                    audioLevelMonitor.stop()
                    onError(error)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = matches?.firstOrNull()
            if (!best.isNullOrBlank()) {
                // Append finalized segment with a trailing space
                if (committedText.isNotEmpty()) committedText.append(" ")
                committedText.append(best.trim())
                currentPartial = ""
                onPartialResult(buildFullText())
            }
            if (isActive && !isPaused) {
                restartSession()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return
            currentPartial = partial.trim()
            onPartialResult(buildFullText())
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun restartSession() {
        // Small delay to avoid hammering the recognizer service
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isActive && !isPaused) {
                // Re-create recognizer to avoid stale state
                createRecognizer()
                startSession()
            }
        }, 150)
    }

    // ─── Language utilities ───────────────────────────────────────────────────

    private fun bcp47(code: String): String = when (code.lowercase()) {
        "en" -> "en-US"
        "el" -> "el-GR"
        "es" -> "es-ES"
        "fr" -> "fr-FR"
        "de" -> "de-DE"
        "it" -> "it-IT"
        "pt" -> "pt-BR"
        "ar" -> "ar"
        "hi" -> "hi-IN"
        "zh" -> "zh-CN"
        else -> code
    }
}
