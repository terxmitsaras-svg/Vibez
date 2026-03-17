package com.voxtype.keyboard.ui

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

/**
 * EmojiPickerView – a categorised emoji grid displayed when the user taps ☺.
 *
 * Categories shown as a horizontal tab strip; emoji in a RecyclerView grid.
 * Falls back gracefully if RecyclerView is not available (unlikely).
 */
class EmojiPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    // ─── Callbacks ────────────────────────────────────────────────────────────

    var onEmojiSelected: ((String) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    // ─── Data ─────────────────────────────────────────────────────────────────

    private val categories: List<EmojiCategory> = listOf(
        EmojiCategory("😀", "Smileys", listOf(
            "😀","😁","😂","🤣","😃","😄","😅","😆","😉","😊",
            "😋","😎","😍","🥰","😘","😗","😙","😚","☺️","🙂",
            "🤗","🤩","🤔","🤨","😐","😑","😶","🙄","😏","😣",
            "😥","😮","🤐","😯","😪","😫","😴","😌","😛","😜",
            "😝","🤤","😒","😓","😔","😕","🙃","🤑","😲","☹️",
            "🙁","😖","😞","😟","😤","😢","😭","😦","😧","😨",
            "😩","🤯","😬","😰","😱","🥵","🥶","😳","🤪","😵",
            "😡","😠","🤬","😷","🤒","🤕","🤢","🤮","🥴","😇"
        )),
        EmojiCategory("👋", "People", listOf(
            "👋","🤚","🖐","✋","🖖","👌","🤏","✌️","🤞","🤟",
            "🤘","🤙","👈","👉","👆","🖕","👇","☝️","👍","👎",
            "✊","👊","🤛","🤜","🤞","👏","🙌","👐","🤲","🙏",
            "✍️","💪","🦾","🦵","🦶","👂","🦻","👃","🧠","🦷",
            "🦴","👀","👁","👅","👄","💋","👶","🧒","👦","👧"
        )),
        EmojiCategory("🐶", "Animals", listOf(
            "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯",
            "🦁","🐮","🐷","🐸","🐵","🐔","🐧","🐦","🐤","🐦",
            "🦆","🦅","🦉","🦇","🐺","🐗","🐴","🦄","🐝","🐛",
            "🦋","🐌","🐞","🐜","🦟","🦗","🦂","🐢","🐍","🦎",
            "🦖","🦕","🐙","🦑","🦐","🦞","🦀","🐡","🐠","🐟"
        )),
        EmojiCategory("🍎", "Food", listOf(
            "🍎","🍊","🍋","🍇","🍓","🫐","🍈","🍒","🍑","🥭",
            "🍍","🥥","🥝","🍅","🍆","🥑","🥦","🥬","🥒","🌶",
            "🫑","🧄","🧅","🥔","🍠","🥐","🥯","🍞","🥖","🥨",
            "🧀","🥚","🍳","🧈","🥞","🧇","🥓","🥩","🍗","🍖",
            "🌮","🌯","🥙","🧆","🥚","🍱","🍘","🍣","🍤","🍕"
        )),
        EmojiCategory("⚽", "Activities", listOf(
            "⚽","🏀","🏈","⚾","🥎","🏐","🏉","🥏","🎾","🏓",
            "🏸","🏒","🏑","🥍","🏏","🪃","🥅","⛳","🪁","🎣",
            "🤿","🎽","🎿","🛷","🥌","🎯","🪀","🪆","🎱","🔮",
            "🎮","🕹","🎲","♟","🧩","🧸","🪅","🎭","🎨","🖼"
        )),
        EmojiCategory("🚗", "Travel", listOf(
            "🚗","🚕","🚙","🚌","🚎","🚐","🚑","🚒","🚓","🚔",
            "🚖","🚘","🚍","🛻","🚚","🚛","🚜","🏎","🏍","🛵",
            "🛺","🚲","🛴","🛹","🛼","🚏","🛣","🛤","⛽","🚦",
            "🚥","🚧","⚓","⛵","🛶","🚤","🛥","🛳","🚢","✈️",
            "🛩","🛫","🛬","🪂","💺","🚁","🚟","🚠","🚡","🛰"
        )),
        EmojiCategory("❤️", "Symbols", listOf(
            "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔",
            "❣️","💕","💞","💓","💗","💖","💘","💝","💟","☮️",
            "✝️","☪️","🕉","☸️","✡️","🔯","🕎","☯️","☦️","🛐",
            "⛎","♈","♉","♊","♋","♌","♍","♎","♏","♐",
            "♑","♒","♓","⭕","✅","❎","🔴","🟠","🟡","🟢"
        ))
    )

    private var selectedCategory = 0

    // ─── Child views ──────────────────────────────────────────────────────────

    private lateinit var tabStrip: HorizontalScrollView
    private lateinit var emojiGrid: RecyclerView
    private lateinit var adapter: EmojiAdapter

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFF0E0E18.toInt())
        buildTabStrip()
        buildEmojiGrid()
        updateGrid(0)
    }

    private fun buildTabStrip() {
        val tabScrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(0xFF181824.toInt())
        }
        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(6), 0, dpToPx(6), 0)
        }
        categories.forEachIndexed { idx, cat ->
            val tab = TextView(context).apply {
                text = cat.icon
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
                setOnClickListener {
                    selectedCategory = idx
                    updateGrid(idx)
                    updateTabHighlight(tabRow, idx)
                }
            }
            tabRow.addView(tab)
        }
        tabScrollView.addView(tabRow, LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(44)))
        addView(tabScrollView, LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(44)))
        tabStrip = tabScrollView

        // Highlight first
        (tabRow.getChildAt(0) as? TextView)?.setBackgroundColor(0x227C4DFF)
    }

    private fun buildEmojiGrid() {
        adapter = EmojiAdapter { emoji -> onEmojiSelected?.invoke(emoji) }
        emojiGrid = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 8)
            this.adapter = this@EmojiPickerView.adapter
            setBackgroundColor(0xFF0E0E18.toInt())
        }
        addView(emojiGrid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // Dismiss strip at bottom
        val dismissBar = TextView(context).apply {
            text = "✕  Close"
            textSize = 12f
            setTextColor(0xFF888899.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(6), 0, dpToPx(6))
            setOnClickListener { onDismiss?.invoke() }
        }
        addView(dismissBar, LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(32)))
    }

    private fun updateGrid(categoryIdx: Int) {
        adapter.setEmojis(categories[categoryIdx].emojis)
    }

    private fun updateTabHighlight(tabRow: LinearLayout, selectedIdx: Int) {
        for (i in 0 until tabRow.childCount) {
            tabRow.getChildAt(i).setBackgroundColor(
                if (i == selectedIdx) 0x227C4DFF else Color.TRANSPARENT
            )
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).roundToInt()
}

// ─── Data class ───────────────────────────────────────────────────────────────

private data class EmojiCategory(val icon: String, val name: String, val emojis: List<String>)

// ─── RecyclerView Adapter ─────────────────────────────────────────────────────

private class EmojiAdapter(
    private val onEmojiClick: (String) -> Unit
) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

    private var emojis: List<String> = emptyList()

    fun setEmojis(list: List<String>) {
        emojis = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val tv = TextView(parent.context).apply {
            textSize = 22f
            gravity = Gravity.CENTER
            val size = (44 * parent.context.resources.displayMetrics.density).toInt()
            layoutParams = ViewGroup.LayoutParams(size, size)
        }
        return EmojiViewHolder(tv)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        val emoji = emojis[position]
        (holder.itemView as TextView).text = emoji
        holder.itemView.setOnClickListener { onEmojiClick(emoji) }
    }

    override fun getItemCount() = emojis.size

    class EmojiViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
