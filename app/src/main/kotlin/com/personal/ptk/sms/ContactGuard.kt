package com.personal.ptk.sms

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.personal.ptk.classify.ContactChecker

class ContactGuard(private val contentResolver: ContentResolver) : ContactChecker {

    override fun isKnown(sender: String): Boolean {
        if (sender.isBlank()) return false

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(sender)
            )
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null, null, null
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Contact lookup failed for $sender", e)
            false
        }
    }

    companion object {
        private const val TAG = "ContactGuard"
    }
}
