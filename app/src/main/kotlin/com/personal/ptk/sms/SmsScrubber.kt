package com.personal.ptk.sms

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.delay

object SmsScrubber {

    private const val TAG = "SmsScrubber"
    private val SMS_INBOX_URI: Uri = Uri.parse("content://sms/inbox")

    suspend fun scrub(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long
    ): Boolean {
        // First attempt
        if (tryDelete(context, sender, body, timestamp)) return true

        // Retry after 500ms — Google Messages may not have written the row yet
        delay(500)
        return tryDelete(context, sender, body, timestamp)
    }

    private fun tryDelete(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long
    ): Boolean {
        return try {
            val bodyPrefix = body.take(40).replace("'", "''")
            val selection = "address = ? AND date BETWEEN ? AND ? AND body LIKE ?"
            val args = arrayOf(
                sender,
                (timestamp - 2000).toString(),
                (timestamp + 5000).toString(),
                "$bodyPrefix%"
            )

            val deleted = context.contentResolver.delete(SMS_INBOX_URI, selection, args)
            if (deleted > 0) {
                Log.d(TAG, "Scrubbed $deleted SMS row(s) from $sender")
                true
            } else {
                Log.d(TAG, "No matching SMS rows found for $sender")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scrub SMS from $sender", e)
            false
        }
    }
}
