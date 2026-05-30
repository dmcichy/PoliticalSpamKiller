package com.personal.ptk.sms

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.personal.ptk.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives a broadcast from the Shizuku orchestration script AFTER PTK has
 * been made the default SMS app. At that point PTK's ContentResolver.delete()
 * is honoured by Samsung's SMS provider because the calling UID matches
 * the default SMS role holder.
 *
 * The broadcast is sent by a shell script running in Shizuku's process,
 * which is independent of PTK's lifecycle.
 */
class PendingDeleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val smsId = intent.getLongExtra(EXTRA_SMS_ID, -1)
        if (smsId < 0) return

        val app = context.applicationContext as? App ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rowUri = ContentUris.withAppendedId(
                    Uri.parse("content://sms"), smsId
                )
                val count = app.contentResolver.delete(rowUri, null, null)
                Log.d(TAG, "Delete smsId=$smsId result=$count")
            } catch (e: Exception) {
                Log.e(TAG, "Delete failed for smsId=$smsId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.personal.ptk.DELETE_SMS"
        const val EXTRA_SMS_ID = "smsId"
        private const val TAG = "PendingDeleteRx"
    }
}
