package io.github.dansparker.coasthint.roaddb.builder

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.dansparker.coasthint.roaddb.Bounds
import io.github.dansparker.coasthint.roaddb.RoadDbReader
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RoadDbBuilderTest {
    private val driver = BundledSQLiteDriver()

    /** OSM data kept in memory. */
    private class MemorySource(
        val ways: List<Triple<Long, Map<String, String>, LongArray>>,
        val nodes: Map<Long, Pair<Double, Double>>,
    ) : OsmSource {
        override fun readWays(wantedKeys: Set<String>, handler: (Long, Map<String, String>, LongArray) -> Unit) {
            ways.forEach { (id, tags, nodeIds) ->
                val kept = tags.filterKeys { it in wantedKeys }
                if (kept.isNotEmpty()) handler(id, kept, nodeIds)
            }
        }

        override fun readNodes(handler: (Long, Double, Double) -> Unit) {
            nodes.forEach { (id, p) -> handler(id, p.first, p.second) }
        }
    }

    private val source = MemorySource(
        ways = listOf(
            Triple(1L, mapOf("highway" to "primary", "maxspeed" to "70", "surface" to "asphalt"), longArrayOf(10, 11, 12)),
            Triple(2L, mapOf("highway" to "residential", "maxspeed" to "30"), longArrayOf(12, 13)),
            Triple(3L, mapOf("highway" to "footway"), longArrayOf(13, 14)),
            Triple(4L, mapOf("building" to "yes"), longArrayOf(14, 15, 16, 14)),
            Triple(5L, mapOf("highway" to "secondary"), longArrayOf(13, 99)), // node 99 not in extract
        ),
        nodes = mapOf(
            10L to (48.2000000 to 16.3700000),
            11L to (48.2010000 to 16.3700001),
            12L to (48.2020000 to 16.3700002),
            13L to (48.2030000 to 16.3710000),
            14L to (48.2040000 to 16.3720000),
            15L to (48.2050000 to 16.3730000),
            16L to (48.2060000 to 16.3740000),
        ),
    )

    private fun build(dir: File): Pair<File, BuildStats> {
        val file = File(dir, "roads.db")
        val stats = driver.open(file.path).use { RoadDbBuilder().build(source, it, "test.osm.pbf", "2026-10-05T12:00:00Z") }
        return file to stats
    }

    @Test
    fun `keeps only complete drivable ways`(@TempDir dir: File) {
        val (file, stats) = build(dir)
        assertEquals(BuildStats(waysWritten = 2, waysSkipped = 1, nodes = 5), stats)
        driver.open(file.path).use { connection ->
            val ways = RoadDbReader(connection).waysIn(Bounds(48.0, 16.0, 49.0, 17.0))
            assertEquals(listOf(1L, 2L), ways.map { it.id }.sorted())
        }
    }

    @Test
    fun `stores the kept tags and exact coordinates`(@TempDir dir: File) {
        val (file, _) = build(dir)
        driver.open(file.path).use { connection ->
            val way = RoadDbReader(connection).waysIn(Bounds(48.0, 16.0, 49.0, 17.0)).single { it.id == 1L }
            assertEquals(mapOf("highway" to "primary", "maxspeed" to "70"), way.tags)
            assertArrayEquals(longArrayOf(10, 11, 12), way.nodeIds)
            assertArrayEquals(intArrayOf(482_000_000, 482_010_000, 482_020_000), way.latE7)
            assertArrayEquals(intArrayOf(163_700_000, 163_700_001, 163_700_002), way.lonE7)
        }
    }

    @Test
    fun `shared nodes keep their ids so ways stay connected`(@TempDir dir: File) {
        val (file, _) = build(dir)
        driver.open(file.path).use { connection ->
            val ways = RoadDbReader(connection).waysIn(Bounds(48.0, 16.0, 49.0, 17.0)).associateBy { it.id }
            assertEquals(ways.getValue(1).nodeIds.last(), ways.getValue(2).nodeIds.first())
        }
    }

    @Test
    fun `info reflects the build`(@TempDir dir: File) {
        val (file, _) = build(dir)
        driver.open(file.path).use { connection ->
            val info = RoadDbReader(connection).info()
            assertEquals(2, info.wayCount)
            assertEquals("test.osm.pbf", info.source)
            assertEquals(48.2, info.bounds.minLat, 1e-9)
            assertEquals(48.203, info.bounds.maxLat, 1e-9)
        }
    }
}
