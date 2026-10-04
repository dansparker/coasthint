package io.github.dansparker.coasthint.osmand

/** Exponential backoff for reconnect attempts: initial, 2×, 4×, … capped at [maxMillis]. */
class Backoff(
    private val initialMillis: Long = 1_000,
    private val maxMillis: Long = 60_000,
) {
    private var attempt = 0

    fun next(): Long {
        val delay = (initialMillis shl attempt.coerceAtMost(MAX_SHIFT)).coerceAtMost(maxMillis)
        attempt++
        return delay
    }

    fun reset() {
        attempt = 0
    }

    private companion object {
        const val MAX_SHIFT = 20
    }
}
