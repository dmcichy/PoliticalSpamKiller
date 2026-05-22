package com.personal.ptk.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Required stub to qualify as a default SMS app.
 * We don't process MMS; this just satisfies the WAP_PUSH_DELIVER requirement.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // no-op — MMS is not relevant for political spam filtering
    }
}
