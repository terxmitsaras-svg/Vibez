package com.vibez.keyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.vibez.keyboard.databinding.ActivityMainBinding
import com.vibez.keyboard.utils.PreferencesManager

/**
 * MainActivity — Launcher / setup guide activity.
 *
 * Guides users through:
 * 1. Enabling Vibez as an input method
 * 2. Setting Vibez as the default keyboard
 * 3. Granting microphone permission
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferencesManager(this)

        setupUI()
    }

    override fun onResume() {
        super.onResume()
        updateSetupSteps()
    }

    private fun setupUI() {
        binding.btnEnableKeyboard.setOnClickListener {
            openInputMethodSettings()
        }

        binding.btnSetDefault.setOnClickListener {
            openInputMethodPicker()
        }

        binding.btnGrantMic.setOnClickListener {
            requestMicPermission()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnGetPremium.setOnClickListener {
            startActivity(Intent(this, subscription.SubscriptionActivity::class.java))
        }
    }

    private fun updateSetupSteps() {
        val isEnabled = isKeyboardEnabled()
        val isDefault = isKeyboardDefault()
        val hasMicPerm = hasMicPermission()

        binding.stepEnableKeyboard.isActivated = isEnabled
        binding.stepSetDefault.isActivated = isDefault
        binding.stepGrantMic.isActivated = hasMicPerm

        binding.ivStep1Check.visibility = if (isEnabled) android.view.View.VISIBLE else android.view.View.INVISIBLE
        binding.ivStep2Check.visibility = if (isDefault) android.view.View.VISIBLE else android.view.View.INVISIBLE
        binding.ivStep3Check.visibility = if (hasMicPerm) android.view.View.VISIBLE else android.view.View.INVISIBLE

        binding.tvSetupStatus.text = when {
            isEnabled && isDefault && hasMicPerm -> "✓ Vibez is ready to use!"
            isEnabled && isDefault -> "Almost there! Grant microphone access for voice."
            isEnabled -> "Enable as default keyboard to start typing."
            else -> "Follow the steps below to set up Vibez."
        }
    }

    private fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledMethods = imm.enabledInputMethodList
        return enabledMethods.any { it.packageName == packageName }
    }

    private fun isKeyboardDefault(): Boolean {
        val defaultIme = Settings.Secure.getString(
            contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        )
        return defaultIme?.startsWith(packageName) == true
    }

    private fun hasMicPermission(): Boolean {
        return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun openInputMethodSettings() {
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    private fun openInputMethodPicker() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    private fun requestMicPermission() {
        requestPermissions(
            arrayOf(android.Manifest.permission.RECORD_AUDIO),
            PERM_MIC_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_MIC_REQUEST) {
            updateSetupSteps()
        }
    }

    companion object {
        private const val PERM_MIC_REQUEST = 100
    }
}
