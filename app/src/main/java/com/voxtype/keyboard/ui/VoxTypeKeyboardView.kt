package com.voxtype.keyboard.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import com.voxtype.keyboard.R
import com.voxtype.keyboard.keyboard.KeyData
import com.voxtype.keyboard.keyboard.KeyType
import com.voxtype.keyboard.keyboard.KeyboardState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * VoxTypeKeyboardView – the main container that holds:
 *  • PredictionBarView    (top suggestions strip)
 *  • KeyCanvasView        (the actual QWERTY / numeric canvas)
 *  • EmojiPickerView      (slide-up emoji picker)
 *  • VoiceOverlayView     (morphed voice-recording circle)
 */
class VoxTypeKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    // ─── Public listener interface ────────────────────────────────────────────

    interface KeyboardActionListener {
        fun onKey(keyData: KeyData)
        fun onSwipeLeft()
        fun onSwipeRight()
        fun onPredictionSelected(word: String)
        fun onVoiceMicPressed()
        fun onVoicePausePressed()
        fun onVoiceResumePressed()
        fun onVoiceStopAndInsert()
        fun onVoiceCancel()
    }

    // ─── Child views ──────────────────────────────────────────────────────────

    private val predictionBar = PredictionBarView(context)
    private val keyCanvas = KeyCanvasView(context)
    private val emojiPicker = EmojiPickerView(context)
    private val voiceOverlay = VoiceOverlayView(context)

    // ─── State ────────────────────────────────────────────────────────────────

    private var keyboardState: KeyboardState = KeyboardState()
    private var languageCode: String = "en"
    private var listener: KeyboardActionListener? = null

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        setBackgroundColor(context.getColor(R.color.keyboard_bg))

        // Prediction bar (fixed height at top)
        addView(predictionBar, LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(44)))

        // Key canvas (fills remaining space below prediction bar)
        val keyParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpToPx(44)
        }
        addView(keyCanvas, keyParams)

        // Emoji picker (hidden by default, same size as key canvas)
        val emojiParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpToPx(44)
        }
        emojiPicker.visibility = View.GONE
        addView(emojiPicker, emojiParams)

        // Voice overlay (fills entire keyboard, hidden by default)
        voiceOverlay.visibility = View.GONE
        voiceOverlay.alpha = 0f
        addView(voiceOverlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Wire up child callbacks
        predictionBar.onWordSelected = { word -> listener?.onPredictionSelected(word) }

        emojiPicker.onEmojiSelected = { emoji ->
            listener?.onKey(KeyData(emoji.codePointAt(0), emoji, type = KeyType.CHAR))
            hideEmojiPicker()
        }
        emojiPicker.onDismiss = { hideEmojiPicker() }

        voiceOverlay.onPause = { listener?.onVoicePausePressed() }
        voiceOverlay.onResume = { listener?.onVoiceResumePressed() }
        voiceOverlay.onStopAndInsert = { listener?.onVoiceStopAndInsert() }
        voiceOverlay.onCancel = { listener?.onVoiceCancel() }

        keyCanvas.onKeyPressed = { keyData ->
            when (keyData.type) {
                KeyType.MIC -> listener?.onVoiceMicPressed()
                KeyType.EMOJI -> showEmojiPicker()
                else -> listener?.onKey(keyData)
            }
        }
        keyCanvas.onSwipeLeft = { listener?.onSwipeLeft() }
        keyCanvas.onSwipeRight = { listener?.onSwipeRight() }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    fun setKeyboardState(state: KeyboardState) {
        keyboardState = state
        keyCanvas.setKeyboardState(state)
    }

    fun setLanguage(code: String) {
        languageCode = code
        keyCanvas.setLanguage(code)
    }

    fun setKeyboardActionListener(l: KeyboardActionListener) {
        listener = l
    }

    fun onEditorInfoChanged(info: EditorInfo) {
        keyCanvas.updateEnterKeyLabel(info)
    }

    fun updateShiftState(state: KeyboardState.ShiftState) {
        keyCanvas.updateShiftIndicator(state)
    }

    fun refreshLayout() {
        keyCanvas.invalidateKeys()
    }

    fun updatePredictions(suggestions: List<String>) {
        predictionBar.setSuggestions(suggestions)
    }

    fun showEmojiPicker() {
        keyCanvas.visibility = View.GONE
        emojiPicker.visibility = View.VISIBLE
    }

    fun hideEmojiPicker() {
        emojiPicker.visibility = View.GONE
        keyCanvas.visibility = View.VISIBLE
        keyboardState.switchToAlpha()
        refreshLayout()
    }

    // ─── Voice mode transitions ───────────────────────────────────────────────

    fun showVoiceMode() {
        voiceOverlay.visibility = View.VISIBLE
        voiceOverlay.clearTranscript()
        val fadeIn = ObjectAnimator.ofFloat(voiceOverlay, "alpha", 0f, 1f).apply {
            duration = 280
            interpolator = AccelerateDecelerateInterpolator()
        }
        val fadeOut = ObjectAnimator.ofFloat(keyCanvas, "alpha", 1f, 0f).apply {
            duration = 200
        }
        fadeOut.start()
        fadeIn.start()
        voiceOverlay.startPulse()
    }

    fun hideVoiceMode() {
        val fadeOut = ObjectAnimator.ofFloat(voiceOverlay, "alpha", 1f, 0f).apply {
            duration = 220
            interpolator = AccelerateDecelerateInterpolator()
        }
        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                voiceOverlay.visibility = View.GONE
                voiceOverlay.stopPulse()
            }
        })
        val fadeIn = ObjectAnimator.ofFloat(keyCanvas, "alpha", 0f, 1f).apply {
            duration = 280
            startDelay = 120
        }
        keyCanvas.alpha = 0f
        fadeOut.start()
        fadeIn.start()
    }

    fun updateVoiceTranscript(text: String) {
        voiceOverlay.updateTranscript(text)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).roundToInt()
}

// ═══════════════════════════════════════════════════════════════════════════════
// KeyCanvasView – Canvas-based key rendering
// ═══════════════════════════════════════════════════════════════════════════════

internal class KeyCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ─── Callbacks ────────────────────────────────────────────────────────────

    var onKeyPressed: ((KeyData) -> Unit)? = null
    var onSwipeLeft: (() -> Unit)? = null
    var onSwipeRight: (() -> Unit)? = null

    // ─── State ────────────────────────────────────────────────────────────────

    private var keyboardState = KeyboardState()
    private var languageCode = "en"
    private var enterLabel = "↵"

    // Computed layout
    private var rows: List<List<KeyData>> = emptyList()
    private data class KeyRect(val key: KeyData, val rect: RectF)
    private var keyRects: List<KeyRect> = emptyList()

    // Touch tracking
    private var pressedKey: KeyData? = null
    private var touchDownX = 0f
    private var touchDownY = 0f
    private val swipeThreshold = 80f

    // Repeat-delete handler
    private val handler = Handler(Looper.getMainLooper())
    private var isRepeating = false
    private val repeatDelay = 400L
    private val repeatInterval = 60L

    // ─── Paints ───────────────────────────────────────────────────────────────

    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2D2D3A.toInt()
        style = Paint.Style.FILL
    }
    private val specialKeyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E1E2C.toInt()
        style = Paint.Style.FILL
    }
    private val pressedKeyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7C4DFF.toInt()
        style = Paint.Style.FILL
    }
    private val keyLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEEEEFF.toInt()
        textSize = 0f // set dynamically
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val secondaryLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99AAAACC.toInt()
        textSize = 0f
        textAlign = Paint.Align.LEFT
    }
    private val micPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7C4DFF.toInt()
        style = Paint.Style.FILL
    }

    // ─── Layout constants ─────────────────────────────────────────────────────

    private val keyMarginH = dpToPx(3).toFloat()
    private val keyMarginV = dpToPx(4).toFloat()
    private val keyCornerRadius = dpToPx(8).toFloat()
    private val paddingH = dpToPx(4).toFloat()
    private val paddingV = dpToPx(6).toFloat()

    // ─── Init / measure ───────────────────────────────────────────────────────

    init {
        rebuildRows()
    }

    private fun rebuildRows() {
        rows = keyboardState.getCurrentRows(languageCode)
        // Apply enter label override
        rows = rows.map { row ->
            row.map { k ->
                if (k.type == KeyType.ENTER) k.copy(label = enterLabel) else k
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rowCount = rows.size
        val rowHeight = dpToPx(52)
        val totalH = rowCount * rowHeight + (paddingV * 2).toInt()
        setMeasuredDimension(w, totalH)
        buildKeyRects(w.toFloat(), totalH.toFloat())
    }

    private fun buildKeyRects(width: Float, height: Float) {
        val rects = mutableListOf<KeyRect>()
        val rowCount = rows.size
        val rowH = (height - paddingV * 2) / rowCount

        rows.forEachIndexed { rowIdx, row ->
            val totalWeight = row.sumOf { it.widthFactor.toDouble() }.toFloat()
            val availableW = width - paddingH * 2
            var xOff = paddingH

            val top = paddingV + rowIdx * rowH + keyMarginV
            val bottom = paddingV + (rowIdx + 1) * rowH - keyMarginV

            row.forEach { key ->
                val keyW = (availableW * key.widthFactor / totalWeight)
                val left = xOff + keyMarginH
                val right = xOff + keyW - keyMarginH
                rects.add(KeyRect(key, RectF(left, top, right, bottom)))
                xOff += keyW
            }
        }
        keyRects = rects

        // Set dynamic paint sizes
        keyLabelPaint.textSize = dpToPx(15).toFloat()
        secondaryLabelPaint.textSize = dpToPx(9).toFloat()
    }

    // ─── Drawing ──────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        keyRects.forEach { (key, rect) ->
            val isPressed = key == pressedKey
            val isSpecial = key.type != KeyType.CHAR && key.type != KeyType.SPACE
            val paint = when {
                isPressed -> pressedKeyBgPaint
                isSpecial -> specialKeyBgPaint
                else -> keyBgPaint
            }
            canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, paint)

            // Draw label
            val cx = rect.centerX()
            val cy = rect.centerY() - (keyLabelPaint.descent() + keyLabelPaint.ascent()) / 2

            when (key.type) {
                KeyType.MIC -> {
                    // Glowing mic icon (circle + label)
                    val circleR = minOf(rect.width(), rect.height()) * 0.36f
                    canvas.drawCircle(cx, rect.centerY(), circleR, micPaint)
                    keyLabelPaint.color = 0xFFFFFFFF.toInt()
                    keyLabelPaint.textSize = dpToPx(14).toFloat()
                    canvas.drawText("🎤", cx, cy, keyLabelPaint)
                    keyLabelPaint.color = 0xFFEEEEFF.toInt()
                    keyLabelPaint.textSize = dpToPx(15).toFloat()
                }
                KeyType.SHIFT -> {
                    val shiftLabel = when (keyboardState.shiftState) {
                        KeyboardState.ShiftState.OFF -> "⇧"
                        KeyboardState.ShiftState.ON -> "⇧"
                        KeyboardState.ShiftState.CAPS_LOCK -> "⇪"
                    }
                    keyLabelPaint.color = when (keyboardState.shiftState) {
                        KeyboardState.ShiftState.OFF -> 0xFFAAAAAA.toInt()
                        else -> 0xFF7C4DFF.toInt()
                    }
                    canvas.drawText(shiftLabel, cx, cy, keyLabelPaint)
                    keyLabelPaint.color = 0xFFEEEEFF.toInt()
                }
                else -> {
                    val displayLabel = if (keyboardState.isUppercase && key.type == KeyType.CHAR) {
                        key.label.uppercase()
                    } else key.label

                    canvas.drawText(displayLabel, cx, cy, keyLabelPaint)

                    // Secondary label (top-left corner)
                    if (key.secondaryLabel.isNotEmpty()) {
                        canvas.drawText(
                            key.secondaryLabel,
                            rect.left + dpToPx(4),
                            rect.top + dpToPx(12).toFloat(),
                            secondaryLabelPaint
                        )
                    }
                }
            }
        }
    }

    // ─── Touch handling ───────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                val hit = findKey(event.x, event.y)
                pressedKey = hit
                invalidate()
                if (hit?.type == KeyType.BACKSPACE) {
                    scheduleRepeat(hit)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                cancelRepeat()
                val hit = findKey(event.x, event.y)
                val dx = event.x - touchDownX

                if (hit?.type == KeyType.SPACE && abs(dx) > swipeThreshold) {
                    if (dx < 0) onSwipeLeft?.invoke() else onSwipeRight?.invoke()
                } else if (hit != null && !isRepeating) {
                    onKeyPressed?.invoke(hit)
                }
                pressedKey = null
                isRepeating = false
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelRepeat()
                pressedKey = null
                isRepeating = false
                invalidate()
                return true
            }
        }
        return false
    }

    private fun findKey(x: Float, y: Float): KeyData? =
        keyRects.firstOrNull { it.rect.contains(x, y) }?.key

    // ─── Repeat delete ────────────────────────────────────────────────────────

    private fun scheduleRepeat(key: KeyData) {
        handler.postDelayed({
            if (pressedKey == key) {
                isRepeating = true
                onKeyPressed?.invoke(key)
                scheduleRepeatFast(key)
            }
        }, repeatDelay)
    }

    private fun scheduleRepeatFast(key: KeyData) {
        handler.postDelayed({
            if (pressedKey == key) {
                onKeyPressed?.invoke(key)
                scheduleRepeatFast(key)
            }
        }, repeatInterval)
    }

    private fun cancelRepeat() {
        handler.removeCallbacksAndMessages(null)
    }

    // ─── Public refresh methods ───────────────────────────────────────────────

    fun setKeyboardState(state: KeyboardState) {
        keyboardState = state
        rebuildRows()
        requestLayout()
    }

    fun setLanguage(code: String) {
        languageCode = code
        rebuildRows()
        requestLayout()
    }

    fun invalidateKeys() {
        rebuildRows()
        requestLayout()
        invalidate()
    }

    fun updateShiftIndicator(state: KeyboardState.ShiftState) {
        invalidate()
    }

    fun updateEnterKeyLabel(info: EditorInfo) {
        enterLabel = when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> "🔍"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> "↵"
        }
        rebuildRows()
        invalidate()
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).roundToInt()
}
