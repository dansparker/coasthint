package io.github.dansparker.coasthint.output

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ChimeTest {
    private val sampleRate = 44_100

    @Test
    fun `length matches the note durations`() {
        val pcm = Chime.render(Chime.DESCENDING, sampleRate)
        assertEquals((150 + 280) * sampleRate / 1000, pcm.size)
    }

    @Test
    fun `starts and ends silent to avoid clicks`() {
        val pcm = Chime.render(Chime.DESCENDING, sampleRate)
        assertTrue(abs(pcm.first().toInt()) < 50)
        assertTrue(abs(pcm.last().toInt()) < 50)
        // Also silent at the note boundary
        val boundary = 150 * sampleRate / 1000
        assertTrue(abs(pcm[boundary - 1].toInt()) < 50)
        assertTrue(abs(pcm[boundary].toInt()) < 50)
    }

    @Test
    fun `peak stays below the amplitude`() {
        val pcm = Chime.render(Chime.DESCENDING, sampleRate, amplitude = 0.6)
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue(peak <= 0.6 * Short.MAX_VALUE + 1)
        assertTrue(peak > 0.3 * Short.MAX_VALUE)
    }

    @Test
    fun `tones have the requested pitch and descend`() {
        val pcm = Chime.render(Chime.DESCENDING, sampleRate)
        val boundary = 150 * sampleRate / 1000
        val first = frequency(pcm.copyOfRange(0, boundary))
        val second = frequency(pcm.copyOfRange(boundary, pcm.size))
        assertEquals(1046.5, first, 15.0)
        assertEquals(784.0, second, 15.0)
    }

    /** Estimates the frequency from the number of sign changes. */
    private fun frequency(samples: ShortArray): Double {
        val signs = samples.filter { it.toInt() != 0 }.map { it > 0 }
        val crossings = signs.zipWithNext().count { (a, b) -> a != b }
        return crossings / 2.0 / (samples.size.toDouble() / sampleRate)
    }
}
