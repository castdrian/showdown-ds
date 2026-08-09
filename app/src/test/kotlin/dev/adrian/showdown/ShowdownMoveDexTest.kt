package dev.adrian.showdown

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

    @Test
    fun parsesSearchableNamesFromShowdownData() {
        val contents = "{\"tackle\":{\"name\":\"Tackle\"},\"icebeam\":{\"name\":\"Ice Beam\"}}"

        assertEquals(listOf("Ice Beam", "Tackle"), ShowdownMoveDex.parseMoveNames(contents))
        assertEquals(listOf("Ice Beam", "Tackle"), ShowdownMoveDex.parsePokemonNames(contents))
    }

    @Test
    fun parsesSearchableNamesFromShowdownScripts() {
        val contents = "exports.BattleItems = {leftovers:{name:\"Leftovers\"},choiceband:{name:\"Choice Band\"}}"

        assertEquals(listOf("Choice Band", "Leftovers"), ShowdownMoveDex.parseScriptNames(contents))
    }

    @Test
    fun ignoresMissingJsonAssets() {
        assertEquals(emptyMap<String, String>(), ShowdownMoveDex.parseMoveTypes(""))
        assertEquals(emptyMap<String, BattleSession.MoveInfo>(), ShowdownMoveDex.parseMoveInfo(""))
        assertEquals(emptyMap<String, List<String>>(), ShowdownMoveDex.parsePokemonTypes(""))
        assertEquals(emptyList<String>(), ShowdownMoveDex.parseMoveNames(""))
    }

    @Test
    fun parsesMovePowerAndAccuracyForPreviews() {
        val info = ShowdownMoveDex.parseMoveInfo(
            """{"splash":{"category":"Status","basePower":0,"accuracy":true},"thunder":{"category":"Special","basePower":110,"accuracy":70}}"""
        )

        assertEquals(BattleSession.MoveInfo("—", "—"), info["splash"])
        assertEquals(BattleSession.MoveInfo("110", "70"), info["thunder"])
    }
}
