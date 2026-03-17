package com.voxtype.keyboard.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.voxtype.keyboard.R
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * VoiceOverlayView – the full-keyboard overlay shown during voice input.
 *
 * Layout (top → bottom):
 *  ┌──────────────────────────────────────────────┐
 *  │  [Live transcript text]                      │
 *  │                                              │
 *  │          ╔══════════════╗                    │
 *  │          ║  Waveform    ║  ← glowing circle  │
 *  │          ╚══════════════╝                    │
 *  │                                              │
 *  │  [Pause]  [Stop & Insert]  [Cancel]          │
 *  └──────────────────────────────────────────────┘
 */
class VoiceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    // ─── Callbacks ────────────────────────────────────────────────────────────

    var onPause: (() -> Unit)? = null
    var onResume: (() -> Unit)? = null
    var onStopAndInsert: (() -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    // ─── Child views ──────────────────────────────────────────────────────────

    private val transcriptText: TextView
    private val waveformView: AudioWaveformView
    private val circleContainer: CircleView
    private val pauseBtn: TextView
    private val stopBtn: TextView
    private val cancelBtn: TextView
    private val statusLabel: TextView

    // ─── Pulse animation ──────────────────────────────────────────────────────

    private var isPaused = false

    private val pulseAnimator = ValueAnimator.ofFloat(0.92f, 1.05f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener { va ->
            val s = va.animatedValue as Float
            circleContainer.scaleX = s
            circleContainer.scaleY = s
        }
    }

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        setBackgroundColor(0xEE0E0E18.toInt())

        val density = context.resources.displayMetrics.density

        // Live transcript
        transcriptText = TextView(context).apply {
            textSize = 14f
            setTextColor(0xFFE0E0FF.toInt())
            gravity = Gravity.CENTER
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(4))
            text = context.getString(R.string.voice_listening)
        }
        val transcriptParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP }
        addView(transcriptText, transcriptParams)

        // Status label (below transcript)
        statusLabel = TextView(context).apply {
            textSize = 11f
            setTextColor(0xFF7C4DFF.toInt())
            gravity = Gravity.CENTER
            text = context.getString(R.string.voice_status_recording)
        }
        val statusParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
            topMargin = dpToPx(60)
        }
        addView(statusLabel, statusParams)

        // Circle container (holds waveform inside a glowing ring)
        circleContainer = CircleView(context)
        val circleSize = dpToPx(130)
        val circleParams = LayoutParams(circleSize, circleSize).apply {
            gravity = Gravity.CENTER
        }
        addView(circleContainer, circleParams)

        // Waveform inside circle
        waveformView = AudioWaveformView(context)
        val waveParams = LayoutParams(
            (circleSize * 0.75).toInt(), (circleSize * 0.35).toInt()
        ).apply { gravity = Gravity.CENTER }
        circleContainer.addView(waveformView, waveParams)

        // Bottom control strip
        val btnStrip = buildButtonStrip(context)
        val btnParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM; bottomMargin = dpToPx(8) }
        addView(btnStrip, btnParams)

        // Retrieve button references (set in buildButtonStrip)
        pauseBtn = btnStrip.findViewWithTag("pause")
        stopBtn = btnStrip.findViewWithTag("stop")
        cancelBtn = btnStrip.findViewWithTag("cancel")

        pauseBtn.setOnClickListener { onPauseToggle() }
        stopBtn.setOnClickListener { onStopAndInsert?.invoke() }
        cancelBtn.setOnClickListener { onCancel?.invoke() }
    }

    // ─── Button strip ─────────────────────────────────────────────────────────

    private fun buildButtonStrip(ctx: Context): FrameLayout {
        val container = FrameLayout(ctx)
        val strip = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val btnPause = makeButton(ctx, ctx.getString(R.string.voice_btn_pause), "pause")
        val btnStop = makeButton(ctx, ctx.getString(R.string.voice_btn_insert), "stop", primary = true)
        val btnCancel = makeButton(ctx, ctx.getString(R.string.voice_btn_cancel), "cancel")

        strip.addView(btnPause)
        strip.addView(btnStop)
        strip.addView(btnCancel)

        container.addView(strip, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        return container
    }

    private fun makeButton(ctx: Context, label: String, tag: String, primary: Boolean = false): TextView {
        return TextView(ctx).apply {
            this.tag = tag
            text = label
            textSize = 13f
            setTextColor(if (primary) 0xFFFFFFFF.toInt() else 0xFFBBBBDD.toInt())
            background = if (primary) {
                android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 20f * ctx.resources.displayMetrics.density
                    setColor(0xFF7C4DFF.toInt())
                }
            } else {
                android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 20f * ctx.resources.displayMetrics.density
                    setColor(0xFF2D2D3A.toInt())
                }
            }
            setPadding(dpToPx(18), dpToPx(8), dpToPx(18), dpToPx(8))
            val ml = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dpToPx(6), 0, dpToPx(6), 0) }
            layoutParams = ml
        }
    }

    // ─── Pause / resume ───────────────────────────────────────────────────────

    private fun onPauseToggle() {
        isPaused = !isPaused
        if (isPaused) {
            onPause?.invoke()
            pauseBtn.text = context.getString(R.string.voice_btn_resume)
            statusLabel.text = context.getString(R.string.voice_status_paused)
            pulseAnimator.pause()
        } else {
            onResume?.invoke()
            pauseBtn.text = context.getString(R.string.voice_btn_pause)
            statusLabel.text = context.getString(R.string.voice_status_recording)
            pulseAnimator.resume()
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    fun updateTranscript(text: String) {
        transcriptText.text = text.ifBlank { context.getString(R.string.voice_listening) }
    }

    fun clearTranscript() {
        transcriptText.text = context.getString(R.string.voice_listening)
        isPaused = false
        pauseBtn.text = context.getString(R.string.voice_btn_pause)
        statusLabel.text = context.getString(R.string.voice_status_recording)
        waveformView.resetAmplitude()
    }

    fun updateAudioLevel(level: Float) {
        waveformView.audioLevel = level
    }

    fun startPulse() {
        pulseAnimator.start()
    }

    fun stopPulse() {
        pulseAnimator.cancel()
        circleContainer.scaleX = 1f
        circleContainer.scaleY = 1f
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).roundToInt()
}

// ═══════════════════════════════════════════════════════════════════════════════
// CircleView – draws the glowing circular recording indicator
// ═══════════════════════════════════════════════════════════════════════════════

internal class CircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF18182A.toInt()
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFF7C4DFF.toInt()
        maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
    }
    private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        color = 0x447C4DFF.toInt()
        maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
    }

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - 8f
        canvas.drawCircle(cx, cy, r, bgPaint)
        canvas.drawCircle(cx, cy, r + 4f, outerGlowPaint)
        canvas.drawCircle(cx, cy, r, ringPaint)
    }
}
