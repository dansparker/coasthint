package io.github.dansparker.coasthint.core

import kotlin.math.roundToLong

/** Raw navigation info for the next maneuver, as delivered by OsmAnd. */
data class NavInfo(
    val distanceToM: Int,
    val turnType: Int,
    val isLeftSide: Boolean = false,
)

/** Something ahead on the road that may require slowing down. */
sealed interface UpcomingEvent {
    /** Stable identity, used to give at most one cue per event. */
    val id: String
    val distanceM: Double

    data class Maneuver(
        override val id: String,
        override val distanceM: Double,
        val turnType: Int,
    ) : UpcomingEvent

    data class SpeedLimit(
        override val distanceM: Double,
        val limitKmh: Int,
        val wayId: Long,
        /** Position where the new limit starts. */
        val lat: Double,
        val lon: Double,
    ) : UpcomingEvent {
        override val id: String =
            "limit-$wayId-${(lat * 1e5).roundToLong()}-${(lon * 1e5).roundToLong()}"
    }
}
