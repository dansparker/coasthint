package io.github.dansparker.coasthint.location

/** One GPS fix; [elapsedMillis] is on the [android.os.SystemClock.elapsedRealtime] clock. */
data class PositionFix(
    val elapsedMillis: Long,
    val lat: Double,
    val lon: Double,
    val speedMps: Double?,
    val bearingDeg: Double?,
    val accuracyM: Double?,
)
