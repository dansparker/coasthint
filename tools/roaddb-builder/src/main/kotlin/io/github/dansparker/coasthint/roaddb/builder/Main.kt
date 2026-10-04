package io.github.dansparker.coasthint.roaddb.builder

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.system.exitProcess

private const val USAGE = """Usage: roaddb-builder <input.osm.pbf> <output.db> [--force]

Converts an OpenStreetMap extract (e.g. https://download.geofabrik.de/europe/austria.html)
into the offline road database for CoastHint. --force overwrites an existing output file."""

fun main(args: Array<String>) {
    val positional = args.filter { !it.startsWith("--") }
    if (positional.size != 2) {
        System.err.println(USAGE)
        exitProcess(2)
    }
    val input = File(positional[0])
    val output = File(positional[1])
    if (!input.isFile) fail("Input not found: $input")
    if (output.exists() && "--force" !in args) fail("Output exists: $output (use --force to overwrite)")

    val started = System.nanoTime()
    // Build into a temporary file so an aborted run never leaves a broken database behind.
    val temp = File(output.absoluteFile.parentFile, output.name + ".tmp").apply { delete() }
    val stats = BundledSQLiteDriver().open(temp.path).use { connection ->
        RoadDbBuilder(log = ::println).build(
            source = PbfOsmSource(input),
            connection = connection,
            sourceName = input.name,
            created = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
        )
    }
    if (output.exists() && !output.delete()) fail("Cannot replace $output")
    if (!temp.renameTo(output)) fail("Cannot rename $temp to $output")
    val seconds = (System.nanoTime() - started) / 1e9
    println(
        "Done in %.0f s: %d ways, %d nodes, %.1f MB -> %s".format(
            seconds, stats.waysWritten, stats.nodes, output.length() / 1e6, output,
        ),
    )
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
