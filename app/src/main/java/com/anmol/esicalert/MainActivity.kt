package com.anmol.esicalert

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val prefsName = "esic_prefs"
    private val intervalOptions = listOf(1, 2, 4, 6, 12, 24)
    private val notificationPermissionRequestCode = 101

    private lateinit var editKeywords: EditText
    private lateinit var spinnerInterval: Spinner
    private lateinit var switchAuto: Switch
    private lateinit var textLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editKeywords = findViewById(R.id.editKeywords)
        spinnerInterval = findViewById(R.id.spinnerInterval)
        switchAuto = findViewById(R.id.switchAuto)
        textLog = findViewById(R.id.textLog)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            intervalOptions.map { "Every $it hour(s)" }
        )
        spinnerInterval.adapter = adapter

        loadSettings()
        requestNotificationPermissionIfNeeded()

        findViewById<Button>(R.id.btnSave).setOnClickListener { saveSettings() }
        findViewById<Button>(R.id.btnCheckNow).setOnClickListener { runCheckNow() }
        findViewById<Button>(R.id.btnTestNotification).setOnClickListener { runTestNotification() }
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
    }

    private fun saveSettings() {
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
                "✓ Saved. Background checks scheduled every $hours hour(s). First check will run on schedule.", 
                Toast.LENGTH_LONG
            ).show()
            android.util.Log.i("EsicAlert", "Background work scheduled for every $hours hour(s)")
        } else {
            WorkManager.getInstance(this).cancelUniqueWork("esic_periodic_check")
            Toast.makeText(this, "✓ Saved. Background checks turned off.", Toast.LENGTH_LONG).show()
            android.util.Log.i("EsicAlert", "Background work cancelled")
        }
    }

    private fun scheduleBackgroundWork(hours: Int) {
        val constraints = Constraints.Builder()
            // Require network to be available, but allow retry if temporarily unavailable
            .setRequiredNetworkType(NetworkType.CONNECTED)
            // Battery saver mode shouldn't block our checks
            .setRequiresBatteryNotLow(false)
            .build()
        
        val request = PeriodicWorkRequestBuilder<EsicWorker>(
            hours.toLong(), 
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            // Add exponential backoff for retries on failure
            // Initial delay: 30 seconds, multiplier: 2x, max delay: 30 minutes
            .setBackoffPolicy(
                BackoffPolicy.EXPONENTIAL,
                30,  // Initial delay in seconds
                TimeUnit.SECONDS
            )
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "esic_periodic_check",
            ExistingPeriodicWorkPolicy.REPLACE,  // REPLACE ensures app updates override old schedules
            request
        )
    }

    private fun runCheckNow() {
        textLog.text = "Checking..."
        thread {
            val log = EsicChecker.checkOnce(applicationContext, sendNotifications = true)
            runOnUiThread {
                textLog.text = log
            }
        }
    }

    private fun runTestNotification() {
        // Save keywords/interval first in case they were just edited.
        saveSettingsQuietly()
        EsicChecker.sendTestNotification(applicationContext)
        textLog.text = "Test notification sent - check your notification shade."
    }

    private fun saveSettingsQuietly() {
        val hours = intervalOptions[spinnerInterval.selectedItemPosition]
        prefs().edit()
            .putString("keywords", editKeywords.text.toString().trim())
            .putInt("interval_hours", hours)
            .apply()
    }
}
