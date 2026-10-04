package io.github.dansparker.coasthint.speedlimit

import io.github.dansparker.coasthint.core.UpcomingEvent
import io.github.dansparker.coasthint.location.PositionFix
import kotlinx.coroutines.flow.StateFlow

data class SpeedLimitInfo(
    /** Road we are matched to, or null if no road fits. */
    val road: RoadWay? = null,
    /** Limit on the current road in travel direction; null if unknown. */
    val current: Maxspeed? = null,
    /** First place ahead where the limit drops below the own speed. */
    val ahead: UpcomingEvent.SpeedLimit? = null,
)

sealed interface SpeedLimitStatus {
    data object Idle : SpeedLimitStatus
    data object Loading : SpeedLimitStatus
    data class Ready(val wayCount: Int) : SpeedLimitStatus
    data class Failed(val reason: String, val retryInMillis: Long) : SpeedLimitStatus
}

/** Source of speed limits around the own position: Overpass now, offline data later. */
interface SpeedLimitProvider {
    val status: StateFlow<SpeedLimitStatus>

    /** Called for every GPS fix. Must return quickly; loading happens in the background. */
    fun lookup(fix: PositionFix, speedKmh: Double): SpeedLimitInfo
}
