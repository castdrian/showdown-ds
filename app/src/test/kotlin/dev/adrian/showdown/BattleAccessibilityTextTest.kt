package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class BattleAccessibilityTextTest {
    @Test
    fun pokemonLabelIncludesHumanReadableGenderHealthAndBattleState() {
        assertEquals(
            "Garchomp, level 50, female, HP 0/183, Fainted",
            BattleAccessibilityText.pokemon("Garchomp", "50", "♀", "0/183", "FNT")
        )
    }

    @Test
    fun readyPokemonLabelOmitsInternalReadyState() {
        assertEquals(
            "Garchomp, level 50, male, HP 183/183",
            BattleAccessibilityText.pokemon("Garchomp", "50", "♂", "183/183", "READY")
        )
    }

    @Test
    fun inspectDetailsExposePossibleAbilitiesAndSpeedAccessibly() {
        val details = BattleSession.PokemonDetails(
            name = "Pikachu",
            types = listOf("ELECTRIC"),
            level = "50",
            gender = "",
            hp = "100/100",
            condition = "READY",
            ability = "Unknown ability",
            item = "No item",
            moves = listOf("Thunderbolt"),
            stats = "Spe 85–117 (before external modifiers)",
            possibleAbilities = listOf("Static", "Lightning Rod")
        )

        assertEquals(
            "Pokémon details for Pikachu. Pikachu, level 50, HP 100/100. types ELECTRIC. " +
                "possible abilities Static, Lightning Rod. item Unknown item. " +
                "stats Spe 85–117 (before external modifiers). moves Thunderbolt. Activate to close details.",
            BattleAccessibilityText.inspectDetails(details, "Pikachu", emptyList())
        )
    }

    @Test
    fun moveLabelIncludesActualPowerAccuracyTypePpCategoryAndDisabledState() {
        assertEquals(
            "Ice Beam, Ice type, power 90, accuracy 100 percent, 0 of 8 PP, Special, disabled",
            BattleAccessibilityText.move("Ice Beam", "ICE", 0, 8, "SPECIAL", "90", "100%", true)
        )
    }

    @Test
    fun moveLabelDescribesMissingMetricsWithoutReadingDashes() {
        assertEquals(
            "Protect, Normal type, power unavailable, accuracy unavailable, 10 of 10 PP, Status",
            BattleAccessibilityText.move("Protect", "NORMAL", 10, 10, "STATUS", "—", "—", false)
        )
    }
}
