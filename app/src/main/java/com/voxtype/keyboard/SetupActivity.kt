package com.voxtype.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * SetupActivity – the launcher screen that walks the user through the three
 * steps needed to activate VoxType:
 *  1. Enable the IME in system Input Method settings.
 *  2. Select VoxType as the active keyboard.
 *  3. Grant RECORD_AUDIO permission.
 */
class SetupActivity : AppCompatActivity() {

    companion object {
        private const val MIC_PERMISSION_REQUEST = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        // Step 1 – open IME settings
        findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        // Step 3 – request mic permission
        findViewById<Button>(R.id.btnGrantMic).setOnClickListener {
            requestMicrophonePermission()
        }

        // Settings shortcut
        findViewById<TextView>(R.id.btnOpenVoxTypeSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStepStates()
    }

    // ─── Permission ───────────────────────────────────────────────────────────

    private fun requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission already granted ✓", Toast.LENGTH_SHORT).show()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MIC_PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MIC_PERMISSION_REQUEST) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone enabled ✓", Toast.LENGTH_SHORT).show()
            } else {
                // Guide user to app settings if permanently denied
                Toast.makeText(
                    this,
                    "Please enable the microphone in App Settings",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        }
    }

    // ─── UI state ─────────────────────────────────────────────────────────────

    private fun updateStepStates() {
        val imeEnabled = isVoxTypeEnabled()
        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        findViewById<TextView>(R.id.step1Title).text = if (imeEnabled) {
            "✅ ${getString(R.string.setup_step1_title)}"
        } else {
            getString(R.string.setup_step1_title)
        }

        if (imeEnabled && micGranted) {
            Toast.makeText(this, getString(R.string.setup_done), Toast.LENGTH_SHORT).show()
        }
    }

    private fun isVoxTypeEnabled(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any {
            it.packageName == packageName
        }
    }
}
