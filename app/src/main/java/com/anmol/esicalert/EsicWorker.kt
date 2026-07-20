package com.anmol.esicalert

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * Runs in the background on the schedule set from MainActivity.
 * WorkManager takes care of surviving reboots/app-swipes on its own -
 * no extra boot receiver needed.
 */
class EsicWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    
    companion object {
        private const val TAG = "EsicWorker"
    }
    
    override fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting background check...")
            val log = EsicChecker.checkOnce(applicationContext, sendNotifications = true)
            Log.d(TAG, "Check completed successfully")
            Log.d(TAG, "Check result: $log")
            Result.success(workDataOf("log" to log))
        } catch (e: Exception) {
            Log.e(TAG, "Background check failed: ${e.message}", e)
            // Retry with exponential backoff when an error occurs
            Result.retry()
        }
    }
}
