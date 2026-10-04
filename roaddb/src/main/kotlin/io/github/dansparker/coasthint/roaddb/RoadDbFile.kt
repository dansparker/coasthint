package io.github.dansparker.coasthint.roaddb

import androidx.sqlite.SQLiteConnection
import java.io.Closeable
import java.io.File
import java.io.InputStream

/**
 * The installed road database file: opened lazily, replaced atomically on import.
 * All methods block and are synchronized; call them off the main thread.
 *
 * @param open opens a connection to the given path, ideally read-only.
 */
class RoadDbFile(val file: File, private val open: (path: String) -> SQLiteConnection) : Closeable {
    private var connection: SQLiteConnection? = null
    private var reader: RoadDbReader? = null
    private var cachedInfo: RoadDbInfo? = null

    /** Description of the installed database, or null if none is installed. */
    @Synchronized
    fun info(): RoadDbInfo? {
        if (!file.isFile) return null
        ensureOpen()
        return cachedInfo
    }

    @Synchronized
    fun waysIn(box: Bounds): List<StoredWay> {
        if (!file.isFile) throw RoadDbException("no offline database installed")
        return ensureOpen().waysIn(box)
    }

    /**
     * Installs a new database from [input]. The data is validated before it replaces the current
     * file; on failure the current file stays untouched.
     */
    @Synchronized
    fun replaceWith(input: InputStream): RoadDbInfo {
        val temp = File(file.parentFile, file.name + ".import")
        try {
            temp.parentFile?.mkdirs()
            temp.outputStream().use { input.copyTo(it) }
            val info = open(temp.path).use { RoadDbReader(it).info() }
            closeConnection()
            if (file.exists() && !file.delete()) throw RoadDbException("cannot replace ${file.name}")
            if (!temp.renameTo(file)) throw RoadDbException("cannot install ${file.name}")
            return info
        } finally {
            temp.delete()
        }
    }

    @Synchronized
    fun delete() {
        closeConnection()
        file.delete()
    }

    @Synchronized
    override fun close() = closeConnection()

    private fun ensureOpen(): RoadDbReader {
        reader?.let { return it }
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

    private fun closeConnection() {
        connection?.close()
        connection = null
        reader = null
        cachedInfo = null
    }
}
