package io.github.dansparker.coasthint.output

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.github.dansparker.coasthint.core.Assessment
import io.github.dansparker.coasthint.settings.OutputSettings

/** Two short pulses. */
class VibrationCueOutput(context: Context) : CueOutput {
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    override suspend fun announce(cue: Assessment) {
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 100, 120), -1))
        }
    }
}

/** Combines tone, speech and vibration as configured in [OutputSettings]. */
class CueOutputs(private val context: Context) : CueOutput {
    private val tone = ToneCueOutput(context)
    private val vibration = VibrationCueOutput(context)

    // Only bound once speech is enabled; created right then so the first cue need not wait for it.
    private var speech: SpeechCueOutput? = null

    var settings = OutputSettings()
        set(value) {
            field = value
            if (value.speech && speech == null) speech = SpeechCueOutput(context)
        }

    override suspend fun announce(cue: Assessment) {
        val s = settings
        if (s.vibration) vibration.announce(cue)
        if (s.tone) tone.announce(cue)
        if (s.speech) speech?.announce(cue)
    }

    fun shutdown() {
        speech?.shutdown()
        speech = null
    }
}
