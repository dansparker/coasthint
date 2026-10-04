package io.github.dansparker.coasthint.service

import io.github.dansparker.coasthint.core.CalibrationResult
import io.github.dansparker.coasthint.core.CoastMode
import io.github.dansparker.coasthint.core.DrivingState
import io.github.dansparker.coasthint.core.Evaluation
import io.github.dansparker.coasthint.core.UpcomingEvent
import io.github.dansparker.coasthint.osmand.NavSample
import io.github.dansparker.coasthint.osmand.OsmAndStatus
import io.github.dansparker.coasthint.speedlimit.SpeedLimitInfo
import io.github.dansparker.coasthint.speedlimit.SpeedLimitStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CueRecord(val event: UpcomingEvent, val atMillis: Long)

/** A running or finished "Ausrollen messen" run. */
data class CalibrationState(
    val active: Boolean = false,
    val mode: CoastMode = CoastMode.ENGINE_BRAKING,
    val samples: Int = 0,
    val lastResult: CalibrationResult? = null,
)

/** Everything the service knows right now, for the UI and the debug screen. */
data class LiveState(
    val running: Boolean = false,
    val osmAnd: OsmAndStatus = OsmAndStatus.Stopped,
    val navigation: NavSample? = null,
    val driving: DrivingState? = null,
    val speedLimit: SpeedLimitInfo? = null,
    val speedLimitStatus: SpeedLimitStatus = SpeedLimitStatus.Idle,
    val evaluation: Evaluation? = null,
    val lastCue: CueRecord? = null,
    val voiceLog: List<String> = emptyList(),
    val calibration: CalibrationState = CalibrationState(),
)

/** Process-wide live state published by [CoastHintService]. */
object CoastHintRuntime {
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    internal fun update(transform: (LiveState) -> LiveState) = _state.update(transform)

    internal fun reset() {
        _state.value = LiveState()
    }
}
