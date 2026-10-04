package io.github.dansparker.coasthint.core

/** Smoothed own speed and its rate of change. */
data class DrivingState(
    val speedMps: Double,
    val accelerationMps2: Double,
)

/**
 * Smooths raw speed samples with an exponential moving average and derives dv/dt from the
 * smoothed values. Samples after a long gap restart the filter so stale values do not leak in.
 */
class SpeedFilter(
    private val alpha: Double = 0.3,
    private val maxGapMillis: Long = 5_000,
) {
    private var lastTimeMillis: Long? = null
    private var speed = 0.0
    private var acceleration = 0.0

    fun update(timeMillis: Long, rawSpeedMps: Double): DrivingState {
        val lastTime = lastTimeMillis
        val dtMillis = if (lastTime == null) null else timeMillis - lastTime
        when {
            dtMillis == null || dtMillis > maxGapMillis -> {
                speed = rawSpeedMps
                acceleration = 0.0
                lastTimeMillis = timeMillis
            }
            dtMillis <= 0 -> Unit // duplicate or out-of-order sample
            else -> {
                val smoothed = alpha * rawSpeedMps + (1 - alpha) * speed
                acceleration = (smoothed - speed) / (dtMillis / 1000.0)
                speed = smoothed
                lastTimeMillis = timeMillis
            }
        }
        return DrivingState(speed, acceleration)
    }
}
