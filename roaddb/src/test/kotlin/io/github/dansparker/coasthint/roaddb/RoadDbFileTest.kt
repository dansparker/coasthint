package io.github.dansparker.coasthint.roaddb

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RoadDbFileTest {
    private val driver = BundledSQLiteDriver()

    private fun database(dir: File, name: String, wayId: Long, source: String): File {
        val file = File(dir, name)
        driver.open(file.path).use { connection ->
            val writer = RoadDbWriter(connection)
            writer.add(
                StoredWay(
                    wayId, mapOf("highway" to "primary"), longArrayOf(1, 2),
                    intArrayOf(480_000_000, 480_010_000), intArrayOf(160_000_000, 160_000_000),
                ),
            )
            writer.finish(source, "2026-10-05T12:00:00Z")
        }
        return file
    }

    private fun installed(dir: File) =
        RoadDbFile(File(dir, "installed/roads.db")) { driver.open(it, SQLITE_OPEN_READONLY) }

    private val everywhere = Bounds(-90.0, -180.0, 90.0, 180.0)

    @Test
    fun `nothing installed`(@TempDir dir: File) {
        val db = installed(dir)
        assertNull(db.info())
        assertThrows(RoadDbException::class.java) { db.waysIn(everywhere) }
    }

    @Test
    fun `import installs and opens the database`(@TempDir dir: File) {
        val db = installed(dir)
        val info = database(dir, "a.db", wayId = 1, source = "a.osm.pbf").inputStream().use(db::replaceWith)
        assertEquals("a.osm.pbf", info.source)
        assertEquals("a.osm.pbf", db.info()?.source)
        assertEquals(listOf(1L), db.waysIn(everywhere).map { it.id })
        db.close()
    }

    @Test
    fun `import replaces an open database`(@TempDir dir: File) {
        val db = installed(dir)
        database(dir, "a.db", wayId = 1, source = "a.osm.pbf").inputStream().use(db::replaceWith)
        assertEquals(listOf(1L), db.waysIn(everywhere).map { it.id })
        database(dir, "b.db", wayId = 2, source = "b.osm.pbf").inputStream().use(db::replaceWith)
        assertEquals(listOf(2L), db.waysIn(everywhere).map { it.id })
        assertEquals("b.osm.pbf", db.info()?.source)
        db.close()
    }

    @Test
    fun `invalid import keeps the current database`(@TempDir dir: File) {
        val db = installed(dir)
        database(dir, "a.db", wayId = 1, source = "a.osm.pbf").inputStream().use(db::replaceWith)
        assertThrows(Exception::class.java) { "not a database".byteInputStream().use(db::replaceWith) }
        assertEquals("a.osm.pbf", db.info()?.source)
        assertFalse(File(dir, "installed/roads.db.import").exists())
        db.close()
    }

    @Test
    fun `delete removes the database`(@TempDir dir: File) {
        val db = installed(dir)
        database(dir, "a.db", wayId = 1, source = "a.osm.pbf").inputStream().use(db::replaceWith)
        db.info()
        db.delete()
        assertNull(db.info())
    }
}
