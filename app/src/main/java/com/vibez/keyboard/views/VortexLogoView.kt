package com.vibez.keyboard.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

/**
 * VortexLogoView — Animated Vortext logo.
 *
 * Renders a rotating vortex/spiral with letters (V, O, R, T, E, X, T)
 * orbiting at different radii and spiraling inward, creating the illusion
 * of a typographic vortex being drawn into a central point.
 */
class VortexLogoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var rotationAngle = 0f
    private var animator: ValueAnimator? = null

    private val letters = listOf("V", "O", "R", "T", "E", "X", "T")
    private val letterRadii = listOf(0.85f, 0.72f, 0.60f, 0.48f, 0.37f, 0.25f, 0.14f)
    private val letterAngles = listOf(0f, 45f, 90f, 140f, 195f, 250f, 310f)
    private val letterSizes = listOf(26f, 23f, 21f, 18f, 15f, 12f, 9f)
    private val letterAlphas = listOf(255, 230, 210, 180, 150, 120, 90)

    private val accentColor = Color.parseColor("#7B61FF")
    private val cyanColor = Color.parseColor("#00D4FF")
    private val whiteColor = Color.WHITE

    private val spiralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = whiteColor
        style = Paint.Style.FILL
    }

    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = whiteColor
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }

    init {
        startAnimation()
    }

    private fun startAnimation() {
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 8000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                rotationAngle = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(width, height) / 2f * 0.9f

        canvas.save()
        canvas.rotate(rotationAngle, cx, cy)

        // Draw spiral arms
        drawSpiral(canvas, cx, cy, maxR)

        canvas.restore()

        // Draw letters (don't rotate with canvas — they stay readable)
        drawLetters(canvas, cx, cy, maxR)

        // Center dot
        canvas.drawCircle(cx, cy, 5f, dotPaint)

        // Center glow
        val centerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            alpha = 120
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx, cy, 8f, centerGlow)
    }

    private fun drawSpiral(canvas: Canvas, cx: Float, cy: Float, maxR: Float) {
        val path = Path()
        val steps = 360
        var first = true

        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val angle = t * 4 * PI.toFloat()  // 2 full rotations
            val r = maxR * (1f - t * 0.85f)
            val x = cx + r * cos(angle)
            val y = cy + r * sin(angle)
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
        }

        // Draw glow spiral
        glowPaint.color = accentColor
        glowPaint.alpha = 80
        canvas.drawPath(path, glowPaint)

        // Draw main spiral with gradient-like effect
        spiralPaint.color = accentColor
        spiralPaint.alpha = 180
        canvas.drawPath(path, spiralPaint)

        // Second spiral offset by 180 degrees (cyan)
        val path2 = Path()
        first = true
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val angle = t * 4 * PI.toFloat() + PI.toFloat()
            val r = maxR * (1f - t * 0.85f)
            val x = cx + r * cos(angle)
            val y = cy + r * sin(angle)
            if (first) {
                path2.moveTo(x, y)
                first = false
            } else {
                path2.lineTo(x, y)
            }
        }
        spiralPaint.color = cyanColor
        spiralPaint.alpha = 120
        canvas.drawPath(path2, spiralPaint)
    }

    private fun drawLetters(canvas: Canvas, cx: Float, cy: Float, maxR: Float) {
        letters.forEachIndexed { idx, letter ->
            val radiusFraction = letterRadii[idx]
            val baseAngleDeg = letterAngles[idx]
            // Add rotation offset (letters orbit the vortex)
            val angleDeg = baseAngleDeg + rotationAngle * (1f - radiusFraction * 0.7f)
            val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
            val r = maxR * radiusFraction

            val x = cx + r * cos(angleRad)
            val y = cy + r * sin(angleRad)

            letterPaint.textSize = letterSizes[idx].coerceAtLeast(8f)
            letterPaint.alpha = letterAlphas[idx]

            // Color gradient: outer letters purple, inner letters cyan→white
            letterPaint.color = when {
                idx < 2 -> accentColor
                idx < 4 -> interpolateColor(accentColor, cyanColor, (idx - 2) / 2f)
                else -> interpolateColor(cyanColor, whiteColor, (idx - 4) / 3f)
            }
            letterPaint.alpha = letterAlphas[idx]

            canvas.drawText(letter, x, y + letterPaint.textSize * 0.35f, letterPaint)
        }
    }

    private fun interpolateColor(start: Int, end: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val sr = Color.red(start); val sg = Color.green(start); val sb = Color.blue(start)
        val er = Color.red(end); val eg = Color.green(end); val eb = Color.blue(end)
        return Color.rgb(
            (sr + (er - sr) * f).toInt(),
            (sg + (eg - sg) * f).toInt(),
            (sb + (eb - sb) * f).toInt()
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(
            MeasureSpec.getSize(heightMeasureSpec)
        ).coerceAtMost(200.dpToPx())
        setMeasuredDimension(size, size)
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator?.isRunning != true) startAnimation()
    }
}
