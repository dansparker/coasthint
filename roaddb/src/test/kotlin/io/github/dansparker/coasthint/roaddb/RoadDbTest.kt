package io.github.dansparker.coasthint.roaddb

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RoadDbTest {
    private val driver = BundledSQLiteDriver()

    /** A short way going north from (lat, lon), ~110 m long. */
    private fun way(id: Long, lat: Double, lon: Double, tags: Map<String, String> = mapOf("highway" to "primary")) =
        StoredWay(
            id = id,
            tags = tags,
            nodeIds = longArrayOf(id * 10, id * 10 + 1),
            latE7 = intArrayOf((lat * 1e7).toInt(), ((lat + 0.001) * 1e7).toInt()),
            lonE7 = intArrayOf((lon * 1e7).toInt(), (lon * 1e7).toInt()),
        )

    private fun build(file: File, ways: List<StoredWay>) {
        driver.open(file.path).use { connection ->
            val writer = RoadDbWriter(connection)
            ways.forEach(writer::add)
            writer.finish(source = "test.osm.pbf", created = "2026-10-05T12:00:00Z")
        }
    }

    @Test
    fun `writes and reads back ways with tags and geometry`(@TempDir dir: File) {
        val file = File(dir, "roads.db")
        val original = way(
            1, 48.2, 16.37,
            mapOf("highway" to "primary", "maxspeed" to "70", "maxspeed:backward" to "50", "name" to "Wiener Straße"),
        )
        build(file, listOf(original, way(2, 47.0, 15.0)))

        driver.open(file.path).use { connection ->
            val reader = RoadDbReader(connection)
            val found = reader.waysIn(Bounds(48.19, 16.36, 48.21, 16.38))
            assertEquals(listOf(1L), found.map { it.id })
            val way = found.single()
            assertEquals(original.tags, way.tags)
            assertArrayEquals(original.nodeIds, way.nodeIds)
            assertArrayEquals(original.latE7, way.latE7)
            assertArrayEquals(original.lonE7, way.lonE7)
        }
    }

    @Test
    fun `info describes the file`(@TempDir dir: File) {
        val file = File(dir, "roads.db")
        build(file, listOf(way(1, 48.2, 16.37), way(2, 47.0, 15.0)))
        driver.open(file.path).use { connection ->
            val info = RoadDbReader(connection).info()
            assertEquals(RoadDbFormat.VERSION, info.formatVersion)
            assertEquals("test.osm.pbf", info.source)
            assertEquals(2, info.wayCount)
            assertEquals(47.0, info.bounds.minLat, 1e-7)
            assertEquals(48.201, info.bounds.maxLat, 1e-7)
            assertEquals(15.0, info.bounds.minLon, 1e-7)
            assertEquals(16.37, info.bounds.maxLon, 1e-7)
        }
    }

    @Test
    fun `query box finds ways that only cross it`(@TempDir dir: File) {
        val file = File(dir, "roads.db")
        // From 48.000 to 48.001 north; box covers only its upper half
        build(file, listOf(way(1, 48.0, 16.0)))
        driver.open(file.path).use { connection ->
            val reader = RoadDbReader(connection)
            assertEquals(1, reader.waysIn(Bounds(48.0005, 15.999, 48.01, 16.001)).size)
            assertEquals(0, reader.waysIn(Bounds(48.002, 15.999, 48.01, 16.001)).size)
        }
    }

    @Test
    fun `many ways are found by their area`(@TempDir dir: File) {
        val file = File(dir, "roads.db")
        // 100 × 100 grid of short ways, 0.01° apart
        val ways = (0 until 100).flatMap { i -> (0 until 100).map { j -> way(i * 100L + j + 1, 47.0 + i * 0.01, 14.0 + j * 0.01) } }
        build(file, ways)
        driver.open(file.path).use { connection ->
            val found = RoadDbReader(connection).waysIn(Bounds(47.495, 14.495, 47.525, 14.525))
            // rows 50..52, columns 50..52
            assertEquals(9, found.size)
        }
    }

    @Test
    fun `other sqlite files are rejected`(@TempDir dir: File) {
        val file = File(dir, "other.db")
        driver.open(file.path).use { it.execSQL("CREATE TABLE something(x INTEGER)") }
        driver.open(file.path).use { connection ->
            assertThrows(RoadDbException::class.java) { RoadDbReader(connection).info() }
        }
    }
}
