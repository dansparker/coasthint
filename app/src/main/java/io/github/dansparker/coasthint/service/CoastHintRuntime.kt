package io.github.dansparker.coasthint.service

import io.github.dansparker.coasthint.core.DrivingState
import io.github.dansparker.coasthint.core.Evaluation
import io.github.dansparker.coasthint.core.UpcomingEvent
import io.github.dansparker.coasthint.osmand.NavSample
import io.github.dansparker.coasthint.osmand.OsmAndStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CueRecord(val event: UpcomingEvent, val atMillis: Long)

/** Everything the service knows right now, for the UI and the debug screen. */
data class LiveState(
    val running: Boolean = false,
    val osmAnd: OsmAndStatus = OsmAndStatus.Stopped,
    val navigation: NavSample? = null,
    val driving: DrivingState? = null,
    val evaluation: Evaluation? = null,
    val lastCue: CueRecord? = null,
    val voiceLog: List<String> = emptyList(),
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
