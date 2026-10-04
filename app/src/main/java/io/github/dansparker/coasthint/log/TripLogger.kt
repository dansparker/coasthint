package io.github.dansparker.coasthint.log

import io.github.dansparker.coasthint.core.Evaluation
import io.github.dansparker.coasthint.core.TurnType
import io.github.dansparker.coasthint.core.UpcomingEvent
import io.github.dansparker.coasthint.core.mpsToKmh
import io.github.dansparker.coasthint.speedlimit.Maxspeed
import java.io.BufferedWriter
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One CSV line: what the app knew and decided at one GPS fix. */
data class TripLogEntry(
    val wallTimeMillis: Long,
    val elapsedMillis: Long,
    val lat: Double,
    val lon: Double,
    val speedMps: Double,
    val accelerationMps2: Double,
    val currentLimit: Maxspeed?,
    val navDistanceM: Int?,
    val navTurnType: Int?,
    val evaluation: Evaluation,
)

object TripCsv {
    const val HEADER =
        "time,elapsed_ms,lat,lon,speed_kmh,accel_mps2,limit_kmh,nav_distance_m,nav_turn_type," +
            "event,d_event_m,v_target_kmh,d_coast_m,d_trigger_m,cue"

    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

    fun row(e: TripLogEntry, zone: ZoneId = ZoneId.systemDefault()): String {
        val next = e.evaluation.next
        return listOf(
            timeFormat.format(Instant.ofEpochMilli(e.wallTimeMillis).atZone(zone)),
            e.elapsedMillis.toString(),
            num(e.lat, 6),
            num(e.lon, 6),
            num(mpsToKmh(e.speedMps), 1),
            num(e.accelerationMps2, 2),
            when (val limit = e.currentLimit) {
                is Maxspeed.Limit -> limit.kmh.toString()
                Maxspeed.Unlimited -> "none"
                null -> ""
            },
            e.navDistanceM?.toString().orEmpty(),
            e.navTurnType?.let(TurnType::label).orEmpty(),
            next?.event?.let(::describe).orEmpty(),
            next?.let { num(it.event.distanceM, 0) }.orEmpty(),
            next?.let { num(mpsToKmh(it.targetSpeedMps), 0) }.orEmpty(),
            next?.let { num(it.coastDistanceM, 0) }.orEmpty(),
            next?.let { num(it.triggerDistanceM, 0) }.orEmpty(),
            if (e.evaluation.cue != null) "1" else "0",
        ).joinToString(",")
    }

    private fun describe(event: UpcomingEvent): String = when (event) {
        is UpcomingEvent.Maneuver -> "maneuver:${TurnType.label(event.turnType)}"
        is UpcomingEvent.SpeedLimit -> "limit:${event.limitKmh}"
    }

    private fun num(value: Double, decimals: Int) = String.format(Locale.ROOT, "%.${decimals}f", value)
}

/** Writes one CSV file per trip into [directory] and keeps only the newest [maxFiles] trips. */
class TripLogger(private val directory: File, private val maxFiles: Int = 30) {
    private var writer: BufferedWriter? = null
    private var linesSinceFlush = 0

    var currentFile: File? = null
        private set

    fun start(startMillis: Long, zone: ZoneId = ZoneId.systemDefault()) {
        stop()
        directory.mkdirs()
        val name = "trip-" + FILE_TIME.format(Instant.ofEpochMilli(startMillis).atZone(zone)) + ".csv"
        val file = File(directory, name)
        writer = file.bufferedWriter().apply {
            write(TripCsv.HEADER)
            newLine()
        }
        currentFile = file
        prune()
    }

    fun append(entry: TripLogEntry) {
        val w = writer ?: return
        w.write(TripCsv.row(entry))
        w.newLine()
        if (++linesSinceFlush >= FLUSH_EVERY) {
            w.flush()
            linesSinceFlush = 0
        }
    }

    fun stop() {
        writer?.close()
        writer = null
        currentFile = null
        linesSinceFlush = 0
    }

    /** Trip files, newest first. */
    fun trips(): List<File> = listTrips(directory)

    private fun prune() {
        trips().drop(maxFiles).forEach { it.delete() }
    }

    companion object {
        private const val FLUSH_EVERY = 10
        private val FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        fun listTrips(directory: File): List<File> =
            directory.listFiles { f -> f.isFile && f.name.startsWith("trip-") && f.name.endsWith(".csv") }
                .orEmpty()
                .sortedByDescending { it.name }
    }
}
