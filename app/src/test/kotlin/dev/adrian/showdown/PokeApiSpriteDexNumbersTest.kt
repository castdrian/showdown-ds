package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PokeApiSpriteDexNumbersTest {
    @Test
    fun parsesBaseSpeciesAndFormsFromShowdownPokedex() {
        val numbers = PokeApiSpriteDexNumbers.parse(
            """{"ogerpon":{"num":1017},"ogerponwellspring":{"num":1017},"invalid":{"num":0}}"""
        )

        assertEquals(1017, numbers["ogerpon"])
        assertEquals(1017, numbers["ogerponwellspring"])
        assertNull(numbers["invalid"])
    }

    @Test
    fun resolvesTheFirstMatchingSpeciesName() {
        val numbers = mapOf("ogerpon" to 1017, "ogerponwellspring" to 1017)

        assertEquals(1017, PokeApiSpriteDexNumbers.resolve(listOf("Ogerpon-Wellspring", "Ogerpon"), numbers))
    }

    @Test
    fun returnsNoNumberWhenShowdownDexHasNoMatch() {
        assertNull(PokeApiSpriteDexNumbers.resolve(listOf("Missingmon"), emptyMap()))
    }
}
