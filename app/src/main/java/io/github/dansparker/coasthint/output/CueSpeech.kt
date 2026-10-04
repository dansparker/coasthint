package io.github.dansparker.coasthint.output

import kotlin.math.max
import kotlin.math.roundToInt

object CueSpeech {
    private const val STEP_M = 50

    /** Distance for announcements, rounded to 50 m steps and at least 50 m, so it sounds natural. */
    fun roundedDistanceM(distanceM: Double): Int = max(STEP_M, (distanceM / STEP_M).roundToInt() * STEP_M)
}
