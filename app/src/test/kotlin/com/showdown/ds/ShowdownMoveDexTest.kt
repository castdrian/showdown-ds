package com.showdown.ds

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownMoveDexTest {
    @Test
    fun parsesOfficialPokemonTypesWithShowdownIdentifiers() {
        val types = ShowdownMoveDex.parsePokemonTypes(
            """{"rotomwash":{"types":["Electric","Water"]},"charizardmegax":{"types":["Fire","Dragon"]}}"""
        )

        assertEquals(listOf("ELECTRIC", "WATER"), types["rotomwash"])
        assertEquals(listOf("FIRE", "DRAGON"), types["charizardmegax"])
        assertEquals("nidoranf", ShowdownMoveDex.speciesId("Nidoran♀"))
    }
}
