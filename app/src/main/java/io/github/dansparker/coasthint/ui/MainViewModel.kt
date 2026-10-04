package io.github.dansparker.coasthint.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.dansparker.coasthint.osmand.OsmAndConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

// The OsmAnd connection lives here until the foreground service takes it over (step 4).
class MainViewModel(app: Application) : AndroidViewModel(app) {
    val osmAnd = OsmAndConnection(app, viewModelScope).also { it.start() }

    /** Most recent OsmAnd voice prompts, newest first. */
    val voiceLog: StateFlow<List<String>> = osmAnd.voiceMessages
        .runningFold(emptyList<String>()) { log, prompts -> (prompts + log).take(VOICE_LOG_SIZE) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    override fun onCleared() {
        osmAnd.stop()
    }

    private companion object {
        const val VOICE_LOG_SIZE = 10
    }
}
