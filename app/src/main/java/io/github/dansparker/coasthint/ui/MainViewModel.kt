package io.github.dansparker.coasthint.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.dansparker.coasthint.output.ToneCueOutput
import io.github.dansparker.coasthint.service.CoastHintRuntime
import io.github.dansparker.coasthint.service.CoastHintService
import io.github.dansparker.coasthint.service.LiveState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    val state: StateFlow<LiveState> = CoastHintRuntime.state

    private val tone = ToneCueOutput(app)

    fun start() = CoastHintService.start(app)

    fun stop() = CoastHintService.stop(app)

    fun playTestTone() {
        viewModelScope.launch { tone.playChime() }
    }
}
