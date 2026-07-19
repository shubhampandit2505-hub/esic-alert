package com.anmol.esicalert

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * Runs in the background on the schedule set from MainActivity.
 * WorkManager takes care of surviving reboots/app-swipes on its own -
 * no extra boot receiver needed.
 */
class EsicWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val log = EsicChecker.checkOnce(applicationContext, sendAlerts = true)
        return Result.success(workDataOf("log" to log))
    }
}
