package io.github.dansparker.coasthint.speedlimit

import io.github.dansparker.coasthint.roaddb.Bounds
import io.github.dansparker.coasthint.roaddb.StoredWay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CorridorLoaderTest {
    private val austria = Bounds(46.37, 9.53, 49.02, 17.16)
    private val vienna = LatLon(48.2, 16.37)
    private val prague = LatLon(50.08, 14.43)

    /** Inside Austria's bounding box, but not in Austria. */
    private val munich = LatLon(48.14, 11.58)

    @Test
    fun `auto uses offline data only where it covers the position`() {
        assertEquals(RoadDataSource.OFFLINE, SelectingLoader.choose(SpeedLimitSource.AUTO, austria, vienna))
        assertEquals(RoadDataSource.ONLINE, SelectingLoader.choose(SpeedLimitSource.AUTO, austria, prague))
        assertEquals(RoadDataSource.ONLINE, SelectingLoader.choose(SpeedLimitSource.AUTO, null, vienna))
    }

    @Test
    fun `explicit setting wins`() {
        assertEquals(RoadDataSource.ONLINE, SelectingLoader.choose(SpeedLimitSource.ONLINE, austria, vienna))
        assertEquals(RoadDataSource.OFFLINE, SelectingLoader.choose(SpeedLimitSource.OFFLINE, null, prague))
    }

    /** A short road through [at]. */
    private fun roadAt(at: LatLon) = RoadNetwork(
        listOf(RoadWay(1, listOf(1, 2), "primary")),
        mapOf(1L to at, 2L to Geo.destination(at, 0.0, 100.0)),
    )

    @Test
    fun `selecting loader delegates to the chosen loader`() = runTest {
        val online = CorridorLoader { LoadedRoads(RoadNetwork.EMPTY, RoadDataSource.ONLINE) }
        val offline = CorridorLoader { LoadedRoads(roadAt(vienna), RoadDataSource.OFFLINE) }
        val loader = SelectingLoader({ SpeedLimitSource.AUTO }, { austria }, online, offline)
        assertEquals(RoadDataSource.OFFLINE, loader.load(CorridorRequest(vienna, listOf(vienna), 250)).source)
        assertEquals(RoadDataSource.ONLINE, loader.load(CorridorRequest(prague, listOf(prague), 250)).source)
    }

    @Test
    fun `auto falls back to online inside the bounding box but outside the extract`() = runTest {
        val online = CorridorLoader { LoadedRoads(RoadNetwork.EMPTY, RoadDataSource.ONLINE) }
        // The extract has no roads around Munich
        val offline = CorridorLoader { LoadedRoads(roadAt(vienna), RoadDataSource.OFFLINE) }
        val auto = SelectingLoader({ SpeedLimitSource.AUTO }, { austria }, online, offline)
        assertEquals(RoadDataSource.ONLINE, auto.load(CorridorRequest(munich, listOf(munich), 250)).source)
        val forcedOffline = SelectingLoader({ SpeedLimitSource.OFFLINE }, { austria }, online, offline)
        assertEquals(RoadDataSource.OFFLINE, forcedOffline.load(CorridorRequest(munich, listOf(munich), 250)).source)
    }

    @Test
    fun `road near position`() {
        val network = roadAt(vienna)
        assertTrue(network.hasRoadNear(Geo.destination(vienna, 90.0, 30.0), 50.0))
        assertFalse(network.hasRoadNear(Geo.destination(vienna, 90.0, 80.0), 50.0))
    }

    @Test
    fun `corridor bounds are widened by the radius`() {
        val a = TestRoads.at(0.0, 0.0)
        val b = TestRoads.at(0.0, 1_000.0)
        val box = corridorBounds(listOf(a, b), radiusM = 250)
        assertEquals(250.0, Geo.distanceM(LatLon(box.minLat, a.lon), a), 1.0)
        assertEquals(250.0, Geo.distanceM(LatLon(b.lat, box.maxLon), b), 1.0)
        assertTrue(box.contains(a.lat, a.lon) && box.contains(b.lat, b.lon))
    }

    @Test
    fun `stored ways become the same network as online data`() {
        fun stored(id: Long, tags: Map<String, String>, vararg nodes: Pair<Long, LatLon>) = StoredWay(
            id, tags,
            LongArray(nodes.size) { nodes[it].first },
            IntArray(nodes.size) { Math.round(nodes[it].second.lat * 1e7).toInt() },
            IntArray(nodes.size) { Math.round(nodes[it].second.lon * 1e7).toInt() },
        )
        val p1 = TestRoads.at(0.0, 0.0)
        val p2 = TestRoads.at(0.0, 500.0)
        val p3 = TestRoads.at(0.0, 1_000.0)
        val network = roadNetworkOf(
            listOf(
                stored(1, mapOf("highway" to "primary", "maxspeed" to "100"), 1L to p1, 2L to p2),
                stored(2, mapOf("highway" to "primary", "maxspeed" to "70", "oneway" to "yes"), 2L to p2, 3L to p3),
            ),
        )
        assertEquals(Maxspeed.Limit(70), network.ways.getValue(2).maxspeedForward)
        assertEquals(Oneway.FORWARD, network.ways.getValue(2).oneway)
        assertEquals(2, network.waysAt(2).size)

        // Same look-ahead result as with the online test data
        val match = MapMatcher().match(network, TestRoads.at(0.0, 100.0), bearingDeg = 0.0)!!
        val event = LookAhead().nextLowerLimit(network, match, speedKmh = 100.0)!!
        assertEquals(70, event.limitKmh)
        assertEquals(400.0, event.distanceM, 0.5)
    }
}
