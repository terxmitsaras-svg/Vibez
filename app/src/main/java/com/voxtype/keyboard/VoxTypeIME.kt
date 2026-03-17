package com.voxtype.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.voxtype.keyboard.autocorrect.AutocorrectEngine
import com.voxtype.keyboard.keyboard.KeyData
import com.voxtype.keyboard.keyboard.KeyboardState
import com.voxtype.keyboard.lang.LanguageManager
import com.voxtype.keyboard.ui.VoxTypeKeyboardView
import com.voxtype.keyboard.voice.VoiceInputManager
import com.voxtype.keyboard.utils.VibrationManager

/**
 * VoxTypeIME – the main Android InputMethodService.
 *
 * Responsibilities:
 *  • Inflate and manage the keyboard view lifecycle.
 *  • Route key events to the active InputConnection.
 *  • Bridge keyboard UI ↔ voice input manager.
 *  • Handle autocorrect and predictive suggestions.
 */
class VoxTypeIME : InputMethodService() {

    // ─── Components ───────────────────────────────────────────────────────────

    private lateinit var keyboardView: VoxTypeKeyboardView
    private lateinit var voiceInputManager: VoiceInputManager
    private lateinit var languageManager: LanguageManager
    private lateinit var autocorrectEngine: AutocorrectEngine
    private lateinit var vibrationManager: VibrationManager
    private val keyboardState = KeyboardState()

    private val mainHandler = Handler(Looper.getMainLooper())

    // Tracks the word currently being typed for autocorrect
    private val currentWordBuffer = StringBuilder()

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        languageManager = LanguageManager(this)
        autocorrectEngine = AutocorrectEngine(this)
        vibrationManager = VibrationManager(this)
        voiceInputManager = VoiceInputManager(this, ::onVoicePartialResult, ::onVoiceFinalResult, ::onVoiceError)
    }

    override fun onCreateInputView(): View {
        keyboardView = VoxTypeKeyboardView(this).apply {
            setKeyboardState(keyboardState)
            setLanguage(languageManager.getCurrentLanguageCode())
            setKeyboardActionListener(keyboardActionListener)
        }
        return keyboardView
    }

    override fun onStartInputView(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        currentWordBuffer.clear()
        keyboardView.onEditorInfoChanged(attribute)
        updatePredictions("")
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        voiceInputManager.stopListening()
        keyboardView.hideVoiceMode()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceInputManager.destroy()
    }

    // ─── Keyboard Action Listener ─────────────────────────────────────────────

    private val keyboardActionListener = object : VoxTypeKeyboardView.KeyboardActionListener {

        override fun onKey(keyData: KeyData) {
            vibrationManager.keyFeedback()
            handleKeyData(keyData)
        }

        override fun onSwipeLeft() {
            // Swipe left on space = move cursor left
            currentInputConnection?.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
            )
        }

        override fun onSwipeRight() {
            currentInputConnection?.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
            )
        }

        override fun onPredictionSelected(word: String) {
            val ic = currentInputConnection ?: return
            // Replace current partial word with selected suggestion
            val partial = currentWordBuffer.toString()
            if (partial.isNotEmpty()) {
                ic.deleteSurroundingText(partial.length, 0)
            }
            ic.commitText("$word ", 1)
            currentWordBuffer.clear()
            updatePredictions("")
        }

        override fun onVoiceMicPressed() {
            startVoiceInput()
        }

        override fun onVoicePausePressed() {
            voiceInputManager.pauseListening()
        }

        override fun onVoiceResumePressed() {
            voiceInputManager.resumeListening()
        }

        override fun onVoiceStopAndInsert() {
            voiceInputManager.stopAndCommit()
        }

        override fun onVoiceCancel() {
            voiceInputManager.cancel()
            keyboardView.hideVoiceMode()
        }
    }

    // ─── Key Handling ─────────────────────────────────────────────────────────

    private fun handleKeyData(keyData: KeyData) {
        val ic = currentInputConnection ?: return

        when (keyData.type) {
            com.voxtype.keyboard.keyboard.KeyType.CHAR -> {
                val char = if (keyboardState.isUppercase) {
                    keyData.label.uppercase()
                } else {
                    keyData.label.lowercase()
                }
                ic.commitText(char, 1)
                currentWordBuffer.append(char)
                keyboardState.onCharCommitted()
                keyboardView.updateShiftState(keyboardState.shiftState)
                updatePredictions(currentWordBuffer.toString())
            }

            com.voxtype.keyboard.keyboard.KeyType.BACKSPACE -> {
                handleBackspace(ic)
            }

            com.voxtype.keyboard.keyboard.KeyType.ENTER -> {
                handleEnter(ic)
            }

            com.voxtype.keyboard.keyboard.KeyType.SPACE -> {
                handleSpace(ic)
            }

            com.voxtype.keyboard.keyboard.KeyType.SHIFT -> {
                keyboardState.onShiftPressed()
                keyboardView.updateShiftState(keyboardState.shiftState)
            }

            com.voxtype.keyboard.keyboard.KeyType.SWITCH -> {
                when (keyData.code) {
                    KeyData.CODE_SWITCH_NUMBERS -> {
                        keyboardState.switchToNumbers()
                        keyboardView.refreshLayout()
                    }
                    KeyData.CODE_SWITCH_SYMBOLS -> {
                        keyboardState.switchToSymbols()
                        keyboardView.refreshLayout()
                    }
                    KeyData.CODE_SWITCH_ALPHA -> {
                        keyboardState.switchToAlpha()
                        keyboardView.refreshLayout()
                    }
                }
            }

            com.voxtype.keyboard.keyboard.KeyType.EMOJI -> {
                keyboardState.switchToEmoji()
                keyboardView.showEmojiPicker()
            }

            com.voxtype.keyboard.keyboard.KeyType.MIC -> {
                startVoiceInput()
            }

            com.voxtype.keyboard.keyboard.KeyType.LANGUAGE -> {
                languageManager.switchToNextLanguage()
                val newCode = languageManager.getCurrentLanguageCode()
                keyboardView.setLanguage(newCode)
                keyboardView.refreshLayout()
            }

            com.voxtype.keyboard.keyboard.KeyType.CLIPBOARD -> {
                handleClipboardPaste(ic)
            }

            else -> {
                // Functional keys with explicit char codes
                if (keyData.code > 0) {
                    ic.commitText(keyData.code.toChar().toString(), 1)
                    currentWordBuffer.append(keyData.code.toChar())
                    updatePredictions(currentWordBuffer.toString())
                }
            }
        }
    }

    private fun handleBackspace(ic: InputConnection) {
        if (currentWordBuffer.isNotEmpty()) {
            currentWordBuffer.deleteCharAt(currentWordBuffer.length - 1)
        }
        ic.deleteSurroundingText(1, 0)
        updatePredictions(currentWordBuffer.toString())
    }

    private fun handleEnter(ic: InputConnection) {
        currentWordBuffer.clear()
        updatePredictions("")
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    private fun handleSpace(ic: InputConnection) {
        // Auto-punctuation: double-space → period + space
        val text = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
        if (text.length == 2 && text[1] == ' ' && text[0].isLetter()) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
        } else {
            ic.commitText(" ", 1)
        }
        currentWordBuffer.clear()
        updatePredictions("")
    }

    private fun handleClipboardPaste(ic: InputConnection) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this)
            ic.commitText(text, 1)
        }
    }

    // ─── Predictions / Autocorrect ────────────────────────────────────────────

    private fun updatePredictions(word: String) {
        if (word.length < 2) {
            keyboardView.updatePredictions(emptyList())
            return
        }
        val suggestions = autocorrectEngine.getSuggestions(
            word, languageManager.getCurrentLanguageCode()
        )
        keyboardView.updatePredictions(suggestions)
    }

    // ─── Voice Input ──────────────────────────────────────────────────────────

    private fun startVoiceInput() {
        voiceInputManager.startListening(languageManager.getCurrentLanguageCode())
        keyboardView.showVoiceMode()
    }

    private fun onVoicePartialResult(partialText: String) {
        mainHandler.post {
            keyboardView.updateVoiceTranscript(partialText)
        }
    }

    private fun onVoiceFinalResult(finalText: String) {
        mainHandler.post {
            val ic = currentInputConnection
            if (ic != null && finalText.isNotBlank()) {
                // Apply voice commands
                val processed = processVoiceCommands(finalText)
                ic.commitText(processed, 1)
            }
            keyboardView.hideVoiceMode()
            currentWordBuffer.clear()
        }
    }

    private fun onVoiceError(errorCode: Int) {
        mainHandler.post {
            keyboardView.hideVoiceMode()
        }
    }

    /**
     * Converts spoken punctuation / command words to actual characters.
     * E.g. "hello comma world" → "hello, world"
     */
    private fun processVoiceCommands(text: String): String {
        var result = text
        val commands = mapOf(
            "\\bnew line\\b".toRegex(RegexOption.IGNORE_CASE) to "\n",
            "\\bcomma\\b".toRegex(RegexOption.IGNORE_CASE) to ",",
            "\\bperiod\\b".toRegex(RegexOption.IGNORE_CASE) to ".",
            "\\bfull stop\\b".toRegex(RegexOption.IGNORE_CASE) to ".",
            "\\bexclamation mark\\b".toRegex(RegexOption.IGNORE_CASE) to "!",
            "\\bquestion mark\\b".toRegex(RegexOption.IGNORE_CASE) to "?",
            "\\bcolon\\b".toRegex(RegexOption.IGNORE_CASE) to ":",
            "\\bsemicolon\\b".toRegex(RegexOption.IGNORE_CASE) to ";",
            "\\bdash\\b".toRegex(RegexOption.IGNORE_CASE) to "-",
            "\\bopen parenthesis\\b".toRegex(RegexOption.IGNORE_CASE) to "(",
            "\\bclose parenthesis\\b".toRegex(RegexOption.IGNORE_CASE) to ")"
        )
        commands.forEach { (pattern, replacement) ->
            result = result.replace(pattern, replacement)
        }
        // Capitalise first letter if needed
        return result.trimStart().replaceFirstChar { it.uppercaseChar() }
    }
}
