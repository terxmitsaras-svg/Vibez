package com.vibez.keyboard.views

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.vibez.keyboard.language.KeyboardLanguage
import com.vibez.keyboard.theme.KeyboardTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * VibezKeyboardView - Custom QWERTY keyboard with full feature support.
 * Draws all keys using Canvas for maximum performance and customization.
 */
class VibezKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // -------------------------------------------------------------------------
    // Key model
    // -------------------------------------------------------------------------
    data class Key(
        val label: String,
        val code: Int,               // KeyEvent code or custom code
        val width: Float = 1f,       // relative width (1 = normal key)
        val type: KeyType = KeyType.CHARACTER,
        val altLabel: String? = null // symbol above main label
    )

    enum class KeyType {
        CHARACTER, SHIFT, BACKSPACE, ENTER, SPACE,
        NUMBERS_TOGGLE, EMOJI, LANGUAGE, VOICE, SYMBOLS_TOGGLE
    }

    // Custom key codes (negative = special)
    companion object {
        const val CODE_SHIFT = -1
        const val CODE_BACKSPACE = -2
        const val CODE_ENTER = -3
        const val CODE_SPACE = -4
        const val CODE_NUMBERS = -5
        const val CODE_EMOJI = -6
        const val CODE_LANGUAGE = -7
        const val CODE_VOICE = -8
        const val CODE_SYMBOLS = -9
        const val CODE_DELETE_WORD = -10

        private const val KEY_CORNER_RADIUS = 8f
        private const val KEY_MARGIN = 4f
        private const val LONG_PRESS_DURATION = 400L
        private const val REPEAT_INTERVAL = 50L
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    var currentLanguage: KeyboardLanguage = KeyboardLanguage.ENGLISH
        set(value) {
            field = value
            buildRows()
            invalidate()
        }

    var isShifted: Boolean = false
        private set

    var isCapsLock: Boolean = false
        private set

    var isNumberMode: Boolean = false
        set(value) {
            field = value
            buildRows()
            invalidate()
        }

    var isSymbolMode: Boolean = false
        set(value) {
            field = value
            isNumberMode = false
            buildRows()
            invalidate()
        }

    var theme: KeyboardTheme = KeyboardTheme.DARK
        set(value) {
            field = value
            applyTheme()
            invalidate()
        }

    var keyboardListener: KeyboardListener? = null

    // -------------------------------------------------------------------------
    // Layout data
    // -------------------------------------------------------------------------
    private var rows: List<List<Key>> = emptyList()
    private var keyRects: List<List<RectF>> = emptyList()
    private var rowHeight: Float = 0f
    private var keyboardHeight: Float = 0f

    // -------------------------------------------------------------------------
    // Paint objects
    // -------------------------------------------------------------------------
    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val specialKeyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressedKeyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val specialKeyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val altTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Bitmaps for special keys
    private var backspaceBitmap: Bitmap? = null
    private var shiftBitmap: Bitmap? = null
    private var shiftActiveBitmap: Bitmap? = null
    private var micBitmap: Bitmap? = null
    private var emojiBitmap: Bitmap? = null
    private var languageBitmap: Bitmap? = null
    private var enterBitmap: Bitmap? = null

    // Touch tracking
    private var pressedKeyIndex: Pair<Int, Int>? = null  // (row, col)
    private var longPressRunnable: Runnable? = null
    private var repeatRunnable: Runnable? = null
    private var lastShiftTapTime = 0L
    private var shiftTapCount = 0

    // -------------------------------------------------------------------------
    // Interface
    // -------------------------------------------------------------------------
    interface KeyboardListener {
        fun onKeyPressed(key: Key)
        fun onKeyLongPressed(key: Key)
        fun onShiftChanged(isShifted: Boolean, isCapsLock: Boolean)
        fun onVoiceToggle()
        fun onEmojiToggle()
        fun onLanguageSwitch()
    }

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------
    init {
        applyTheme()
        buildRows()
    }

    private fun applyTheme() {
        keyBgPaint.color = theme.keyBackground
        specialKeyBgPaint.color = theme.specialKeyBackground
        pressedKeyBgPaint.color = theme.pressedKeyBackground
        keyTextPaint.color = theme.keyTextColor
        keyTextPaint.textSize = theme.keyTextSize
        specialKeyTextPaint.color = theme.specialKeyTextColor
        specialKeyTextPaint.textSize = theme.keyTextSize * 0.75f
        altTextPaint.color = theme.altTextColor
        altTextPaint.textSize = theme.keyTextSize * 0.5f
    }

    // -------------------------------------------------------------------------
    // Row building
    // -------------------------------------------------------------------------
    private fun buildRows() {
        rows = when {
            isNumberMode -> buildNumberRows()
            isSymbolMode -> buildSymbolRows()
            else -> buildQwertyRows()
        }
    }

    private fun buildQwertyRows(): List<List<Key>> {
        val lang = currentLanguage
        return listOf(
            // Row 1: number row
            lang.row1.mapIndexed { i, ch ->
                Key(
                    label = if (isShifted || isCapsLock) ch.uppercase() else ch,
                    code = ch.lowercase()[0].code,
                    altLabel = lang.row1Numbers.getOrNull(i)
                )
            },
            // Row 2
            lang.row2.mapIndexed { i, ch ->
                Key(
                    label = if (isShifted || isCapsLock) ch.uppercase() else ch,
                    code = ch.lowercase()[0].code,
                    altLabel = lang.row2Numbers.getOrNull(i)
                )
            },
            // Row 3: with shift
            buildRow3(lang),
            // Row 4: bottom row
            buildBottomRow()
        )
    }

    private fun buildRow3(lang: KeyboardLanguage): List<Key> {
        val keys = mutableListOf<Key>()
        keys.add(Key("⇧", CODE_SHIFT, 1.5f, KeyType.SHIFT))
        lang.row3.forEach { ch ->
            keys.add(
                Key(
                    label = if (isShifted || isCapsLock) ch.uppercase() else ch,
                    code = ch.lowercase()[0].code
                )
            )
        }
        keys.add(Key("⌫", CODE_BACKSPACE, 1.5f, KeyType.BACKSPACE))
        return keys
    }

    private fun buildBottomRow(): List<Key> = listOf(
        Key("?123", CODE_NUMBERS, 1.5f, KeyType.NUMBERS_TOGGLE),
        Key("😊", CODE_EMOJI, 1f, KeyType.EMOJI),
        Key(" ", CODE_SPACE, 4f, KeyType.SPACE),
        Key("🎤", CODE_VOICE, 1f, KeyType.VOICE),
        Key("↵", CODE_ENTER, 1.5f, KeyType.ENTER)
    )

    private fun buildNumberRows(): List<List<Key>> = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0").map {
            Key(it, it[0].code)
        },
        listOf("-","/",":",";","(",")","$","&","@","\"").map {
            Key(it, it[0].code)
        },
        listOf(
            Key("#+=", CODE_SYMBOLS, 1.5f, KeyType.SYMBOLS_TOGGLE)
        ) + listOf(".",",","?","!","'").map {
            Key(it, it[0].code)
        } + listOf(
            Key("⌫", CODE_BACKSPACE, 1.5f, KeyType.BACKSPACE)
        ),
        listOf(
            Key("ABC", CODE_NUMBERS, 1.5f, KeyType.NUMBERS_TOGGLE),
            Key("😊", CODE_EMOJI, 1f, KeyType.EMOJI),
            Key(" ", CODE_SPACE, 4f, KeyType.SPACE),
            Key("🎤", CODE_VOICE, 1f, KeyType.VOICE),
            Key("↵", CODE_ENTER, 1.5f, KeyType.ENTER)
        )
    )

    private fun buildSymbolRows(): List<List<Key>> = listOf(
        listOf("[","]","{","}","#","%","^","*","+","=").map {
            Key(it, it[0].code)
        },
        listOf("_","\\","|","~","<",">","€","£","¥","•").map {
            Key(it, it[0].code)
        },
        listOf(
            Key("123", CODE_NUMBERS, 1.5f, KeyType.NUMBERS_TOGGLE)
        ) + listOf(".",",","?","!","'").map {
            Key(it, it[0].code)
        } + listOf(
            Key("⌫", CODE_BACKSPACE, 1.5f, KeyType.BACKSPACE)
        ),
        listOf(
            Key("ABC", CODE_NUMBERS, 1.5f, KeyType.NUMBERS_TOGGLE),
            Key("😊", CODE_EMOJI, 1f, KeyType.EMOJI),
            Key(" ", CODE_SPACE, 4f, KeyType.SPACE),
            Key("🎤", CODE_VOICE, 1f, KeyType.VOICE),
            Key("↵", CODE_ENTER, 1.5f, KeyType.ENTER)
        )
    )

    // -------------------------------------------------------------------------
    // Layout calculation
    // -------------------------------------------------------------------------
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateLayout(w.toFloat(), h.toFloat())
    }

    private fun calculateLayout(width: Float, height: Float) {
        if (rows.isEmpty()) return
        rowHeight = height / rows.size
        keyboardHeight = height
        keyRects = rows.mapIndexed { rowIndex, row ->
            val totalWeight = row.sumOf { it.width.toDouble() }.toFloat()
            val availableWidth = width - KEY_MARGIN * (row.size + 1)
            val unitWidth = availableWidth / totalWeight
            var x = KEY_MARGIN
            row.map { key ->
                val keyWidth = unitWidth * key.width
                val rect = RectF(
                    x,
                    rowIndex * rowHeight + KEY_MARGIN,
                    x + keyWidth,
                    (rowIndex + 1) * rowHeight - KEY_MARGIN
                )
                x += keyWidth + KEY_MARGIN
                rect
            }
        }
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(theme.keyboardBackground)

        rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { colIndex, key ->
                val rect = keyRects.getOrNull(rowIndex)?.getOrNull(colIndex) ?: return@forEachIndexed
                drawKey(canvas, key, rect, rowIndex to colIndex)
            }
        }
    }

    private fun drawKey(canvas: Canvas, key: Key, rect: RectF, index: Pair<Int, Int>) {
        val isPressed = pressedKeyIndex == index
        val paint = when {
            isPressed -> pressedKeyBgPaint
            key.type == KeyType.CHARACTER -> keyBgPaint
            else -> specialKeyBgPaint
        }

        // Key background
        canvas.drawRoundRect(rect, KEY_CORNER_RADIUS, KEY_CORNER_RADIUS, paint)

        // Key shadow/elevation effect
        if (!isPressed) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(60, 0, 0, 0)
                style = Paint.Style.FILL
            }
            val shadowRect = RectF(rect.left + 1, rect.top + 2, rect.right + 1, rect.bottom + 2)
            // Draw subtle bottom shadow
        }

        val cx = rect.centerX()
        val cy = rect.centerY()

        when (key.type) {
            KeyType.SHIFT -> drawShiftKey(canvas, key, rect, cx, cy)
            KeyType.BACKSPACE -> drawIconKey(canvas, "⌫", rect, cx, cy)
            KeyType.ENTER -> drawEnterKey(canvas, rect, cx, cy)
            KeyType.VOICE -> drawVoiceKey(canvas, rect, cx, cy)
            KeyType.EMOJI -> drawTextKey(canvas, "😊", rect, cx, cy, 22f)
            KeyType.LANGUAGE -> drawTextKey(canvas, "🌐", rect, cx, cy, 22f)
            KeyType.SPACE -> drawSpaceKey(canvas, rect, cx, cy)
            KeyType.NUMBERS_TOGGLE -> drawTextKey(canvas, key.label, rect, cx, cy, theme.keyTextSize * 0.72f, specialKeyTextPaint)
            KeyType.SYMBOLS_TOGGLE -> drawTextKey(canvas, key.label, rect, cx, cy, theme.keyTextSize * 0.72f, specialKeyTextPaint)
            KeyType.CHARACTER -> drawCharacterKey(canvas, key, rect, cx, cy)
        }

        // Shift indicator dot for caps lock
        if (key.type == KeyType.SHIFT && isCapsLock) {
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.accentColor
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, rect.bottom - 8f, 3f, dotPaint)
        }
    }

    private fun drawCharacterKey(canvas: Canvas, key: Key, rect: RectF, cx: Float, cy: Float) {
        // Alt label (small, top-right)
        key.altLabel?.let { alt ->
            canvas.drawText(alt, rect.right - 8f, rect.top + 14f, altTextPaint)
        }
        // Main label
        val textY = cy + keyTextPaint.textSize * 0.35f
        canvas.drawText(key.label, cx, textY, keyTextPaint)
    }

    private fun drawShiftKey(canvas: Canvas, key: Key, rect: RectF, cx: Float, cy: Float) {
        val shiftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isShifted || isCapsLock) theme.accentColor else theme.specialKeyTextColor
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            textSize = theme.keyTextSize * 1.1f
        }
        val textY = cy + shiftPaint.textSize * 0.35f
        val symbol = when {
            isCapsLock -> "⇪"
            isShifted -> "⇧"
            else -> "⇧"
        }
        canvas.drawText(symbol, cx, textY, shiftPaint)
    }

    private fun drawIconKey(canvas: Canvas, icon: String, rect: RectF, cx: Float, cy: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.specialKeyTextColor
            textAlign = Paint.Align.CENTER
            textSize = theme.keyTextSize * 1.1f
        }
        canvas.drawText(icon, cx, cy + p.textSize * 0.35f, p)
    }

    private fun drawEnterKey(canvas: Canvas, rect: RectF, cx: Float, cy: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.specialKeyTextColor
            textAlign = Paint.Align.CENTER
            textSize = theme.keyTextSize * 0.75f
        }
        canvas.drawText("return", cx, cy + p.textSize * 0.35f, p)
    }

    private fun drawVoiceKey(canvas: Canvas, rect: RectF, cx: Float, cy: Float) {
        // Draw microphone icon using paths
        val micPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accentColor
            style = Paint.Style.FILL
        }
        val r = (rect.height() * 0.3f)
        // Mic body
        val micRect = RectF(cx - r * 0.5f, cy - r, cx + r * 0.5f, cy + r * 0.1f)
        canvas.drawRoundRect(micRect, r * 0.5f, r * 0.5f, micPaint)
        // Mic stand
        val standPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accentColor
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            strokeCap = Paint.Cap.ROUND
        }
        val path = Path().apply {
            moveTo(cx - r * 0.8f, cy + r * 0.1f)
            quadTo(cx - r * 0.8f, cy + r * 0.7f, cx, cy + r * 0.7f)
            quadTo(cx + r * 0.8f, cy + r * 0.7f, cx + r * 0.8f, cy + r * 0.1f)
        }
        canvas.drawPath(path, standPaint)
        canvas.drawLine(cx, cy + r * 0.7f, cx, cy + r * 1.0f, standPaint)
        canvas.drawLine(cx - r * 0.5f, cy + r * 1.0f, cx + r * 0.5f, cy + r * 1.0f, standPaint)
    }

    private fun drawSpaceKey(canvas: Canvas, rect: RectF, cx: Float, cy: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.specialKeyTextColor
            textAlign = Paint.Align.CENTER
            textSize = theme.keyTextSize * 0.65f
        }
        canvas.drawText(currentLanguage.spaceName, cx, cy + p.textSize * 0.35f, p)
    }

    private fun drawTextKey(
        canvas: Canvas, text: String, rect: RectF,
        cx: Float, cy: Float, size: Float,
        paint: Paint = keyTextPaint
    ) {
        val p = Paint(paint).apply { textSize = size }
        canvas.drawText(text, cx, cy + size * 0.35f, p)
    }

    // -------------------------------------------------------------------------
    // Touch handling
    // -------------------------------------------------------------------------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                handlePress(event.getX(pointerIndex), event.getY(pointerIndex))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                handleRelease(event.getX(pointerIndex), event.getY(pointerIndex))
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelPress()
            }
        }
        return true
    }

    private fun handlePress(x: Float, y: Float) {
        val (row, col) = findKey(x, y) ?: return
        pressedKeyIndex = row to col
        invalidate()

        val key = rows.getOrNull(row)?.getOrNull(col) ?: return

        // Haptic feedback
        performHapticFeedback()

        // Schedule long press
        longPressRunnable = Runnable {
            handleLongPress(key)
        }
        postDelayed(longPressRunnable, LONG_PRESS_DURATION)

        // For backspace — start repeating
        if (key.type == KeyType.BACKSPACE) {
            scheduleRepeat(key)
        }
    }

    private fun handleRelease(x: Float, y: Float) {
        cancelLongPress()
        cancelRepeat()

        val pressed = pressedKeyIndex
        pressedKeyIndex = null
        invalidate()

        if (pressed == null) return
        val (row, col) = pressed
        val key = rows.getOrNull(row)?.getOrNull(col) ?: return

        handleKeyAction(key)
    }

    private fun handleKeyAction(key: Key) {
        when (key.type) {
            KeyType.SHIFT -> handleShiftKey()
            KeyType.NUMBERS_TOGGLE -> toggleNumberMode()
            KeyType.SYMBOLS_TOGGLE -> toggleSymbolMode()
            KeyType.VOICE -> keyboardListener?.onVoiceToggle()
            KeyType.EMOJI -> keyboardListener?.onEmojiToggle()
            KeyType.LANGUAGE -> keyboardListener?.onLanguageSwitch()
            else -> {
                keyboardListener?.onKeyPressed(key)
                // Auto-release shift after character press (not caps lock)
                if (isShifted && !isCapsLock && key.type == KeyType.CHARACTER) {
                    isShifted = false
                    buildRows()
                    invalidate()
                    keyboardListener?.onShiftChanged(false, false)
                }
            }
        }
    }

    private fun handleShiftKey() {
        val now = System.currentTimeMillis()
        if (now - lastShiftTapTime < 500) {
            shiftTapCount++
        } else {
            shiftTapCount = 1
        }
        lastShiftTapTime = now

        when {
            shiftTapCount >= 2 -> {
                // Double tap = caps lock
                isCapsLock = true
                isShifted = false
                shiftTapCount = 0
            }
            isCapsLock -> {
                isCapsLock = false
                isShifted = false
            }
            isShifted -> {
                isShifted = false
            }
            else -> {
                isShifted = true
            }
        }
        buildRows()
        invalidate()
        keyboardListener?.onShiftChanged(isShifted, isCapsLock)
    }

    private fun handleLongPress(key: Key) {
        keyboardListener?.onKeyLongPressed(key)
    }

    private fun cancelPress() {
        cancelLongPress()
        cancelRepeat()
        pressedKeyIndex = null
        invalidate()
    }

    private fun cancelLongPress() {
        longPressRunnable?.let { removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun scheduleRepeat(key: Key) {
        repeatRunnable = object : Runnable {
            override fun run() {
                keyboardListener?.onKeyPressed(key)
                postDelayed(this, REPEAT_INTERVAL)
            }
        }
        postDelayed(repeatRunnable, LONG_PRESS_DURATION)
    }

    private fun cancelRepeat() {
        repeatRunnable?.let { removeCallbacks(it) }
        repeatRunnable = null
    }

    private fun toggleNumberMode() {
        isNumberMode = !isNumberMode
        if (isNumberMode) isSymbolMode = false
        buildRows()
        calculateLayout(width.toFloat(), height.toFloat())
        invalidate()
    }

    private fun toggleSymbolMode() {
        isSymbolMode = !isSymbolMode
        if (isSymbolMode) isNumberMode = false
        buildRows()
        calculateLayout(width.toFloat(), height.toFloat())
        invalidate()
    }

    // -------------------------------------------------------------------------
    // Hit testing
    // -------------------------------------------------------------------------
    private fun findKey(x: Float, y: Float): Pair<Int, Int>? {
        keyRects.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { colIndex, rect ->
                if (rect.contains(x, y)) {
                    return rowIndex to colIndex
                }
            }
        }
        return null
    }

    // -------------------------------------------------------------------------
    // Haptic feedback
    // -------------------------------------------------------------------------
    private fun performHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(25)
                }
            }
        } catch (e: Exception) {
            // Ignore vibration errors
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------
    fun resetShift() {
        isShifted = false
        isCapsLock = false
        buildRows()
        invalidate()
    }

    fun setTheme(newTheme: KeyboardTheme) {
        theme = newTheme
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * 0.55f).roundToInt()  // Keyboard aspect ratio
        setMeasuredDimension(width, height)
    }
}
