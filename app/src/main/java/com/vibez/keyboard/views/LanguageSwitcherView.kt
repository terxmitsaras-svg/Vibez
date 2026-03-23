package com.vibez.keyboard.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vibez.keyboard.language.KeyboardLanguage
import com.vibez.keyboard.theme.KeyboardTheme

/**
 * LanguageSwitcherView — Bottom sheet style language picker for the keyboard.
 * Shows all 12 supported languages with flag emojis.
 */
class LanguageSwitcherView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onLanguageSelected: ((KeyboardLanguage) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null
    var theme: KeyboardTheme = KeyboardTheme.DARK

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#1A1A2E"))
        buildUI()
    }

    private fun buildUI() {
        // Header
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
        }
        header.addView(TextView(context).apply {
            text = "Select Language"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#B0B0C8"))
            textSize = 16f
            setPadding(16, 0, 0, 0)
            setOnClickListener { onDismiss?.invoke() }
        })
        addView(header)

        // Divider
        addView(View(context).apply {
            setBackgroundColor(Color.parseColor("#2D2D44"))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 1)
        })

        // Language list
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = LanguageAdapter(KeyboardLanguage.all) { lang ->
                onLanguageSelected?.invoke(lang)
            }
        }
        addView(recycler, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private class LanguageAdapter(
        private val languages: List<KeyboardLanguage>,
        private val onSelect: (KeyboardLanguage) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.LangVH>() {

        class LangVH(val view: LinearLayout) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LangVH {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20, 14, 20, 14)
                setBackgroundResource(android.R.drawable.list_selector_background)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            row.addView(TextView(parent.context).apply { tag = "flag"; textSize = 24f })
            row.addView(TextView(parent.context).apply {
                tag = "name"
                setTextColor(Color.WHITE)
                textSize = 15f
                setPadding(16, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(parent.context).apply {
                tag = "locale"
                setTextColor(Color.parseColor("#888888"))
                textSize = 12f
            })
            return LangVH(row)
        }

        override fun onBindViewHolder(holder: LangVH, position: Int) {
            val lang = languages[position]
            (holder.view.findViewWithTag("flag") as TextView).text = lang.flagEmoji
            (holder.view.findViewWithTag("name") as TextView).text = lang.displayName
            (holder.view.findViewWithTag("locale") as TextView).text = lang.locale
            holder.view.setOnClickListener { onSelect(lang) }
        }

        override fun getItemCount() = languages.size
    }
}
