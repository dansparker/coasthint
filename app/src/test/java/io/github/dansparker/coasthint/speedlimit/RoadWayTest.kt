package io.github.dansparker.coasthint.speedlimit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.api.Test

class RoadWayTest {
    private fun way(vararg tags: Pair<String, String>) = RoadWay.fromTags(1, listOf(1, 2), mapOf(*tags))

    @ParameterizedTest(name = "{0} / {1} / {2} → {3}")
    @CsvSource(
        "primary, , , NO",
        "primary, yes, , FORWARD",
        "primary, 1, , FORWARD",
        "primary, -1, , BACKWARD",
        "primary, no, , NO",
        "primary, , roundabout, FORWARD",
        "motorway, , , FORWARD",
        "motorway, no, , NO",
    )
    fun `oneway handling`(highway: String, oneway: String?, junction: String?, expected: Oneway) {
        val tags = buildMap {
            put("highway", highway)
            oneway?.let { put("oneway", it) }
            junction?.let { put("junction", it) }
        }
        assertEquals(expected, RoadWay.fromTags(1, listOf(1, 2), tags).oneway)
    }

    @Test
    fun `one-way roads allow only their direction`() {
        val road = way("highway" to "primary", "oneway" to "yes")
        assertTrue(road.allows(forward = true))
        assertFalse(road.allows(forward = false))
    }

    @Test
    fun `direction specific limits override the general one`() {
        val road = way("highway" to "primary", "maxspeed" to "100", "maxspeed:backward" to "70")
        assertEquals(Maxspeed.Limit(100), road.maxspeed(forward = true))
        assertEquals(Maxspeed.Limit(70), road.maxspeed(forward = false))
    }

    @Test
    fun `name falls back to ref`() {
        assertEquals("B17", way("highway" to "primary", "ref" to "B17").name)
        assertEquals("Hauptstraße", way("highway" to "primary", "name" to "Hauptstraße", "ref" to "B17").name)
    }

    @Test
    fun `network drops ways with missing nodes`() {
        val network = RoadNetwork(
            listOf(RoadWay(1, listOf(1, 2), "primary"), RoadWay(2, listOf(2, 3), "primary")),
            mapOf(1L to TestRoads.at(0.0, 0.0), 2L to TestRoads.at(0.0, 10.0)),
        )
        assertEquals(setOf(1L), network.ways.keys)
        assertEquals(1, network.waysAt(2).size)
    }

    @Test
    fun `merge combines networks`() {
        val a = TestRoads.Builder().node(1, 0.0, 0.0).node(2, 0.0, 100.0).way(1, 1, 2, maxspeed = 50).build()
        val b = TestRoads.Builder().node(2, 0.0, 100.0).node(3, 0.0, 200.0).way(2, 2, 3, maxspeed = 30).build()
        val merged = RoadNetwork.merge(listOf(a, b))
        assertEquals(setOf(1L, 2L), merged.ways.keys)
        assertEquals(2, merged.waysAt(2).size)
    }
}
