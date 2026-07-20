package com.anmol.esicalert

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val prefsName = "esic_prefs"
    private val intervalOptions = listOf(1, 2, 4, 6, 12, 24)
    private val notificationPermissionRequestCode = 101

    private lateinit var editKeywords: EditText
    private lateinit var spinnerInterval: Spinner
    private lateinit var switchAuto: SwitchMaterial
    private lateinit var textLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_main)
            
            // Initialize views with safety checks
            try {
                editKeywords = findViewById(R.id.editKeywords) ?: throw Exception("editKeywords not found")
                spinnerInterval = findViewById(R.id.spinnerInterval) ?: throw Exception("spinnerInterval not found")
                switchAuto = findViewById(R.id.switchAuto) ?: throw Exception("switchAuto not found")
                textLog = findViewById(R.id.textLog) ?: throw Exception("textLog not found")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to initialize views: ${e.message}", e)
                showError("UI Initialization Error", "Failed to load app interface: ${e.message}")
                return
            }

            try {
                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    intervalOptions.map { "Every $it hour(s)" }
                )
                spinnerInterval.adapter = adapter
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to setup spinner: ${e.message}", e)
            }

            try {
                loadSettings()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to load settings: ${e.message}", e)
            }

            try {
                requestNotificationPermissionIfNeeded()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to request notification permission: ${e.message}", e)
            }

            try {
                findViewById<Button>(R.id.btnSave).setOnClickListener { saveSettings() }
                findViewById<Button>(R.id.btnCheckNow).setOnClickListener { runCheckNow() }
                findViewById<Button>(R.id.btnTestNotification).setOnClickListener { runTestNotification() }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to setup button listeners: ${e.message}", e)
                showError("Button Setup Error", "Failed to setup buttons: ${e.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Critical error in onCreate: ${e.message}", e)
            showError("Critical Error", "App failed to initialize: ${e.message}")
        }
    }

    private fun showError(title: String, message: String) {
        try {
            Toast.makeText(this, "$title: $message", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to show error toast: ${e.message}")
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), notificationPermissionRequestCode)
            }
        }
    }

    private fun prefs() = getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private fun loadSettings() {
        try {
            val p = prefs()
            editKeywords.setText(
                p.getString(
                    "keywords",
                    "physiotherapist, paramedical, gorakhpur, uttar pradesh, lucknow, kanpur, hospital administration, medical record, udc, mts"
                )
            )
            val savedInterval = p.getInt("interval_hours", 2)
            val idx = intervalOptions.indexOf(savedInterval).let { if (it == -1) 1 else it }
            spinnerInterval.setSelection(idx)
            switchAuto.isChecked = p.getBoolean("auto_enabled", false)
            textLog.text = p.getString("last_log", "Nothing checked yet.")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error loading settings: ${e.message}", e)
            textLog.text = "Error loading settings: ${e.message}"
        }
    }

    private fun saveSettings() {
        try {
            val hours = intervalOptions[spinnerInterval.selectedItemPosition]
            prefs().edit()
                .putString("keywords", editKeywords.text.toString().trim())
                .putInt("interval_hours", hours)
                .putBoolean("auto_enabled", switchAuto.isChecked)
                .apply()

            if (switchAuto.isChecked) {
                scheduleBackgroundWork(hours)
                Toast.makeText(
                    this, 
                    "✓ Saved. Background checks scheduled every $hours hour(s).", 
                    Toast.LENGTH_LONG
                ).show()
                android.util.Log.i("EsicAlert", "Background work scheduled for every $hours hour(s)")
            } else {
                WorkManager.getInstance(this).cancelUniqueWork("esic_periodic_check")
                Toast.makeText(this, "✓ Saved. Background checks turned off.", Toast.LENGTH_LONG).show()
                android.util.Log.i("EsicAlert", "Background work cancelled")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error saving settings: ${e.message}", e)
            Toast.makeText(this, "Error saving settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun scheduleBackgroundWork(hours: Int) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build()
            
            val request = PeriodicWorkRequestBuilder<EsicWorker>(
                hours.toLong(), 
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "esic_periodic_check",
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error scheduling background work: ${e.message}", e)
            Toast.makeText(this, "Error scheduling background checks: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun runCheckNow() {
        try {
            textLog.text = "Checking..."
            thread {
                try {
                    val log = EsicChecker.checkOnce(applicationContext, sendNotifications = true)
                    runOnUiThread {
                        try {
                            textLog.text = log
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Error updating log text: ${e.message}", e)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        try {
                            textLog.text = "Error during check: ${e.message}"
                        } catch (e2: Exception) {
                            android.util.Log.e("MainActivity", "Error showing check error: ${e2.message}", e2)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error starting check: ${e.message}", e)
            Toast.makeText(this, "Error starting check: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun runTestNotification() {
        try {
            saveSettingsQuietly()
            EsicChecker.sendTestNotification(applicationContext)
            textLog.text = "Test notification sent - check your notification shade."
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error sending test notification: ${e.message}", e)
            Toast.makeText(this, "Error sending test notification: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveSettingsQuietly() {
        try {
            val hours = intervalOptions[spinnerInterval.selectedItemPosition]
            prefs().edit()
                .putString("keywords", editKeywords.text.toString().trim())
                .putInt("interval_hours", hours)
                .apply()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in saveSettingsQuietly: ${e.message}", e)
        }
    }
}
