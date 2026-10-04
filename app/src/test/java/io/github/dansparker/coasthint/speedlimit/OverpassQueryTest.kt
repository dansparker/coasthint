package io.github.dansparker.coasthint.speedlimit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OverpassQueryTest {

    @Test
    fun `corridor reaches from behind to far ahead`() {
        val corridor = OverpassQuery.corridor(TestRoads.ORIGIN, bearingDeg = 0.0)
        // -100, 200, 500, 800, 1100, 1400, 1500
        assertEquals(7, corridor.size)
        assertEquals(100.0, Geo.distanceM(TestRoads.ORIGIN, corridor.first()), 0.01)
        assertEquals(180.0, Geo.bearingDeg(TestRoads.ORIGIN, corridor.first()), 0.01)
        assertEquals(1_500.0, Geo.distanceM(TestRoads.ORIGIN, corridor.last()), 0.01)
        assertEquals(0.0, Geo.bearingDeg(TestRoads.ORIGIN, corridor.last()), 0.01)
    }

    @Test
    fun `without bearing the corridor is just the position`() {
        assertEquals(listOf(TestRoads.ORIGIN), OverpassQuery.corridor(TestRoads.ORIGIN, bearingDeg = null))
    }

    @Test
    fun `query uses around filter with dot decimals`() {
        val query = OverpassQuery.build(listOf(LatLon(48.2, 16.37), LatLon(48.21, 16.37)), 250)
        assertTrue(query.startsWith("[out:json]"))
        assertTrue("way(around:250,48.200000,16.370000,48.210000,16.370000)" in query)
        assertTrue("highway~" in query)
        assertTrue(query.endsWith("(._;>;);out body qt;"))
    }

    @Test
    fun `parses ways, nodes and tags`() {
        val json = TestRoads.Builder()
            .node(1, 0.0, 0.0).node(2, 0.0, 100.0).node(3, 0.0, 200.0)
            .way(10, 1, 2, tags = mapOf("maxspeed" to "AT:urban", "name" to "Ringstraße"))
            .way(11, 2, 3, maxspeed = null, oneway = true)
            .overpassJson()
        val network = OverpassQuery.parse(json)
        assertEquals(setOf(10L, 11L), network.ways.keys)
        assertEquals(Maxspeed.Limit(50), network.ways.getValue(10).maxspeedForward)
        assertEquals("Ringstraße", network.ways.getValue(10).name)
        assertEquals(Oneway.FORWARD, network.ways.getValue(11).oneway)
        assertEquals(TestRoads.at(0.0, 100.0), network.position(2))
    }
}
