package com.personal.ptk.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personal.ptk.data.PtkDatabase
import java.util.concurrent.TimeUnit

class WeeklySummaryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = PtkDatabase.getInstance(applicationContext)
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val count = db.vaultDao().countSince(weekAgo)

        if (count > 0) {
            postNotification(count)
        }

        pruneVault(db)

        return Result.success()
    }

    private fun postNotification(count: Int) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Weekly Summary",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Weekly spam kill count"
        }
        nm.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("PoliticalTextKiller Weekly Summary")
            .setContentText("Killed $count spam message${if (count != 1) "s" else ""} this week.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    private suspend fun pruneVault(db: PtkDatabase) {
        val prefs = applicationContext.getSharedPreferences("ptk_prefs", Context.MODE_PRIVATE)
        val retentionDays = prefs.getInt("vault_retention_days", 90)
        val cutoff = System.currentTimeMillis() - retentionDays.toLong() * 24 * 60 * 60 * 1000
        db.vaultDao().deleteOlderThan(cutoff)
    }

    companion object {
        private const val CHANNEL_ID = "ptk_weekly"
        private const val NOTIFICATION_ID = 9002
        private const val WORK_NAME = "ptk_weekly_summary"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(
                7, TimeUnit.DAYS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
