package com.personal.ptk.notif

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.personal.ptk.App

class NotificationKiller : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != GOOGLE_MESSAGES_PACKAGE) return

        val app = applicationContext as? App ?: return
        if (!app.isKillSwitchEnabled()) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        if (app.killRingBuffer.matches(title, text)) {
            Log.d(TAG, "Cancelling notification from $title")
            cancelNotification(sbn.key)

            // Also cancel the summary notification if present
            if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
                cancelNotification(sbn.key)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        onNotificationPosted(sbn)
    }

    companion object {
        private const val TAG = "NotificationKiller"
        private const val GOOGLE_MESSAGES_PACKAGE = "com.google.android.apps.messaging"
    }
}
