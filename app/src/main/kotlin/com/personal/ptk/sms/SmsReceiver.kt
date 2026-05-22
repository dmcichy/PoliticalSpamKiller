package com.personal.ptk.sms

import android.content.BroadcastReceiver
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
import com.personal.ptk.util.SmsRoleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val app = context.applicationContext as? App ?: return
        if (!app.isKillSwitchEnabled()) return

        // If PTK is currently the default SMS app, SmsDeliverReceiver handles
        // it (and never writes spam to the store at all). Don't double-process.
        if (SmsRoleHelper.isDefaultSmsApp(app)) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                processMessages(app, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processMessages(app: App, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        val grouped = messages
            .filter { it.originatingAddress != null }
            .groupBy { it.originatingAddress!! }

        for ((sender, parts) in grouped) {
            val body = parts.joinToString("") { it.messageBody ?: "" }
            if (body.isBlank()) continue
            val timestamp = parts.first().timestampMillis

            val verdict = app.classifier.classify(sender, body)
            if (verdict !is Verdict.Kill) continue

            Log.d(TAG, "KILL from $sender: reason=${verdict.reason}, rule=${verdict.matchedRule}")

            // Silence the notification immediately via the ring buffer
            app.killRingBuffer.add(sender, body)

            // Auto-blocklist so subsequent texts from this sender are instant kills
            autoBlockSender(app, sender)

            // Log to vault (scrubbed=false; message is still in Google Messages inbox)
            val vaultDao = app.database.vaultDao()
            vaultDao.insert(
                VaultEntry(
                    sender = sender,
                    body = body,
                    timestamp = timestamp,
                    reason = verdict.reason,
                    matchedRule = verdict.matchedRule,
                    scrubbed = false,
                    smsId = null
                )
            )

            // Background poll for the SMS row id so a future Purge can delete it precisely
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val smsId = SmsLookup.findSmsId(app, sender, body, timestamp)
                if (smsId != null) {
                    val entry = vaultDao.getAll().firstOrNull {
                        it.sender == sender && it.body == body && it.timestamp == timestamp
                    }
                    if (entry != null) {
                        vaultDao.setSmsId(entry.id, smsId)
                        Log.d(TAG, "Tagged vault entry ${entry.id} with smsId=$smsId")
                    }
                }
            }
        }
    }

    private suspend fun autoBlockSender(app: App, sender: String) {
        val normalized = Classifier.normalizeNumber(sender)
        if (normalized.isBlank()) return

        val dao = app.database.ruleDao()
        val alreadyBlocked = dao.countByTypeAndValue(RuleType.BLOCKLIST_NUMBER, normalized)
        if (alreadyBlocked == 0) {
            dao.insert(RuleEntry(type = RuleType.BLOCKLIST_NUMBER, value = normalized))
            Log.d(TAG, "Auto-blocklisted sender: $normalized")
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
