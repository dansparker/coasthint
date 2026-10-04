package io.github.dansparker.coasthint.speedlimit

import org.json.JSONArray
import org.json.JSONObject

/** Builds small road networks in metres east (x) / north (y) of a fixed origin. */
object TestRoads {
    val ORIGIN = LatLon(48.2, 16.37)

    fun at(x: Double, y: Double): LatLon = Geo.destination(Geo.destination(ORIGIN, 90.0, x), 0.0, y)

    class Builder {
        private val nodes = linkedMapOf<Long, LatLon>()
        private val ways = mutableListOf<Pair<RoadWay, Map<String, String>>>()

        fun node(id: Long, x: Double, y: Double) = apply { nodes[id] = at(x, y) }

        fun way(id: Long, vararg nodeIds: Long, tags: Map<String, String> = emptyMap()) = apply {
            val allTags = mapOf("highway" to "primary") + tags
            ways += RoadWay.fromTags(id, nodeIds.toList(), allTags) to allTags
        }

        fun way(id: Long, vararg nodeIds: Long, maxspeed: Int?, oneway: Boolean = false) = way(
            id,
            *nodeIds,
            tags = buildMap {
                maxspeed?.let { put("maxspeed", it.toString()) }
                if (oneway) put("oneway", "yes")
            },
        )

        fun build() = RoadNetwork(ways.map { it.first }, nodes)

        /** The same network as an Overpass JSON answer. */
        fun overpassJson(): String {
            val elements = JSONArray()
            ways.forEach { (way, tags) ->
                elements.put(
                    JSONObject()
                        .put("type", "way")
                        .put("id", way.id)
                        .put("nodes", JSONArray(way.nodeIds))
                        .put("tags", JSONObject(tags)),
                )
            }
            nodes.forEach { (id, p) ->
                elements.put(JSONObject().put("type", "node").put("id", id).put("lat", p.lat).put("lon", p.lon))
            }
            return JSONObject().put("elements", elements).toString()
        }
    }
}
