package io.github.dansparker.coasthint.roaddb.builder

import com.google.protobuf.ByteString
import crosby.binary.Osmformat
import crosby.binary.file.BlockOutputStream
import crosby.binary.file.FileBlock
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Writes a small PBF file with the protobuf classes of osmpbf and reads it back. */
class PbfOsmSourceTest {

    private val strings = listOf("", "highway", "primary", "maxspeed", "70", "building", "yes", "name", "Hauptstraße")
    private fun s(value: String) = strings.indexOf(value)

    private fun writePbf(file: File) {
        val header = Osmformat.HeaderBlock.newBuilder()
            .addRequiredFeatures("OsmSchema-V0.6")
            .addRequiredFeatures("DenseNodes")
            .build()
        // Granularity 100 nanodegrees: stored value = degrees · 1e7. Ids and coordinates delta-coded.
        val dense = Osmformat.DenseNodes.newBuilder()
            .addAllId(listOf(1_000L, 1L, 1L))
            .addAllLat(listOf(482_000_000L, 10_000L, 10_000L))
            .addAllLon(listOf(163_700_000L, 1L, -2L))
        val ways = listOf(
            Osmformat.Way.newBuilder().setId(77)
                .addAllKeys(listOf(s("highway"), s("maxspeed"), s("name")))
                .addAllVals(listOf(s("primary"), s("70"), s("Hauptstraße")))
                .addAllRefs(listOf(1_000L, 1L, 1L)),
            Osmformat.Way.newBuilder().setId(78)
                .addAllKeys(listOf(s("building")))
                .addAllVals(listOf(s("yes")))
                .addAllRefs(listOf(1_000L, 2L)),
        )
        val block = Osmformat.PrimitiveBlock.newBuilder()
            .setStringtable(Osmformat.StringTable.newBuilder().addAllS(strings.map(ByteString::copyFromUtf8)))
            .addPrimitivegroup(Osmformat.PrimitiveGroup.newBuilder().setDense(dense))
            .addPrimitivegroup(Osmformat.PrimitiveGroup.newBuilder().addAllWays(ways.map { it.build() }))
            .build()
        file.outputStream().use { out ->
            val stream = BlockOutputStream(out)
            stream.write(FileBlock.newInstance("OSMHeader", header.toByteString(), null))
            stream.write(FileBlock.newInstance("OSMData", block.toByteString(), null))
            stream.flush()
        }
    }

    @Test
    fun `reads ways with wanted tags and decoded node references`(@TempDir dir: File) {
        val file = File(dir, "test.osm.pbf").also(::writePbf)
        val ways = mutableListOf<Triple<Long, Map<String, String>, LongArray>>()
        PbfOsmSource(file).readWays(setOf("highway", "maxspeed")) { id, tags, nodes -> ways += Triple(id, tags, nodes) }
        assertEquals(1, ways.size)
        val (id, tags, nodes) = ways.single()
        assertEquals(77L, id)
        assertEquals(mapOf("highway" to "primary", "maxspeed" to "70"), tags)
        assertArrayEquals(longArrayOf(1_000, 1_001, 1_002), nodes)
    }

    @Test
    fun `reads dense nodes with decoded ids and coordinates`(@TempDir dir: File) {
        val file = File(dir, "test.osm.pbf").also(::writePbf)
        val nodes = mutableListOf<Triple<Long, Double, Double>>()
        PbfOsmSource(file).readNodes { id, lat, lon -> nodes += Triple(id, lat, lon) }
        assertEquals(listOf(1_000L, 1_001L, 1_002L), nodes.map { it.first })
        assertEquals(48.2, nodes[0].second, 1e-9)
        assertEquals(48.202, nodes[2].second, 1e-9)
        assertEquals(16.3700001, nodes[1].third, 1e-9)
        assertEquals(16.3699999, nodes[2].third, 1e-9)
    }
}
