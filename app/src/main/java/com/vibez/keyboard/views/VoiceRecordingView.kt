package com.vibez.keyboard.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.vibez.keyboard.theme.KeyboardTheme
import kotlin.math.*

/**
 * VoiceRecordingView - Circular recording interface with live sine wave animation.
 *
 * Shows:
 * - Pulsing outer ring that responds to audio amplitude
 * - Animated sine wave inside the circle
 * - Live transcription text below
 * - Stop/cancel controls
 */
class VoiceRecordingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // -------------------------------------------------------------------------
    // Config
    // -------------------------------------------------------------------------
    var theme: KeyboardTheme = KeyboardTheme.DARK
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }

    var onStopRecording: (() -> Unit)? = null
    var onCancelRecording: (() -> Unit)? = null

    // Current amplitude (0f..1f) — updated from audio
    var amplitude: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            targetAmplitude = field
        }

    // Current transcription text
    var transcriptionText: String = ""
        set(value) {
            field = value
            invalidate()
        }

    // -------------------------------------------------------------------------
    // Animation state
    // -------------------------------------------------------------------------
    private var wavePhase = 0f
    private var pulseScale = 1f
    private var smoothAmplitude = 0f
    private var targetAmplitude = 0f
    private var waveAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null
    private val wavePoints = FloatArray(512)

    // -------------------------------------------------------------------------
    // Geometry
    // -------------------------------------------------------------------------
    private var centerX = 0f
    private var centerY = 0f
    private var circleRadius = 0f

    // -------------------------------------------------------------------------
    // Paint objects
    // -------------------------------------------------------------------------
    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val waveGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val micIconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val transcriptionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stopButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stopButtonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val wavePath = Path()
    private val clipPath = Path()

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------
    init {
        updatePaints()
        startAnimations()
    }

    private fun updatePaints() {
        val accent = theme.accentColor

        outerRingPaint.apply {
            color = accent
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        outerGlowPaint.apply {
            color = accent
            alpha = 60
            style = Paint.Style.STROKE
            strokeWidth = 12f
            maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
        }

        circleFillPaint.apply {
            color = Color.argb(180, 0, 0, 0)
            style = Paint.Style.FILL
        }

        wavePaint.apply {
            color = accent
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            strokeCap = Paint.Cap.ROUND
        }

        waveGlowPaint.apply {
            color = accent
            alpha = 80
            style = Paint.Style.STROKE
            strokeWidth = 6f
            maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        }

        micIconPaint.apply {
            color = accent
            style = Paint.Style.FILL
        }

        transcriptionPaint.apply {
            color = Color.WHITE
            textSize = 15f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        hintPaint.apply {
            color = Color.argb(150, 200, 200, 200)
            textSize = 13f
            textAlign = Paint.Align.CENTER
        }

        stopButtonPaint.apply {
            color = Color.argb(200, 220, 50, 50)
            style = Paint.Style.FILL
        }

        stopButtonTextPaint.apply {
            color = Color.WHITE
            textSize = 14f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    // -------------------------------------------------------------------------
    // Animations
    // -------------------------------------------------------------------------
    private fun startAnimations() {
        // Continuous wave phase animation
        waveAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                wavePhase = anim.animatedValue as Float
                smoothAmplitude += (targetAmplitude - smoothAmplitude) * 0.15f
                buildWave()
                invalidate()
            }
            start()
        }

        // Pulse animation
        pulseAnimator = ValueAnimator.ofFloat(1f, 1.08f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                pulseScale = anim.animatedValue as Float
            }
            start()
        }
    }

    private fun buildWave() {
        if (circleRadius == 0f) return
        val waveWidth = circleRadius * 1.6f
        val step = waveWidth / wavePoints.size * 2
        val baseAmp = circleRadius * 0.18f
        val dynamicAmp = baseAmp + (smoothAmplitude * circleRadius * 0.35f)
        val freq = 2.5f

        var i = 0
        var x = centerX - waveWidth * 0.5f
        while (i < wavePoints.size) {
            wavePoints[i] = x
            wavePoints[i + 1] = centerY + (dynamicAmp *
                sin(freq * (x - centerX + centerX) / circleRadius + wavePhase) *
                cos(wavePhase * 0.3f + i * 0.01f)).toFloat()
            x += step
            i += 2
        }
    }

    // -------------------------------------------------------------------------
    // Layout
    // -------------------------------------------------------------------------
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w * 0.5f
        centerY = h * 0.42f
        circleRadius = min(w, h) * 0.28f
        buildWave()
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawBackground(canvas)
        drawOuterRing(canvas)
        drawCircleWithWave(canvas)
        drawMicIcon(canvas)
        drawTranscriptionArea(canvas)
        drawControls(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        canvas.drawColor(theme.keyboardBackground)
    }

    private fun drawOuterRing(canvas: Canvas) {
        val r = circleRadius * pulseScale + (smoothAmplitude * circleRadius * 0.2f)

        // Glow ring
        val glowPaint = Paint(outerGlowPaint).apply {
            alpha = (60 + smoothAmplitude * 120).toInt().coerceIn(0, 255)
        }
        canvas.drawCircle(centerX, centerY, r + 12f, glowPaint)

        // Outer ring
        val ringPaint = Paint(outerRingPaint).apply {
            alpha = (180 + smoothAmplitude * 75).toInt().coerceIn(0, 255)
        }
        canvas.drawCircle(centerX, centerY, r, ringPaint)

        // Second pulsing ring
        val outerR = r + 20f + smoothAmplitude * 10f
        val outerRingPaint2 = Paint(outerRingPaint).apply {
            alpha = (50 + smoothAmplitude * 60).toInt().coerceIn(0, 255)
            strokeWidth = 1.5f
        }
        canvas.drawCircle(centerX, centerY, outerR, outerRingPaint2)
    }

    private fun drawCircleWithWave(canvas: Canvas) {
        // Filled circle background
        canvas.drawCircle(centerX, centerY, circleRadius - 2f, circleFillPaint)

        if (wavePoints.size < 4) return

        // Clip wave to circle
        canvas.save()
        clipPath.reset()
        clipPath.addCircle(centerX, centerY, circleRadius - 3f, Path.Direction.CW)
        canvas.clipPath(clipPath)

        // Build wave path
        wavePath.reset()
        wavePath.moveTo(wavePoints[0], wavePoints[1])
        var i = 2
        while (i < wavePoints.size - 2) {
            val cx1 = (wavePoints[i] + wavePoints[i - 2]) * 0.5f
            val cy1 = (wavePoints[i + 1] + wavePoints[i - 1]) * 0.5f
            wavePath.quadTo(wavePoints[i - 2], wavePoints[i - 1], cx1, cy1)
            i += 2
        }

        // Draw glow wave
        canvas.drawPath(wavePath, waveGlowPaint)
        // Draw main wave
        canvas.drawPath(wavePath, wavePaint)

        canvas.restore()
    }

    private fun drawMicIcon(canvas: Canvas) {
        // Small mic icon at center when amplitude is low
        if (smoothAmplitude < 0.05f) {
            val r = circleRadius * 0.22f
            val micPaint = Paint(micIconPaint).apply {
                alpha = ((1f - smoothAmplitude * 20f).coerceIn(0f, 1f) * 180).toInt()
            }
            // Mic body
            val micRect = RectF(centerX - r * 0.5f, centerY - r * 1.1f,
                centerX + r * 0.5f, centerY + r * 0.1f)
            canvas.drawRoundRect(micRect, r * 0.5f, r * 0.5f, micPaint)

            // Stand
            val standPaint = Paint(micIconPaint).apply {
                alpha = micPaint.alpha
                style = Paint.Style.STROKE
                strokeWidth = 2f
                strokeCap = Paint.Cap.ROUND
            }
            val path = Path().apply {
                moveTo(centerX - r * 0.8f, centerY + r * 0.1f)
                quadTo(centerX - r * 0.8f, centerY + r * 0.6f, centerX, centerY + r * 0.6f)
                quadTo(centerX + r * 0.8f, centerY + r * 0.6f, centerX + r * 0.8f, centerY + r * 0.1f)
            }
            canvas.drawPath(path, standPaint)
            canvas.drawLine(centerX, centerY + r * 0.6f, centerX, centerY + r, standPaint)
        }
    }

    private fun drawTranscriptionArea(canvas: Canvas) {
        val textStartY = centerY + circleRadius + 32f

        if (transcriptionText.isNotEmpty()) {
            // Wrap text
            val maxWidth = width * 0.85f
            val words = transcriptionText.split(" ")
            val lines = mutableListOf<String>()
            var currentLine = ""

            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (transcriptionPaint.measureText(testLine) <= maxWidth) {
                    currentLine = testLine
                } else {
                    if (currentLine.isNotEmpty()) lines.add(currentLine)
                    currentLine = word
                }
            }
            if (currentLine.isNotEmpty()) lines.add(currentLine)

            // Show last 2 lines
            val displayLines = lines.takeLast(2)
            displayLines.forEachIndexed { idx, line ->
                canvas.drawText(line, centerX, textStartY + idx * 22f, transcriptionPaint)
            }
        } else {
            canvas.drawText("Speak now...", centerX, textStartY, hintPaint)
        }
    }

    private fun drawControls(canvas: Canvas) {
        val buttonY = height - 28f
        val stopButtonRadius = 30f

        // Stop button (red square icon inside circle)
        canvas.drawCircle(centerX, buttonY, stopButtonRadius, stopButtonPaint)
        val squareSize = 14f
        val squarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(
            centerX - squareSize, buttonY - squareSize,
            centerX + squareSize, buttonY + squareSize,
            squarePaint
        )
    }

    // -------------------------------------------------------------------------
    // Touch handling for stop button
    // -------------------------------------------------------------------------
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun handleTouch(x: Float, y: Float) {
        val buttonY = height - 28f
        val dist = sqrt((x - centerX).pow(2) + (y - buttonY).pow(2))
        if (dist <= 40f) {
            onStopRecording?.invoke()
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        waveAnimator?.cancel()
        pulseAnimator?.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (waveAnimator?.isRunning != true) {
            startAnimations()
        }
    }
}
