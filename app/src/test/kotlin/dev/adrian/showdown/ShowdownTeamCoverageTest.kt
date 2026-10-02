package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownTeamCoverageTest {
    @Test
    fun parsesShowdownTypeChartAndIgnoresNonTypeDamageEffects() {
        val chart = ShowdownMoveDex.parseTypeChart(
            "exports.BattleTypeChart = {fire:{damageTaken:{brn:3,Bug:2,Ground:1,Water:1,Stellar:0}},steel:{damageTaken:{psn:3,Fighting:1,Ghost:0}}}"
        )

        assertEquals(
            mapOf("BUG" to 2, "GROUND" to 1, "WATER" to 1, "STELLAR" to 0),
            chart["fire"]
        )
        assertEquals(mapOf("FIGHTING" to 1, "GHOST" to 0), chart["steel"])
        assertEquals(emptyMap<String, Map<String, Int>>(), ShowdownMoveDex.parseTypeChart(""))
        val supportedTypeNames = ShowdownMoveDex.typeChartNames()
        val supportedTypes = supportedTypeNames.map { ShowdownMoveDex.moveId(it) }
        val completeEffects = supportedTypeNames.joinToString(",") { "$it:0" }
        val completeSource = supportedTypeNames.joinToString(",") {
            "${ShowdownMoveDex.moveId(it)}:{damageTaken:{$completeEffects}}"
        }
        val completeChart = ShowdownMoveDex.parseTypeChart(completeSource)
        assertEquals(supportedTypes.toSet(), completeChart.keys)
        assertTrue(ShowdownMoveDex.isCompleteTypeChart(completeChart))
        assertFalse(ShowdownMoveDex.isCompleteTypeChart(completeChart - "stellar"))
        assertFalse(ShowdownMoveDex.isCompleteTypeChart(completeChart + ("fire" to emptyMap())))
        assertFalse(ShowdownMoveDex.isCompleteTypeChart(completeChart + ("fire" to (completeChart.getValue("fire") - "WATER"))))
    }

    @Test
    fun combinesDualTypeEffectsAndCountsEachPokemonOnce() {
        val results = ShowdownTeamCoverage.analyze(
            listOf(ShowdownTeamCoverage.Member(listOf("Fire", "Flying"), "")),
            typeChart,
            9
        ).typeResults

        assertEquals(1, result(results, "Rock").weaknesses)
        assertEquals(1, result(results, "Ground").immunities)
        assertEquals(1, result(results, "Fighting").resistances)
        assertEquals(1, result(results, "Electric").weaknesses)
        assertEquals(0, result(results, "Ghost").weaknesses)
        assertEquals(0, result(results, "Ghost").resistances)
        assertEquals(0, result(results, "Ghost").immunities)
    }

    @Test
    fun appliesHistoricalTypeChartChanges() {
        val genOne = ShowdownTeamCoverage.analyze(
            listOf(
                ShowdownTeamCoverage.Member(listOf("Psychic"), ""),
                ShowdownTeamCoverage.Member(listOf("Bug"), ""),
                ShowdownTeamCoverage.Member(listOf("Poison"), ""),
                ShowdownTeamCoverage.Member(listOf("Fire"), "")
            ),
            historicalTypeChart,
            1
        ).typeResults

        assertEquals(1, result(genOne, "Ghost").immunities)
        assertEquals(1, result(genOne, "Poison").weaknesses)
        assertEquals(1, result(genOne, "Bug").weaknesses)
        assertEquals(0, result(genOne, "Ice").resistances)
        assertFalse(genOne.any { it.attackingType == "DARK" })
        assertFalse(genOne.any { it.attackingType == "STEEL" })
        assertFalse(genOne.any { it.attackingType == "FAIRY" })

        val genTwo = ShowdownTeamCoverage.analyze(
            listOf(ShowdownTeamCoverage.Member(listOf("Fire", "Steel"), "")),
            historicalTypeChart,
            2
        ).typeResults

        assertEquals(1, result(genTwo, "Ice").resistances)
        assertEquals(1, result(genTwo, "Ghost").resistances)
        assertEquals(1, result(genTwo, "Dark").resistances)
    }

    @Test
    fun exposesTypesOnlyInTheirIntroducedGenerations() {
        val genFive = ShowdownTeamCoverage.analyze(emptyList(), typeChart, 5).typeResults
        val genSix = ShowdownTeamCoverage.analyze(emptyList(), typeChart, 6).typeResults
        val genEight = ShowdownTeamCoverage.analyze(emptyList(), typeChart, 8).typeResults
        val genNine = ShowdownTeamCoverage.analyze(emptyList(), typeChart, 9).typeResults

        assertFalse(genFive.any { it.attackingType == "FAIRY" })
        assertTrue(genSix.any { it.attackingType == "FAIRY" })
        assertFalse(genEight.any { it.attackingType == "STELLAR" })
        assertTrue(genNine.any { it.attackingType == "STELLAR" })
    }

    @Test
    fun readsGenerationsFromFormatIdsAndLabels() {
        assertEquals(7, ShowdownTeamCoverage.generation("gen7ou"))
        assertEquals(7, ShowdownTeamCoverage.generation("Gen 7 OU"))
        assertEquals(7, ShowdownTeamCoverage.generation("[Gen 7] OU"))
        assertEquals(9, ShowdownTeamCoverage.generation("random battle"))
    }

    @Test
    fun appliesAbilitiesOnlyInGenerationsWhereTheirEffectsExist() {
        val cases = listOf(
            AbilityCase("Levitate", "Ground", "Normal", 2, 3, "neutral", "immune"),
            AbilityCase("Earth Eater", "Ground", "Normal", 8, 9, "neutral", "immune"),
            AbilityCase("Flash Fire", "Fire", "Normal", 2, 3, "neutral", "immune"),
            AbilityCase("Well-Baked Body", "Fire", "Normal", 8, 9, "neutral", "immune"),
            AbilityCase("Primordial Sea", "Fire", "Normal", 5, 6, "neutral", "immune"),
            AbilityCase("Desolate Land", "Water", "Normal", 5, 6, "neutral", "immune"),
            AbilityCase("Dry Skin", "Water", "Normal", 3, 4, "neutral", "immune"),
            AbilityCase("Volt Absorb", "Electric", "Normal", 2, 3, "neutral", "immune"),
            AbilityCase("Motor Drive", "Electric", "Normal", 3, 4, "neutral", "immune"),
            AbilityCase("Lightning Rod", "Electric", "Normal", 4, 5, "neutral", "immune"),
            AbilityCase("Water Absorb", "Water", "Normal", 2, 3, "neutral", "immune"),
            AbilityCase("Storm Drain", "Water", "Normal", 4, 5, "neutral", "immune"),
            AbilityCase("Sap Sipper", "Grass", "Normal", 4, 5, "neutral", "immune"),
            AbilityCase("Thick Fat", "Fire", "Ice", 2, 3, "weak", "neutral"),
            AbilityCase("Heatproof", "Fire", "Steel", 3, 4, "weak", "neutral"),
            AbilityCase("Water Bubble", "Fire", "Grass", 6, 7, "weak", "neutral"),
            AbilityCase("Fluffy", "Fire", "Normal", 6, 7, "neutral", "weak"),
            AbilityCase("Purifying Salt", "Ghost", "Psychic", 8, 9, "weak", "neutral"),
            AbilityCase("Delta Stream", "Electric", "Flying", 5, 6, "weak", "neutral"),
            AbilityCase("Wonder Guard", "Normal", "Normal", 2, 3, "neutral", "immune")
        )

        cases.forEach { case ->
            val member = ShowdownTeamCoverage.Member(listOf(case.defendingType), case.ability)
            assertEquals(case.ability, case.resultBefore, case.result(case.beforeGeneration, member))
            assertEquals(case.ability, case.resultAfter, case.result(case.activeGeneration, member))
        }
    }

    @Test
    fun appliesFieldAbilitiesAcrossTheTeamAndReportsConditionalCoverage() {
        val deltaStream = ShowdownTeamCoverage.analyze(
            listOf(
                ShowdownTeamCoverage.Member(listOf("Flying"), "Delta Stream"),
                ShowdownTeamCoverage.Member(listOf("Flying"), "")
            ),
            typeChart,
            9
        )
        assertEquals(0, result(deltaStream.typeResults, "Rock").weaknesses)
        assertEquals("Assumes Delta Stream is active.", deltaStream.environmentalNote)

        val primordialSea = ShowdownTeamCoverage.analyze(
            listOf(
                ShowdownTeamCoverage.Member(listOf("Normal"), "Primordial Sea"),
                ShowdownTeamCoverage.Member(listOf("Water"), "")
            ),
            typeChart,
            9
        )
        assertEquals(2, result(primordialSea.typeResults, "Fire").immunities)
        assertEquals("Assumes Primordial Sea is active.", primordialSea.environmentalNote)

        val desolateLand = ShowdownTeamCoverage.analyze(
            listOf(
                ShowdownTeamCoverage.Member(listOf("Normal"), "Desolate Land"),
                ShowdownTeamCoverage.Member(listOf("Fire"), "")
            ),
            typeChart,
            9
        )
        assertEquals(2, result(desolateLand.typeResults, "Water").immunities)
        assertEquals("Assumes Desolate Land is active.", desolateLand.environmentalNote)

        val conflictingWeather = ShowdownTeamCoverage.analyze(
            listOf(
                ShowdownTeamCoverage.Member(listOf("Normal"), "Primordial Sea"),
                ShowdownTeamCoverage.Member(listOf("Normal"), "Desolate Land")
            ),
            typeChart,
            9
        )
        assertEquals(0, result(conflictingWeather.typeResults, "Fire").immunities)
        assertEquals(0, result(conflictingWeather.typeResults, "Water").immunities)
        assertEquals("Conflicting field-setting abilities; field effects are omitted.", conflictingWeather.environmentalNote)
    }

    private fun AbilityCase.result(generation: Int, member: ShowdownTeamCoverage.Member): String {
        val results = ShowdownTeamCoverage.analyze(listOf(member), typeChart, generation).typeResults
        val matchup = result(results, attackingType)
        return when {
            matchup.immunities > 0 -> "immune"
            matchup.weaknesses > 0 -> "weak"
            matchup.resistances > 0 -> "resist"
            else -> "neutral"
        }
    }

    private fun result(results: List<ShowdownTeamCoverage.TypeResult>, attackingType: String) =
        results.single { it.attackingType.equals(attackingType, true) }

    private data class AbilityCase(
        val ability: String,
        val attackingType: String,
        val defendingType: String,
        val beforeGeneration: Int,
        val activeGeneration: Int,
        val resultBefore: String,
        val resultAfter: String
    )

    private companion object {
        val typeChart = mapOf(
            "normal" to mapOf("FIGHTING" to 1, "GHOST" to 3),
            "fire" to mapOf("WATER" to 1, "GROUND" to 1, "ROCK" to 1, "ICE" to 2),
            "flying" to mapOf("ELECTRIC" to 1, "ICE" to 1, "ROCK" to 1, "GROUND" to 3, "FIGHTING" to 2),
            "ice" to mapOf("FIRE" to 1),
            "grass" to mapOf("FIRE" to 1),
            "psychic" to mapOf("GHOST" to 1),
            "steel" to mapOf("FIRE" to 1, "DARK" to 0, "GHOST" to 0)
        )
        val historicalTypeChart = mapOf(
            "psychic" to mapOf("GHOST" to 1),
            "bug" to mapOf("POISON" to 2),
            "poison" to mapOf("BUG" to 2),
            "fire" to mapOf("ICE" to 2),
            "steel" to mapOf("DARK" to 0, "GHOST" to 0)
        )
    }
}
