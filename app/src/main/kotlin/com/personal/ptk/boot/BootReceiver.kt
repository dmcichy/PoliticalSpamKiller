package com.personal.ptk.boot

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Boot completed — checking permissions")

        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            Log.w(TAG, "Missing permissions after boot: $missing")
            postPermissionWarning(context, missing)
        } else {
            Log.d(TAG, "All permissions intact after boot")
        }
    }

    private fun postPermissionWarning(context: Context, missing: List<String>) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Permission Alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(channel)

        val names = missing.joinToString(", ") { it.substringAfterLast('.') }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("PoliticalTextKiller needs attention")
            .setContentText("Missing permissions: $names")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "PoliticalTextKiller cannot protect you without these permissions: $names. " +
                    "Open the app to re-grant them."
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "BootReceiver"
        private const val CHANNEL_ID = "ptk_permissions"
        private const val NOTIFICATION_ID = 9001

        private val REQUIRED_PERMISSIONS = listOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS
        )
    }
}
