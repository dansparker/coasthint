package io.github.dansparker.coasthint.core

import kotlin.math.abs
import kotlin.math.ln

/** How the car decelerates while coasting. */
sealed interface CoastModel {
    /** Distance needed to coast from [v1Mps] down to [v2Mps], without reaction time. */
    fun distanceM(v1Mps: Double, v2Mps: Double): Double

    data class Constant(val decelerationMps2: Double) : CoastModel {
        override fun distanceM(v1Mps: Double, v2Mps: Double): Double =
            (v1Mps * v1Mps - v2Mps * v2Mps) / (2 * decelerationMps2)
    }

    /**
     * Rolling resistance plus air drag: a(v) = c0 + c2 · v².
     * Integrating v / a(v) dv from v2 to v1 gives ln((c0 + c2·v1²) / (c0 + c2·v2²)) / (2 · c2).
     */
    data class Quadratic(val c0: Double, val c2: Double) : CoastModel {
        fun decelerationAt(vMps: Double): Double = c0 + c2 * vMps * vMps

        override fun distanceM(v1Mps: Double, v2Mps: Double): Double {
            if (abs(c2) < 1e-9) return Constant(c0).distanceM(v1Mps, v2Mps)
            return ln(decelerationAt(v1Mps) / decelerationAt(v2Mps)) / (2 * c2)
        }
    }
}
