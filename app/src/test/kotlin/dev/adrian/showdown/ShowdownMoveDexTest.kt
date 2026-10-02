package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

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
    fun parsesOfficialBaseStatsForSpecies() {
        val baseStats = ShowdownMoveDex.parsePokemonBaseStats(
            """{"pikachu":{"baseStats":{"hp":35,"atk":55,"def":40,"spa":50,"spd":50,"spe":90}},"slowbro":{"baseStats":{"spe":30}}}"""
        )

        assertEquals(ShowdownStatPresentation.BaseStats(35, 55, 40, 50, 50, 90), baseStats["pikachu"])
        assertEquals(ShowdownStatPresentation.BaseStats(0, 0, 0, 0, 0, 30), baseStats["slowbro"])
    }

    @Test
    fun parsesBundledGenerationSpeciesOverrides() {
        val overrides = ShowdownMoveDex.parseGenerationSpeciesOverrides(
            """{"gen1":{"pikachu":{"baseStats":{"hp":35,"atk":55,"def":30,"spa":50,"spd":50,"spe":90},"abilities":{"0":"Static"},"types":["Electric"]}}}"""
        )

        assertEquals(
            BattleSpeciesOverride(
                ShowdownStatPresentation.BaseStats(35, 55, 30, 50, 50, 90),
                mapOf("0" to "static")
            ),
            overrides["gen1"]?.get("pikachu")
        )
    }

    @Test
    fun resolvesSpeciesOverridesFromCurrentGenerationBackToBattleGeneration() {
        val latestStats = ShowdownStatPresentation.BaseStats(35, 55, 40, 50, 50, 110)
        val overrides = mapOf(
            "gen9" to mapOf("pikachu" to BattleSpeciesOverride(latestStats, mapOf("0" to "static"))),
            "gen8" to mapOf("pikachu" to BattleSpeciesOverride(latestStats.copy(speed = 100), mapOf("0" to "lightningrod"))),
            "gen7" to mapOf("pikachu" to BattleSpeciesOverride(latestStats.copy(speed = 80), mapOf("0" to "static")))
        )

        val resolved = ShowdownMoveDex.resolveBattleSpeciesData(
            "pikachu",
            latestStats,
            mapOf("0" to "static", "H" to "lightningrod"),
            7,
            "[Gen 7] OU",
            overrides
        )

        assertEquals(80, resolved.baseStats?.speed)
        assertEquals(mapOf("0" to "static"), resolved.abilities)
    }

    @Test
    fun appliesFormatSpeciesOverridesAfterGenerationOverrides() {
        val latestStats = ShowdownStatPresentation.BaseStats(35, 55, 40, 50, 50, 110)
        val overrides = mapOf(
            "gen7" to mapOf("pikachu" to BattleSpeciesOverride(latestStats.copy(speed = 80), mapOf("0" to "static"))),
            "gen7letsgo" to mapOf("pikachu" to BattleSpeciesOverride(latestStats.copy(speed = 65), mapOf("0" to "noability")))
        )

        val resolved = ShowdownMoveDex.resolveBattleSpeciesData(
            "pikachu",
            latestStats,
            mapOf("0" to "static"),
            7,
            "[Gen 7 Let's Go] Random Battle",
            overrides
        )

        assertEquals(65, resolved.baseStats?.speed)
        assertEquals(mapOf("0" to "noability"), resolved.abilities)
    }

    @Test
    fun filtersPossibleAbilitiesByBattleGeneration() {
        val abilities = mapOf("0" to "overgrow", "H" to "contrary")

        assertTrue(ShowdownMoveDex.availableBattleAbilityIds(abilities, 2).isEmpty())
        assertEquals(listOf("overgrow"), ShowdownMoveDex.availableBattleAbilityIds(abilities, 4))
        assertEquals(listOf("overgrow", "contrary"), ShowdownMoveDex.availableBattleAbilityIds(abilities, 5))
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
    fun parsesContactFlagsForMeleeMoveAnimation() {
        val info = ShowdownMoveDex.parseMoveInfo(
            """{"tackle":{"category":"Physical","basePower":40,"accuracy":100,"flags":{"contact":1}},"earthquake":{"category":"Physical","basePower":100,"accuracy":100,"flags":{"contact":0}}}"""
        )

        assertTrue(info["tackle"]?.contact == true)
        assertFalse(info["earthquake"]?.contact == true)
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

    @Test
    fun buildsShowdownIdentifiersIndependentlyOfTheSystemLocale() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("icebeam", ShowdownMoveDex.moveId("Ice Beam"))
            assertEquals("electric", ShowdownMoveDex.moveId("Electric"))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
