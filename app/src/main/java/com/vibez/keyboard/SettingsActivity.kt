package com.vibez.keyboard

import android.os.Bundle
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.vibez.keyboard.R
import com.vibez.keyboard.language.KeyboardLanguage
import com.vibez.keyboard.theme.KeyboardTheme
import com.vibez.keyboard.utils.PreferencesManager

/**
 * SettingsActivity — User settings for Vibez keyboard.
 *
 * Settings:
 * - Language selection (12 languages)
 * - Theme (Dark, Light, Midnight, Ocean)
 * - Auto-theme (follow system)
 * - Haptic feedback
 * - Sound on keypress
 * - Voice language
 * - Auto punctuation
 * - Offline voice preference
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)
        prefs = PreferencesManager(this)

        setupLanguageSpinner()
        setupThemeSpinner()
        setupVoiceLanguageSpinner()
        setupSwitches()
    }

    private fun setupLanguageSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerLanguage)
        val languages = KeyboardLanguage.all
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            languages.map { "${it.flagEmoji} ${it.displayName}" }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.adapter = adapter
        spinner.setSelection(languages.indexOf(prefs.getLanguage()))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                prefs.saveLanguage(languages[pos])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupVoiceLanguageSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerVoiceLanguage)
        val languages = KeyboardLanguage.all
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            languages.map { "${it.flagEmoji} ${it.displayName}" }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.adapter = adapter
        spinner.setSelection(languages.indexOf(prefs.getVoiceLanguage()))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                prefs.saveVoiceLanguage(languages[pos])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupThemeSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerTheme)
        val themes = listOf("Dark", "Light", "Midnight", "Ocean")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.adapter = adapter
        val currentTheme = prefs.getTheme()
        spinner.setSelection(KeyboardTheme.values().indexOf(currentTheme))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                prefs.saveTheme(KeyboardTheme.values()[pos])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSwitches() {
        // Auto theme
        val switchAutoTheme = findViewById<SwitchCompat>(R.id.switchAutoTheme)
        switchAutoTheme.isChecked = prefs.isAutoTheme()
        switchAutoTheme.setOnCheckedChangeListener { _, checked -> prefs.setAutoTheme(checked) }

        // Haptic
        val switchHaptic = findViewById<SwitchCompat>(R.id.switchHaptic)
        switchHaptic.isChecked = prefs.isHapticEnabled()
        switchHaptic.setOnCheckedChangeListener { _, checked -> prefs.setHapticEnabled(checked) }

        // Sound
        val switchSound = findViewById<SwitchCompat>(R.id.switchSound)
        switchSound.isChecked = prefs.isSoundEnabled()
        switchSound.setOnCheckedChangeListener { _, checked -> prefs.setSoundEnabled(checked) }

        // Auto punctuation
        val switchAutoPunct = findViewById<SwitchCompat>(R.id.switchAutoPunctuation)
        switchAutoPunct.isChecked = prefs.isPunctuationAutoInsert()
        switchAutoPunct.setOnCheckedChangeListener { _, checked -> prefs.setPunctuationAutoInsert(checked) }

        // Offline voice
        val switchOfflineVoice = findViewById<SwitchCompat>(R.id.switchOfflineVoice)
        switchOfflineVoice.isChecked = prefs.isOfflineVoiceEnabled()
        switchOfflineVoice.setOnCheckedChangeListener { _, checked -> prefs.setOfflineVoiceEnabled(checked) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
