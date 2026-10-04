package io.github.dansparker.coasthint.speedlimit

import org.json.JSONObject
import java.util.Locale

/** Builds Overpass queries for the road corridor ahead and parses the JSON answer. */
object OverpassQuery {
    /**
     * All drivable roads, not only those with `maxspeed`: roads without a limit are needed
     * to follow the road ahead across gaps.
     */
    private const val HIGHWAY_FILTER =
        "^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|" +
            "motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$"

    /** Points along the expected path: a little behind, then straight ahead in travel direction. */
    fun corridor(
        position: LatLon,
        bearingDeg: Double?,
        behindM: Double = 100.0,
        aheadM: Double = 1_500.0,
        stepM: Double = 300.0,
    ): List<LatLon> {
        if (bearingDeg == null) return listOf(position)
        val distances = generateSequence(-behindM) { it + stepM }.takeWhile { it < aheadM } + aheadM
        return distances.map { d ->
            if (d < 0) Geo.destination(position, bearingDeg + 180, -d) else Geo.destination(position, bearingDeg, d)
        }.toList()
    }

    fun build(corridor: List<LatLon>, radiusM: Int): String {
        val points = corridor.joinToString(",") { String.format(Locale.ROOT, "%.6f,%.6f", it.lat, it.lon) }
        return "[out:json][timeout:25];" +
            "way(around:$radiusM,$points)[highway~\"$HIGHWAY_FILTER\"];" +
            "(._;>;);out body qt;"
    }

    fun parse(json: String): RoadNetwork {
        val elements = JSONObject(json).getJSONArray("elements")
        val nodes = HashMap<Long, LatLon>()
        val ways = ArrayList<RoadWay>()
        for (i in 0 until elements.length()) {
            val e = elements.getJSONObject(i)
            when (e.getString("type")) {
                "node" -> nodes[e.getLong("id")] = LatLon(e.getDouble("lat"), e.getDouble("lon"))
                "way" -> {
                    val nodeArray = e.getJSONArray("nodes")
                    val nodeIds = List(nodeArray.length()) { nodeArray.getLong(it) }
                    val tagObject = e.optJSONObject("tags")
                    val tags = tagObject?.keys()?.asSequence()?.associateWith { tagObject.getString(it) }.orEmpty()
                    ways += RoadWay.fromTags(e.getLong("id"), nodeIds, tags)
                }
            }
        }
        return RoadNetwork(ways, nodes)
    }
}
