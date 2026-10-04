package io.github.dansparker.coasthint.roaddb

import java.io.ByteArrayOutputStream

/**
 * Compact way geometry: node count, then per node the zigzag-varint deltas of node id,
 * latitude and longitude (1e-7 degrees) to the previous node. Typical nodes take 6–9 bytes.
 */
object GeometryCodec {
    class Geometry(val nodeIds: LongArray, val latE7: IntArray, val lonE7: IntArray)

    fun encode(nodeIds: LongArray, latE7: IntArray, lonE7: IntArray): ByteArray {
        val out = ByteArrayOutputStream(nodeIds.size * 9 + 2)
        writeVarint(out, nodeIds.size.toLong())
        var id = 0L
        var lat = 0L
        var lon = 0L
        for (i in nodeIds.indices) {
            writeVarint(out, zigzag(nodeIds[i] - id))
            writeVarint(out, zigzag(latE7[i] - lat))
            writeVarint(out, zigzag(lonE7[i] - lon))
            id = nodeIds[i]
            lat = latE7[i].toLong()
            lon = lonE7[i].toLong()
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): Geometry {
        val reader = Reader(bytes)
        val n = reader.varint().toInt()
        val ids = LongArray(n)
        val lats = IntArray(n)
        val lons = IntArray(n)
        var id = 0L
        var lat = 0L
        var lon = 0L
        for (i in 0 until n) {
            id += unzigzag(reader.varint())
            lat += unzigzag(reader.varint())
            lon += unzigzag(reader.varint())
            ids[i] = id
            lats[i] = lat.toInt()
            lons[i] = lon.toInt()
        }
        return Geometry(ids, lats, lons)
    }

    private fun zigzag(v: Long): Long = (v shl 1) xor (v shr 63)

    private fun unzigzag(v: Long): Long = (v ushr 1) xor -(v and 1)

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    private class Reader(private val bytes: ByteArray) {
        private var pos = 0

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(pos < bytes.size) { "truncated geometry" }
                val b = bytes[pos++].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                require(shift < 64) { "varint too long" }
            }
        }
    }
}
