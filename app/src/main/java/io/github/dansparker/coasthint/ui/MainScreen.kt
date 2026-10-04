package io.github.dansparker.coasthint.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dansparker.coasthint.R
import io.github.dansparker.coasthint.osmand.OsmAndStatus

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    var showDebug by rememberSaveable { mutableStateOf(false) }
    val status by viewModel.osmAnd.status.collectAsStateWithLifecycle()

    if (showDebug) {
        BackHandler { showDebug = false }
        val navigation by viewModel.osmAnd.navigation.collectAsStateWithLifecycle()
        val voiceLog by viewModel.voiceLog.collectAsStateWithLifecycle()
        DebugScreen(status, navigation, voiceLog, onClose = { showDebug = false })
    } else {
        StatusScreen(status, onOpenDebug = { showDebug = true })
    }
}

@Composable
private fun StatusScreen(status: OsmAndStatus, onOpenDebug: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = osmAndStatusText(status),
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(onClick = onOpenDebug) {
                Text(stringResource(R.string.debug_open))
            }
        }
    }
}

@Composable
fun osmAndStatusText(status: OsmAndStatus): String = when (status) {
    OsmAndStatus.Stopped -> stringResource(R.string.osmand_stopped)
    OsmAndStatus.NotInstalled -> stringResource(R.string.osmand_not_installed)
    OsmAndStatus.Connecting -> stringResource(R.string.osmand_connecting)
    is OsmAndStatus.Connected -> stringResource(R.string.osmand_connected, status.packageName)
    is OsmAndStatus.Retrying ->
        stringResource(R.string.osmand_retrying, status.reason, status.delayMillis / 1000)
}

@Preview(showBackground = true)
@Composable
private fun StatusScreenPreview() {
    CoastHintTheme { StatusScreen(OsmAndStatus.Connected("net.osmand.plus"), onOpenDebug = {}) }
}
