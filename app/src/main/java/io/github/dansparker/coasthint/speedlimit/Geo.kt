package io.github.dansparker.coasthint.speedlimit

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** Where a point falls onto a segment a→b: [fraction] 0 = a, 1 = b. */
data class SegmentProjection(val fraction: Double, val distanceM: Double)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceM(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceAtMost(1.0)))
    }

    /** Initial bearing from a to b in degrees, 0 = north, clockwise, range [0, 360). */
    fun bearingDeg(a: LatLon, b: LatLon): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return normalize(Math.toDegrees(atan2(y, x)))
    }

    /** Smallest angle between two bearings, range [0, 180]. */
    fun angleDiffDeg(a: Double, b: Double): Double {
        val d = abs(normalize(a) - normalize(b))
        return if (d > 180) 360 - d else d
    }

    fun destination(from: LatLon, bearingDeg: Double, distanceM: Double): LatLon {
        val delta = distanceM / EARTH_RADIUS_M
        val theta = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(from.lat)
        val lon1 = Math.toRadians(from.lon)
        val lat2 = asin(sin(lat1) * cos(delta) + cos(lat1) * sin(delta) * cos(theta))
        val lon2 = lon1 + atan2(sin(theta) * sin(delta) * cos(lat1), cos(delta) - sin(lat1) * sin(lat2))
        return LatLon(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    /** Projects p onto segment a→b in a local flat approximation (fine for road segments). */
    fun projectOnSegment(p: LatLon, a: LatLon, b: LatLon): SegmentProjection {
        val cosLat = cos(Math.toRadians(p.lat))
        fun x(q: LatLon) = Math.toRadians(q.lon - p.lon) * cosLat * EARTH_RADIUS_M
        fun y(q: LatLon) = Math.toRadians(q.lat - p.lat) * EARTH_RADIUS_M
        val ax = x(a)
        val ay = y(a)
        val dx = x(b) - ax
        val dy = y(b) - ay
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSq).coerceIn(0.0, 1.0)
        return SegmentProjection(t, hypot(ax + t * dx, ay + t * dy))
    }

    fun interpolate(a: LatLon, b: LatLon, fraction: Double): LatLon =
        LatLon(a.lat + (b.lat - a.lat) * fraction, a.lon + (b.lon - a.lon) * fraction)

    private fun normalize(deg: Double): Double = ((deg % 360) + 360) % 360
}
