package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownBattleDetailTablesTest {
    private val pokedex = """{"tornadus":{"types":["Flying"],"baseStats":{"hp":79,"atk":115,"def":70,"spa":125,"spd":80,"spe":111},"abilities":{"0":"Prankster","H":"Defiant"}}}"""

    @Test
    fun ShowdownMoveDex_ParseBattleDetailTables_IncludesSpeciesTypesFromThePokedex() {
        val tables = ShowdownMoveDex.parseBattleDetailTables(pokedex, abilityContents = "", itemContents = "", generationOverridesContents = "")

        assertEquals(listOf("FLYING"), tables.pokemonTypes["tornadus"])
    }

    @Test
    fun ShowdownMoveDex_ParseBattleDetailTables_KeepsAbilitySlotsAndBaseStats() {
        val tables = ShowdownMoveDex.parseBattleDetailTables(pokedex, abilityContents = "", itemContents = "", generationOverridesContents = "")

        assertEquals("prankster", tables.pokemonAbilitySlots["tornadus"]?.get("0"))
        assertTrue(tables.pokemonBaseStats.containsKey("tornadus"))
    }

    @Test
    fun ShowdownMoveDex_ParseBattleDetailTables_ReadsSortedAbilityNamesFromTheScript() {
        val abilities = """exports.BattleAbilities = {"prankster":{name:"Prankster"},"defiant":{name:"Defiant"}}"""

        val tables = ShowdownMoveDex.parseBattleDetailTables(pokedex, abilities, itemContents = "", generationOverridesContents = "")

        assertEquals(listOf("Defiant", "Prankster"), tables.abilityNames)
    }

    @Test
    fun ShowdownMoveDex_ParseBattleDetailTables_ReadsSortedItemNamesFromTheScript() {
        val items = """exports.BattleItems = {"leftovers":{name:"Leftovers"},"assaultvest":{name:"Assault Vest"}}"""

        val tables = ShowdownMoveDex.parseBattleDetailTables(pokedex, abilityContents = "", itemContents = items, generationOverridesContents = "")

        assertEquals(listOf("Assault Vest", "Leftovers"), tables.itemNames)
    }
}
