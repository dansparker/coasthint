package io.github.dansparker.coasthint.speedlimit

/** Where and when the road data was last requested. */
data class FetchRecord(val position: LatLon, val bearingDeg: Double?, val elapsedMillis: Long)

/**
 * Decides when to load new road data: when there is none yet, after [refetchDistanceM] or after a
 * course change of more than [refetchCourseChangeDeg]; never more often than [minIntervalMillis]
 * to respect Overpass rate limits.
 */
class FetchPolicy(
    private val refetchDistanceM: Double = 500.0,
    private val refetchCourseChangeDeg: Double = 30.0,
    private val minIntervalMillis: Long = 10_000,
) {
    fun shouldFetch(last: FetchRecord?, position: LatLon, bearingDeg: Double?, nowMillis: Long): Boolean {
        if (last == null) return true
        if (nowMillis - last.elapsedMillis < minIntervalMillis) return false
        if (Geo.distanceM(last.position, position) >= refetchDistanceM) return true
        val lastBearing = last.bearingDeg ?: return bearingDeg != null
        return bearingDeg != null && Geo.angleDiffDeg(lastBearing, bearingDeg) > refetchCourseChangeDeg
    }
}
