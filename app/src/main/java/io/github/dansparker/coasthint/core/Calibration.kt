package io.github.dansparker.coasthint.core

data class CalibrationSample(val elapsedMillis: Long, val speedMps: Double)

/** One "Ausrollen messen" run, reduced to a constant deceleration over its speed range. */
data class CoastMeasurement(
    val decelerationMps2: Double,
    val startSpeedMps: Double,
    val endSpeedMps: Double,
    val durationS: Double,
    val rSquared: Double,
) {
    val meanSpeedMps: Double get() = (startSpeedMps + endSpeedMps) / 2
}

sealed interface CalibrationResult {
    data class Success(val measurement: CoastMeasurement) : CalibrationResult
    data class Failure(val reason: Reason) : CalibrationResult

    enum class Reason { TOO_SHORT, TOO_LITTLE_SLOWDOWN, NOT_SLOWING_DOWN, TOO_NOISY }
}

/**
 * Derives the coast deceleration from logged v(t): a linear regression gives the slope,
 * the deceleration is its negative. Several measurements are averaged; with enough spread in
 * speed they also yield the quadratic model a(v) = c0 + c2 · v².
 */
object Calibration {
    private const val MIN_DURATION_S = 5.0
    private const val MIN_SLOWDOWN_MPS = 2.0
    private const val MIN_R_SQUARED = 0.8

    /** Samples below this speed (standing, creeping) are ignored. */
    private const val MIN_SPEED_MPS = 3.0
    private const val MIN_QUADRATIC_MEASUREMENTS = 3

    /** The squared mean speeds must differ by at least this much (≈ 30 vs. 60 km/h). */
    private const val MIN_SPEED_SQUARED_SPREAD = 150.0

    fun analyze(samples: List<CalibrationSample>): CalibrationResult {
        val usable = samples.filter { it.speedMps >= MIN_SPEED_MPS }.sortedBy { it.elapsedMillis }
        if (usable.size < 3) return CalibrationResult.Failure(CalibrationResult.Reason.TOO_SHORT)
        val t0 = usable.first().elapsedMillis
        val ts = usable.map { (it.elapsedMillis - t0) / 1000.0 }
        val vs = usable.map { it.speedMps }
        val duration = ts.last()
        if (duration < MIN_DURATION_S) return CalibrationResult.Failure(CalibrationResult.Reason.TOO_SHORT)

        val fit = linearFit(ts, vs) ?: return CalibrationResult.Failure(CalibrationResult.Reason.TOO_SHORT)
        val deceleration = -fit.slope
        if (deceleration <= 0) return CalibrationResult.Failure(CalibrationResult.Reason.NOT_SLOWING_DOWN)
        val start = fit.intercept
        val end = fit.intercept + fit.slope * duration
        if (start - end < MIN_SLOWDOWN_MPS) {
            return CalibrationResult.Failure(CalibrationResult.Reason.TOO_LITTLE_SLOWDOWN)
        }
        if (fit.rSquared < MIN_R_SQUARED) return CalibrationResult.Failure(CalibrationResult.Reason.TOO_NOISY)
        return CalibrationResult.Success(CoastMeasurement(deceleration, start, end, duration, fit.rSquared))
    }

    /** Mean deceleration of all measurements, or null if there are none. */
    fun averageDeceleration(measurements: List<CoastMeasurement>): Double? =
        measurements.takeIf { it.isNotEmpty() }?.map { it.decelerationMps2 }?.average()

    /** Fits a(v) = c0 + c2 · v² through the measurements; null if the data does not support it. */
    fun fitQuadratic(measurements: List<CoastMeasurement>): CoastModel.Quadratic? {
        if (measurements.size < MIN_QUADRATIC_MEASUREMENTS) return null
        val x = measurements.map { it.meanSpeedMps * it.meanSpeedMps }
        if (x.max() - x.min() < MIN_SPEED_SQUARED_SPREAD) return null
        val fit = linearFit(x, measurements.map { it.decelerationMps2 }) ?: return null
        if (fit.intercept <= 0 || fit.slope < 0) return null
        return CoastModel.Quadratic(c0 = fit.intercept, c2 = fit.slope)
    }

    data class LinearFit(val slope: Double, val intercept: Double, val rSquared: Double)

    fun linearFit(x: List<Double>, y: List<Double>): LinearFit? {
        require(x.size == y.size)
        val n = x.size
        if (n < 2) return null
        val meanX = x.average()
        val meanY = y.average()
        var sxx = 0.0
        var sxy = 0.0
        var syy = 0.0
        for (i in 0 until n) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            sxx += dx * dx
            sxy += dx * dy
            syy += dy * dy
        }
        if (sxx == 0.0) return null
        val slope = sxy / sxx
        val rSquared = if (syy == 0.0) 1.0 else (sxy * sxy) / (sxx * syy)
        return LinearFit(slope, meanY - slope * meanX, rSquared)
    }
}
