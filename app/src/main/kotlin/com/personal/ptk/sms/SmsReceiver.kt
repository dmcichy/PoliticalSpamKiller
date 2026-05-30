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
import com.personal.ptk.util.ShizukuHelper
import com.personal.ptk.util.SmsRoleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

            val vaultDao = app.database.vaultDao()

            if (ShizukuHelper.isActive(app)) {
                // Shizuku Power Mode — two-phase approach:
                //  Phase 1 (here): vault the entry BEFORE any role switching.
                //    The role switch kills our process ("permissions revoked"),
                //    so all DB work must finish first.
                //  Phase 2 (fireAndForgetDelete): a self-contained shell script
                //    launched via Shizuku that runs in Shizuku's own process,
                //    independent of PTK's lifecycle. It handles:
                //    role-switch → delete SMS → switch back → force-stop Messages.
                val smsId = SmsLookup.findSmsId(app, sender, body, timestamp)

                vaultDao.insert(
                    VaultEntry(
                        sender = sender,
                        body = body,
                        timestamp = timestamp,
                        reason = verdict.reason,
                        matchedRule = verdict.matchedRule,
                        scrubbed = smsId != null,
                        smsId = smsId
                    )
                )
                Log.d(TAG, "Power Mode: vaulted sender=$sender smsId=$smsId")

                if (smsId != null) {
                    val prevPkg = SmsRoleHelper.currentDefaultPackage(app)
                        ?: "com.google.android.apps.messaging"
                    ShizukuHelper.fireAndForgetDelete(
                        app.packageName, prevPkg, smsId
                    )
                }
            } else {
                // Standard mode: vault first, then look up smsId in background.
                // The vault insert is inline so it always completes.
                val smsId = SmsLookup.findSmsId(app, sender, body, timestamp,
                    maxAttempts = 3, delayMs = 300)
                vaultDao.insert(
                    VaultEntry(
                        sender = sender,
                        body = body,
                        timestamp = timestamp,
                        reason = verdict.reason,
                        matchedRule = verdict.matchedRule,
                        scrubbed = false,
                        smsId = smsId
                    )
                )
                Log.d(TAG, "Standard mode: vaulted sender=$sender smsId=$smsId")
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
