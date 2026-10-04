package io.github.dansparker.coasthint.roaddb

import androidx.sqlite.SQLiteConnection
import java.io.Closeable
import java.io.File
import java.io.InputStream

/** An installed database, or a broken file with the reason. */
data class InstalledRoadDb(val name: String, val sizeBytes: Long, val info: RoadDbInfo?, val error: String?)

/**
 * All installed road databases, one file per region (e.g. one per country) in [directory].
 * Importing a database built from the same source file replaces the old one, so a country can
 * be updated on its own. All methods block; call them off the main thread.
 */
class RoadDbLibrary(private val directory: File, private val open: (path: String) -> SQLiteConnection) : Closeable {
    private val files = LinkedHashMap<String, RoadDbFile>()

    @Synchronized
    fun installed(): List<InstalledRoadDb> = load().map { (name, db) ->
        try {
            InstalledRoadDb(name, db.file.length(), db.info(), null)
        } catch (e: Exception) {
            InstalledRoadDb(name, db.file.length(), null, e.message ?: e.javaClass.simpleName)
        }
    }

    /** Whether any valid database covers the position (by its bounding box). */
    @Synchronized
    fun covers(lat: Double, lon: Double): Boolean = valid().any { (_, info) -> info.bounds.contains(lat, lon) }

    /** Ways from all databases overlapping [box]; ways present in several (border roads) appear once. */
    @Synchronized
    fun waysIn(box: Bounds): List<StoredWay> {
        val dbs = valid().filter { (_, info) -> info.bounds.intersects(box) }
        if (dbs.isEmpty()) throw RoadDbException("no offline data for this area")
        if (dbs.size == 1) return dbs.single().first.waysIn(box)
        return dbs.flatMap { (db, _) -> db.waysIn(box) }.associateBy { it.id }.values.toList()
    }

    /**
     * Installs a database from [input]. It is validated first; on failure nothing changes.
     * Returns the name it was installed under.
     */
    @Synchronized
    fun import(input: InputStream): String {
        directory.mkdirs()
        val temp = File(directory, "import.tmp")
        try {
            temp.outputStream().use { input.copyTo(it) }
            val info = open(temp.path).use { RoadDbReader(it).info() }
            val name = RoadDbFormat.baseName(info.source)
            files.remove(name)?.close()
            val target = File(directory, "$name$EXTENSION")
            if (target.exists() && !target.delete()) throw RoadDbException("cannot replace ${target.name}")
            if (!temp.renameTo(target)) throw RoadDbException("cannot install ${target.name}")
            return name
        } finally {
            temp.delete()
        }
    }

    @Synchronized
    fun delete(name: String) {
        files.remove(name)?.close()
        File(directory, "$name$EXTENSION").delete()
    }

    @Synchronized
    override fun close() {
        files.values.forEach(RoadDbFile::close)
        files.clear()
    }

    /** Syncs [files] with the directory content, so files added or removed elsewhere are noticed. */
    private fun load(): List<Pair<String, RoadDbFile>> {
        val present = directory.listFiles { f -> f.isFile && f.name.endsWith(EXTENSION) }.orEmpty()
            .associateBy { it.name.removeSuffix(EXTENSION) }
        (files.keys - present.keys).forEach { files.remove(it)?.close() }
        present.forEach { (name, file) -> files.getOrPut(name) { RoadDbFile(file, open) } }
        return files.entries.sortedBy { it.key }.map { it.key to it.value }
    }

    private fun valid(): List<Pair<RoadDbFile, RoadDbInfo>> = load().mapNotNull { (_, db) ->
        runCatching { db to db.info() }.getOrNull()
    }

    private companion object {
        const val EXTENSION = ".db"
    }
}
