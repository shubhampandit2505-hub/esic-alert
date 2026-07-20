package com.anmol.esicalert

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager

/**
 * Application class for ESIC Alert.
 * Initializes WorkManager with proper configuration for background scheduling.
 */
class EsicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        try {
            // Initialize WorkManager with default configuration
            // This ensures WorkManager is ready for use throughout the app lifecycle
            WorkManager.initialize(
                this,
                Configuration.Builder()
                    // Enable detailed logging in debug builds to help troubleshoot issues
                    .setMinimumLoggingLevel(android.util.Log.INFO)
                    .build()
            )
            android.util.Log.i("EsicApp", "WorkManager initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("EsicApp", "Failed to initialize WorkManager: ${e.message}", e)
            // Continue anyway - WorkManager might have default initialization
        }
    }
}
