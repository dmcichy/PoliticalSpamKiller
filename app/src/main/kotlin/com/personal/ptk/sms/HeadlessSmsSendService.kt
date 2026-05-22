package com.personal.ptk.sms

import android.app.IntentService
import android.content.Intent

/**
 * Required stub to qualify as a default SMS app.
 * Handles "respond via message" from incoming call screen.
 * We don't actually send SMS so this is a no-op.
 */
@Suppress("DEPRECATION")
class HeadlessSmsSendService : IntentService("HeadlessSmsSend") {
    override fun onHandleIntent(intent: Intent?) {
        // no-op
    }
}
