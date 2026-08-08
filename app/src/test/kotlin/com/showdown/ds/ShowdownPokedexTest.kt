package com.showdown.ds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownPokedexTest {
    private val source = """
        {
          "bulbasaur": {
            "name": "Bulbasaur",
            "num": 1,
            "types": ["Grass", "Poison"],
            "abilities": {"0": "Overgrow", "H": "Chlorophyll"},
            "baseStats": {"hp": 45, "atk": 49, "def": 49},
            "heightm": 0.7,
            "weightkg": 6.9,
            "tier": "PU",
            "gen": 1,
            "color": "Green",
            "eggGroups": ["Monster", "Grass"],
            "evos": ["ivysaur"]
          },
          "pikachu": {
            "name": "Pikachu",
            "num": 25,
            "types": ["Electric"],
            "abilities": {"0": "Static", "H": "Lightning Rod"},
            "baseStats": {"hp": 35, "atk": 55, "def": 40}
          }
        }
    """.trimIndent()

    @Test
    fun parsesDetailsAndSortsByNationalNumber() {
        val entries = ShowdownPokedex.parse(source)

        assertEquals(listOf("Bulbasaur", "Pikachu"), entries.map { it.name })
        assertEquals(listOf("Grass", "Poison"), entries.first().types)
        assertEquals(listOf("Overgrow", "Chlorophyll"), entries.first().abilities)
        assertEquals(45, entries.first().baseStats["hp"])
        assertEquals(listOf("ivysaur"), entries.first().evolutions)
        assertEquals(0.7, entries.first().heightMeters!!, 0.001)
    }

    @Test
    fun searchesNamesAndIds() {
        val entries = ShowdownPokedex.parse(source)
        val pokedex = ShowdownPokedex(entries)

        assertEquals("Pikachu", pokedex.search("pika").single().name)
        assertEquals("Bulbasaur", pokedex.search("bulba").single().name)
        assertTrue(pokedex.search("missingno").isEmpty())
        assertNull(pokedex.find("Missingno"))
        pokedex.close()
    }

    @Test
    fun ignoresInvalidJson() {
        assertTrue(ShowdownPokedex.parse("not json").isEmpty())
    }
}
