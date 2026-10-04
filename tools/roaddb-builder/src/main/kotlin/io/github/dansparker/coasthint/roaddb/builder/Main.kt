package io.github.dansparker.coasthint.roaddb.builder

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.system.exitProcess

private const val USAGE = """Usage: roaddb-builder <input> [output] [--force]

Converts OpenStreetMap extracts (e.g. from https://download.geofabrik.de) into offline road
databases for CoastHint.

  input    an .osm.pbf file, or a folder with .osm.pbf files (one per country/region)
  output   for a file: the database file (default: <region>.db next to the input)
           for a folder: the output folder (default: <input>/roaddb)
  --force  rebuild even if a database is newer than its extract

Large countries need more memory; set e.g. ROADDB_BUILDER_OPTS=-Xmx8g before starting."""

fun main(args: Array<String>) {
    val positional = args.filter { !it.startsWith("--") }
    if (positional.size !in 1..2 || "--help" in args) {
        System.err.println(USAGE)
        exitProcess(2)
    }
    val input = File(positional[0])
    if (!input.exists()) fail("Input not found: $input")
    val jobs = BuildPlan.jobs(input, positional.getOrNull(1)?.let(::File))
    if (jobs.isEmpty()) fail("No .osm.pbf files in $input")

    val force = "--force" in args
    var failed = 0
    for (job in jobs) {
        if (job.upToDate && !force) {
            println("${job.input.name}: up to date (${job.output.name})")
            continue
        }
        println("${job.input.name} -> ${job.output}")
        try {
            convert(job)
        } catch (e: Exception) {
            failed++
            System.err.println("${job.input.name}: FAILED: ${e.message ?: e}")
        } catch (e: OutOfMemoryError) {
            failed++
            System.err.println("${job.input.name}: out of memory, set ROADDB_BUILDER_OPTS=-Xmx8g (or more)")
        }
    }
    if (failed > 0) fail("$failed of ${jobs.size} conversions failed")
}

private fun convert(job: BuildJob) {
    val started = System.nanoTime()
    job.output.absoluteFile.parentFile.mkdirs()
    // Build into a temporary file so an aborted run never leaves a broken database behind.
    val temp = File(job.output.absoluteFile.parentFile, job.output.name + ".tmp").apply { delete() }
    try {
        val stats = BundledSQLiteDriver().open(temp.path).use { connection ->
            RoadDbBuilder(log = { println("  $it") }).build(
                source = PbfOsmSource(job.input),
                connection = connection,
                sourceName = job.input.name,
                created = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            )
        }
        if (job.output.exists() && !job.output.delete()) error("cannot replace ${job.output}")
        if (!temp.renameTo(job.output)) error("cannot rename $temp")
        val seconds = (System.nanoTime() - started) / 1e9
        println(
            "  Done in %.0f s: %d ways, %d nodes, %.1f MB".format(
                seconds, stats.waysWritten, stats.nodes, job.output.length() / 1e6,
            ),
        )
    } finally {
        temp.delete()
    }
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
