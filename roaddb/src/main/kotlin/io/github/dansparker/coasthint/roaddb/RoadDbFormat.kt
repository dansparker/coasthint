package io.github.dansparker.coasthint.roaddb

/**
 * Offline road database: one SQLite file with the drivable OSM ways of a region.
 *
 * ```
 * meta(key TEXT PRIMARY KEY, value TEXT)
 * ways(id INTEGER PRIMARY KEY, <one TEXT column per tag in TAG_KEYS>, geometry BLOB)
 * way_index USING rtree(id, min_lat, max_lat, min_lon, max_lon)
 * ```
 * Only the raw tags are stored; interpreting them (one-way rules, maxspeed parsing) is left to
 * the app, so online and offline data go through exactly the same code.
 */
object RoadDbFormat {
    const val VERSION = 1

    /** Roads cars drive on; tracks, service roads and paths are left out. */
    val DRIVABLE_HIGHWAYS = setOf(
        "motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential",
        "living_street", "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link",
    )

    /** OSM tags kept per way. */
    val TAG_KEYS = listOf(
        "highway", "oneway", "junction", "maxspeed", "maxspeed:forward", "maxspeed:backward", "name", "ref",
    )

    internal fun column(tagKey: String) = "tag_" + tagKey.replace(':', '_')

    internal object Meta {
        const val FORMAT_VERSION = "format_version"
        const val SOURCE = "source"
        const val CREATED = "created"
        const val WAY_COUNT = "way_count"
        const val MIN_LAT = "min_lat"
        const val MIN_LON = "min_lon"
        const val MAX_LAT = "max_lat"
        const val MAX_LON = "max_lon"
    }

    internal val CREATE_STATEMENTS = listOf(
        "CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)",
        "CREATE TABLE ways(id INTEGER PRIMARY KEY, " +
            TAG_KEYS.joinToString("") { "${column(it)} TEXT, " } + "geometry BLOB NOT NULL)",
        "CREATE VIRTUAL TABLE way_index USING rtree(id, min_lat, max_lat, min_lon, max_lon)",
    )
}

/** Geographic bounding box in degrees. */
data class Bounds(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
    fun contains(lat: Double, lon: Double): Boolean = lat in minLat..maxLat && lon in minLon..maxLon

    companion object {
        fun around(lats: List<Double>, lons: List<Double>): Bounds =
            Bounds(lats.min(), lons.min(), lats.max(), lons.max())
    }
}

/** A drivable OSM way with its kept tags and node geometry (coordinates in 1e-7 degrees). */
class StoredWay(
    val id: Long,
    val tags: Map<String, String>,
    val nodeIds: LongArray,
    val latE7: IntArray,
    val lonE7: IntArray,
) {
    init {
        require(nodeIds.size == latE7.size && nodeIds.size == lonE7.size) { "geometry arrays differ in length" }
    }

    fun lat(index: Int): Double = latE7[index] / E7

    fun lon(index: Int): Double = lonE7[index] / E7

    val bounds: Bounds
        get() = Bounds(latE7.min() / E7, lonE7.min() / E7, latE7.max() / E7, lonE7.max() / E7)

    companion object {
        const val E7 = 1e7
    }
}

/** What a database file contains, from its meta table. */
data class RoadDbInfo(
    val formatVersion: Int,
    val source: String,
    val created: String,
    val wayCount: Int,
    val bounds: Bounds,
)
