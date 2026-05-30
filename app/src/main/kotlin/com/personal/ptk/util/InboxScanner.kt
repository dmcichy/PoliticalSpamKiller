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
    private val MMS_INBOX_URI: Uri = Uri.parse("content://mms/inbox")
    private val MMS_URI: Uri = Uri.parse("content://mms")

    fun countInbox(context: Context): Int {
        val sms = context.contentResolver.query(
            SMS_INBOX_URI, arrayOf("_id"), null, null, null
        )?.use { it.count } ?: 0
        val mms = context.contentResolver.query(
            MMS_INBOX_URI, arrayOf("_id"), null, null, null
        )?.use { it.count } ?: 0
        return sms + mms
    }

    fun getMmsBodyPublic(context: Context, mmsId: Long): String = getMmsBody(context, mmsId)
    fun getMmsSenderPublic(context: Context, mmsId: Long): String = getMmsSender(context, mmsId)

    private fun getMmsBody(context: Context, mmsId: Long): String {
        val partUri = Uri.parse("content://mms/$mmsId/part")
        val cursor = context.contentResolver.query(
            partUri, arrayOf("ct", "text", "_data"),
            "ct = 'text/plain'", null, null
        ) ?: return ""
        return cursor.use {
            if (it.moveToFirst()) {
                it.getString(it.getColumnIndexOrThrow("text")) ?: ""
            } else ""
        }
    }

    private fun getMmsSender(context: Context, mmsId: Long): String {
        val addrUri = Uri.parse("content://mms/$mmsId/addr")
        val cursor = context.contentResolver.query(
            addrUri, arrayOf("address", "type"),
            "type = 137", null, null  // 137 = PduHeaders.FROM
        ) ?: return ""
        return cursor.use {
            if (it.moveToFirst()) {
                it.getString(it.getColumnIndexOrThrow("address")) ?: ""
            } else ""
        }
    }

    /**
     * @param deleteFromInbox If true, deletes detected spam from the SMS inbox
     *   during the scan (requires PTK to be default SMS app). If false,
     *   vaults spam with scrubbed=false for later review + purge.
     */
    suspend fun scan(
        context: Context,
        deleteFromInbox: Boolean = true,
        onProgress: (current: Int, total: Int, killed: Int) -> Unit = { _, _, _ -> }
    ): ScanResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext as App
        val vaultDao = app.database.vaultDao()
        val ruleDao = app.database.ruleDao()

        // Diagnostic: log active rules so we can verify they exist
        val diagKeywords = app.database.ruleDao().getAllActiveKeywords()
        Log.d(TAG, "DIAG: ${diagKeywords.size} keywords active: ${diagKeywords.take(10)}")
        val diagBlocklist = app.database.ruleDao().getByType(
            com.personal.ptk.data.entities.RuleType.BLOCKLIST_NUMBER
        )
        Log.d(TAG, "DIAG: ${diagBlocklist.size} blocklist entries")
        val diagAllowlist = app.database.ruleDao().getByType(
            com.personal.ptk.data.entities.RuleType.ALLOWLIST_NUMBER
        )
        Log.d(TAG, "DIAG: ${diagAllowlist.size} allowlist entries: ${diagAllowlist.map { it.value }.take(10)}")

        data class SpamHit(
            val smsId: Long,
            val sender: String,
            val body: String,
            val timestamp: Long,
            val reason: String,
            val matchedRule: String,
            val isMms: Boolean = false
        )

        val hits = mutableListOf<SpamHit>()

        // --- Phase 1: Scan SMS inbox ---
        val smsCursor = context.contentResolver.query(
            SMS_INBOX_URI,
            arrayOf("_id", "address", "body", "date"),
            null, null, "date DESC"
        )
        val smsCount = smsCursor?.count ?: 0

        // --- Phase 2: Count MMS inbox ---
        val mmsCursor = context.contentResolver.query(
            MMS_INBOX_URI,
            arrayOf("_id", "date"),
            null, null, "date DESC"
        )
        val mmsCount = mmsCursor?.count ?: 0
        val total = smsCount + mmsCount

        // Scan SMS
        smsCursor?.use {
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

        // Scan MMS
        mmsCursor?.use {
            val idIdx = it.getColumnIndexOrThrow("_id")
            val dateIdx = it.getColumnIndexOrThrow("date")
            var mmsScanned = 0

            while (it.moveToNext()) {
                val mmsId = it.getLong(idIdx)
                val timestamp = it.getLong(dateIdx) * 1000  // MMS dates are in seconds
                mmsScanned++

                withContext(Dispatchers.Main) {
                    onProgress(smsCount + mmsScanned, total, hits.size)
                }

                val sender = getMmsSender(context, mmsId)
                val body = getMmsBody(context, mmsId)
                if (sender.isBlank() || body.isBlank()) continue

                val verdict = app.classifier.classify(sender, body)
                if (verdict is Verdict.Kill) {
                    hits.add(SpamHit(mmsId, sender, body, timestamp, verdict.reason, verdict.matchedRule, isMms = true))
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
                ruleDao.deleteFromAllowlist(normalized)
            }

            var thisDeleted = false
            if (deleteFromInbox) {
                try {
                    val baseUri = if (hit.isMms) MMS_URI else SMS_URI
                    val rowUri = ContentUris.withAppendedId(baseUri, hit.smsId)
                    val rows = context.contentResolver.delete(rowUri, null, null)
                    if (rows > 0) {
                        deleted++
                        thisDeleted = true
                    } else {
                        deleteFailed++
                        Log.w(TAG, "Delete returned 0 for ${if (hit.isMms) "MMS" else "SMS"} id=${hit.smsId} from ${hit.sender}")
                    }
                } catch (e: Exception) {
                    deleteFailed++
                    Log.e(TAG, "Failed to delete id=${hit.smsId} from ${hit.sender}", e)
                }
            }

            vaultDao.insert(
                VaultEntry(
                    sender = hit.sender,
                    body = hit.body,
                    timestamp = hit.timestamp,
                    reason = hit.reason,
                    matchedRule = hit.matchedRule,
                    scrubbed = thisDeleted,
                    smsId = hit.smsId
                )
            )
            // If insert was ignored (duplicate), reset the old entry back to reviewable
            if (!thisDeleted) {
                vaultDao.resetExisting(
                    sender = hit.sender,
                    body = hit.body,
                    timestamp = hit.timestamp,
                    smsId = hit.smsId,
                    reason = hit.reason,
                    matchedRule = hit.matchedRule
                )
            }
        }

        val inboxAfter = countInbox(context)
        Log.d(TAG, "Scan: $total scanned, ${hits.size} spam, $deleted deleted, $deleteFailed failed, $inboxAfter remaining")
        ScanResult(total, hits.size, deleted, deleteFailed, inboxAfter)
    }
}
