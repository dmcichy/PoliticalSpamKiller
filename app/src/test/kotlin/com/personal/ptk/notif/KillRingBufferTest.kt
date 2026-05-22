package com.personal.ptk.notif

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class KillRingBufferTest {

    private lateinit var buffer: KillRingBuffer

    @BeforeEach
    fun setup() {
        buffer = KillRingBuffer(capacity = 5, ttlMs = 2000L)
    }

    @Test
    @DisplayName("Added entry matches immediately")
    fun entryMatchesImmediately() {
        buffer.add("+15559876543", "Donate now to save democracy!")
        assertTrue(buffer.matches("5559876543", "Donate now to save democracy!"))
    }

    @Test
    @DisplayName("No entries -> no match")
    fun emptyBufferNoMatch() {
        assertFalse(buffer.matches("5559876543", "Hello world"))
    }

    @Test
    @DisplayName("Different sender -> no match")
    fun differentSenderNoMatch() {
        buffer.add("5559876543", "Donate now!")
        assertFalse(buffer.matches("5551112222", "Donate now!"))
    }

    @Test
    @DisplayName("Different body prefix -> no match")
    fun differentBodyNoMatch() {
        buffer.add("5559876543", "Donate now!")
        assertFalse(buffer.matches("5559876543", "Your package has arrived"))
    }

    @Test
    @DisplayName("Capacity overflow evicts oldest entry")
    fun capacityOverflow() {
        repeat(6) { i ->
            buffer.add("555000${i}000", "Message $i body text here")
        }
        assertEquals(5, buffer.size())
        // Oldest entry (i=0) should be evicted
        assertFalse(buffer.matches("5550000000", "Message 0 body text here"))
        // Newest entry (i=5) should still be present
        assertTrue(buffer.matches("5550005000", "Message 5 body text here"))
    }

    @Test
    @DisplayName("TTL expiry removes stale entries")
    fun ttlExpiry() {
        val shortTtlBuffer = KillRingBuffer(capacity = 50, ttlMs = 50L)
        shortTtlBuffer.add("5559876543", "Donate now!")

        // Should match immediately
        assertTrue(shortTtlBuffer.matches("5559876543", "Donate now!"))

        // Wait for TTL to expire
        Thread.sleep(100)

        // Should not match after expiry
        assertFalse(shortTtlBuffer.matches("5559876543", "Donate now!"))
    }

    @Test
    @DisplayName("Clear empties the buffer")
    fun clearWorks() {
        buffer.add("5559876543", "Donate now!")
        assertEquals(1, buffer.size())
        buffer.clear()
        assertEquals(0, buffer.size())
        assertFalse(buffer.matches("5559876543", "Donate now!"))
    }

    @Test
    @DisplayName("Phone number normalization handles formatted numbers")
    fun phoneNormalization() {
        buffer.add("+1 (555) 987-6543", "Political spam here")
        assertTrue(buffer.matches("5559876543", "Political spam here"))
    }

    @Test
    @DisplayName("Thread safety: concurrent adds and matches")
    fun threadSafety() {
        val threads = (0 until 10).map { i ->
            Thread {
                repeat(100) { j ->
                    buffer.add("555${i}${j}0000".take(10), "Message $i-$j")
                    buffer.matches("5559876543", "Some text")
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        // No crash = success; buffer should have at most capacity entries
        assertTrue(buffer.size() <= 5)
    }
}
