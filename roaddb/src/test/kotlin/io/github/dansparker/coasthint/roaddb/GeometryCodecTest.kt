package io.github.dansparker.coasthint.roaddb

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeometryCodecTest {

    @Test
    fun `round trip keeps ids and coordinates exactly`() {
        val ids = longArrayOf(1_234_567_890_123, 1_234_567_890_200, 17, 9_000_000_000_000)
        val lats = intArrayOf(482_081_743, 482_082_001, -335_000_000, 900_000_000)
        val lons = intArrayOf(163_738_189, 163_739_000, 1_799_999_999, -1_800_000_000)
        val decoded = GeometryCodec.decode(GeometryCodec.encode(ids, lats, lons))
        assertArrayEquals(ids, decoded.nodeIds)
        assertArrayEquals(lats, decoded.latE7)
        assertArrayEquals(lons, decoded.lonE7)
    }

    @Test
    fun `neighbouring nodes encode compactly`() {
        val n = 100
        val ids = LongArray(n) { 5_000_000_000 + it * 3L }
        val lats = IntArray(n) { 482_000_000 + it * 900 }
        val lons = IntArray(n) { 163_000_000 - it * 1_200 }
        val bytes = GeometryCodec.encode(ids, lats, lons)
        assertTrue(bytes.size < n * 7, "got ${bytes.size} bytes")
    }

    @Test
    fun `empty geometry`() {
        assertEquals(0, GeometryCodec.decode(GeometryCodec.encode(LongArray(0), IntArray(0), IntArray(0))).nodeIds.size)
    }

    @Test
    fun `truncated data is rejected`() {
        val bytes = GeometryCodec.encode(longArrayOf(1, 2), intArrayOf(10, 20), intArrayOf(30, 40))
        assertThrows(IllegalArgumentException::class.java) { GeometryCodec.decode(bytes.copyOf(bytes.size - 1)) }
    }
}
