package io.github.dansparker.coasthint.settings

import io.github.dansparker.coasthint.core.Calibration
import io.github.dansparker.coasthint.core.CoastMeasurement
import io.github.dansparker.coasthint.core.CoastMode
import io.github.dansparker.coasthint.core.CoastSettings
import io.github.dansparker.coasthint.speedlimit.OverpassClient
import io.github.dansparker.coasthint.speedlimit.SpeedLimitSource
import java.util.Locale

data class OutputSettings(
    val tone: Boolean = true,
    val speech: Boolean = false,
    val vibration: Boolean = false,
)

/** Everything the user can configure, as stored. */
data class AppSettings(
    /** Manually set values; decelerations are replaced by calibration results where available. */
    val coast: CoastSettings = CoastSettings(),
    val output: OutputSettings = OutputSettings(),
    val speedLimitSource: SpeedLimitSource = SpeedLimitSource.AUTO,
    val overpassServer: String = OverpassClient.DEFAULT_SERVER,
    val tripLogEnabled: Boolean = true,
    val calibrations: Map<CoastMode, List<CoastMeasurement>> = emptyMap(),
) {
    fun measurements(mode: CoastMode): List<CoastMeasurement> = calibrations[mode].orEmpty()

    /** The settings the advisor should use: calibrated decelerations win over manual ones. */
    val effectiveCoast: CoastSettings
        get() {
            val engine = measurements(CoastMode.ENGINE_BRAKING)
            val sailing = measurements(CoastMode.SAILING)
            return coast.copy(
                engineBrakingDecelerationMps2 = Calibration.averageDeceleration(engine)
                    ?: coast.engineBrakingDecelerationMps2,
                sailingDecelerationMps2 = Calibration.averageDeceleration(sailing) ?: coast.sailingDecelerationMps2,
                engineBrakingQuadratic = Calibration.fitQuadratic(engine),
                sailingQuadratic = Calibration.fitQuadratic(sailing),
            )
        }
}

/** Compact text form for storing measurements: one per line, comma-separated numbers. */
object MeasurementCodec {
    fun encode(measurements: List<CoastMeasurement>): String = measurements.joinToString("\n") { m ->
        listOf(m.decelerationMps2, m.startSpeedMps, m.endSpeedMps, m.durationS, m.rSquared)
            .joinToString(",") { String.format(Locale.ROOT, "%.6f", it) }
    }

    /** Lines that cannot be read are skipped. */
    fun decode(text: String?): List<CoastMeasurement> = text.orEmpty().lines().mapNotNull { line ->
        val v = line.split(',').mapNotNull { it.trim().toDoubleOrNull() }
        if (v.size == 5) CoastMeasurement(v[0], v[1], v[2], v[3], v[4]) else null
    }
}
