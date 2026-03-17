package com.voxtype.keyboard.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * PredictionBarView – a horizontally scrollable strip of autocorrect / predictive
 * text suggestions displayed above the keyboard rows.
 *
 * Tapping a chip calls [onWordSelected] with the chosen word.
 */
class PredictionBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {

    var onWordSelected: ((String) -> Unit)? = null

    private val chipContainer = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dpToPx(6), 0, dpToPx(6), 0)
    }

    private val dividerPaint = android.graphics.Paint().apply {
        color = 0x33FFFFFF
        strokeWidth = 1f
    }

    init {
        isHorizontalScrollBarEnabled = false
        setBackgroundColor(0xFF181824.toInt())
        addView(chipContainer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    fun setSuggestions(words: List<String>) {
        chipContainer.removeAllViews()
        words.forEachIndexed { index, word ->
            if (index > 0) chipContainer.addView(buildDivider())
            chipContainer.addView(buildChip(word))
        }
    }

    // ─── Chip builder ─────────────────────────────────────────────────────────

    private fun buildChip(word: String): TextView {
        return TextView(context).apply {
            text = word
            textSize = 14f
            setTextColor(0xFFDDDDFF.toInt())
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dpToPx(14), dpToPx(4), dpToPx(14), dpToPx(4))
            setOnClickListener { onWordSelected?.invoke(word) }
        }
    }

    private fun buildDivider(): View {
        return View(context).apply {
            setBackgroundColor(0x33FFFFFF)
            val params = LinearLayout.LayoutParams(dpToPx(1), dpToPx(20)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            layoutParams = params
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).roundToInt()
}
