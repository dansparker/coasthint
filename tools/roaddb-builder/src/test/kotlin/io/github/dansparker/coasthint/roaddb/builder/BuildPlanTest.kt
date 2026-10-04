package io.github.dansparker.coasthint.roaddb.builder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BuildPlanTest {

    @Test
    fun `folder input converts every extract into a roaddb subfolder`(@TempDir dir: File) {
        File(dir, "austria-latest.osm.pbf").writeText("x")
        File(dir, "Germany-latest.osm.pbf").writeText("x")
        File(dir, "notes.txt").writeText("x")
        val jobs = BuildPlan.jobs(dir, output = null)
        assertEquals(listOf("austria-latest.osm.pbf", "Germany-latest.osm.pbf"), jobs.map { it.input.name })
        assertEquals(listOf(File(dir, "roaddb/austria.db"), File(dir, "roaddb/germany.db")), jobs.map { it.output })
    }

    @Test
    fun `folder input with explicit output folder`(@TempDir dir: File) {
        File(dir, "austria-latest.osm.pbf").writeText("x")
        val out = File(dir, "out")
        assertEquals(File(out, "austria.db"), BuildPlan.jobs(dir, out).single().output)
    }

    @Test
    fun `single file defaults to a database next to it`(@TempDir dir: File) {
        val input = File(dir, "liechtenstein-latest.osm.pbf").apply { writeText("x") }
        assertEquals(File(dir, "liechtenstein.db"), BuildPlan.jobs(input, output = null).single().output)
        val explicit = File(dir, "li.db")
        assertEquals(explicit, BuildPlan.jobs(input, explicit).single().output)
    }

    @Test
    fun `output newer than its extract is up to date`(@TempDir dir: File) {
        val input = File(dir, "austria-latest.osm.pbf").apply { writeText("x") }
        val output = File(dir, "roaddb/austria.db")
        assertFalse(BuildPlan.jobs(dir, null).single().upToDate)

        output.parentFile.mkdirs()
        output.writeText("db")
        input.setLastModified(1_000_000_000_000)
        output.setLastModified(1_000_000_100_000)
        assertTrue(BuildPlan.jobs(dir, null).single().upToDate)

        // A newer extract was downloaded
        input.setLastModified(1_000_000_200_000)
        assertFalse(BuildPlan.jobs(dir, null).single().upToDate)
    }
}
