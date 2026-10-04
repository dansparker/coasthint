package io.github.dansparker.coasthint.output

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.github.dansparker.coasthint.R
import io.github.dansparker.coasthint.core.Assessment
import io.github.dansparker.coasthint.core.TurnCategory
import io.github.dansparker.coasthint.core.TurnType
import io.github.dansparker.coasthint.core.UpcomingEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** Speaks e.g. "Gas weg – Kurve in 400 Metern" with transient, duckable audio focus. */
class SpeechCueOutput(context: Context) : CueOutput {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val initialized = CompletableDeferred<Boolean>()
    @Volatile private var pending: CompletableDeferred<Unit>? = null
    private val tts = TextToSpeech(appContext) { status -> initialized.complete(status == TextToSpeech.SUCCESS) }

    init {
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    pending?.complete(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    pending?.complete(Unit)
                }
            },
        )
    }

    override suspend fun announce(cue: Assessment) = speak(text(cue))

    private fun text(cue: Assessment): String {
        val distance = CueSpeech.roundedDistanceM(cue.event.distanceM)
        return when (val event = cue.event) {
            is UpcomingEvent.SpeedLimit -> appContext.getString(R.string.speech_limit, event.limitKmh, distance)
            is UpcomingEvent.Maneuver ->
                if (TurnType.category(event.turnType) == TurnCategory.ROUNDABOUT) {
                    appContext.getString(R.string.speech_roundabout, distance)
                } else {
                    appContext.getString(R.string.speech_turn, distance)
                }
        }
    }

    private suspend fun speak(text: String) {
        val ready = withTimeoutOrNull(INIT_TIMEOUT_MILLIS) { initialized.await() } ?: false
        if (!ready) return
        tts.language = Locale.GERMAN
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        try {
            val done = CompletableDeferred<Unit>().also { pending = it }
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) == TextToSpeech.SUCCESS) {
                withTimeoutOrNull(SPEAK_TIMEOUT_MILLIS) { done.await() }
            }
        } finally {
            pending = null
            audioManager.abandonAudioFocusRequest(focus)
        }
    }

    fun shutdown() {
        tts.shutdown()
    }

    private companion object {
        const val UTTERANCE_ID = "coast-cue"
        const val INIT_TIMEOUT_MILLIS = 3_000L
        const val SPEAK_TIMEOUT_MILLIS = 10_000L
    }
}
