package io.github.dansparker.coasthint.roaddb

import androidx.sqlite.SQLiteConnection

class RoadDbException(message: String) : Exception(message)

/** Reads a road database. Not thread-safe: use one reader per thread or guard it. */
class RoadDbReader(private val connection: SQLiteConnection) {
    private val columns = RoadDbFormat.TAG_KEYS.joinToString(", ") { "w." + RoadDbFormat.column(it) }

    /** Validates the file and returns its description. */
    fun info(): RoadDbInfo {
        val meta: Map<String, String> = try {
            connection.prepare("SELECT key, value FROM meta").use { stmt ->
                buildMap { while (stmt.step()) put(stmt.getText(0), stmt.getText(1)) }
            }
        } catch (e: Exception) {
            throw RoadDbException("not a CoastHint road database: ${e.message}")
        }
        fun value(key: String) = meta[key] ?: throw RoadDbException("missing meta '$key'")
        val version = value(RoadDbFormat.Meta.FORMAT_VERSION).toIntOrNull()
        if (version != RoadDbFormat.VERSION) throw RoadDbException("unsupported format version $version")
        return RoadDbInfo(
            formatVersion = version,
            source = value(RoadDbFormat.Meta.SOURCE),
            created = value(RoadDbFormat.Meta.CREATED),
            wayCount = value(RoadDbFormat.Meta.WAY_COUNT).toInt(),
            bounds = Bounds(
                value(RoadDbFormat.Meta.MIN_LAT).toDouble(),
                value(RoadDbFormat.Meta.MIN_LON).toDouble(),
                value(RoadDbFormat.Meta.MAX_LAT).toDouble(),
                value(RoadDbFormat.Meta.MAX_LON).toDouble(),
            ),
        )
    }

    /** All ways whose bounding box intersects [box]. */
    fun waysIn(box: Bounds): List<StoredWay> = connection.prepare(
        "SELECT w.id, $columns, w.geometry FROM way_index i JOIN ways w ON w.id = i.id " +
            "WHERE i.max_lat >= ? AND i.min_lat <= ? AND i.max_lon >= ? AND i.min_lon <= ?",
    ).use { stmt ->
        stmt.bindDouble(1, box.minLat)
        stmt.bindDouble(2, box.maxLat)
        stmt.bindDouble(3, box.minLon)
        stmt.bindDouble(4, box.maxLon)
        val geometryColumn = RoadDbFormat.TAG_KEYS.size + 1
        buildList {
            while (stmt.step()) {
                val tags = buildMap {
                    RoadDbFormat.TAG_KEYS.forEachIndexed { i, key ->
                        if (!stmt.isNull(i + 1)) put(key, stmt.getText(i + 1))
                    }
                }
                val geometry = GeometryCodec.decode(stmt.getBlob(geometryColumn))
                add(StoredWay(stmt.getLong(0), tags, geometry.nodeIds, geometry.latE7, geometry.lonE7))
            }
        }
    }
}
