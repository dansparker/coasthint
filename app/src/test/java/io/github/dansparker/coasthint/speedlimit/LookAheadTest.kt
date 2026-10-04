package io.github.dansparker.coasthint.speedlimit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LookAheadTest {
    private val matcher = MapMatcher()
    private val lookAhead = LookAhead(maxDistanceM = 1_500.0)

    /** Drives north from (0, 100) and looks for the next lower limit. */
    private fun ahead(network: RoadNetwork, speedKmh: Double, from: LatLon = TestRoads.at(0.0, 100.0)) =
        lookAhead.nextLowerLimit(network, matcher.match(network, from, bearingDeg = 0.0)!!, speedKmh)

    private fun straight(firstLimit: Int?, secondLimit: Int?) = TestRoads.Builder()
        .node(1, 0.0, 0.0).node(2, 0.0, 500.0).node(3, 0.0, 1_000.0)
        .way(1, 1, 2, maxspeed = firstLimit)
        .way(2, 2, 3, maxspeed = secondLimit)
        .build()

    @Test
    fun `finds the lower limit at the next way`() {
        val event = ahead(straight(100, 70), speedKmh = 100.0)
        assertNotNull(event)
        assertEquals(70, event!!.limitKmh)
        assertEquals(2L, event.wayId)
        assertEquals(400.0, event.distanceM, 0.5)
        assertEquals(TestRoads.at(0.0, 500.0).lat, event.lat, 1e-9)
    }

    @Test
    fun `ignores limits that are not below the own speed`() {
        assertNull(ahead(straight(100, 70), speedKmh = 60.0))
    }

    @Test
    fun `split way with the same limit is no event`() {
        assertNull(ahead(straight(100, 100), speedKmh = 115.0))
    }

    @Test
    fun `continues across ways without maxspeed`() {
        val network = TestRoads.Builder()
            .node(1, 0.0, 0.0).node(2, 0.0, 500.0).node(3, 0.0, 1_000.0).node(4, 0.0, 1_500.0)
            .way(1, 1, 2, maxspeed = 100)
            .way(2, 2, 3, maxspeed = null)
            .way(3, 3, 4, maxspeed = 50)
            .build()
        val event = ahead(network, speedKmh = 100.0)!!
        assertEquals(50, event.limitKmh)
        assertEquals(900.0, event.distanceM, 0.5)
    }

    @Test
    fun `stops after the look-ahead distance`() {
        val network = TestRoads.Builder()
            .node(1, 0.0, 0.0).node(2, 0.0, 1_800.0).node(3, 0.0, 2_500.0)
            .way(1, 1, 2, maxspeed = 100)
            .way(2, 2, 3, maxspeed = 50)
            .build()
        assertNull(ahead(network, speedKmh = 100.0))
    }

    @Test
    fun `follows the straightest successor at a fork`() {
        val network = TestRoads.Builder()
            .node(1, 0.0, 0.0).node(2, 0.0, 500.0)
            .node(3, 300.0, 500.0).node(4, 50.0, 1_000.0)
            .way(1, 1, 2, maxspeed = 100)
            .way(2, 2, 3, maxspeed = 30) // sharp right
            .way(3, 2, 4, maxspeed = 70) // almost straight
            .build()
        assertEquals(70, ahead(network, speedKmh = 100.0)!!.limitKmh)
    }

    @Test
    fun `does not continue into a one-way street against its direction`() {
        val network = TestRoads.Builder()
            .node(1, 0.0, 0.0).node(2, 0.0, 500.0).node(3, 0.0, 1_000.0).node(4, 300.0, 550.0)
            .way(1, 1, 2, maxspeed = 100)
            .way(2, 3, 2, maxspeed = 30, oneway = true) // straight on, but one-way towards us
            .way(3, 2, 4, maxspeed = 50) // turning right
            .build()
        val event = ahead(network, speedKmh = 100.0)!!
        assertEquals(50, event.limitKmh)
        assertEquals(3L, event.wayId)
    }

    @Test
    fun `dead end gives no event`() {
        val network = TestRoads.Builder()
            .node(1, 0.0, 0.0).node(2, 0.0, 500.0)
            .way(1, 1, 2, maxspeed = 100)
            .build()
        assertNull(ahead(network, speedKmh = 100.0))
    }

    @Test
    fun `works when driving against the node order`() {
        val network = TestRoads.Builder()
            .node(1, 0.0, 0.0).node(2, 0.0, 500.0).node(3, 0.0, 1_000.0)
            .way(1, 2, 1, maxspeed = 100)
            .way(2, 3, 2, maxspeed = 50)
            .build()
        val event = ahead(network, speedKmh = 100.0)!!
        assertEquals(50, event.limitKmh)
        assertEquals(400.0, event.distanceM, 0.5)
    }

    @Test
    fun `uses the limit for the travel direction`() {
        val network = TestRoads.Builder()
            .node(1, 0.0, 0.0).node(2, 0.0, 500.0).node(3, 0.0, 1_000.0)
            .way(1, 1, 2, maxspeed = 100)
            .way(2, 2, 3, tags = mapOf("maxspeed:forward" to "60", "maxspeed:backward" to "100"))
            .build()
        assertEquals(60, ahead(network, speedKmh = 100.0)!!.limitKmh)
    }
}
