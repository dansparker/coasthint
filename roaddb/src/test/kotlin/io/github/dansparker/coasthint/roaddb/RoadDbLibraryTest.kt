package io.github.dansparker.coasthint.roaddb

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RoadDbLibraryTest {
    private val driver = BundledSQLiteDriver()

    /** A database with one short way per (id, lat, lon). */
    private fun database(dir: File, source: String, vararg ways: Triple<Long, Double, Double>): File {
        val file = File(dir, "build-$source.db")
        driver.open(file.path).use { connection ->
            val writer = RoadDbWriter(connection)
            ways.forEach { (id, lat, lon) ->
                writer.add(
                    StoredWay(
                        id, mapOf("highway" to "primary"), longArrayOf(id * 10, id * 10 + 1),
                        intArrayOf((lat * 1e7).toInt(), ((lat + 0.001) * 1e7).toInt()),
                        intArrayOf((lon * 1e7).toInt(), (lon * 1e7).toInt()),
                    ),
                )
            }
            writer.finish(source, "2026-10-05T12:00:00Z")
        }
        return file
    }

    private fun library(dir: File) = RoadDbLibrary(File(dir, "installed")) { driver.open(it, SQLITE_OPEN_READONLY) }

    private val everywhere = Bounds(-90.0, -180.0, 90.0, 180.0)

    @Test
    fun `nothing installed`(@TempDir dir: File) {
        val lib = library(dir)
        assertEquals(emptyList<InstalledRoadDb>(), lib.installed())
        assertFalse(lib.covers(48.2, 16.37))
        assertThrows(RoadDbException::class.java) { lib.waysIn(everywhere) }
    }

    @Test
    fun `imports are named after the source region`(@TempDir dir: File) {
        val lib = library(dir)
        val name = database(dir, "austria-latest.osm.pbf", Triple(1L, 48.2, 16.37)).inputStream().use(lib::import)
        assertEquals("austria", name)
        val installed = lib.installed().single()
        assertEquals("austria", installed.name)
        assertEquals("austria-latest.osm.pbf", installed.info?.source)
        assertNull(installed.error)
        lib.close()
    }

    @Test
    fun `several regions are queried together and border ways appear once`(@TempDir dir: File) {
        val lib = library(dir)
        // Way 5 crosses the border and is in both extracts
        database(dir, "austria-latest.osm.pbf", Triple(1L, 47.6, 13.0), Triple(5L, 47.7, 12.9)).inputStream().use(lib::import)
        database(dir, "germany-latest.osm.pbf", Triple(2L, 47.8, 12.8), Triple(5L, 47.7, 12.9)).inputStream().use(lib::import)
        assertEquals(listOf("austria", "germany"), lib.installed().map { it.name })
        val ways = lib.waysIn(Bounds(47.5, 12.7, 47.9, 13.1))
        assertEquals(listOf(1L, 2L, 5L), ways.map { it.id }.sorted())
        lib.close()
    }

    @Test
    fun `importing the same region again replaces it`(@TempDir dir: File) {
        val lib = library(dir)
        database(dir, "austria-latest.osm.pbf", Triple(1L, 48.2, 16.37)).inputStream().use(lib::import)
        assertEquals(listOf(1L), lib.waysIn(everywhere).map { it.id })
        // Newer extract of the same region, built in another folder
        val newer = File(dir, "newer").apply { mkdirs() }
        database(newer, "austria-latest.osm.pbf", Triple(2L, 48.2, 16.37)).inputStream().use(lib::import)
        assertEquals(1, lib.installed().size)
        assertEquals(listOf(2L), lib.waysIn(everywhere).map { it.id })
        lib.close()
    }

    @Test
    fun `coverage uses the bounding boxes`(@TempDir dir: File) {
        val lib = library(dir)
        database(dir, "austria-latest.osm.pbf", Triple(1L, 47.0, 10.0), Triple(2L, 48.9, 17.0)).inputStream().use(lib::import)
        assertTrue(lib.covers(48.2, 16.37))
        assertFalse(lib.covers(50.08, 14.43))
        lib.close()
    }

    @Test
    fun `invalid import changes nothing`(@TempDir dir: File) {
        val lib = library(dir)
        database(dir, "austria-latest.osm.pbf", Triple(1L, 48.2, 16.37)).inputStream().use(lib::import)
        assertThrows(Exception::class.java) { "not a database".byteInputStream().use(lib::import) }
        assertEquals(listOf("austria"), lib.installed().map { it.name })
        assertFalse(File(dir, "installed/import.tmp").exists())
        lib.close()
    }

    @Test
    fun `broken files are listed with an error and skipped in queries`(@TempDir dir: File) {
        val lib = library(dir)
        database(dir, "austria-latest.osm.pbf", Triple(1L, 48.2, 16.37)).inputStream().use(lib::import)
        File(dir, "installed/broken.db").writeText("garbage")
        val broken = lib.installed().single { it.name == "broken" }
        assertNull(broken.info)
        assertNotNull(broken.error)
        assertEquals(listOf(1L), lib.waysIn(everywhere).map { it.id })
        lib.close()
    }

    @Test
    fun `delete removes one region`(@TempDir dir: File) {
        val lib = library(dir)
        database(dir, "austria-latest.osm.pbf", Triple(1L, 48.2, 16.37)).inputStream().use(lib::import)
        database(dir, "germany-latest.osm.pbf", Triple(2L, 48.1, 11.6)).inputStream().use(lib::import)
        lib.delete("austria")
        assertEquals(listOf("germany"), lib.installed().map { it.name })
        lib.close()
    }

    @Test
    fun `region names from extract file names`() {
        assertEquals("austria", RoadDbFormat.baseName("austria-latest.osm.pbf"))
        assertEquals("liechtenstein", RoadDbFormat.baseName("C:\\OSM\\liechtenstein-latest.osm.pbf"))
        assertEquals("bayern", RoadDbFormat.baseName("/data/bayern.osm.pbf"))
        assertEquals("baden-w-rttemberg", RoadDbFormat.baseName("Baden-Württemberg.osm.pbf"))
        assertEquals("roads", RoadDbFormat.baseName(".osm.pbf"))
    }
}
