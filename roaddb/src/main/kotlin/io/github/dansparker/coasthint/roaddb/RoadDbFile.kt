package io.github.dansparker.coasthint.roaddb

import androidx.sqlite.SQLiteConnection
import java.io.Closeable
import java.io.File

/**
 * One road database file, opened lazily. All methods block and are synchronized; call them off
 * the main thread.
 *
 * @param open opens a connection to the given path, ideally read-only.
 */
class RoadDbFile(val file: File, private val open: (path: String) -> SQLiteConnection) : Closeable {
    private var connection: SQLiteConnection? = null
    private var reader: RoadDbReader? = null
    private var cachedInfo: RoadDbInfo? = null

    /** Description of the database; throws [RoadDbException] if the file is not a valid one. */
    @Synchronized
    fun info(): RoadDbInfo {
        ensureOpen()
        return cachedInfo!!
    }

    @Synchronized
    fun waysIn(box: Bounds): List<StoredWay> = ensureOpen().waysIn(box)

    @Synchronized
    override fun close() {
        connection?.close()
        connection = null
        reader = null
        cachedInfo = null
    }

    private fun ensureOpen(): RoadDbReader {
        reader?.let { return it }
        if (!file.isFile) throw RoadDbException("${file.name} does not exist")
        val c = open(file.path)
        try {
            val r = RoadDbReader(c)
            cachedInfo = r.info()
            connection = c
            reader = r
            return r
        } catch (e: Exception) {
            c.close()
            throw e
        }
    }
}
