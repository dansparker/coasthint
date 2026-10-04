package io.github.dansparker.coasthint.ui

import android.content.res.Resources
import io.github.dansparker.coasthint.R
import io.github.dansparker.coasthint.core.TurnCategory
import io.github.dansparker.coasthint.core.TurnType
import io.github.dansparker.coasthint.core.UpcomingEvent

/** Short German description of an event, e.g. "Abbiegung" or "Tempo 70". */
fun Resources.describeEvent(event: UpcomingEvent): String = when (event) {
    is UpcomingEvent.SpeedLimit -> getString(R.string.event_speed_limit, event.limitKmh)
    is UpcomingEvent.Maneuver -> getString(
        when (TurnType.category(event.turnType)) {
            TurnCategory.TURN -> R.string.event_turn
            TurnCategory.SHARP_TURN -> R.string.event_sharp_turn
            TurnCategory.SLIGHT_TURN -> R.string.event_slight_turn
            TurnCategory.ROUNDABOUT -> R.string.event_roundabout
            TurnCategory.NONE -> R.string.event_maneuver
        },
    )
}
