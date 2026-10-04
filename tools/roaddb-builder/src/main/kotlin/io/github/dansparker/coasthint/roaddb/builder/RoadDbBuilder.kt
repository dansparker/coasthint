package io.github.dansparker.coasthint.roaddb.builder

import androidx.sqlite.SQLiteConnection
import io.github.dansparker.coasthint.roaddb.RoadDbFormat
import io.github.dansparker.coasthint.roaddb.RoadDbWriter
import io.github.dansparker.coasthint.roaddb.StoredWay
import kotlin.math.roundToLong

data class BuildStats(val waysWritten: Int, val waysSkipped: Int, val nodes: Int)

/**
 * Converts OSM data into a road database in two passes: first the drivable ways and the ids of
 * their nodes, then only the coordinates of those nodes. Ways with nodes missing from the
 * extract are skipped.
 */
class RoadDbBuilder(private val log: (String) -> Unit = {}) {

    /** Tag values in the order of [RoadDbFormat.TAG_KEYS]; far lighter than a map for millions of ways. */
    private class CollectedWay(val id: Long, val tagValues: Array<String?>, val nodeIds: LongArray) {
        fun tags(): Map<String, String> = buildMap {
            tagValues.forEachIndexed { i, v -> if (v != null) put(RoadDbFormat.TAG_KEYS[i], v) }
        }
    }

    fun build(source: OsmSource, connection: SQLiteConnection, sourceName: String, created: String): BuildStats {
        val ways = ArrayList<CollectedWay>()
        // Values like "50" or "primary" repeat millions of times; keep one instance of each.
        val strings = HashMap<String, String>()
        source.readWays(RoadDbFormat.TAG_KEYS.toSet()) { id, tags, nodeIds ->
            if (tags["highway"] in RoadDbFormat.DRIVABLE_HIGHWAYS && nodeIds.size >= 2) {
                val values = Array(RoadDbFormat.TAG_KEYS.size) { i ->
                    tags[RoadDbFormat.TAG_KEYS[i]]?.let { strings.getOrPut(it) { it } }
                }
                ways += CollectedWay(id, values, nodeIds)
            }
        }
        log("Pass 1: ${ways.size} drivable ways")

        val nodeIds = uniqueSorted(ways)
        val lat = IntArray(nodeIds.size)
        val lon = IntArray(nodeIds.size)
        val found = BooleanArray(nodeIds.size)
        source.readNodes { id, nodeLat, nodeLon ->
            val i = nodeIds.binarySearch(id)
            if (i >= 0) {
                lat[i] = toE7(nodeLat)
                lon[i] = toE7(nodeLon)
                found[i] = true
            }
        }
        log("Pass 2: ${found.count { it }} of ${nodeIds.size} nodes found")

        val writer = RoadDbWriter(connection)
        var written = 0
        var skipped = 0
        for (way in ways) {
            val indices = IntArray(way.nodeIds.size) { nodeIds.binarySearch(way.nodeIds[it]) }
            if (indices.any { !found[it] }) {
                skipped++
                continue
            }
            writer.add(
                StoredWay(
                    id = way.id,
                    tags = way.tags(),
                    nodeIds = way.nodeIds,
                    latE7 = IntArray(indices.size) { lat[indices[it]] },
                    lonE7 = IntArray(indices.size) { lon[indices[it]] },
                ),
            )
            written++
        }
        writer.finish(sourceName, created)
        log("Written: $written ways, skipped (incomplete): $skipped")
        return BuildStats(written, skipped, nodeIds.size)
    }

    private fun uniqueSorted(ways: List<CollectedWay>): LongArray {
        val all = LongArray(ways.sumOf { it.nodeIds.size })
        var pos = 0
        for (way in ways) {
            way.nodeIds.copyInto(all, pos)
            pos += way.nodeIds.size
        }
        all.sort()
        var unique = 0
        for (i in all.indices) {
            if (i == 0 || all[i] != all[i - 1]) all[unique++] = all[i]
        }
        return all.copyOf(unique)
    }

    private fun toE7(degrees: Double): Int = (degrees * StoredWay.E7).roundToLong().toInt()
}
