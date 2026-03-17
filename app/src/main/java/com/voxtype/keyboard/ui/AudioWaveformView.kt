package com.voxtype.keyboard.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

/**
 * AudioWaveformView – draws an animated sine-wave that reacts to microphone amplitude.
 *
 * Visual design:
 *  • Multiple overlapping sine waves at different phases / frequencies
 *  • Wave amplitude scales with the current audio level (0.0 – 1.0)
 *  • Idle animation: gentle slow pulse when amplitude is zero
 *  • Glow effect via double-draw with a blurred paint
 */
class AudioWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ─── Current audio level ──────────────────────────────────────────────────

    /** Set from outside; range 0.0 (silence) – 1.0 (loud). */
    var audioLevel: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            // Smooth toward target
            targetAmplitude = 0.08f + field * 0.42f
        }

    private var currentAmplitude = 0.08f
    private var targetAmplitude = 0.08f

    // ─── Animation phase ──────────────────────────────────────────────────────

    private var phase1 = 0f
    private var phase2 = 0f
    private var phase3 = 0f

    private val animator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 1800
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            val t = it.animatedValue as Float
            phase1 = t
            phase2 = t * 1.4f
            phase3 = t * 0.7f
            // Lerp amplitude
            currentAmplitude += (targetAmplitude - currentAmplitude) * 0.12f
            invalidate()
        }
    }

    // ─── Paints ───────────────────────────────────────────────────────────────

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 8f
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }

    // Wave colour gradient – purple → teal
    private var waveShader: LinearGradient? = null

    // ─── Path ─────────────────────────────────────────────────────────────────

    private val wavePath1 = Path()
    private val wavePath2 = Path()
    private val wavePath3 = Path()

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        waveShader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(0xFF7C4DFF.toInt(), 0xFF00BCD4.toInt(), 0xFF7C4DFF.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    // ─── Drawing ──────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return

        val cx = width / 2f
        val cy = height / 2f
        val amp = currentAmplitude * cy * 0.85f

        buildWave(wavePath1, phase1, amp, frequency = 1.0f)
        buildWave(wavePath2, phase2, amp * 0.65f, frequency = 1.8f)
        buildWave(wavePath3, phase3, amp * 0.4f, frequency = 2.6f)

        waveShader?.let {
            glowPaint.shader = it
            glowPaint.alpha = 80
            wavePaint.shader = it
            wavePaint.alpha = 255
        }

        // Glow layer (drawn first, behind)
        glowPaint.strokeWidth = 10f
        canvas.drawPath(wavePath1, glowPaint)

        // Crisp layer
        wavePaint.strokeWidth = 3.5f
        canvas.drawPath(wavePath1, wavePaint)
        wavePaint.alpha = 160
        wavePaint.strokeWidth = 2f
        canvas.drawPath(wavePath2, wavePaint)
        wavePaint.alpha = 100
        wavePaint.strokeWidth = 1.5f
        canvas.drawPath(wavePath3, wavePaint)
    }

    private fun buildWave(path: Path, phase: Float, amplitude: Float, frequency: Float) {
        path.reset()
        val cy = height / 2f
        val steps = width.coerceAtLeast(1)
        path.moveTo(0f, cy)
        for (x in 0..steps) {
            val ratio = x.toFloat() / steps
            val y = cy + amplitude * sin(ratio * 2 * PI * frequency + phase).toFloat()
            path.lineTo(x.toFloat(), y)
        }
    }

    // ─── External control ─────────────────────────────────────────────────────

    fun resetAmplitude() {
        audioLevel = 0f
        currentAmplitude = 0.08f
        targetAmplitude = 0.08f
    }
}
