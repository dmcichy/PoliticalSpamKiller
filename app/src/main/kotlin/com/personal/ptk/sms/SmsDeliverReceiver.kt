package com.personal.ptk.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.personal.ptk.App
import com.personal.ptk.classify.Classifier
import com.personal.ptk.classify.Verdict
import com.personal.ptk.data.entities.RuleEntry
import com.personal.ptk.data.entities.RuleType
import com.personal.ptk.data.entities.VaultEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Primary SMS filter. Receives SMS_DELIVER when PTK is the default SMS app.
 * - Spam: vaulted + blocklisted. Never written to SMS store — it never existed.
 * - Legit: written to SMS store so Google Messages shows it normally.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val app = context.applicationContext as? App ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                processMessages(app, context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS_DELIVER", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processMessages(app: App, context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        val grouped = messages
            .filter { it.originatingAddress != null }
            .groupBy { it.originatingAddress!! }

        for ((sender, parts) in grouped) {
            val body = parts.joinToString("") { it.messageBody ?: "" }
            if (body.isBlank()) continue
            val timestamp = parts.first().timestampMillis

            if (app.isKillSwitchEnabled()) {
                val verdict = app.classifier.classify(sender, body)

                if (verdict is Verdict.Kill) {
                    Log.d(TAG, "KILL from $sender — NOT writing to SMS store")

                    app.killRingBuffer.add(sender, body)
                    autoBlockSender(app, sender)

                    app.database.vaultDao().insert(
                        VaultEntry(
                            sender = sender,
                            body = body,
                            timestamp = timestamp,
                            reason = verdict.reason,
                            matchedRule = verdict.matchedRule,
                            scrubbed = true
                        )
                    )
                    continue
                }
            }

            writeToSmsStore(context, sender, body, timestamp)
        }
    }

    private fun writeToSmsStore(context: Context, sender: String, body: String, timestamp: Long) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, sender)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
            }
            context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write SMS to store from $sender", e)
        }
    }

    private suspend fun autoBlockSender(app: App, sender: String) {
        val normalized = Classifier.normalizeNumber(sender)
        if (normalized.isBlank()) return
        val dao = app.database.ruleDao()
        if (dao.countByTypeAndValue(RuleType.BLOCKLIST_NUMBER, normalized) == 0) {
            dao.insert(RuleEntry(type = RuleType.BLOCKLIST_NUMBER, value = normalized))
        }
    }

    companion object {
        private const val TAG = "SmsDeliverReceiver"
    }
}
