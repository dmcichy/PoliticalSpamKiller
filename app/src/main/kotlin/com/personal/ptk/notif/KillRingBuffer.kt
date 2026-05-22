package com.personal.ptk.notif

import com.personal.ptk.classify.Classifier

/**
 * Thread-safe ring buffer of recently-killed message metadata.
 * Used by NotificationKiller to cross-reference incoming Google Messages
 * notifications against messages we've already classified as spam.
 *
 * Entries expire after [ttlMs] and the buffer holds at most [capacity] entries.
 */
class KillRingBuffer(
    private val capacity: Int = 50,
    private val ttlMs: Long = 5_000L
) {
    private data class Entry(
        val senderDigits: String,
        val bodyPrefix: String,
        val timestamp: Long
    )

    private val buffer = ArrayDeque<Entry>(capacity)

    @Synchronized
    fun add(sender: String, body: String) {
        if (buffer.size >= capacity) buffer.removeFirst()
        buffer.addLast(
            Entry(
                senderDigits = Classifier.normalizeNumber(sender),
                bodyPrefix = body.take(40).lowercase(),
                timestamp = System.currentTimeMillis()
            )
        )
    }

    @Synchronized
    fun matches(notificationTitle: String, notificationText: String): Boolean {
        val now = System.currentTimeMillis()
        buffer.removeAll { now - it.timestamp > ttlMs }

        val titleDigits = Classifier.normalizeNumber(notificationTitle)
        val textPrefix = notificationText.take(40).lowercase()

        return buffer.any { entry ->
            (entry.senderDigits == titleDigits || notificationTitle.contains(entry.senderDigits)) &&
                (entry.bodyPrefix.isNotEmpty() && textPrefix.startsWith(entry.bodyPrefix.take(20)))
        }
    }

    @Synchronized
    fun clear() = buffer.clear()

    @Synchronized
    fun size(): Int = buffer.size
}
