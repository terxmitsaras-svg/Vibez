package com.voxtype.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

/**
 * SettingsActivity – exposes user-facing preferences for VoxType.
 *
 * All preferences are stored in "voxtype_settings" SharedPreferences so both
 * the activity and the IME service can read them.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.apply {
            title = getString(R.string.settings_title)
            setDisplayHomeAsUpEnabled(true)
        }

        prefs = getSharedPreferences("voxtype_settings", Context.MODE_PRIVATE)

        bindSwitch(R.id.switchVibration,           "vibration_enabled",        true)
        bindSwitch(R.id.switchAutocorrect,         "autocorrect_enabled",      true)
        bindSwitch(R.id.switchAutoPunctuation,     "auto_punctuation_enabled", true)
        bindSwitch(R.id.switchDoubleSpacePeriod,   "double_space_period",      true)
        bindSwitch(R.id.switchOfflineSpeech,       "prefer_offline_speech",    true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun bindSwitch(viewId: Int, prefKey: String, defaultValue: Boolean) {
        val sw = findViewById<SwitchCompat>(viewId)
        sw.isChecked = prefs.getBoolean(prefKey, defaultValue)
        sw.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(prefKey, isChecked).apply()
        }
    }
}
