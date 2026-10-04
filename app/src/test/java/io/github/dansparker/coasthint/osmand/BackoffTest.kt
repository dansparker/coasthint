package io.github.dansparker.coasthint.osmand

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackoffTest {

    @Test
    fun `doubles up to the maximum`() {
        val backoff = Backoff(initialMillis = 1_000, maxMillis = 10_000)
        val delays = List(6) { backoff.next() }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 10_000L, 10_000L), delays)
    }

    @Test
    fun `reset starts over`() {
        val backoff = Backoff(initialMillis = 1_000)
        repeat(3) { backoff.next() }
        backoff.reset()
        assertEquals(1_000L, backoff.next())
    }

    @Test
    fun `many attempts do not overflow`() {
        val backoff = Backoff(initialMillis = 1_000, maxMillis = 60_000)
        repeat(100) { backoff.next() }
        assertEquals(60_000L, backoff.next())
    }
}
