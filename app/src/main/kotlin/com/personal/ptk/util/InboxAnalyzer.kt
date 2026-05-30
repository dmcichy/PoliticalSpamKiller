package com.personal.ptk.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class SenderStats(
    val sender: String,
    val contactName: String?,
    val count: Int,
    val oldestYear: Int,
    val newestYear: Int
)

data class YearStats(
    val year: Int,
    val smsCount: Int,
    val mmsCount: Int
) {
    val total get() = smsCount + mmsCount
}

data class SenderMessage(
    val id: Long,
    val body: String,
    val timestamp: Long,
    val isMms: Boolean
)

object InboxAnalyzer {

    private const val TAG = "InboxAnalyzer"
    private val SMS_URI: Uri = Uri.parse("content://sms")
    private val MMS_URI: Uri = Uri.parse("content://mms")

    suspend fun getTopSenders(
        context: Context,
        limit: Int = 50,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): List<SenderStats> = withContext(Dispatchers.IO) {
        val senderMap = mutableMapOf<String, MutableList<Int>>() // sender -> list of years

        val smsCursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address", "date"), null, null, null
        )
        val mmsCursor = context.contentResolver.query(
            Uri.parse("content://mms/inbox"),
            arrayOf("_id", "date"), null, null, null
        )
        val smsCount = smsCursor?.count ?: 0
        val mmsCount = mmsCursor?.count ?: 0
        val total = smsCount + mmsCount
        var scanned = 0

        smsCursor?.use { c ->
            val addrIdx = c.getColumnIndexOrThrow("address")
            val dateIdx = c.getColumnIndexOrThrow("date")
            while (c.moveToNext()) {
                val sender = c.getString(addrIdx) ?: continue
                val date = c.getLong(dateIdx)
                val cal = Calendar.getInstance().apply { timeInMillis = date }
                val year = cal.get(Calendar.YEAR)
                senderMap.getOrPut(sender) { mutableListOf() }.add(year)
                scanned++
                if (scanned % 2000 == 0) {
                    withContext(Dispatchers.Main) { onProgress(scanned, total) }
                }
            }
        }

        mmsCursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow("_id")
            val dateIdx = c.getColumnIndexOrThrow("date")
            while (c.moveToNext()) {
                val mmsId = c.getLong(idIdx)
                val date = c.getLong(dateIdx) * 1000
                val sender = InboxScanner.getMmsSenderPublic(context, mmsId)
                if (sender.isBlank()) continue
                val cal = Calendar.getInstance().apply { timeInMillis = date }
                val year = cal.get(Calendar.YEAR)
                senderMap.getOrPut(sender) { mutableListOf() }.add(year)
                scanned++
                if (scanned % 500 == 0) {
                    withContext(Dispatchers.Main) { onProgress(scanned, total) }
                }
            }
        }

        withContext(Dispatchers.Main) { onProgress(total, total) }

        senderMap.map { (sender, years) ->
            SenderStats(
                sender = sender,
                contactName = lookupContactName(context, sender),
                count = years.size,
                oldestYear = years.min(),
                newestYear = years.max()
            )
        }.sortedByDescending { it.count }.take(limit)
    }

    suspend fun getYearBreakdown(
        context: Context,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): List<YearStats> = withContext(Dispatchers.IO) {
        val smsYears = mutableMapOf<Int, Int>()
        val mmsYears = mutableMapOf<Int, Int>()

        val smsCursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("date"), null, null, null
        )
        val mmsCursor = context.contentResolver.query(
            Uri.parse("content://mms/inbox"),
            arrayOf("date"), null, null, null
        )
        val total = (smsCursor?.count ?: 0) + (mmsCursor?.count ?: 0)
        var scanned = 0

        smsCursor?.use { c ->
            val dateIdx = c.getColumnIndexOrThrow("date")
            while (c.moveToNext()) {
                val cal = Calendar.getInstance().apply { timeInMillis = c.getLong(dateIdx) }
                val year = cal.get(Calendar.YEAR)
                smsYears[year] = (smsYears[year] ?: 0) + 1
                scanned++
                if (scanned % 5000 == 0) {
                    withContext(Dispatchers.Main) { onProgress(scanned, total) }
                }
            }
        }

        mmsCursor?.use { c ->
            val dateIdx = c.getColumnIndexOrThrow("date")
            while (c.moveToNext()) {
                val cal = Calendar.getInstance().apply { timeInMillis = c.getLong(dateIdx) * 1000 }
                val year = cal.get(Calendar.YEAR)
                mmsYears[year] = (mmsYears[year] ?: 0) + 1
                scanned++
                if (scanned % 1000 == 0) {
                    withContext(Dispatchers.Main) { onProgress(scanned, total) }
                }
            }
        }

        withContext(Dispatchers.Main) { onProgress(total, total) }

        val allYears = (smsYears.keys + mmsYears.keys).sorted()
        allYears.map { year ->
            YearStats(year, smsYears[year] ?: 0, mmsYears[year] ?: 0)
        }
    }

    suspend fun deleteBySender(
        context: Context,
        sender: String,
        onProgress: (deleted: Int) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        var deleted = 0

        // Delete SMS
        try {
            val rows = context.contentResolver.delete(
                SMS_URI, "address = ?", arrayOf(sender)
            )
            deleted += rows
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete SMS for $sender", e)
        }

        // Delete MMS by sender (need to find MMS IDs first)
        val mmsCursor = context.contentResolver.query(
            Uri.parse("content://mms/inbox"),
            arrayOf("_id"), null, null, null
        )
        mmsCursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow("_id")
            while (c.moveToNext()) {
                val mmsId = c.getLong(idIdx)
                val mmsSender = InboxScanner.getMmsSenderPublic(context, mmsId)
                if (mmsSender == sender) {
                    try {
                        val rowUri = ContentUris.withAppendedId(MMS_URI, mmsId)
                        if (context.contentResolver.delete(rowUri, null, null) > 0) {
                            deleted++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete MMS $mmsId", e)
                    }
                }
            }
        }

        withContext(Dispatchers.Main) { onProgress(deleted) }
        deleted
    }

    suspend fun deleteByYear(
        context: Context,
        year: Int,
        onProgress: (deleted: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        cal.set(year, 0, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startMs = cal.timeInMillis
        cal.set(year + 1, 0, 1, 0, 0, 0)
        val endMs = cal.timeInMillis

        var deleted = 0

        // Delete SMS in year range
        try {
            val rows = context.contentResolver.delete(
                SMS_URI, "date >= ? AND date < ?",
                arrayOf(startMs.toString(), endMs.toString())
            )
            deleted += rows
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete SMS for year $year", e)
        }

        // Delete MMS in year range (MMS dates are in seconds)
        val startSec = startMs / 1000
        val endSec = endMs / 1000
        val mmsCursor = context.contentResolver.query(
            Uri.parse("content://mms/inbox"),
            arrayOf("_id", "date"),
            "date >= ? AND date < ?",
            arrayOf(startSec.toString(), endSec.toString()),
            null
        )
        mmsCursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow("_id")
            while (c.moveToNext()) {
                val mmsId = c.getLong(idIdx)
                try {
                    val rowUri = ContentUris.withAppendedId(MMS_URI, mmsId)
                    if (context.contentResolver.delete(rowUri, null, null) > 0) {
                        deleted++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete MMS $mmsId", e)
                }
            }
        }

        withContext(Dispatchers.Main) { onProgress(deleted, deleted) }
        deleted
    }

    fun lookupContactName(context: Context, phoneNumber: String): String? {
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    return c.getString(0)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    suspend fun getSampleMessages(
        context: Context,
        sender: String,
        limit: Int = 3
    ): List<SenderMessage> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SenderMessage>()

        // SMS samples
        context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("_id", "body", "date"),
            "address = ?", arrayOf(sender),
            "date DESC"
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow("_id")
            val bodyIdx = c.getColumnIndexOrThrow("body")
            val dateIdx = c.getColumnIndexOrThrow("date")
            var count = 0
            while (c.moveToNext() && count < limit) {
                results.add(
                    SenderMessage(
                        id = c.getLong(idIdx),
                        body = c.getString(bodyIdx) ?: "",
                        timestamp = c.getLong(dateIdx),
                        isMms = false
                    )
                )
                count++
            }
        }

        // MMS samples if we need more
        if (results.size < limit) {
            context.contentResolver.query(
                Uri.parse("content://mms/inbox"),
                arrayOf("_id", "date"),
                null, null, "date DESC"
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow("_id")
                val dateIdx = c.getColumnIndexOrThrow("date")
                while (c.moveToNext() && results.size < limit) {
                    val mmsId = c.getLong(idIdx)
                    val mmsSender = InboxScanner.getMmsSenderPublic(context, mmsId)
                    if (mmsSender == sender) {
                        val body = InboxScanner.getMmsBodyPublic(context, mmsId)
                        if (body.isNotBlank()) {
                            results.add(
                                SenderMessage(
                                    id = mmsId,
                                    body = body,
                                    timestamp = c.getLong(dateIdx) * 1000,
                                    isMms = true
                                )
                            )
                        }
                    }
                }
            }
        }

        results.sortedByDescending { it.timestamp }
    }
}
