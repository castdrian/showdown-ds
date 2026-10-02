package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownStatPresentationTest {
    @Test
    fun formatsExactStatsInReadableRowsWithSpeedVisible() {
        assertEquals(
            "Atk 81 · Def 65 · SpA 77 · SpD 78 · Spe 120",
            ShowdownStatPresentation.exactStats(mapOf("atk" to 81, "def" to 65, "spa" to 77, "spd" to 78, "spe" to 120))
        )
    }

    @Test
    fun formatsGenerationOneSpecialStatAsOneValue() {
        assertEquals(
            "Atk 81 · Def 65 · Spc 77 · Spe 120",
            ShowdownStatPresentation.exactStats(
                mapOf("atk" to 81, "def" to 65, "spa" to 77, "spd" to 78, "spe" to 120),
                1
            )
        )
    }

    @Test
    fun matchesShowdownRandomBattleSpeedEstimate() {
        assertEquals(
            "Spe 60 or 86 (before external modifiers)",
            ShowdownStatPresentation.speedRange(55, 50, 9, true)
        )
    }

    @Test
    fun matchesShowdownUnrevealedNatureSpeedRange() {
        assertEquals(
            "Spe 54–75–107–117 (before external modifiers)",
            ShowdownStatPresentation.speedRange(55, 50, 9, false)
        )
    }

    @Test
    fun matchesShowdownComputerGeneratedTeamSpeedEstimate() {
        assertEquals(
            "Spe 117–75–107–117 (before external modifiers)",
            ShowdownStatPresentation.speedRange(55, 50, 9, false, "[Gen 9] Computer-Generated Teams")
        )
    }

    @Test
    fun matchesShowdownLetsGoSpeedRange() {
        assertEquals(
            "Spe 55–76–83 (before external modifiers)",
            ShowdownStatPresentation.speedRange(55, 50, 7, false, "[Gen 7] Let's Go, Pikachu!")
        )
    }

    @Test
    fun matchesShowdownRandomLetsGoSpeedEstimate() {
        assertEquals(
            "Spe 61 or 76 (before external modifiers)",
            ShowdownStatPresentation.speedRange(55, 50, 7, true, "[Gen 7] Let's Go Random Battle")
        )
    }

    @Test
    fun matchesShowdownChampionsSpeedRange() {
        assertEquals(
            "Spe 67–75–107–117 (before external modifiers)",
            ShowdownStatPresentation.speedRange(55, 50, 9, false, "[Gen 9] Champions")
        )
    }

    @Test
    fun matchesShowdownRandomChampionsSpeedEstimate() {
        assertEquals(
            "Spe 75 or 86 (before external modifiers)",
            ShowdownStatPresentation.speedRange(55, 50, 9, true, "[Gen 9] Champions Random Battle")
        )
    }

    @Test
    fun matchesShowdownLegacyGenerationSpeedEstimate() {
        assertEquals(
            "Spe 106 (before stat stage changes)",
            ShowdownStatPresentation.speedRange(55, 50, 2, true)
        )
    }

    @Test
    fun appliesScalemonsSpeedRuleBeforeShowingARandomBattleEstimate() {
        assertEquals(
            "Spe 183 or 209 (before external modifiers)",
            ShowdownStatPresentation.speedRange(
                ShowdownStatPresentation.BaseStats(35, 55, 40, 50, 50, 90),
                50,
                9,
                true,
                "[Gen 9] Scalemons"
            )
        )
    }

    @Test
    fun appliesFlippedSpeedRuleBeforeShowingAStandardSpeedRange() {
        assertEquals(
            "Spe 36–55–87–95 (before external modifiers)",
            ShowdownStatPresentation.speedRange(
                ShowdownStatPresentation.BaseStats(35, 55, 40, 50, 50, 90),
                50,
                9,
                false,
                "[Gen 9] Flipped"
            )
        )
    }

    @Test
    fun doublesOnlyLowBaseStatTotalSpeciesIn350Cup() {
        assertEquals(
            "Spe 185 or 211 (before external modifiers)",
            ShowdownStatPresentation.speedRange(
                ShowdownStatPresentation.BaseStats(35, 55, 40, 50, 50, 90),
                50,
                9,
                true,
                "[Gen 9] 350 Cup"
            )
        )
    }

    @Test
    fun leavesSpeedUnavailableForFusionsWithoutFusionDexData() {
        assertEquals(
            "Speed range unavailable for this format",
            ShowdownStatPresentation.speedRange(
                ShowdownStatPresentation.BaseStats(35, 55, 40, 50, 50, 90),
                50,
                9,
                false,
                "[Gen 9] Frantic Fusions"
            )
        )
    }
}
