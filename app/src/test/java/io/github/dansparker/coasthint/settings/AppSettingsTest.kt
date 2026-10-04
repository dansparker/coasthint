package io.github.dansparker.coasthint.settings

import io.github.dansparker.coasthint.core.CoastMeasurement
import io.github.dansparker.coasthint.core.CoastMode
import io.github.dansparker.coasthint.core.CoastSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AppSettingsTest {
    private fun m(a: Double, meanSpeed: Double = 20.0) = CoastMeasurement(a, meanSpeed + 2, meanSpeed - 2, 10.0, 0.98)

    @Test
    fun `manual decelerations apply without calibration`() {
        val settings = AppSettings(coast = CoastSettings(engineBrakingDecelerationMps2 = 0.7))
        assertEquals(0.7, settings.effectiveCoast.engineBrakingDecelerationMps2)
        assertNull(settings.effectiveCoast.engineBrakingQuadratic)
    }

    @Test
    fun `calibration average replaces the manual value of its mode only`() {
        val settings = AppSettings(
            coast = CoastSettings(engineBrakingDecelerationMps2 = 0.7, sailingDecelerationMps2 = 0.3),
            calibrations = mapOf(CoastMode.ENGINE_BRAKING to listOf(m(0.5), m(0.6))),
        )
        assertEquals(0.55, settings.effectiveCoast.engineBrakingDecelerationMps2, 1e-9)
        assertEquals(0.3, settings.effectiveCoast.sailingDecelerationMps2)
    }

    @Test
    fun `quadratic model is derived from calibrations`() {
        val measurements = listOf(10.0, 20.0, 30.0).map { v -> m(0.2 + 0.0005 * v * v, meanSpeed = v) }
        val settings = AppSettings(calibrations = mapOf(CoastMode.SAILING to measurements))
        assertNotNull(settings.effectiveCoast.sailingQuadratic)
        assertNull(settings.effectiveCoast.engineBrakingQuadratic)
    }

    @Test
    fun `measurements survive encoding`() {
        val measurements = listOf(CoastMeasurement(0.512345, 25.5, 14.25, 18.0, 0.97), m(0.3))
        val decoded = MeasurementCodec.decode(MeasurementCodec.encode(measurements))
        assertEquals(2, decoded.size)
        assertEquals(0.512345, decoded[0].decelerationMps2, 1e-9)
        assertEquals(14.25, decoded[0].endSpeedMps, 1e-9)
    }

    @Test
    fun `broken lines are skipped when decoding`() {
        assertEquals(1, MeasurementCodec.decode("0.5,20,15,10,0.9\ngarbage\n1,2").size)
        assertEquals(0, MeasurementCodec.decode(null).size)
        assertEquals(0, MeasurementCodec.decode("").size)
    }
}
