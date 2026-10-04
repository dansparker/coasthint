package io.github.dansparker.coasthint.core

/**
 * Assigns stable ids to OsmAnd maneuvers. OsmAnd only reports the distance to the *next*
 * maneuver, so a new maneuver is recognised when the turn type changes or the distance
 * jumps up (beyond GPS jitter) instead of continuing to shrink.
 */
class ManeuverTracker(private val jumpThresholdM: Int = 30) {
    private var sequence = 0L
    private var last: NavInfo? = null

    fun track(nav: NavInfo): UpcomingEvent.Maneuver {
        val prev = last
        if (prev == null ||
            nav.turnType != prev.turnType ||
            nav.distanceToM > prev.distanceToM + jumpThresholdM
        ) {
            sequence++
        }
        last = nav
        return UpcomingEvent.Maneuver(
            id = "maneuver-$sequence",
            distanceM = nav.distanceToM.toDouble(),
            turnType = nav.turnType,
        )
    }
}
