package com.voxtype.keyboard.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

/**
 * AudioLevelMonitor – captures raw PCM audio and emits normalised RMS levels
 * (0.0 – 1.0) on a background thread.
 *
 * This runs alongside Android's SpeechRecognizer (which also uses the mic).
 * On API 29+ both can share the microphone. On earlier APIs the recognizer
 * takes priority; we fall back to the onRmsChanged callback from
 * RecognitionListener instead (see VoiceInputManager).
 */
class AudioLevelMonitor(private val onLevel: (Float) -> Unit) {

    private val sampleRate = 16_000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(2048)

    @Volatile private var running = false
    private var thread: Thread? = null
    private var audioRecord: AudioRecord? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({
            val record = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }.getOrNull()

            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                running = false
                return@Thread
            }
            audioRecord = record
            record.startRecording()

            val buf = ShortArray(bufferSize / 2)
            while (running) {
                val read = record.read(buf, 0, buf.size)
                if (read > 0) {
                    val rms = computeRms(buf, read)
                    // Normalise: typical speech peak ~3000, silence ~50
                    val level = (rms / 3000f).coerceIn(0f, 1f)
                    onLevel(level)
                }
            }
            record.stop()
            record.release()
            audioRecord = null
        }, "AudioLevelMonitor")
        thread?.isDaemon = true
        thread?.start()
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun computeRms(buf: ShortArray, count: Int): Float {
        var sum = 0.0
        for (i in 0 until count) {
            val v = buf[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / count).toFloat()
    }
}
