package dev.adrian.showdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleFeedMessageIdentityTest {
    @Test
    fun ignoresShowdownPunctuationAndWhitespaceDifferences() {
        assertTrue(BattleFeedMessageIdentity.matches(" Pikachu restored HP!  ", "Pikachu recovered health."))
        assertTrue(BattleFeedMessageIdentity.matches("Pikachu had its HP restored.", "Pikachu restored health!"))
    }

    @Test
    fun matchesOfficialDamageWordingToProtocolDamagePercentages() {
        assertTrue(
            BattleFeedMessageIdentity.matchesProtocolFallback(
                "(iluvgermany's Perrserker lost 25% of its health!)",
                "The opposing Perrserker lost some of its HP!"
            )
        )
    }

    @Test
    fun matchesOfficialLeftoversWordingToProtocolHealingFallback() {
        assertTrue(
            BattleFeedMessageIdentity.matchesProtocolFallback(
                "iluvgermany's Salazzle restored HP using its Leftovers!",
                "The opposing Salazzle restored a little HP using its Leftovers!"
            )
        )
    }

    @Test
    fun matchesOfficialWhiteHerbWordingToProtocolItemFallback() {
        assertTrue(
            BattleFeedMessageIdentity.matchesProtocolFallback(
                "(Minior used its White Herb!)",
                "Minior returned its stats to normal using its White Herb!"
            )
        )
    }

    @Test
    fun matchesPerspectiveWordingForKnownTrainerRegardlessOfActionVerb() {
        assertTrue(
            BattleFeedMessageIdentity.matchesProtocolFallback(
                "iluvgermany's Salazzle must do an encore!",
                "The opposing Salazzle must do an encore!",
                listOf("T0RcH3D", "iluvgermany")
            )
        )
    }

    @Test
    fun doesNotTreatDifferentDamagePercentagesAsTheSameFeedMessage() {
        assertFalse(
            BattleFeedMessageIdentity.matches(
                "Perrserker lost 25% of its health!",
                "Perrserker lost 50% of its health!"
            )
        )
    }

    @Test
    fun matchesTrainerPerspectiveToOfficialOpponentStatWording() {
        assertTrue(
            BattleFeedMessageIdentity.matchesProtocolFallback(
                "iluvgermany's Perrserker's Defense fell!",
                "The opposing Perrserker's Defense fell!"
            )
        )
    }

    @Test
    fun matchesOfficialSwitchWordingAcrossPlayerAndOpponentPerspectives() {
        val trainers = listOf("Casual dot exe", "qiuescent")

        assertTrue(
            BattleFeedMessageIdentity.matchesProtocolFallback(
                "Casual dot exe sent out Gliscor!",
                "Go! Gliscor!",
                trainers
            )
        )
        assertTrue(
            BattleFeedMessageIdentity.matchesProtocolFallback(
                "Casual dot exe sent out Minior (Minior-Yellow)!",
                "Go! Minior (Minior-Red)!",
                trainers
            )
        )
        assertTrue(
            BattleFeedMessageIdentity.matchesProtocolFallback(
                "qiuescent sent out Swanna!",
                "The opposing Swanna was sent out!",
                trainers
            )
        )
        assertTrue(
            BattleFeedMessageIdentity.matchesProtocolFallback(
                "Casual dot exe withdrew Gliscor!",
                "Gliscor, come back!",
                trainers
            )
        )
        assertTrue(
            BattleFeedMessageIdentity.matchesProtocolFallback(
                "qiuescent withdrew Swanna!",
                "The opposing Swanna was withdrawn!",
                trainers
            )
        )
        assertTrue(
            BattleFeedMessageIdentity.matchesProtocolFallback(
                "Casual dot exe withdrew Dewgong!",
                "Dewgong went back to Casual dot exe!",
                trainers
            )
        )
    }

    @Test
    fun matchesTrainerPerspectiveForMultiWordPokemonNames() {
        assertTrue(
            BattleFeedMessageIdentity.matchesProtocolFallback(
                "trainer's Great Tusk used Headlong Rush!",
                "The opposing Great Tusk used Headlong Rush!"
            )
        )
    }

    @Test
    fun keepsDifferentPokemonBattleEventsDistinct() {
        assertFalse(
            BattleFeedMessageIdentity.matches(
                "Perrserker lost 25% of its health!",
                "Lugia lost 25% of its health!"
            )
        )
    }

    @Test
    fun keepsPokemonEventsFromDifferentBattleSidesDistinct() {
        assertFalse(
            BattleFeedMessageIdentity.matches(
                "Pikachu used Thunderbolt!",
                "The opposing Pikachu used Thunderbolt!"
            )
        )
    }

    @Test
    fun keepsDifferentBattleEventsDistinct() {
        assertFalse(BattleFeedMessageIdentity.matches("Pikachu used Thunderbolt!", "Pikachu used Tackle!"))
    }
}
