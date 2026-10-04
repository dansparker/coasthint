package io.github.dansparker.coasthint.ui

import android.Manifest
import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dansparker.coasthint.R
import io.github.dansparker.coasthint.core.kmhToMps
import io.github.dansparker.coasthint.osmand.OsmAndStatus
import io.github.dansparker.coasthint.service.LiveState

private enum class Screen { STATUS, SETTINGS, CALIBRATION, TRIPS, OFFLINE, DEBUG }

/** Settings may only be changed while (almost) standing still. */
private val SETTINGS_LOCK_SPEED_MPS = kmhToMps(5.0)

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    var screen by rememberSaveable { mutableStateOf(Screen.STATUS) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importOffline(uri)
    }

    if (screen != Screen.STATUS) BackHandler { screen = Screen.STATUS }
    val back = { screen = Screen.STATUS }
    val current = settings
    when (screen) {
        Screen.STATUS -> StatusScreen(
            state = state,
            permissionDenied = permissionDenied,
            onStart = requestStart,
            onStop = viewModel::stop,
            onTestTone = viewModel::playTestTone,
            onNavigate = { screen = it },
        )
        Screen.SETTINGS -> if (current != null) {
            val moving = state.running && (state.driving?.speedMps ?: 0.0) >= SETTINGS_LOCK_SPEED_MPS
            SettingsScreen(current, locked = moving, onUpdate = viewModel::updateSettings, onBack = back)
        }
        Screen.CALIBRATION -> if (current != null) {
            CalibrationScreen(
                state = state,
                settings = current,
                onStart = viewModel::startCalibration,
                onStop = viewModel::stopCalibration,
                onClear = viewModel::clearCalibration,
                onBack = back,
            )
        }
        Screen.TRIPS -> {
            LaunchedEffect(Unit) { viewModel.refreshTrips() }
            val shareTitle = stringResource(R.string.trips_share_title)
            TripsScreen(
                trips = trips,
                onShare = { file ->
                    context.startActivity(Intent.createChooser(viewModel.shareIntent(file), shareTitle))
                },
                onBack = back,
            )
        }
        Screen.OFFLINE -> {
            LaunchedEffect(Unit) { viewModel.refreshOffline() }
            OfflineDataScreen(
                state = offline,
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onDelete = viewModel::deleteOffline,
                onBack = back,
            )
        }
        Screen.DEBUG -> DebugScreen(state, onClose = back)
    }
}

@Composable
private fun StatusScreen(
    state: LiveState,
    permissionDenied: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTestTone: () -> Unit,
    onNavigate: (Screen) -> Unit,
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
            OutlinedButton(onClick = { onNavigate(Screen.SETTINGS) }) { Text(stringResource(R.string.nav_settings)) }
            OutlinedButton(onClick = { onNavigate(Screen.CALIBRATION) }) { Text(stringResource(R.string.nav_calibration)) }
            OutlinedButton(onClick = { onNavigate(Screen.TRIPS) }) { Text(stringResource(R.string.nav_trips)) }
            OutlinedButton(onClick = { onNavigate(Screen.OFFLINE) }) { Text(stringResource(R.string.nav_offline)) }
            OutlinedButton(onClick = { onNavigate(Screen.DEBUG) }) { Text(stringResource(R.string.debug_open)) }
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
            onNavigate = {},
        )
    }
}
