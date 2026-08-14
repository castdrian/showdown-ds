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

    @Test
    fun preservesEntriesSeparatedByShowdownBlockMarkup() {
        val entries = ShowdownBattleLogFilter.visibleEntries(
            "<div><strong>Gholdengo</strong> used Make It Rain!</div><div>The opposing team lost 30% of its health.</div>"
        )

        assertEquals(
            listOf("Gholdengo used Make It Rain!", "The opposing team lost 30% of its health."),
            entries
        )
    }

    @Test
    fun removesDebugAndTimestampRowsWithoutDroppingTheBattleEventsAroundThem() {
        val entries = ShowdownBattleLogFilter.visibleEntries(
            "<div>Turn 2</div><div><small style=\"color:#999\">[DEBUG] internal parser state</small></div><div><small>[12:35] internal timestamp</small></div><div>Gholdengo used Make It Rain!</div>"
        )

        assertEquals(listOf("Turn 2", "Gholdengo used Make It Rain!"), entries)
    }

    @Test
    fun removesOnlyTheRegistrationControlWhenItSharesAnAddDivWithOtherRows() {
        val entries = ShowdownBattleLogFilter.visibleEntries(
            "<div>Battle started!</div><div>Register an account to protect your ladder rating!<button>Register</button></div><div>Go! Pikachu!</div>"
        )

        assertEquals(listOf("Battle started!", "Go! Pikachu!"), entries)
    }
}
