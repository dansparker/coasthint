package io.github.dansparker.coasthint.speedlimit

import io.github.dansparker.coasthint.core.UpcomingEvent

/**
 * Follows the matched road in travel direction and finds the first place where the speed limit
 * drops below the own speed. At the end of a way it continues on the straightest allowed
 * successor; it stops at dead ends, sharp turns or after [maxDistanceM].
 */
class LookAhead(
    private val maxDistanceM: Double = 1_500.0,
    private val maxTurnDeg: Double = 100.0,
) {
    private data class Successor(val way: RoadWay, val forward: Boolean, val startIndex: Int)

    fun nextLowerLimit(network: RoadNetwork, match: RoadMatch, speedKmh: Double): UpcomingEvent.SpeedLimit? {
        var way = match.way
        var forward = match.forward
        var nextIndex = if (forward) match.segmentIndex + 1 else match.segmentIndex
        var previous = match.point
        var distance = 0.0
        var limitBefore = way.maxspeed(forward)
        val visited = mutableSetOf(way.id)

        while (true) {
            val step = if (forward) 1 else -1
            var i = nextIndex
            while (i in way.nodeIds.indices) {
                val p = network.position(way.nodeIds[i])
                distance += Geo.distanceM(previous, p)
                if (distance > maxDistanceM) return null
                previous = p
                i += step
            }
            val endIndex = i - step
            val endNode = way.nodeIds[endIndex]
            val incoming = Geo.bearingDeg(network.position(way.nodeIds[endIndex - step]), previous)
            val next = successor(network, endNode, way, incoming) ?: return null
            if (!visited.add(next.way.id)) return null

            val newLimit = next.way.maxspeed(next.forward)
            if (newLimit is Maxspeed.Limit && newLimit.kmh < speedKmh && isLower(newLimit, limitBefore)) {
                val at = network.position(endNode)
                return UpcomingEvent.SpeedLimit(distance, newLimit.kmh, next.way.id, at.lat, at.lon)
            }
            // A way without maxspeed keeps the previous limit.
            if (newLimit != null) limitBefore = newLimit
            way = next.way
            forward = next.forward
            nextIndex = next.startIndex + if (forward) 1 else -1
        }
    }

    private fun isLower(limit: Maxspeed.Limit, before: Maxspeed?): Boolean =
        before !is Maxspeed.Limit || limit.kmh < before.kmh

    private fun successor(network: RoadNetwork, node: Long, current: RoadWay, incomingDeg: Double): Successor? {
        val from = network.position(node)
        return network.waysAt(node)
            .filter { it.way.id != current.id }
            .flatMap { (way, index) ->
                buildList {
                    if (index < way.nodeIds.lastIndex && way.allows(true)) add(Successor(way, true, index))
                    if (index > 0 && way.allows(false)) add(Successor(way, false, index))
                }
            }
            .map { s ->
                val nextNode = s.way.nodeIds[s.startIndex + if (s.forward) 1 else -1]
                s to Geo.angleDiffDeg(incomingDeg, Geo.bearingDeg(from, network.position(nextNode)))
            }
            .filter { (_, turn) -> turn <= maxTurnDeg }
            .minByOrNull { (_, turn) -> turn }
            ?.first
    }
}
