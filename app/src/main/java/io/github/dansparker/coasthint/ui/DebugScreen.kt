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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dansparker.coasthint.R
import io.github.dansparker.coasthint.core.NavInfo
import io.github.dansparker.coasthint.core.TurnType
import io.github.dansparker.coasthint.osmand.NavSample
import io.github.dansparker.coasthint.osmand.OsmAndStatus
import kotlinx.coroutines.delay

@Composable
fun DebugScreen(
    status: OsmAndStatus,
    navigation: NavSample?,
    voiceLog: List<String>,
    onClose: () -> Unit,
) {
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            delay(500)
            value = SystemClock.elapsedRealtime()
        }
    }
    val dash = stringResource(R.string.debug_none)

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.debug_title), style = MaterialTheme.typography.headlineSmall)
            Text(osmAndStatusText(status))
            HorizontalDivider()

            val info = navigation?.info
            ValueRow(stringResource(R.string.debug_distance_to), info?.let { "${it.distanceToM} m" } ?: dash)
            ValueRow(
                stringResource(R.string.debug_turn_type),
                info?.let { "${TurnType.label(it.turnType)} (${it.turnType})" } ?: dash,
            )
            ValueRow(stringResource(R.string.debug_left_side), info?.let { it.isLeftSide.toString() } ?: dash)
            ValueRow(
                stringResource(R.string.debug_age),
                navigation?.let { "%.1f s".format((now - it.receivedAtMillis) / 1000.0) } ?: dash,
            )
            HorizontalDivider()

            Text(stringResource(R.string.debug_voice), style = MaterialTheme.typography.titleSmall)
            if (voiceLog.isEmpty()) Text(dash)
            voiceLog.forEach { Text(it, fontFamily = FontFamily.Monospace) }

            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.debug_close)) }
        }
    }
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
    CoastHintTheme {
        DebugScreen(
            status = OsmAndStatus.Connected("net.osmand.plus"),
            navigation = NavSample(NavInfo(420, TurnType.TL), SystemClock.elapsedRealtime()),
            voiceLog = listOf("Nach 400 Metern links abbiegen"),
            onClose = {},
        )
    }
}
