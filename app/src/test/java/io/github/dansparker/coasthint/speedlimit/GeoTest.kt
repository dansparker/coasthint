package io.github.dansparker.coasthint.speedlimit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeoTest {

    @Test
    fun `one degree of latitude is about 111 km`() {
        assertEquals(111_195.0, Geo.distanceM(LatLon(48.0, 16.0), LatLon(49.0, 16.0)), 1.0)
    }

    @Test
    fun `bearings point the right way`() {
        val o = TestRoads.ORIGIN
        assertEquals(0.0, Geo.bearingDeg(o, TestRoads.at(0.0, 100.0)), 0.01)
        assertEquals(90.0, Geo.bearingDeg(o, TestRoads.at(100.0, 0.0)), 0.01)
        assertEquals(180.0, Geo.bearingDeg(o, TestRoads.at(0.0, -100.0)), 0.01)
        assertEquals(270.0, Geo.bearingDeg(o, TestRoads.at(-100.0, 0.0)), 0.01)
    }

    @Test
    fun `angle difference wraps around north`() {
        assertEquals(20.0, Geo.angleDiffDeg(350.0, 10.0), 1e-9)
        assertEquals(180.0, Geo.angleDiffDeg(0.0, 180.0), 1e-9)
        assertEquals(90.0, Geo.angleDiffDeg(-45.0, 405.0), 1e-9)
    }

    @Test
    fun `destination is consistent with distance and bearing`() {
        val target = Geo.destination(TestRoads.ORIGIN, 37.0, 1_234.0)
        assertEquals(1_234.0, Geo.distanceM(TestRoads.ORIGIN, target), 0.01)
        assertEquals(37.0, Geo.bearingDeg(TestRoads.ORIGIN, target), 0.01)
    }

    @Test
    fun `projection onto a segment`() {
        val a = TestRoads.at(0.0, 0.0)
        val b = TestRoads.at(0.0, 100.0)
        val mid = Geo.projectOnSegment(TestRoads.at(10.0, 50.0), a, b)
        assertEquals(0.5, mid.fraction, 0.001)
        assertEquals(10.0, mid.distanceM, 0.01)
        val before = Geo.projectOnSegment(TestRoads.at(0.0, -30.0), a, b)
        assertEquals(0.0, before.fraction)
        assertEquals(30.0, before.distanceM, 0.01)
    }
}
