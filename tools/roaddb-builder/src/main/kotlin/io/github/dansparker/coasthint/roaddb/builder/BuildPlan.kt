package io.github.dansparker.coasthint.roaddb.builder

import io.github.dansparker.coasthint.roaddb.RoadDbFormat
import java.io.File

/** One extract to convert. [upToDate]: the output exists and is newer than the input. */
data class BuildJob(val input: File, val output: File, val upToDate: Boolean)

object BuildPlan {
    private const val EXTENSION = ".osm.pbf"

    /**
     * Jobs for [input], which is either one `.osm.pbf` file or a folder of them.
     * Outputs are named after the region ("austria-latest.osm.pbf" → "austria.db"); by default
     * next to a single input file, or in a `roaddb` subfolder of an input folder.
     */
    fun jobs(input: File, output: File?): List<BuildJob> {
        val pairs = if (input.isDirectory) {
            val outDir = output ?: File(input, "roaddb")
            input.listFiles { f -> f.isFile && f.name.endsWith(EXTENSION, ignoreCase = true) }.orEmpty()
                .sortedBy { it.name.lowercase() }
                .map { it to File(outDir, outputName(it)) }
        } else {
            listOf(input to (output ?: File(input.absoluteFile.parentFile, outputName(input))))
        }
        return pairs.map { (i, o) -> BuildJob(i, o, o.isFile && o.lastModified() >= i.lastModified()) }
    }

    fun outputName(input: File) = RoadDbFormat.baseName(input.name) + ".db"
}
