package io.github.dansparker.coasthint.log

import io.github.dansparker.coasthint.core.CoastAdvisor
import io.github.dansparker.coasthint.core.DrivingState
import io.github.dansparker.coasthint.core.NavInfo
import io.github.dansparker.coasthint.core.TurnType
import io.github.dansparker.coasthint.core.kmhToMps
import io.github.dansparker.coasthint.speedlimit.Maxspeed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.ZoneId
import java.time.ZoneOffset

class TripLoggerTest {
    private val utc: ZoneId = ZoneOffset.UTC

    private fun entry(navDistance: Int, cueExpected: Boolean = false): TripLogEntry {
        val driving = DrivingState(kmhToMps(72.0), -0.12)
        val nav = NavInfo(navDistance, TurnType.TR)
        val evaluation = CoastAdvisor().evaluate(driving, nav, null)
        assertEquals(cueExpected, evaluation.cue != null)
        return TripLogEntry(
            wallTimeMillis = 1_791_200_000_000, // 2026-10-05T11:33:20Z
            elapsedMillis = 123_456,
            lat = 48.2081743,
            lon = 16.3738189,
            speedMps = driving.speedMps,
            accelerationMps2 = driving.accelerationMps2,
            currentLimit = Maxspeed.Limit(70),
            navDistanceM = navDistance,
            navTurnType = TurnType.TR,
            evaluation = evaluation,
        )
    }

    @Test
    fun `row has one value per header column`() {
        val columns = TripCsv.HEADER.split(',').size
        assertEquals(columns, TripCsv.row(entry(1_000), utc).split(',').size)
    }

    @Test
    fun `row contents`() {
        val row = TripCsv.row(entry(300, cueExpected = true), utc).split(',')
        assertEquals("2026-10-05T11:33:20.000", row[0])
        assertEquals("48.208174", row[2])
        assertEquals("72.0", row[4])
        assertEquals("-0.12", row[5])
        assertEquals("70", row[6])
        assertEquals("300", row[7])
        assertEquals("TR", row[8])
        assertEquals("maneuver:TR", row[9])
        assertEquals("25", row[11])
        assertEquals("1", row[14])
    }

    @Test
    fun `no event leaves the event columns empty`() {
        val row = TripCsv.row(entry(5_000).copy(evaluation = CoastAdvisor().evaluate(DrivingState(10.0, 0.0), null, null)), utc)
        assertTrue(row.endsWith(",,,,,,0"))
    }

    @Test
    fun `writes header and rows to a file named after the start time`(@TempDir dir: File) {
        val logger = TripLogger(dir)
        logger.start(1_791_200_000_000, utc)
        repeat(3) { logger.append(entry(1_000)) }
        val file = logger.currentFile!!
        logger.stop()
        assertEquals("trip-20261005-113320.csv", file.name)
        val lines = file.readLines()
        assertEquals(TripCsv.HEADER, lines.first())
        assertEquals(4, lines.size)
    }

    @Test
    fun `keeps only the newest trips`(@TempDir dir: File) {
        val logger = TripLogger(dir, maxFiles = 2)
        listOf(1_000_000_000_000L, 1_100_000_000_000L, 1_200_000_000_000L).forEach {
            logger.start(it, utc)
            logger.stop()
        }
        val names = logger.trips().map { it.name }
        assertEquals(listOf("trip-20080110-212000.csv", "trip-20041109-113320.csv"), names)
    }
}
