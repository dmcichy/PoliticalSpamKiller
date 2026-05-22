package com.personal.ptk.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.util.Log
import com.personal.ptk.App
import com.personal.ptk.classify.Classifier
import com.personal.ptk.classify.Verdict
import com.personal.ptk.data.entities.RuleEntry
import com.personal.ptk.data.entities.RuleType
import com.personal.ptk.data.entities.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanResult(
    val scanned: Int,
    val killed: Int,
    val deleted: Int,
    val deleteFailed: Int,
    val inboxAfter: Int
)

object InboxScanner {

    private const val TAG = "InboxScanner"
    private val SMS_URI: Uri = Uri.parse("content://sms")
    private val SMS_INBOX_URI: Uri = Uri.parse("content://sms/inbox")

    fun countInbox(context: Context): Int {
        val cursor = context.contentResolver.query(
            SMS_INBOX_URI, arrayOf("_id"), null, null, null
        ) ?: return -1
        val count = cursor.count
        cursor.close()
        return count
    }

    suspend fun scan(
        context: Context,
        onProgress: (current: Int, total: Int, killed: Int) -> Unit = { _, _, _ -> }
    ): ScanResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext as App
        val vaultDao = app.database.vaultDao()
        val ruleDao = app.database.ruleDao()

        val cursor = context.contentResolver.query(
            SMS_INBOX_URI,
            arrayOf("_id", "address", "body", "date"),
            null, null, "date DESC"
        ) ?: return@withContext ScanResult(0, 0, 0, 0, -1)

        data class SpamHit(
            val smsId: Long,
            val sender: String,
            val body: String,
            val timestamp: Long,
            val reason: String,
            val matchedRule: String
        )

        val hits = mutableListOf<SpamHit>()
        val total: Int

        cursor.use {
            total = it.count
            val idIdx = it.getColumnIndexOrThrow("_id")
            val addrIdx = it.getColumnIndexOrThrow("address")
            val bodyIdx = it.getColumnIndexOrThrow("body")
            val dateIdx = it.getColumnIndexOrThrow("date")
            var scanned = 0

            while (it.moveToNext()) {
                val smsId = it.getLong(idIdx)
                val sender = it.getString(addrIdx) ?: continue
                val body = it.getString(bodyIdx) ?: continue
                val timestamp = it.getLong(dateIdx)
                scanned++

                withContext(Dispatchers.Main) {
                    onProgress(scanned, total, hits.size)
                }

                val verdict = app.classifier.classify(sender, body)
                if (verdict is Verdict.Kill) {
                    hits.add(SpamHit(smsId, sender, body, timestamp, verdict.reason, verdict.matchedRule))
                }
            }
        }

        var deleted = 0
        var deleteFailed = 0

        for (hit in hits) {
            val normalized = Classifier.normalizeNumber(hit.sender)
            if (normalized.isNotBlank() &&
                ruleDao.countByTypeAndValue(RuleType.BLOCKLIST_NUMBER, normalized) == 0
            ) {
                ruleDao.insert(RuleEntry(type = RuleType.BLOCKLIST_NUMBER, value = normalized))
            }

            try {
                val rowUri = ContentUris.withAppendedId(SMS_URI, hit.smsId)
                val rows = context.contentResolver.delete(rowUri, null, null)
                if (rows > 0) {
                    deleted++
                } else {
                    deleteFailed++
                    Log.w(TAG, "Delete returned 0 for SMS id=${hit.smsId} from ${hit.sender}")
                }
            } catch (e: Exception) {
                deleteFailed++
                Log.e(TAG, "Failed to delete SMS id=${hit.smsId} from ${hit.sender}", e)
            }

            vaultDao.insert(
                VaultEntry(
                    sender = hit.sender,
                    body = hit.body,
                    timestamp = hit.timestamp,
                    reason = hit.reason,
                    matchedRule = hit.matchedRule,
                    scrubbed = true,
                    smsId = hit.smsId
                )
            )
        }

        val inboxAfter = countInbox(context)
        Log.d(TAG, "Scan: $total scanned, ${hits.size} spam, $deleted deleted, $deleteFailed failed, $inboxAfter remaining")
        ScanResult(total, hits.size, deleted, deleteFailed, inboxAfter)
    }
}
