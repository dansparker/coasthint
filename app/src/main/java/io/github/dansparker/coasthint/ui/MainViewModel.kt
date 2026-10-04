package io.github.dansparker.coasthint.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.dansparker.coasthint.R
import io.github.dansparker.coasthint.core.CoastMode
import io.github.dansparker.coasthint.log.TripLogger
import io.github.dansparker.coasthint.output.ToneCueOutput
import io.github.dansparker.coasthint.roaddb.RoadDbException
import io.github.dansparker.coasthint.service.CoastHintRuntime
import io.github.dansparker.coasthint.service.CoastHintService
import io.github.dansparker.coasthint.service.LiveState
import io.github.dansparker.coasthint.settings.AppSettings
import io.github.dansparker.coasthint.settings.SettingsRepository
import io.github.dansparker.coasthint.speedlimit.OfflineRoads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    val state: StateFlow<LiveState> = CoastHintRuntime.state

    private val repository = SettingsRepository(app)
    val settings: StateFlow<AppSettings?> = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _trips = MutableStateFlow<List<File>>(emptyList())
    val trips: StateFlow<List<File>> = _trips.asStateFlow()

    private val tone = ToneCueOutput(app)

    fun start() = CoastHintService.start(app)

    fun stop() = CoastHintService.stop(app)

    fun playTestTone() {
        viewModelScope.launch { tone.playChime() }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun startCalibration() = CoastHintService.startCalibration(app)

    fun stopCalibration() = CoastHintService.stopCalibration(app)

    fun clearCalibration(mode: CoastMode) {
        viewModelScope.launch { repository.clearMeasurements(mode) }
    }

    private val offlineRoads = OfflineRoads.get(app)
    private val _offline = MutableStateFlow(OfflineDataState())
    val offline: StateFlow<OfflineDataState> = _offline.asStateFlow()

    fun refreshOffline() {
        viewModelScope.launch { _offline.value = readOffline() }
    }

    fun importOffline(uri: Uri) {
        viewModelScope.launch {
            _offline.value = _offline.value.copy(busy = true, message = null, error = null)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val input = app.contentResolver.openInputStream(uri) ?: throw RoadDbException("cannot open file")
                    input.use(offlineRoads::import)
                }
            }
            _offline.value = readOffline().copy(
                message = result.getOrNull()?.let { app.getString(R.string.offline_imported, it) },
                error = result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName },
            )
        }
    }

    fun deleteOffline(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { offlineRoads.delete(name) }
            _offline.value = readOffline()
        }
    }

    private suspend fun readOffline(): OfflineDataState =
        withContext(Dispatchers.IO) { OfflineDataState(installed = offlineRoads.installed()) }

    fun refreshTrips() {
        viewModelScope.launch {
            _trips.value = withContext(Dispatchers.IO) { TripLogger.listTrips(CoastHintService.tripDirectory(app)) }
        }
    }

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        return Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
