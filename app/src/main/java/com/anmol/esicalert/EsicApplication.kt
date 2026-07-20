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
        
        // Initialize WorkManager with default configuration
        // This ensures WorkManager is ready for use throughout the app lifecycle
        WorkManager.initialize(
            this,
            Configuration.Builder()
                // Enable detailed logging in debug builds to help troubleshoot issues
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build()
        )
    }
}
