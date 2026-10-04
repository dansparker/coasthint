package io.github.dansparker.coasthint.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoastModelTest {

    @Test
    fun `constant model follows v squared over 2a`() {
        assertEquals(312.5, CoastModel.Constant(0.6).distanceM(20.0, 5.0), 1e-9)
    }

    @Test
    fun `quadratic model matches numerical integration`() {
        val model = CoastModel.Quadratic(c0 = 0.25, c2 = 0.0006)
        // Integrate dx = v / a(v) dv numerically
        var x = 0.0
        val steps = 100_000
        val dv = (30.0 - 10.0) / steps
        for (i in 0 until steps) {
            val v = 10.0 + (i + 0.5) * dv
            x += v / model.decelerationAt(v) * dv
        }
        assertEquals(x, model.distanceM(30.0, 10.0), 0.01)
    }

    @Test
    fun `quadratic without drag term equals constant`() {
        assertEquals(
            CoastModel.Constant(0.4).distanceM(25.0, 8.0),
            CoastModel.Quadratic(c0 = 0.4, c2 = 0.0).distanceM(25.0, 8.0),
            1e-9,
        )
    }

    @Test
    fun `air drag shortens the coast distance at high speed`() {
        // Same deceleration at 15 m/s, but stronger above due to drag
        val quadratic = CoastModel.Quadratic(c0 = 0.3, c2 = 0.001)
        val constant = CoastModel.Constant(quadratic.decelerationAt(15.0))
        assertTrue(quadratic.distanceM(35.0, 15.0) < constant.distanceM(35.0, 15.0))
    }
}
