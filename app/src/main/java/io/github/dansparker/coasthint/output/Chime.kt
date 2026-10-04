package io.github.dansparker.coasthint.output

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Synthesises the cue sound as 16-bit mono PCM. Pure Kotlin so it can be unit-tested. */
object Chime {
    data class Note(val frequencyHz: Double, val durationMs: Int)

    /** Two descending tones (C6 → G5), soft attack and decay. */
    val DESCENDING = listOf(Note(1046.5, 150), Note(784.0, 280))

    private const val ATTACK_MS = 8.0
    private const val RELEASE_MS = 25.0
    private const val DECAY_PER_S = 4.0

    fun render(notes: List<Note>, sampleRate: Int, amplitude: Double = 0.6): ShortArray {
        val out = ShortArray(notes.sumOf { samplesFor(it, sampleRate) })
        var offset = 0
        for (note in notes) {
            val n = samplesFor(note, sampleRate)
            val attack = ATTACK_MS / 1000 * sampleRate
            val release = RELEASE_MS / 1000 * sampleRate
            for (i in 0 until n) {
                val t = i.toDouble() / sampleRate
                val envelope = min(1.0, i / attack) * min(1.0, (n - 1 - i) / release) * exp(-DECAY_PER_S * t)
                val value = amplitude * envelope * sin(2 * PI * note.frequencyHz * t)
                out[offset + i] = (value * Short.MAX_VALUE).roundToInt().toShort()
            }
            offset += n
        }
        return out
    }

    private fun samplesFor(note: Note, sampleRate: Int) = note.durationMs * sampleRate / 1000
}
