package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownBattleLogFilterTest {
    @Test
    fun removesDiagnosticsWithoutDroppingNeighboringBattleMessages() {
        val entries = ShowdownBattleLogFilter.visibleEntries(
            "Go! Ogerpon!<br />Error parsing: Turn message must be a heading.<br />Ogerpon used Ivy Cudgel!<br />Error: sanitizeHTML requires caja<br /> at Battle.log (https://play.pokemonshowdown.com/js/battle.js:1364:16)"
        )

        assertEquals(listOf("Go! Ogerpon!", "Ogerpon used Ivy Cudgel!"), entries)
    }

    @Test
    fun removesInternalTimestampLinesAndPreservesMarkupForTheExistingSanitizer() {
        val entries = ShowdownBattleLogFilter.visibleEntries(
            "Battle started!<br /><small>[12:35] hidden</small><br />Turn 1"
        )

        assertEquals(listOf("Battle started!", "Turn 1"), entries)
    }

    @Test
    fun preservesUserFacingErrorMessages() {
        val entries = ShowdownBattleLogFilter.visibleEntries(
            "Error: Your move is disabled.<br />The opposing Pokémon used Protect!"
        )

        assertEquals(
            listOf("Error: Your move is disabled.", "The opposing Pokémon used Protect!"),
            entries
        )
    }

    @Test
    fun removesAccountRegistrationControlsFromTheBattleTranscript() {
        val entries = ShowdownBattleLogFilter.visibleEntries(
            "<div>Register an account to protect your ladder rating!</div><button>Register</button>"
        )

        assertEquals(emptyList<String>(), entries)
    }
}
