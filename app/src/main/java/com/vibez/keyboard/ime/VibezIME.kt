package com.vibez.keyboard.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.*
import com.vibez.keyboard.language.KeyboardLanguage
import com.vibez.keyboard.subscription.SubscriptionManager
import com.vibez.keyboard.theme.KeyboardTheme
import com.vibez.keyboard.utils.PreferencesManager
import com.vibez.keyboard.views.EmojiPickerView
import com.vibez.keyboard.views.LanguageSwitcherView
import com.vibez.keyboard.views.VibezKeyboardView
import com.vibez.keyboard.views.VoiceRecordingView
import com.vibez.keyboard.voice.SpeechRecognitionManager
import com.vibez.keyboard.voice.VoiceCommandProcessor

/**
 * VibezIME — The main Input Method Service.
 *
 * Manages:
 * - Keyboard view (QWERTY / numbers / symbols)
 * - Voice recording mode
 * - Emoji picker
 * - Text insertion into host app
 * - Language switching (English ↔ Greek)
 * - Clipboard integration
 * - Subscription enforcement
 */
class VibezIME : InputMethodService() {

    // -------------------------------------------------------------------------
    // Views
    // -------------------------------------------------------------------------
    private lateinit var rootView: FrameLayout
    private lateinit var keyboardView: VibezKeyboardView
    private lateinit var voiceView: VoiceRecordingView
    private lateinit var emojiView: EmojiPickerView
    private lateinit var clipboardBar: LinearLayout
    private lateinit var clipboardText: TextView
    private lateinit var voiceMinutesBar: LinearLayout
    private lateinit var voiceMinutesText: TextView
    private var languageSwitcherView: LanguageSwitcherView? = null

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private var currentMode = KeyboardMode.TYPING
    private var currentLanguage = KeyboardLanguage.ENGLISH
    private var currentTheme = KeyboardTheme.DARK

    // Voice transcription state
    private var partialTranscript = ""    // live partial result
    private var committedTranscript = ""  // confirmed text so far in this session
    private var pendingInsertionStart = 0 // cursor position when voice started

    // -------------------------------------------------------------------------
    // Managers
    // -------------------------------------------------------------------------
    private lateinit var speechManager: SpeechRecognitionManager
    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var prefsManager: PreferencesManager

    enum class KeyboardMode {
        TYPING, VOICE, EMOJI
    }

    // -------------------------------------------------------------------------
    // IME lifecycle
    // -------------------------------------------------------------------------
    override fun onCreate() {
        super.onCreate()
        prefsManager = PreferencesManager(this)
        subscriptionManager = SubscriptionManager(this)
        speechManager = SpeechRecognitionManager(this, createSpeechListener())

        loadPreferences()
    }

    override fun onCreateInputView(): View {
        rootView = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(currentTheme.keyboardBackground)
        }

        buildKeyboardView()
        buildVoiceView()
        buildEmojiView()
        buildClipboardBar()
        buildVoiceMinutesBar()

        showMode(KeyboardMode.TYPING)
        return rootView
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        // Adjust enter key label based on action
        when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> { /* update enter key */ }
            EditorInfo.IME_ACTION_SEND -> { }
            else -> { }
        }

        // Load saved language preference
        currentLanguage = prefsManager.getLanguage()
        keyboardView.currentLanguage = currentLanguage

        // Update theme
        applyCurrentTheme()

        // Show clipboard bar if clipboard has content
        updateClipboardBar()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (currentMode == KeyboardMode.VOICE) {
            stopVoiceRecording()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Reload theme based on system dark mode
        val isDark = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        if (prefsManager.isAutoTheme()) {
            currentTheme = KeyboardTheme.fromSystemDarkMode(isDark)
            applyCurrentTheme()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechManager.destroy()
        subscriptionManager.disconnect()
    }

    // -------------------------------------------------------------------------
    // View building
    // -------------------------------------------------------------------------
    private fun buildKeyboardView() {
        keyboardView = VibezKeyboardView(this).apply {
            theme = currentTheme
            currentLanguage = this@VibezIME.currentLanguage
            keyboardListener = object : VibezKeyboardView.KeyboardListener {
                override fun onKeyPressed(key: VibezKeyboardView.Key) {
                    handleKey(key)
                }
                override fun onKeyLongPressed(key: VibezKeyboardView.Key) {
                    handleLongPress(key)
                }
                override fun onShiftChanged(isShifted: Boolean, isCapsLock: Boolean) {
                    // Handled internally by keyboard view
                }
                override fun onVoiceToggle() {
                    toggleVoiceMode()
                }
                override fun onEmojiToggle() {
                    toggleEmojiMode()
                }
                override fun onLanguageSwitch() {
                    switchLanguage()
                }
            }
        }
        rootView.addView(keyboardView)
    }

    private fun buildVoiceView() {
        voiceView = VoiceRecordingView(this).apply {
            visibility = View.GONE
            theme = currentTheme
            onStopRecording = { stopVoiceRecording() }
            onCancelRecording = { cancelVoiceRecording() }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    handleTouch(event.x, event.y)
                }
                true
            }
        }
        rootView.addView(voiceView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun buildEmojiView() {
        emojiView = EmojiPickerView(this).apply {
            visibility = View.GONE
            onEmojiSelected = { emoji ->
                insertText(emoji)
            }
            onBackToKeyboard = {
                showMode(KeyboardMode.TYPING)
            }
        }
        rootView.addView(emojiView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun buildClipboardBar() {
        clipboardBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(16, 8, 16, 8)
            setBackgroundColor(currentTheme.specialKeyBackground)

            clipboardText = TextView(this@VibezIME).apply {
                setTextColor(currentTheme.keyTextColor)
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(clipboardText)

            val pasteBtn = TextView(this@VibezIME).apply {
                text = "Paste"
                setTextColor(currentTheme.accentColor)
                textSize = 12f
                setPadding(16, 0, 0, 0)
                setOnClickListener { pasteFromClipboard() }
            }
            addView(pasteBtn)

            val dismissBtn = TextView(this@VibezIME).apply {
                text = "✕"
                setTextColor(currentTheme.specialKeyTextColor)
                textSize = 12f
                setPadding(16, 0, 0, 0)
                setOnClickListener { clipboardBar.visibility = View.GONE }
            }
            addView(dismissBtn)
        }
        rootView.addView(clipboardBar, 0)
    }

    private fun buildVoiceMinutesBar() {
        voiceMinutesBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(16, 4, 16, 4)
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF2D1B00.toInt())

            voiceMinutesText = TextView(this@VibezIME).apply {
                setTextColor(0xFFFFB800.toInt())
                textSize = 11f
                text = ""
            }
            addView(voiceMinutesText)

            val upgradeBtn = TextView(this@VibezIME).apply {
                text = " → Upgrade"
                setTextColor(0xFFFFD700.toInt())
                textSize = 11f
                setOnClickListener { openSubscriptionScreen() }
            }
            addView(upgradeBtn)
        }
        rootView.addView(voiceMinutesBar)
    }

    // -------------------------------------------------------------------------
    // Key handling
    // -------------------------------------------------------------------------
    private fun handleKey(key: VibezKeyboardView.Key) {
        val ic = currentInputConnection ?: return

        when (key.type) {
            VibezKeyboardView.KeyType.CHARACTER -> {
                ic.commitText(key.label, 1)
            }
            VibezKeyboardView.KeyType.SPACE -> {
                ic.commitText(" ", 1)
            }
            VibezKeyboardView.KeyType.BACKSPACE -> {
                ic.deleteSurroundingText(1, 0)
            }
            VibezKeyboardView.KeyType.ENTER -> {
                val info = currentInputEditorInfo
                val actionId = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                    ?: EditorInfo.IME_ACTION_NONE
                if (actionId != EditorInfo.IME_ACTION_NONE &&
                    actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    ic.performEditorAction(actionId)
                } else {
                    ic.commitText("\n", 1)
                }
            }
            else -> {
                // Other types handled by keyboard view itself
            }
        }
    }

    private fun handleLongPress(key: VibezKeyboardView.Key) {
        val ic = currentInputConnection ?: return
        when (key.type) {
            VibezKeyboardView.KeyType.BACKSPACE -> {
                // Delete entire word
                deleteWordBackward(ic)
            }
            VibezKeyboardView.KeyType.SPACE -> {
                // Show clipboard
                updateClipboardBar()
                clipboardBar.visibility = View.VISIBLE
            }
            else -> {
                // Show alt characters popup (simplified)
                key.altLabel?.let { ic.commitText(it, 1) }
            }
        }
    }

    private fun deleteWordBackward(ic: InputConnection) {
        val text = ic.getTextBeforeCursor(50, 0)?.toString() ?: return
        if (text.isEmpty()) return
        var deleteCount = 0
        var i = text.length - 1
        // Skip trailing spaces
        while (i >= 0 && text[i] == ' ') { i--; deleteCount++ }
        // Delete word characters
        while (i >= 0 && text[i] != ' ') { i--; deleteCount++ }
        if (deleteCount > 0) {
            ic.deleteSurroundingText(deleteCount, 0)
        }
    }

    // -------------------------------------------------------------------------
    // Mode switching
    // -------------------------------------------------------------------------
    private fun showMode(mode: KeyboardMode) {
        currentMode = mode
        keyboardView.visibility = if (mode == KeyboardMode.TYPING) View.VISIBLE else View.GONE
        voiceView.visibility = if (mode == KeyboardMode.VOICE) View.VISIBLE else View.GONE
        emojiView.visibility = if (mode == KeyboardMode.EMOJI) View.VISIBLE else View.GONE
    }

    private fun toggleVoiceMode() {
        if (currentMode == KeyboardMode.VOICE) {
            stopVoiceRecording()
        } else {
            startVoiceRecording()
        }
    }

    private fun toggleEmojiMode() {
        if (currentMode == KeyboardMode.EMOJI) {
            showMode(KeyboardMode.TYPING)
        } else {
            showMode(KeyboardMode.EMOJI)
        }
    }

    // -------------------------------------------------------------------------
    // Voice recording
    // -------------------------------------------------------------------------
    private fun startVoiceRecording() {
        // Check subscription / free tier limits
        if (!subscriptionManager.canUseVoice()) {
            showVoiceLimitReached()
            return
        }

        // Record cursor position for insertion
        val ic = currentInputConnection
        val extracted = ic?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
        pendingInsertionStart = extracted?.selectionStart ?: 0

        committedTranscript = ""
        partialTranscript = ""

        showMode(KeyboardMode.VOICE)
        voiceView.transcriptionText = ""
        voiceView.amplitude = 0f

        subscriptionManager.startVoiceSession()
        speechManager.startListening(currentLanguage)
    }

    private fun stopVoiceRecording() {
        speechManager.stopListening()
        subscriptionManager.endVoiceSession()

        // Commit any remaining partial transcript
        if (partialTranscript.isNotEmpty()) {
            commitVoiceText(partialTranscript)
            partialTranscript = ""
        }

        showMode(KeyboardMode.TYPING)
        updateVoiceMinutesBar()
    }

    private fun cancelVoiceRecording() {
        speechManager.stopListening()
        subscriptionManager.endVoiceSession()
        // Don't insert any text
        showMode(KeyboardMode.TYPING)
    }

    private fun commitVoiceText(text: String) {
        val ic = currentInputConnection ?: return
        val processed = VoiceCommandProcessor.process(text, currentLanguage)

        when (processed.command) {
            VoiceCommandProcessor.VoiceCommand.NEW_LINE -> {
                if (processed.text.isNotBlank()) {
                    ic.commitText(processed.text + "\n", 1)
                } else {
                    ic.commitText("\n", 1)
                }
            }
            VoiceCommandProcessor.VoiceCommand.DELETE_WORD -> {
                if (processed.text.isNotBlank()) ic.commitText(processed.text, 1)
                deleteWordBackward(ic)
            }
            VoiceCommandProcessor.VoiceCommand.DELETE_SENTENCE -> {
                if (processed.text.isNotBlank()) ic.commitText(processed.text, 1)
                deleteSentenceBackward(ic)
            }
            VoiceCommandProcessor.VoiceCommand.CLEAR_ALL -> {
                ic.performContextMenuAction(android.R.id.selectAll)
                ic.commitText("", 1)
            }
            VoiceCommandProcessor.VoiceCommand.SUBMIT -> {
                if (processed.text.isNotBlank()) ic.commitText(processed.text, 1)
                ic.performEditorAction(EditorInfo.IME_ACTION_SEND)
            }
            null -> {
                val formatted = VoiceCommandProcessor.formatForInsertion(processed.text)
                if (formatted.isNotBlank()) {
                    ic.commitText(formatted + " ", 1)
                }
            }
        }
    }

    private fun deleteSentenceBackward(ic: InputConnection) {
        val text = ic.getTextBeforeCursor(200, 0)?.toString() ?: return
        if (text.isEmpty()) return
        val lastSentenceStart = text.lastIndexOf('.')
            .coerceAtLeast(text.lastIndexOf('!'))
            .coerceAtLeast(text.lastIndexOf('?'))
        val deleteCount = if (lastSentenceStart >= 0) {
            text.length - lastSentenceStart - 1
        } else {
            text.length
        }
        if (deleteCount > 0) {
            ic.deleteSurroundingText(deleteCount, 0)
        }
    }

    // -------------------------------------------------------------------------
    // Speech listener
    // -------------------------------------------------------------------------
    private fun createSpeechListener() = object : SpeechRecognitionManager.SpeechListener {
        override fun onPartialResult(text: String) {
            partialTranscript = text
            voiceView.transcriptionText = committedTranscript +
                (if (committedTranscript.isNotEmpty()) " " else "") + text
        }

        override fun onFinalResult(text: String) {
            partialTranscript = ""
            commitVoiceText(text)
            committedTranscript += (if (committedTranscript.isNotEmpty()) " " else "") + text
            voiceView.transcriptionText = committedTranscript
        }

        override fun onAmplitudeChanged(amplitude: Float) {
            voiceView.amplitude = amplitude
        }

        override fun onError(error: String) {
            voiceView.transcriptionText = "Error: $error"
        }

        override fun onRecordingStarted() {
            voiceView.transcriptionText = ""
        }

        override fun onRecordingStopped() {
            // handled in stopVoiceRecording()
        }
    }

    // -------------------------------------------------------------------------
    // Language switching
    // -------------------------------------------------------------------------
    private fun switchLanguage() {
        // Toggle between showing language picker and cycling
        if (languageSwitcherView?.visibility == View.VISIBLE) {
            hideLangSwitcher()
            return
        }
        showLangSwitcher()
    }

    private fun showLangSwitcher() {
        if (languageSwitcherView == null) {
            languageSwitcherView = LanguageSwitcherView(this).apply {
                theme = currentTheme
                onLanguageSelected = { lang ->
                    currentLanguage = lang
                    keyboardView.currentLanguage = lang
                    if (currentMode == KeyboardMode.VOICE) {
                        speechManager.setLanguage(lang)
                    }
                    prefsManager.saveLanguage(lang)
                    hideLangSwitcher()
                }
                onDismiss = { hideLangSwitcher() }
                visibility = View.GONE
            }
            rootView.addView(languageSwitcherView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ))
        }
        languageSwitcherView?.visibility = View.VISIBLE
    }

    private fun hideLangSwitcher() {
        languageSwitcherView?.visibility = View.GONE
    }

    // -------------------------------------------------------------------------
    // Clipboard
    // -------------------------------------------------------------------------
    private fun updateClipboardBar() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this).toString()
            if (text.isNotBlank()) {
                clipboardText.text = text
                clipboardBar.visibility = View.VISIBLE
                return
            }
        }
        clipboardBar.visibility = View.GONE
    }

    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this).toString()
            currentInputConnection?.commitText(text, 1)
        }
        clipboardBar.visibility = View.GONE
    }

    // -------------------------------------------------------------------------
    // Subscription / voice minutes UI
    // -------------------------------------------------------------------------
    private fun showVoiceLimitReached() {
        val remaining = subscriptionManager.getRemainingVoiceMinutes()
        voiceMinutesText.text = "Free voice minutes used up ($remaining min used today)"
        voiceMinutesBar.visibility = View.VISIBLE
    }

    private fun updateVoiceMinutesBar() {
        if (!subscriptionManager.isPremium()) {
            val remaining = subscriptionManager.getRemainingVoiceMinutes()
            voiceMinutesText.text = "Voice: ${remaining}min remaining today"
            voiceMinutesBar.visibility = View.VISIBLE
        }
    }

    private fun openSubscriptionScreen() {
        val intent = android.content.Intent(this,
            com.vibez.keyboard.subscription.SubscriptionActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    // -------------------------------------------------------------------------
    // Theme
    // -------------------------------------------------------------------------
    private fun applyCurrentTheme() {
        currentTheme = prefsManager.getTheme()
        keyboardView.theme = currentTheme
        voiceView.theme = currentTheme
        rootView.setBackgroundColor(currentTheme.keyboardBackground)
        // Rebuild clipboard bar colors
        clipboardBar.setBackgroundColor(currentTheme.specialKeyBackground)
        clipboardText.setTextColor(currentTheme.keyTextColor)
    }

    // -------------------------------------------------------------------------
    // Preferences
    // -------------------------------------------------------------------------
    private fun loadPreferences() {
        currentLanguage = prefsManager.getLanguage()
        currentTheme = prefsManager.getTheme()
    }

    // -------------------------------------------------------------------------
    // Text insertion helper
    // -------------------------------------------------------------------------
    private fun insertText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }
}
