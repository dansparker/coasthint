package io.github.dansparker.coasthint.output

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CueSpeechTest {

    @ParameterizedTest(name = "{0} m → {1} m")
    @CsvSource("412, 400", "425, 450", "374, 350", "60, 50", "10, 50", "0, 50", "1234, 1250")
    fun `distances are rounded to 50 m`(distance: Double, expected: Int) {
        assertEquals(expected, CueSpeech.roundedDistanceM(distance))
    }
}
