package io.github.dansparker.coasthint.speedlimit

enum class Oneway { NO, FORWARD, BACKWARD }

/** A drivable OSM way. "Forward" means in the order of [nodeIds]. */
data class RoadWay(
    val id: Long,
    val nodeIds: List<Long>,
    val highway: String,
    val oneway: Oneway = Oneway.NO,
    val maxspeedForward: Maxspeed? = null,
    val maxspeedBackward: Maxspeed? = null,
    val name: String? = null,
) {
    fun maxspeed(forward: Boolean): Maxspeed? = if (forward) maxspeedForward else maxspeedBackward

    fun allows(forward: Boolean): Boolean = when (oneway) {
        Oneway.NO -> true
        Oneway.FORWARD -> forward
        Oneway.BACKWARD -> !forward
    }

    companion object {
        private val ONEWAY_YES = setOf("yes", "true", "1")
        private val ONEWAY_REVERSE = setOf("-1", "reverse")
        private val ROUNDABOUT = setOf("roundabout", "circular")

        fun fromTags(id: Long, nodeIds: List<Long>, tags: Map<String, String>): RoadWay {
            val maxspeed = MaxspeedParser.parse(tags["maxspeed"])
            val onewayTag = tags["oneway"]
            val oneway = when {
                onewayTag in ONEWAY_YES -> Oneway.FORWARD
                onewayTag in ONEWAY_REVERSE -> Oneway.BACKWARD
                onewayTag == "no" -> Oneway.NO
                tags["junction"] in ROUNDABOUT || tags["highway"] == "motorway" -> Oneway.FORWARD
                else -> Oneway.NO
            }
            return RoadWay(
                id = id,
                nodeIds = nodeIds,
                highway = tags["highway"].orEmpty(),
                oneway = oneway,
                maxspeedForward = MaxspeedParser.parse(tags["maxspeed:forward"]) ?: maxspeed,
                maxspeedBackward = MaxspeedParser.parse(tags["maxspeed:backward"]) ?: maxspeed,
                name = tags["name"] ?: tags["ref"],
            )
        }
    }
}

/** A way passing through a node, with the node's position in [RoadWay.nodeIds]. */
data class WayAtNode(val way: RoadWay, val index: Int)

/** Drivable roads with node positions and node → way connectivity. */
class RoadNetwork(ways: Collection<RoadWay>, nodes: Map<Long, LatLon>) {
    val ways: Map<Long, RoadWay> = ways
        .filter { way -> way.nodeIds.size >= 2 && way.nodeIds.all { it in nodes } }
        .associateBy { it.id }
    val nodes: Map<Long, LatLon> = nodes

    private val waysAtNode: Map<Long, List<WayAtNode>> = buildMap<Long, MutableList<WayAtNode>> {
        for (way in this@RoadNetwork.ways.values) {
            way.nodeIds.forEachIndexed { index, nodeId ->
                getOrPut(nodeId) { mutableListOf() }.add(WayAtNode(way, index))
            }
        }
    }

    fun position(nodeId: Long): LatLon = nodes.getValue(nodeId)

    fun waysAt(nodeId: Long): List<WayAtNode> = waysAtNode[nodeId].orEmpty()

    val isEmpty: Boolean get() = ways.isEmpty()

    companion object {
        val EMPTY = RoadNetwork(emptyList(), emptyMap())

        /** Union of several networks; later ones win for duplicate ids. */
        fun merge(networks: List<RoadNetwork>): RoadNetwork = RoadNetwork(
            networks.flatMap { it.ways.values }.associateBy { it.id }.values,
            networks.fold(emptyMap()) { acc, n -> acc + n.nodes },
        )
    }
}
