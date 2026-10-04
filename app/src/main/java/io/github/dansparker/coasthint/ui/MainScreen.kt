package io.github.dansparker.coasthint.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import io.github.dansparker.coasthint.service.LiveState

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    var showDebug by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        // Notifications are optional; precise location is required.
        permissionDenied = result[Manifest.permission.ACCESS_FINE_LOCATION] != true
        if (!permissionDenied) viewModel.start()
    }
    val requestStart = {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    if (showDebug) {
        BackHandler { showDebug = false }
        DebugScreen(state, onClose = { showDebug = false })
    } else {
        StatusScreen(
            state = state,
            permissionDenied = permissionDenied,
            onStart = requestStart,
            onStop = viewModel::stop,
            onTestTone = viewModel::playTestTone,
            onOpenDebug = { showDebug = true },
        )
    }
}

@Composable
private fun StatusScreen(
    state: LiveState,
    permissionDenied: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTestTone: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(if (state.running) R.string.service_running else R.string.service_stopped),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.running) Text(osmAndStatusText(state.osmAnd))
            if (permissionDenied) {
                Text(
                    stringResource(R.string.permission_location_denied),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.running) {
                    Button(onClick = onStop) { Text(stringResource(R.string.action_stop)) }
                } else {
                    Button(onClick = onStart) { Text(stringResource(R.string.action_start)) }
                }
                OutlinedButton(onClick = onTestTone) { Text(stringResource(R.string.action_test_tone)) }
            }
            OutlinedButton(onClick = onOpenDebug) { Text(stringResource(R.string.debug_open)) }
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
    CoastHintTheme {
        StatusScreen(
            state = LiveState(running = true, osmAnd = OsmAndStatus.Connected("net.osmand.plus")),
            permissionDenied = false,
            onStart = {},
            onStop = {},
            onTestTone = {},
            onOpenDebug = {},
        )
    }
}
