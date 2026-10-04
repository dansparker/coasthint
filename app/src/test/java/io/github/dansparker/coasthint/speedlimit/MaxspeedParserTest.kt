package io.github.dansparker.coasthint.speedlimit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class MaxspeedParserTest {

    @ParameterizedTest(name = "{0} → {1} km/h")
    @CsvSource(
        "50, 50",
        "130, 130",
        "' 70 ', 70",
        "50 km/h, 50",
        "50km/h, 50",
        "30 mph, 48",
        "70 mph, 113",
        "AT:urban, 50",
        "AT:rural, 100",
        "AT:motorway, 130",
        "DE:urban, 50",
        "DE:rural, 100",
        "walk, 7",
        "DE:zone30, 30",
        "AT:zone:30, 30",
        "'50;30', 30",
        "'none;80', 80",
    )
    fun `parses limits`(raw: String, expectedKmh: Int) {
        assertEquals(Maxspeed.Limit(expectedKmh), MaxspeedParser.parse(raw))
    }

    @ParameterizedTest
    @ValueSource(strings = ["none", "DE:motorway"])
    fun `parses unlimited`(raw: String) {
        assertEquals(Maxspeed.Unlimited, MaxspeedParser.parse(raw))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "  ", "signals", "variable", "fast", "XX:unknown", "-50"])
    fun `returns null for unusable values`(raw: String) {
        assertNull(MaxspeedParser.parse(raw))
    }

    @Test
    fun `returns null for missing tag`() {
        assertNull(MaxspeedParser.parse(null))
    }
}
