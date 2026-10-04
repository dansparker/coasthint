package io.github.dansparker.coasthint.speedlimit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapMatcherTest {
    private val matcher = MapMatcher()

    /** North-south road (way 1) crossing an east-west road (way 2) at (0, 100). */
    private val crossing = TestRoads.Builder()
        .node(1, 0.0, 0.0).node(2, 0.0, 100.0).node(3, 0.0, 200.0)
        .node(4, -100.0, 100.0).node(5, 100.0, 100.0)
        .way(1, 1, 2, 3, maxspeed = 50)
        .way(2, 4, 2, 5, maxspeed = 30)
        .build()

    @Test
    fun `matches the road in driving direction`() {
        val match = matcher.match(crossing, TestRoads.at(5.0, 50.0), bearingDeg = 2.0)!!
        assertEquals(1L, match.way.id)
        assertTrue(match.forward)
        assertEquals(0, match.segmentIndex)
        assertEquals(5.0, match.distanceM, 0.01)
    }

    @Test
    fun `detects driving against the node order`() {
        val match = matcher.match(crossing, TestRoads.at(0.0, 150.0), bearingDeg = 180.0)!!
        assertEquals(1L, match.way.id)
        assertFalse(match.forward)
        assertEquals(1, match.segmentIndex)
    }

    @Test
    fun `picks the road with fitting bearing at an intersection`() {
        assertEquals(2L, matcher.match(crossing, TestRoads.at(3.0, 101.0), bearingDeg = 88.0)!!.way.id)
        assertEquals(1L, matcher.match(crossing, TestRoads.at(3.0, 101.0), bearingDeg = 358.0)!!.way.id)
    }

    @Test
    fun `no match when too far away`() {
        assertNull(matcher.match(crossing, TestRoads.at(30.0, 50.0), bearingDeg = 0.0))
    }

    @Test
    fun `no match when the bearing does not fit`() {
        assertNull(matcher.match(crossing, TestRoads.at(2.0, 50.0), bearingDeg = 45.0))
    }

    @Test
    fun `one-way road is not matched against its direction`() {
        val oneway = TestRoads.Builder()
            .node(1, 0.0, 0.0).node(2, 0.0, 200.0)
            .way(1, 1, 2, maxspeed = 50, oneway = true)
            .build()
        assertNull(matcher.match(oneway, TestRoads.at(0.0, 100.0), bearingDeg = 180.0))
    }

    @Test
    fun `prefers the closer of two parallel roads`() {
        val parallel = TestRoads.Builder()
            .node(1, 0.0, 0.0).node(2, 0.0, 200.0)
            .node(3, 15.0, 0.0).node(4, 15.0, 200.0)
            .way(1, 1, 2, maxspeed = 100)
            .way(2, 3, 4, maxspeed = 50)
            .build()
        assertEquals(2L, matcher.match(parallel, TestRoads.at(11.0, 100.0), bearingDeg = 0.0)!!.way.id)
        assertEquals(1L, matcher.match(parallel, TestRoads.at(4.0, 100.0), bearingDeg = 0.0)!!.way.id)
    }
}
