package io.github.dansparker.coasthint.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TurnTypeTest {

    @Test
    fun `labels known turn types`() {
        assertEquals("TL", TurnType.label(TurnType.TL))
        assertEquals("RNLB", TurnType.label(TurnType.RNLB))
        assertEquals("C", TurnType.label(1))
    }

    @Test
    fun `unknown turn type gets a placeholder`() {
        assertEquals("?", TurnType.label(0))
        assertEquals("?", TurnType.label(99))
    }
}
