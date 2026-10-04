package io.github.dansparker.coasthint.roaddb.builder

import crosby.binary.BinaryParser
import crosby.binary.Osmformat
import crosby.binary.file.BlockInputStream
import java.io.File

/** OSM data that can be read twice: once for ways, once for nodes. */
interface OsmSource {
    /** Ways having at least one of [wantedKeys]; only those tags are passed on. */
    fun readWays(wantedKeys: Set<String>, handler: (id: Long, tags: Map<String, String>, nodeIds: LongArray) -> Unit)

    fun readNodes(handler: (id: Long, lat: Double, lon: Double) -> Unit)
}

/** Reads an `.osm.pbf` file. Each read is a full pass over the file. */
class PbfOsmSource(private val file: File) : OsmSource {

    override fun readWays(
        wantedKeys: Set<String>,
        handler: (id: Long, tags: Map<String, String>, nodeIds: LongArray) -> Unit,
    ) = parse(object : Parser() {
        override fun parseWays(ways: List<Osmformat.Way>) {
            for (way in ways) {
                var tags: MutableMap<String, String>? = null
                for (i in 0 until way.keysCount) {
                    val key = getStringById(way.getKeys(i))
                    if (key in wantedKeys) {
                        val map = tags ?: HashMap<String, String>().also { tags = it }
                        map[key] = getStringById(way.getVals(i))
                    }
                }
                val found = tags ?: continue
                // Node references are delta-coded.
                var ref = 0L
                val nodeIds = LongArray(way.refsCount) { i ->
                    ref += way.getRefs(i)
                    ref
                }
                handler(way.id, found, nodeIds)
            }
        }
    })

    override fun readNodes(handler: (id: Long, lat: Double, lon: Double) -> Unit) = parse(object : Parser() {
        override fun parseDense(nodes: Osmformat.DenseNodes) {
            // Ids and coordinates are delta-coded.
            var id = 0L
            var lat = 0L
            var lon = 0L
            for (i in 0 until nodes.idCount) {
                id += nodes.getId(i)
                lat += nodes.getLat(i)
                lon += nodes.getLon(i)
                handler(id, parseLat(lat), parseLon(lon))
            }
        }

        override fun parseNodes(nodes: List<Osmformat.Node>) {
            for (node in nodes) handler(node.id, parseLat(node.lat), parseLon(node.lon))
        }
    })

    private fun parse(parser: Parser) {
        file.inputStream().buffered().use { BlockInputStream(it, parser).process() }
    }

    private abstract class Parser : BinaryParser() {
        override fun parseRelations(relations: List<Osmformat.Relation>) = Unit
        override fun parseDense(nodes: Osmformat.DenseNodes) = Unit
        override fun parseNodes(nodes: List<Osmformat.Node>) = Unit
        override fun parseWays(ways: List<Osmformat.Way>) = Unit
        override fun parse(header: Osmformat.HeaderBlock) = Unit
        override fun complete() = Unit
    }
}
