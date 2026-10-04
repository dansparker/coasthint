package io.github.dansparker.coasthint.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpeedFilterTest {

    @Test
    fun `first sample is taken as is`() {
        val state = SpeedFilter().update(0, 20.0)
        assertEquals(20.0, state.speedMps)
        assertEquals(0.0, state.accelerationMps2)
    }

    @Test
    fun `applies exponential moving average`() {
        val filter = SpeedFilter(alpha = 0.3)
        filter.update(0, 20.0)
        val state = filter.update(1000, 10.0)
        // 0.3 · 10 + 0.7 · 20
        assertEquals(17.0, state.speedMps, 1e-9)
        assertEquals(-3.0, state.accelerationMps2, 1e-9)
    }

    @Test
    fun `acceleration uses the actual time step`() {
        val filter = SpeedFilter(alpha = 0.3)
        filter.update(0, 20.0)
        assertEquals(-6.0, filter.update(500, 10.0).accelerationMps2, 1e-9)
    }

    @Test
    fun `constant speed converges with zero acceleration`() {
        val filter = SpeedFilter()
        var state = filter.update(0, 25.0)
        for (t in 1..10) state = filter.update(t * 1000L, 25.0)
        assertEquals(25.0, state.speedMps, 1e-9)
        assertEquals(0.0, state.accelerationMps2, 1e-9)
    }

    @Test
    fun `out of order samples are ignored`() {
        val filter = SpeedFilter()
        filter.update(1000, 20.0)
        val state = filter.update(1000, 5.0)
        assertEquals(20.0, state.speedMps)
    }

    @Test
    fun `long gap restarts the filter`() {
        val filter = SpeedFilter(maxGapMillis = 5_000)
        filter.update(0, 30.0)
        val state = filter.update(10_000, 10.0)
        assertEquals(10.0, state.speedMps)
        assertEquals(0.0, state.accelerationMps2)
    }
}
