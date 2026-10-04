package io.github.dansparker.coasthint.output

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import io.github.dansparker.coasthint.core.Assessment
import kotlinx.coroutines.delay

/** Tells the driver to lift off the throttle. */
interface CueOutput {
    suspend fun announce(cue: Assessment)
}

/**
 * Plays [Chime.DESCENDING] as navigation guidance with transient, duckable audio focus, so
 * OsmAnd's own voice prompts are only lowered, never cut off.
 */
class ToneCueOutput(context: Context) : CueOutput {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val pcm = Chime.render(Chime.DESCENDING, SAMPLE_RATE)
    private val durationMillis = pcm.size * 1000L / SAMPLE_RATE

    override suspend fun announce(cue: Assessment) = playChime()

    suspend fun playChime() {
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        // Denied e.g. during a phone call; then stay silent.
        if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            delay(durationMillis + TAIL_MILLIS)
        } finally {
            track.release()
            audioManager.abandonAudioFocusRequest(focus)
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val TAIL_MILLIS = 80L
    }
}
