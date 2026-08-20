package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun parsesSpeciesAbilitiesInOfficialSlotOrder() {
        val abilities = ShowdownMoveDex.parsePokemonAbilities(
            """{"pikachu":{"abilities":{"H":"Lightning Rod","0":"Static"}},"eevee":{"abilities":{"0":"Run Away","1":"Adaptability","H":"Anticipation"}}}"""
        )

        assertEquals(listOf("static", "lightningrod"), abilities["pikachu"])
        assertEquals(listOf("runaway", "adaptability", "anticipation"), abilities["eevee"])
    }

    @Test
    fun parsesSpeciesAbilitySlotsForOfficialPackedTeams() {
        val slots = ShowdownMoveDex.parsePokemonAbilitySlots(
            """{"pikachu":{"abilities":{"H":"Lightning Rod","0":"Static"}},"eevee":{"abilities":{"0":"Run Away","1":"Adaptability","H":"Anticipation"}}}"""
        )

        assertEquals(mapOf("H" to "lightningrod", "0" to "static"), slots["pikachu"])
        assertEquals("adaptability", slots["eevee"]?.get("1"))
    }

    @Test
    fun parsesOfficialSpeciesLearnsetsWithoutReadingMoveDataAsSpecies() {
        val learnsets = ShowdownMoveDex.parseLearnsets(
            "exports.BattleLearnsets = {pikachu:{learnset:{thunderbolt:[\"9M\"],volttackle:[\"9E\"]}},eevee:{learnset:{tackle:[\"9L1\"]}}}"
        )

        assertEquals(listOf("thunderbolt", "volttackle"), learnsets["pikachu"])
        assertEquals(listOf("tackle"), learnsets["eevee"])
        assertFalse(learnsets.containsKey("thunderbolt"))
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
        assertEquals(BattleSession.MoveInfo("110", "70", "Special"), info["thunder"])
    }

    @Test
    fun identifiesFixedGimmickMovePower() {
        val info = ShowdownMoveDex.parseMoveInfo(
            """{"catastropika":{"isZ":"pikaniumz","category":"Physical","basePower":210,"accuracy":true},"maxflare":{"isMax":true,"category":"Physical","basePower":100,"accuracy":true},"gmaxdrumsolo":{"isMax":"Rillaboom","category":"Physical","basePower":160,"accuracy":true}}"""
        )

        assertTrue(info["catastropika"]?.fixedGimmickPower == true)
        assertFalse(info["maxflare"]?.fixedGimmickPower == true)
        assertTrue(info["gmaxdrumsolo"]?.fixedGimmickPower == true)
    }

    @Test
    fun keepsFocusBlastAsAFightingMove() {
        val types = ShowdownMoveDex.parseMoveTypes(
            """{"focusblast":{"name":"Focus Blast","type":"Fighting"}}"""
        )

        assertEquals("FIGHTING", types["focusblast"])
    }

    @Test
    fun exposesStellarForTeraTypesWithoutAddingItToHiddenPowerTypes() {
        assertEquals(16, ShowdownMoveDex.typeNames().size)
        assertFalse(ShowdownMoveDex.typeNames().contains("Fairy"))
        assertFalse(ShowdownMoveDex.typeNames().contains("Normal"))
        assertFalse(ShowdownMoveDex.typeNames().contains("Stellar"))
        assertTrue(ShowdownMoveDex.teraTypeNames().contains("Stellar"))
    }
}
