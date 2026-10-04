package io.github.dansparker.coasthint.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dansparker.coasthint.R
import io.github.dansparker.coasthint.core.CoastAdvisor
import io.github.dansparker.coasthint.core.DrivingState
import io.github.dansparker.coasthint.core.NavInfo
import io.github.dansparker.coasthint.core.TurnType
import io.github.dansparker.coasthint.core.mpsToKmh
import io.github.dansparker.coasthint.osmand.NavSample
import io.github.dansparker.coasthint.osmand.OsmAndStatus
import io.github.dansparker.coasthint.service.LiveState
import io.github.dansparker.coasthint.speedlimit.Maxspeed
import io.github.dansparker.coasthint.speedlimit.SpeedLimitStatus
import kotlinx.coroutines.delay

@Composable
fun DebugScreen(state: LiveState, onClose: () -> Unit) {
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            delay(500)
            value = SystemClock.elapsedRealtime()
        }
    }
    val resources = LocalResources.current
    val dash = stringResource(R.string.debug_none)
    fun ago(millis: Long) = "%.1f s".format((now - millis) / 1000.0)
    fun meters(value: Double) = "%.0f m".format(value)

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.debug_title), style = MaterialTheme.typography.headlineSmall)
            if (!state.running) Text(stringResource(R.string.debug_service_off))

            Section(stringResource(R.string.debug_section_osmand))
            Text(osmAndStatusText(state.osmAnd))
            val info = state.navigation?.info
            ValueRow(stringResource(R.string.debug_distance_to), info?.let { "${it.distanceToM} m" } ?: dash)
            ValueRow(
                stringResource(R.string.debug_turn_type),
                info?.let { "${TurnType.label(it.turnType)} (${it.turnType})" } ?: dash,
            )
            ValueRow(stringResource(R.string.debug_left_side), info?.isLeftSide?.toString() ?: dash)
            ValueRow(stringResource(R.string.debug_age), state.navigation?.let { ago(it.receivedAtMillis) } ?: dash)

            Section(stringResource(R.string.debug_section_driving))
            val driving = state.driving
            ValueRow(stringResource(R.string.debug_speed), driving?.let { "%.1f km/h".format(mpsToKmh(it.speedMps)) } ?: dash)
            ValueRow(
                stringResource(R.string.debug_acceleration),
                driving?.let { "%+.2f m/s²".format(it.accelerationMps2) } ?: dash,
            )

            Section(stringResource(R.string.debug_section_limits))
            Text(speedLimitStatusText(state.speedLimitStatus))
            val limits = state.speedLimit
            ValueRow(
                stringResource(R.string.debug_road),
                limits?.road?.let { "${it.name ?: it.highway} (${it.id})" } ?: stringResource(R.string.debug_no_road),
            )
            ValueRow(stringResource(R.string.debug_current_limit), limits?.current?.let(::maxspeedText) ?: dash)
            ValueRow(
                stringResource(R.string.debug_next_limit),
                limits?.ahead?.let { "${it.limitKmh} km/h in ${meters(it.distanceM)}" } ?: dash,
            )

            Section(stringResource(R.string.debug_section_advice))
            val next = state.evaluation?.next
            ValueRow(stringResource(R.string.debug_event), next?.let { resources.describeEvent(it.event) } ?: dash)
            ValueRow(stringResource(R.string.debug_d_event), next?.let { meters(it.event.distanceM) } ?: dash)
            ValueRow(
                stringResource(R.string.debug_v_target),
                next?.let { "%.0f km/h".format(mpsToKmh(it.targetSpeedMps)) } ?: dash,
            )
            ValueRow(stringResource(R.string.debug_d_coast), next?.let { meters(it.coastDistanceM) } ?: dash)
            ValueRow(stringResource(R.string.debug_d_trigger), next?.let { meters(it.triggerDistanceM) } ?: dash)
            ValueRow(
                stringResource(R.string.debug_last_cue),
                state.lastCue?.let {
                    stringResource(R.string.debug_last_cue_value, resources.describeEvent(it.event), ago(it.atMillis))
                } ?: dash,
            )

            Section(stringResource(R.string.debug_voice))
            if (state.voiceLog.isEmpty()) Text(dash)
            state.voiceLog.forEach { Text(it, fontFamily = FontFamily.Monospace) }

            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.debug_close)) }
        }
    }
}

@Composable
private fun speedLimitStatusText(status: SpeedLimitStatus): String = when (status) {
    SpeedLimitStatus.Idle -> stringResource(R.string.limit_status_idle)
    SpeedLimitStatus.Loading -> stringResource(R.string.limit_status_loading)
    is SpeedLimitStatus.Ready -> stringResource(R.string.limit_status_ready, status.wayCount)
    is SpeedLimitStatus.Failed ->
        stringResource(R.string.limit_status_failed, status.reason, status.retryInMillis / 1000)
}

private fun maxspeedText(maxspeed: Maxspeed): String = when (maxspeed) {
    is Maxspeed.Limit -> "${maxspeed.kmh} km/h"
    Maxspeed.Unlimited -> "∞"
}

@Composable
private fun Section(title: String) {
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontFamily = FontFamily.Monospace)
    }
}

@Preview(showBackground = true)
@Composable
private fun DebugScreenPreview() {
    val nav = NavInfo(420, TurnType.TL)
    val driving = DrivingState(speedMps = 22.0, accelerationMps2 = 0.1)
    CoastHintTheme {
        DebugScreen(
            state = LiveState(
                running = true,
                osmAnd = OsmAndStatus.Connected("net.osmand.plus"),
                navigation = NavSample(nav, SystemClock.elapsedRealtime()),
                driving = driving,
                evaluation = CoastAdvisor().evaluate(driving, nav, null),
                voiceLog = listOf("Nach 400 Metern links abbiegen"),
            ),
            onClose = {},
        )
    }
}
