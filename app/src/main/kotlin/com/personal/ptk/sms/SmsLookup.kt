package com.personal.ptk.sms

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.delay

/**
 * Finds the row ID in content://sms for a recently-received SMS, so we can
 * later delete it by ID even though we are not the default SMS app.
 *
 * When PTK gets SMS_RECEIVED (broadcast at priority lower than SMS_DELIVER),
 * Google Messages may not have written the row to the store yet. We poll a
 * few times to give it a chance to land.
 */
object SmsLookup {

    private const val TAG = "SmsLookup"
    private val SMS_INBOX_URI: Uri = Uri.parse("content://sms/inbox")

    /**
     * Returns the SMS row ID for the message matching [sender] + [body]
     * arriving near [approxTimestamp], or null if not found within the polling
     * window. Polls up to [maxAttempts] times with [delayMs] between attempts.
     */
    suspend fun findSmsId(
        context: Context,
        sender: String,
        body: String,
        approxTimestamp: Long,
        maxAttempts: Int = 6,
        delayMs: Long = 750
    ): Long? {
        val bodyPrefix = body.take(40)
        val low = approxTimestamp - 5_000
        val high = approxTimestamp + 30_000

        repeat(maxAttempts) { attempt ->
            try {
                context.contentResolver.query(
                    SMS_INBOX_URI,
                    arrayOf("_id", "address", "body", "date"),
                    "date BETWEEN ? AND ?",
                    arrayOf(low.toString(), high.toString()),
                    "date DESC"
                )?.use { c ->
                    val idIdx = c.getColumnIndexOrThrow("_id")
                    val addrIdx = c.getColumnIndexOrThrow("address")
                    val bodyIdx = c.getColumnIndexOrThrow("body")
                    while (c.moveToNext()) {
                        val candidateSender = c.getString(addrIdx) ?: continue
                        val candidateBody = c.getString(bodyIdx) ?: continue
                        if (sameSender(candidateSender, sender) &&
                            candidateBody.startsWith(bodyPrefix)
                        ) {
                            return c.getLong(idIdx)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SMS lookup error on attempt ${attempt + 1}", e)
            }
            delay(delayMs)
        }
        Log.w(TAG, "Could not find SMS row for $sender within poll window")
        return null
    }

    private fun sameSender(a: String, b: String): Boolean {
        if (a == b) return true
        val ad = a.filter { it.isDigit() }
        val bd = b.filter { it.isDigit() }
        if (ad.isBlank() || bd.isBlank()) return a == b
        return ad.takeLast(10) == bd.takeLast(10)
    }
}
