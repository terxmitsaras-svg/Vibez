package com.vibez.keyboard.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vibez.keyboard.theme.KeyboardTheme

/**
 * EmojiPickerView — Categorized emoji grid picker.
 * Categories: Smileys, People, Animals, Food, Travel, Objects, Symbols, Flags
 */
class EmojiPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onEmojiSelected: ((String) -> Unit)? = null
    var onBackToKeyboard: (() -> Unit)? = null
    var theme: KeyboardTheme = KeyboardTheme.DARK
        set(value) {
            field = value
            applyTheme()
        }

    private val categories = mapOf(
        "😀" to listOf(
            "😀","😁","😂","🤣","😃","😄","😅","😆","😉","😊",
            "😋","😎","😍","🥰","😘","😗","😙","😚","🙂","🤗",
            "🤩","🤔","🤨","😐","😑","😶","🙄","😏","😣","😥",
            "😮","🤐","😯","😪","😫","🥱","😴","😌","😛","😜",
            "😝","🤤","😒","😓","😔","😕","🙃","🤑","😲","☹",
            "🙁","😖","😞","😟","😤","😢","😭","😦","😧","😨",
            "😩","🤯","😬","😰","😱","🥵","🥶","😳","🤪","😵",
            "😡","😠","🤬","😷","🤒","🤕","🤢","🤮","🤧","😇"
        ),
        "👋" to listOf(
            "👋","🤚","🖐","✋","🖖","👌","🤌","🤏","✌","🤞",
            "🤟","🤘","🤙","👈","👉","👆","🖕","👇","☝","👍",
            "👎","✊","👊","🤛","🤜","👏","🙌","👐","🤲","🤝",
            "🙏","✍","💅","🤳","💪","🦾","🦿","🦵","🦶","👂",
            "🦻","👃","🧠","🫀","🫁","🦷","🦴","👀","👁","👅",
            "👶","🧒","👦","👧","🧑","👱","👨","🧔","👩","🧓",
            "👴","👵","🙍","🙎","🙅","🙆","💁","🙋","🧏","🙇"
        ),
        "🐶" to listOf(
            "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯",
            "🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐔","🐧",
            "🐦","🐤","🦆","🦅","🦉","🦇","🐺","🐗","🐴","🦄",
            "🐝","🐛","🦋","🐌","🐞","🐜","🪲","🦟","🦗","🪳",
            "🐢","🐍","🦎","🦖","🦕","🐙","🦑","🦐","🦞","🦀",
            "🐡","🐠","🐟","🐬","🐳","🐋","🦈","🐊","🐅","🐆"
        ),
        "🍎" to listOf(
            "🍎","🍊","🍋","🍇","🍓","🫐","🍈","🍒","🍑","🥭",
            "🍍","🥥","🥝","🍅","🍆","🥑","🥦","🥬","🥒","🌶",
            "🫑","🧄","🧅","🥔","🍠","🥐","🥖","🍞","🥨","🧀",
            "🥚","🍳","🧈","🥞","🧇","🥓","🥩","🍗","🍖","🌭",
            "🍔","🍟","🍕","🫓","🥪","🥙","🧆","🌮","🌯","🫔",
            "🥗","🥘","🫕","🥫","🍝","🍜","🍲","🍛","🍣","🍱"
        ),
        "✈" to listOf(
            "🚗","🚕","🚙","🚌","🚎","🏎","🚓","🚑","🚒","🚐",
            "🛻","🚚","🚛","🚜","🏍","🛵","🚲","🛴","🛹","🛼",
            "🚁","🛸","✈","🚀","🛶","⛵","🚤","🛥","🛳","⛴",
            "🚂","🚃","🚄","🚅","🚆","🚇","🚈","🚉","🚊","🚝",
            "🗺","🧭","⛰","🌋","🏔","🏕","🏖","🏜","🏝","🏞",
            "🌅","🌄","🌠","🎇","🎆","🏙","🌃","🌌","🌉","🌁"
        ),
        "⚽" to listOf(
            "⚽","🏀","🏈","⚾","🥎","🏐","🏉","🥏","🎾","🏸",
            "🏒","🥍","🏓","🥊","🥋","🤺","⛷","🏂","🏋","🤸",
            "🤿","🧗","🏇","🚴","🏆","🥇","🥈","🥉","🎮","🎲",
            "🎯","🎳","🎭","🎨","🎬","🎤","🎧","🎸","🎹","🎺",
            "🎻","🥁","🎷","🪗","🎵","🎶","🎙","📻","📺","📷"
        ),
        "❤" to listOf(
            "❤","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔",
            "❤‍🔥","❤‍🩹","💕","💞","💓","💗","💖","💘","💝","💟",
            "☮","✝","☪","🕉","☸","✡","🔯","🕎","☯","☦",
            "🛐","⛎","♈","♉","♊","♋","♌","♍","♎","♏",
            "✨","🌟","⭐","🌠","💫","⚡","🔥","🌈","☀","🌤",
            "⛅","🌥","☁","🌦","🌧","⛈","🌩","🌨","❄","🌬"
        ),
        "🎌" to listOf(
            "🎌","🏴","🏳","🏁","🚩","🏴‍☠","🇦🇺","🇧🇷","🇨🇦","🇨🇳",
            "🇩🇪","🇪🇸","🇫🇷","🇬🇧","🇬🇷","🇮🇳","🇮🇹","🇯🇵","🇰🇷","🇲🇽",
            "🇳🇱","🇵🇱","🇵🇹","🇷🇺","🇸🇦","🇹🇷","🇺🇸","🇿🇦","🇦🇷","🇦🇹",
            "🇧🇪","🇨🇭","🇨🇿","🇩🇰","🇪🇬","🇫🇮","🇭🇺","🇮🇩","🇮🇱","🇮🇷"
        )
    )

    private val categoryKeys get() = categories.keys.toList()
    private var selectedCategory = categoryKeys.firstOrNull() ?: "😀"
    private lateinit var emojiGrid: RecyclerView
    private lateinit var categoryBar: LinearLayout

    init {
        orientation = VERTICAL
        buildUI()
    }

    private fun buildUI() {
        // Category bar
        categoryBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        buildCategoryBar()
        addView(categoryBar)

        // Emoji grid
        emojiGrid = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 8)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(Color.parseColor("#1A1A2E"))
        }
        addView(emojiGrid)

        // Bottom bar with back button
        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#16213E"))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 44.dpToPx())
        }
        val backBtn = TextView(context).apply {
            text = "⌨ Keyboard"
            setTextColor(Color.parseColor("#7B61FF"))
            textSize = 13f
            setPadding(24, 0, 24, 0)
            gravity = Gravity.CENTER
            setOnClickListener { onBackToKeyboard?.invoke() }
        }
        bottomBar.addView(backBtn)
        addView(bottomBar)

        loadEmojis(selectedCategory)
    }

    private fun buildCategoryBar() {
        categoryBar.removeAllViews()
        for (cat in categoryKeys) {
            val btn = TextView(context).apply {
                text = cat
                textSize = 22f
                gravity = Gravity.CENTER
                setPadding(12, 8, 12, 8)
                isSelected = (cat == selectedCategory)
                alpha = if (cat == selectedCategory) 1f else 0.5f
                setOnClickListener {
                    selectedCategory = cat
                    loadEmojis(cat)
                    buildCategoryBar()
                }
            }
            categoryBar.addView(btn)
        }
    }

    private fun loadEmojis(category: String) {
        val emojis = categories[category] ?: return
        emojiGrid.adapter = EmojiAdapter(emojis) { emoji ->
            onEmojiSelected?.invoke(emoji)
        }
    }

    private fun applyTheme() {
        setBackgroundColor(theme.keyboardBackground)
        categoryBar.setBackgroundColor(theme.specialKeyBackground)
        emojiGrid.setBackgroundColor(theme.keyboardBackground)
    }

    private fun Int.dpToPx(): Int = (this * context.resources.displayMetrics.density).toInt()

    // -------------------------------------------------------------------------
    // Emoji adapter
    // -------------------------------------------------------------------------
    private class EmojiAdapter(
        private val emojis: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

        class EmojiViewHolder(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
            val tv = TextView(parent.context).apply {
                textSize = 24f
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    48.dpToPx(parent.context)
                )
            }
            return EmojiViewHolder(tv)
        }

        override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
            holder.tv.text = emojis[position]
            holder.tv.setOnClickListener { onClick(emojis[position]) }
        }

        override fun getItemCount() = emojis.size

        private fun Int.dpToPx(context: Context): Int =
            (this * context.resources.displayMetrics.density).toInt()
    }
}
