package io.github.dansparker.coasthint.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.math.sin

class CalibrationTest {

    /** v(t) = v0 − a·t sampled at 1 Hz, with optional deterministic noise. */
    private fun coast(v0: Double, a: Double, seconds: Int, noise: Double = 0.0) = List(seconds + 1) { t ->
        CalibrationSample(t * 1000L, v0 - a * t + noise * sin(t * 1.7))
    }

    private fun measurement(meanSpeed: Double, a: Double) =
        CoastMeasurement(a, meanSpeed + 2, meanSpeed - 2, 10.0, 0.99)

    @Test
    fun `clean coast gives the deceleration`() {
        val result = Calibration.analyze(coast(v0 = 25.0, a = 0.55, seconds = 15)) as CalibrationResult.Success
        assertEquals(0.55, result.measurement.decelerationMps2, 1e-9)
        assertEquals(25.0, result.measurement.startSpeedMps, 1e-9)
        assertEquals(25.0 - 0.55 * 15, result.measurement.endSpeedMps, 1e-9)
        assertEquals(15.0, result.measurement.durationS)
    }

    @Test
    fun `moderate GPS noise is averaged out`() {
        val result = Calibration.analyze(coast(v0 = 25.0, a = 0.5, seconds = 20, noise = 0.3)) as CalibrationResult.Success
        assertEquals(0.5, result.measurement.decelerationMps2, 0.03)
    }

    @Test
    fun `too short run is rejected`() {
        assertEquals(
            CalibrationResult.Failure(CalibrationResult.Reason.TOO_SHORT),
            Calibration.analyze(coast(v0 = 25.0, a = 0.6, seconds = 3)),
        )
    }

    @Test
    fun `run without slowing down is rejected`() {
        assertEquals(
            CalibrationResult.Failure(CalibrationResult.Reason.NOT_SLOWING_DOWN),
            Calibration.analyze(coast(v0 = 20.0, a = -0.2, seconds = 10)),
        )
    }

    @Test
    fun `run with little slowdown is rejected`() {
        assertEquals(
            CalibrationResult.Failure(CalibrationResult.Reason.TOO_LITTLE_SLOWDOWN),
            Calibration.analyze(coast(v0 = 20.0, a = 0.1, seconds = 10)),
        )
    }

    @Test
    fun `very noisy run is rejected`() {
        assertEquals(
            CalibrationResult.Failure(CalibrationResult.Reason.TOO_NOISY),
            Calibration.analyze(coast(v0 = 20.0, a = 0.3, seconds = 10, noise = 3.0)),
        )
    }

    @Test
    fun `standing samples at the end are ignored`() {
        val samples = coast(v0 = 15.0, a = 1.0, seconds = 12) + List(10) { CalibrationSample(13_000L + it * 1000, 0.0) }
        val result = Calibration.analyze(samples) as CalibrationResult.Success
        assertEquals(1.0, result.measurement.decelerationMps2, 1e-9)
    }

    @Test
    fun `average of several measurements`() {
        assertNull(Calibration.averageDeceleration(emptyList()))
        assertEquals(0.5, Calibration.averageDeceleration(listOf(measurement(20.0, 0.4), measurement(25.0, 0.6))))
    }

    @Test
    fun `quadratic fit recovers rolling and air resistance`() {
        val truth = CoastModel.Quadratic(c0 = 0.25, c2 = 0.0008)
        val measurements = listOf(10.0, 18.0, 25.0, 32.0).map { measurement(it, truth.decelerationAt(it)) }
        val fit = Calibration.fitQuadratic(measurements)
        assertNotNull(fit)
        assertEquals(0.25, fit!!.c0, 1e-9)
        assertEquals(0.0008, fit.c2, 1e-12)
    }

    @Test
    fun `quadratic fit needs enough measurements and speed spread`() {
        assertNull(Calibration.fitQuadratic(listOf(measurement(10.0, 0.3), measurement(30.0, 0.9))))
        assertNull(Calibration.fitQuadratic(listOf(measurement(20.0, 0.5), measurement(21.0, 0.5), measurement(22.0, 0.52))))
    }

    @Test
    fun `implausible quadratic fit is rejected`() {
        // Deceleration falling with speed would mean negative drag
        val measurements = listOf(measurement(10.0, 0.9), measurement(20.0, 0.6), measurement(30.0, 0.3))
        assertNull(Calibration.fitQuadratic(measurements))
    }
}
