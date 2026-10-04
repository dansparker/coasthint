package io.github.dansparker.coasthint.roaddb

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL

/**
 * Bulk-writes a fresh road database into an empty [connection]. Call [add] for every way and
 * [finish] once at the end; the whole build runs in a single transaction.
 */
class RoadDbWriter(private val connection: SQLiteConnection) {
    private val insertWay: SQLiteStatement
    private val insertIndex: SQLiteStatement
    private var count = 0
    private var minLat = Int.MAX_VALUE
    private var minLon = Int.MAX_VALUE
    private var maxLat = Int.MIN_VALUE
    private var maxLon = Int.MIN_VALUE

    init {
        // A half-written file is useless anyway, so skip the journal for speed.
        connection.execSQL("PRAGMA journal_mode = OFF")
        connection.execSQL("PRAGMA synchronous = OFF")
        RoadDbFormat.CREATE_STATEMENTS.forEach(connection::execSQL)
        connection.execSQL("BEGIN")
        val columns = RoadDbFormat.TAG_KEYS.joinToString(", ") { RoadDbFormat.column(it) }
        val placeholders = RoadDbFormat.TAG_KEYS.joinToString(", ") { "?" }
        insertWay = connection.prepare("INSERT INTO ways(id, $columns, geometry) VALUES (?, $placeholders, ?)")
        insertIndex = connection.prepare(
            "INSERT INTO way_index(id, min_lat, max_lat, min_lon, max_lon) VALUES (?, ?, ?, ?, ?)",
        )
    }

    fun add(way: StoredWay) {
        require(way.nodeIds.size >= 2) { "way ${way.id} has fewer than two nodes" }
        insertWay.bindLong(1, way.id)
        RoadDbFormat.TAG_KEYS.forEachIndexed { i, key ->
            val value = way.tags[key]
            if (value == null) insertWay.bindNull(i + 2) else insertWay.bindText(i + 2, value)
        }
        insertWay.bindBlob(RoadDbFormat.TAG_KEYS.size + 2, GeometryCodec.encode(way.nodeIds, way.latE7, way.lonE7))
        insertWay.step()
        insertWay.reset()

        val b = way.bounds
        insertIndex.bindLong(1, way.id)
        insertIndex.bindDouble(2, b.minLat)
        insertIndex.bindDouble(3, b.maxLat)
        insertIndex.bindDouble(4, b.minLon)
        insertIndex.bindDouble(5, b.maxLon)
        insertIndex.step()
        insertIndex.reset()

        count++
        minLat = minOf(minLat, way.latE7.min())
        minLon = minOf(minLon, way.lonE7.min())
        maxLat = maxOf(maxLat, way.latE7.max())
        maxLon = maxOf(maxLon, way.lonE7.max())
    }

    fun finish(source: String, created: String) {
        check(count > 0) { "no ways written" }
        val meta = mapOf(
            RoadDbFormat.Meta.FORMAT_VERSION to RoadDbFormat.VERSION.toString(),
            RoadDbFormat.Meta.SOURCE to source,
            RoadDbFormat.Meta.CREATED to created,
            RoadDbFormat.Meta.WAY_COUNT to count.toString(),
            RoadDbFormat.Meta.MIN_LAT to (minLat / StoredWay.E7).toString(),
            RoadDbFormat.Meta.MIN_LON to (minLon / StoredWay.E7).toString(),
            RoadDbFormat.Meta.MAX_LAT to (maxLat / StoredWay.E7).toString(),
            RoadDbFormat.Meta.MAX_LON to (maxLon / StoredWay.E7).toString(),
        )
        connection.prepare("INSERT INTO meta(key, value) VALUES (?, ?)").use { insert ->
            meta.forEach { (key, value) ->
                insert.bindText(1, key)
                insert.bindText(2, value)
                insert.step()
                insert.reset()
            }
        }
        insertWay.close()
        insertIndex.close()
        connection.execSQL("COMMIT")
    }
}
