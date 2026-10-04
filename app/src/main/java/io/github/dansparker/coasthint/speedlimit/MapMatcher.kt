package io.github.dansparker.coasthint.speedlimit

/** The road segment we are driving on, and in which direction. */
data class RoadMatch(
    val way: RoadWay,
    /** Segment from node [segmentIndex] to node [segmentIndex] + 1. */
    val segmentIndex: Int,
    /** Own position projected onto the segment. */
    val point: LatLon,
    /** Travelling in the order of the way's nodes. */
    val forward: Boolean,
    val distanceM: Double,
    val angleDiffDeg: Double,
)

/**
 * Simple map matching: the nearest segment whose direction fits the own bearing.
 * Segments the driver may not use in that direction (one-way) are ignored.
 */
class MapMatcher(
    private val maxDistanceM: Double = 25.0,
    private val maxAngleDeg: Double = 35.0,
) {
    fun match(network: RoadNetwork, position: LatLon, bearingDeg: Double): RoadMatch? {
        var best: RoadMatch? = null
        var bestScore = Double.MAX_VALUE
        for (way in network.ways.values) {
            for (i in 0 until way.nodeIds.size - 1) {
                val a = network.position(way.nodeIds[i])
                val b = network.position(way.nodeIds[i + 1])
                val projection = Geo.projectOnSegment(position, a, b)
                if (projection.distanceM > maxDistanceM) continue
                val segmentBearing = Geo.bearingDeg(a, b)
                val diffForward = Geo.angleDiffDeg(bearingDeg, segmentBearing)
                val diffBackward = Geo.angleDiffDeg(bearingDeg, segmentBearing + 180)
                val forward = diffForward <= diffBackward
                val angle = if (forward) diffForward else diffBackward
                if (angle > maxAngleDeg || !way.allows(forward)) continue
                val score = projection.distanceM / maxDistanceM + angle / maxAngleDeg
                if (score < bestScore) {
                    bestScore = score
                    best = RoadMatch(
                        way = way,
                        segmentIndex = i,
                        point = Geo.interpolate(a, b, projection.fraction),
                        forward = forward,
                        distanceM = projection.distanceM,
                        angleDiffDeg = angle,
                    )
                }
            }
        }
        return best
    }
}
