package com.personal.ptk.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.util.Log
import com.personal.ptk.App
import com.personal.ptk.sms.SmsLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PurgeResult(
    val attempted: Int,
    val deleted: Int,
    val notFound: Int,
    val failed: Int,
    val inboxAfter: Int
)

/**
 * Deletes SMS rows from the inbox that PTK has previously flagged as spam.
 * Requires PTK to currently be the default SMS app.
 *
 * Uses the [com.personal.ptk.data.entities.VaultEntry.smsId] column populated
 * by SmsReceiver. For older entries without an smsId we fall back to a
 * sender+body+timestamp lookup against the live SMS store.
 */
object InboxPurger {

    private const val TAG = "InboxPurger"
    private val SMS_URI: Uri = Uri.parse("content://sms")

    suspend fun purge(
        context: Context,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): PurgeResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext as App
        val vaultDao = app.database.vaultDao()
        val pending = vaultDao.getPendingPurge()

        var deleted = 0
        var notFound = 0
        var failed = 0

        for ((index, entry) in pending.withIndex()) {
            withContext(Dispatchers.Main) { onProgress(index + 1, pending.size) }

            val smsId = entry.smsId
                ?: SmsLookup.findSmsId(
                    context, entry.sender, entry.body, entry.timestamp,
                    maxAttempts = 1, delayMs = 0
                )?.also { vaultDao.setSmsId(entry.id, it) }

            if (smsId == null) {
                notFound++
                continue
            }

            try {
                val rowUri = ContentUris.withAppendedId(SMS_URI, smsId)
                val rows = context.contentResolver.delete(rowUri, null, null)
                if (rows > 0) {
                    deleted++
                    vaultDao.markScrubbed(entry.id)
                } else {
                    failed++
                    Log.w(TAG, "Delete returned 0 for smsId=$smsId from ${entry.sender}")
                }
            } catch (e: Exception) {
                failed++
                Log.e(TAG, "Failed to delete smsId=$smsId from ${entry.sender}", e)
            }
        }

        val inboxAfter = InboxScanner.countInbox(context)
        Log.d(TAG, "Purge: ${pending.size} attempted, $deleted deleted, $notFound not found, $failed failed, $inboxAfter remaining")
        PurgeResult(pending.size, deleted, notFound, failed, inboxAfter)
    }
}
